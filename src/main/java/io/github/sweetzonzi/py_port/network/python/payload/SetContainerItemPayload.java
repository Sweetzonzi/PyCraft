package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.github.sweetzonzi.py_port.network.python.infrastructure.PyContext;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyHandleResult;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayload;
import io.github.sweetzonzi.py_port.network.python.infrastructure.PyPayloadType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.concurrent.CompletableFuture;

public record SetContainerItemPayload(
        ResourceLocation level,
        int x,
        int y,
        int z,
        int slot,
        ResourceLocation item,
        int count
) implements PyPayload {

    public static final Codec<SetContainerItemPayload> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            ResourceLocation.CODEC
                                    .fieldOf("level")
                                    .forGetter(SetContainerItemPayload::level),

                            Codec.INT
                                    .fieldOf("x")
                                    .forGetter(SetContainerItemPayload::x),

                            Codec.INT
                                    .fieldOf("y")
                                    .forGetter(SetContainerItemPayload::y),

                            Codec.INT
                                    .fieldOf("z")
                                    .forGetter(SetContainerItemPayload::z),

                            Codec.INT
                                    .fieldOf("slot")
                                    .forGetter(SetContainerItemPayload::slot),

                            ResourceLocation.CODEC
                                    .fieldOf("item")
                                    .forGetter(SetContainerItemPayload::item),

                            Codec.INT
                                    .fieldOf("count")
                                    .forGetter(SetContainerItemPayload::count)
                    ).apply(instance, SetContainerItemPayload::new)
            );

    public static final PyPayloadType<SetContainerItemPayload> TYPE =
            new PyPayloadType<>(
                    "set_container_item",
                    CODEC
            );

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(
            SetContainerItemPayload payload,
            PyContext context
    ) {
        var server = context.getServer();

        if (server == null) {
            return PyHandleResult.fail(
                    "Server is not running"
            );
        }

        CompletableFuture<JsonObject> future =
                new CompletableFuture<>();

        server.execute(() -> {
            try {
                // 1. 获取维度
                var levelKey = ResourceKey.create(
                        Registries.DIMENSION,
                        payload.level()
                );

                var level = server.getLevel(levelKey);

                if (level == null) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Level " +
                                            payload.level() +
                                            " not found"
                            )
                    );
                    return;
                }

                // 2. 获取目标位置的方块实体
                BlockPos pos = new BlockPos(
                        payload.x(),
                        payload.y(),
                        payload.z()
                );

                BlockEntity blockEntity =
                        level.getBlockEntity(pos);

                if (blockEntity == null) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "No block entity found at " + pos
                            )
                    );
                    return;
                }

                // 3. 判断方块实体是否为容器
                if (!(blockEntity instanceof Container container)) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Block at " + pos +
                                            " is not a container"
                            )
                    );
                    return;
                }

                // 4. 检查槽位是否合法
                if (payload.slot() < 0 ||
                        payload.slot() >= container.getContainerSize()) {

                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Invalid slot " +
                                            payload.slot() +
                                            ". Container size is " +
                                            container.getContainerSize()
                            )
                    );
                    return;
                }

                // 5. 检查物品是否存在
                Item item = BuiltInRegistries.ITEM
                        .getOptional(payload.item())
                        .orElse(null);

                if (item == null) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Unknown item: " +
                                            payload.item()
                            )
                    );
                    return;
                }

                // 6. 检查数量
                if (payload.count() <= 0) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Count must be greater than 0"
                            )
                    );
                    return;
                }

                ItemStack stack = new ItemStack(
                        item,
                        payload.count()
                );

                if (payload.count() > stack.getMaxStackSize()) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Count " +
                                            payload.count() +
                                            " exceeds maximum stack size " +
                                            stack.getMaxStackSize()
                            )
                    );
                    return;
                }

                /*
                 * setItem 会直接覆盖该槽位原来的物品。
                 *
                 * 例如：
                 * slot 0 原来有苹果，
                 * 调用后会被钻石替换。
                 */
                container.setItem(
                        payload.slot(),
                        stack
                );

                // 通知 Minecraft 保存方块实体变化
                blockEntity.setChanged();

                JsonObject data = new JsonObject();

                data.addProperty(
                        "level",
                        payload.level().toString()
                );

                data.addProperty(
                        "x",
                        payload.x()
                );

                data.addProperty(
                        "y",
                        payload.y()
                );

                data.addProperty(
                        "z",
                        payload.z()
                );

                data.addProperty(
                        "slot",
                        payload.slot()
                );

                data.addProperty(
                        "item",
                        payload.item().toString()
                );

                data.addProperty(
                        "count",
                        payload.count()
                );

                data.addProperty(
                        "container_size",
                        container.getContainerSize()
                );

                future.complete(data);

            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return PyHandleResult.success(
                    future.get()
            );
        } catch (Exception e) {
            Throwable cause = e.getCause();

            return PyHandleResult.fail(
                    cause != null
                            ? cause.getMessage()
                            : e.getMessage()
            );
        }
    }
}