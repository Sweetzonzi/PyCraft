package io.github.sweetzonzi.py_port.network.python.payload;

import com.google.gson.JsonArray;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.concurrent.CompletableFuture;

public record GetContainerItemsPayload(
        ResourceLocation level,
        int x,
        int y,
        int z
) implements PyPayload {

    public static final Codec<GetContainerItemsPayload> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            ResourceLocation.CODEC
                                    .fieldOf("level")
                                    .forGetter(GetContainerItemsPayload::level),

                            Codec.INT
                                    .fieldOf("x")
                                    .forGetter(GetContainerItemsPayload::x),

                            Codec.INT
                                    .fieldOf("y")
                                    .forGetter(GetContainerItemsPayload::y),

                            Codec.INT
                                    .fieldOf("z")
                                    .forGetter(GetContainerItemsPayload::z)
                    ).apply(instance, GetContainerItemsPayload::new)
            );

    public static final PyPayloadType<GetContainerItemsPayload> TYPE =
            new PyPayloadType<>(
                    "get_container_items",
                    CODEC
            );

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(
            GetContainerItemsPayload payload,
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

                // 3. 检查是否为容器
                if (!(blockEntity instanceof Container container)) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Block at " + pos +
                                            " is not a container"
                            )
                    );
                    return;
                }

                JsonArray items = new JsonArray();

                int totalItemCount = 0;

                // 4. 遍历所有槽位
                for (
                        int slot = 0;
                        slot < container.getContainerSize();
                        slot++
                ) {
                    ItemStack stack =
                            container.getItem(slot);

                    // 不返回空槽位
                    if (stack.isEmpty()) {
                        continue;
                    }

                    ResourceLocation itemId =
                            BuiltInRegistries.ITEM.getKey(
                                    stack.getItem()
                            );

                    JsonObject itemData =
                            new JsonObject();

                    itemData.addProperty(
                            "slot",
                            slot
                    );

                    itemData.addProperty(
                            "item",
                            itemId.toString()
                    );

                    itemData.addProperty(
                            "count",
                            stack.getCount()
                    );

                    itemData.addProperty(
                            "max_stack_size",
                            stack.getMaxStackSize()
                    );

                    /*
                     * 物品有自定义名称时一并返回。
                     * 普通钻石可能返回“Diamond”或本地化后的显示名称，
                     * 因此程序逻辑仍应使用 item 字段判断物品类型。
                     */
                    itemData.addProperty(
                            "display_name",
                            stack.getHoverName().getString()
                    );

                    items.add(itemData);

                    totalItemCount += stack.getCount();
                }

                JsonObject data =
                        new JsonObject();

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
                        "container_size",
                        container.getContainerSize()
                );

                data.addProperty(
                        "occupied_slots",
                        items.size()
                );

                data.addProperty(
                        "total_item_count",
                        totalItemCount
                );

                data.add(
                        "items",
                        items
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