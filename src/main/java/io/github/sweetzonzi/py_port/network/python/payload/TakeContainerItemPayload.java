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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.concurrent.CompletableFuture;

public record TakeContainerItemPayload(
        ResourceLocation level,
        int x,
        int y,
        int z,
        int slot,
        int count,
        int playerId
) implements PyPayload {

    public static final Codec<TakeContainerItemPayload> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            ResourceLocation.CODEC
                                    .fieldOf("level")
                                    .forGetter(TakeContainerItemPayload::level),

                            Codec.INT
                                    .fieldOf("x")
                                    .forGetter(TakeContainerItemPayload::x),

                            Codec.INT
                                    .fieldOf("y")
                                    .forGetter(TakeContainerItemPayload::y),

                            Codec.INT
                                    .fieldOf("z")
                                    .forGetter(TakeContainerItemPayload::z),

                            Codec.INT
                                    .fieldOf("slot")
                                    .forGetter(TakeContainerItemPayload::slot),

                            Codec.INT
                                    .fieldOf("count")
                                    .forGetter(TakeContainerItemPayload::count),

                            Codec.INT
                                    .fieldOf("player_id")
                                    .forGetter(TakeContainerItemPayload::playerId)
                    ).apply(instance, TakeContainerItemPayload::new)
            );

    public static final PyPayloadType<TakeContainerItemPayload> TYPE =
            new PyPayloadType<>(
                    "take_container_item",
                    CODEC
            );

    @Override
    public PyPayloadType<?> type() {
        return TYPE;
    }

    public static PyHandleResult handle(
            TakeContainerItemPayload payload,
            PyContext context
    ) {
        var server = context.getServer();

        if (server == null) {
            return PyHandleResult.fail("Server is not running");
        }

        CompletableFuture<JsonObject> future =
                new CompletableFuture<>();

        server.execute(() -> {
            try {
                var levelKey = ResourceKey.create(
                        Registries.DIMENSION,
                        payload.level()
                );

                var level = server.getLevel(levelKey);

                if (level == null) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Level " + payload.level() + " not found"
                            )
                    );
                    return;
                }

                var entity = level.getEntity(payload.playerId());

                if (!(entity instanceof ServerPlayer player)) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Entity " + payload.playerId()
                                            + " is not a player"
                            )
                    );
                    return;
                }

                BlockPos pos = new BlockPos(
                        payload.x(),
                        payload.y(),
                        payload.z()
                );

                BlockEntity blockEntity =
                        level.getBlockEntity(pos);

                if (!(blockEntity instanceof Container container)) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Block at " + pos + " is not a container"
                            )
                    );
                    return;
                }

                if (payload.slot() < 0
                        || payload.slot() >= container.getContainerSize()) {

                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Invalid slot: " + payload.slot()
                            )
                    );
                    return;
                }

                if (payload.count() <= 0) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Count must be greater than 0"
                            )
                    );
                    return;
                }

                ItemStack chestStack =
                        container.getItem(payload.slot());

                if (chestStack.isEmpty()) {
                    future.completeExceptionally(
                            new IllegalArgumentException(
                                    "Container slot "
                                            + payload.slot()
                                            + " is empty"
                            )
                    );
                    return;
                }

                int takeCount = Math.min(
                        payload.count(),
                        chestStack.getCount()
                );

                /*
                 * removeItem 会从宝箱中真正移除物品，
                 * 并返回被移除的 ItemStack。
                 */
                ItemStack takenStack =
                        container.removeItem(
                                payload.slot(),
                                takeCount
                        );

                /*
                 * add 返回 true，表示整个物品堆都放入背包；
                 * 返回 false 时，takenStack 中会留下没有放进去的部分。
                 */
                boolean fullyAdded =
                        player.getInventory().add(takenStack);

                /*
                 * 如果背包空间不足，将剩余物品放回宝箱，
                 * 防止物品丢失。
                 */
                if (!takenStack.isEmpty()) {
                    ItemStack currentStack =
                            container.getItem(payload.slot());

                    if (currentStack.isEmpty()) {
                        container.setItem(
                                payload.slot(),
                                takenStack
                        );
                    } else if (
                            ItemStack.isSameItemSameComponents(
                                    currentStack,
                                    takenStack
                            )
                    ) {
                        int availableSpace =
                                currentStack.getMaxStackSize()
                                        - currentStack.getCount();

                        int returnCount = Math.min(
                                availableSpace,
                                takenStack.getCount()
                        );

                        currentStack.grow(returnCount);
                        takenStack.shrink(returnCount);

                        container.setItem(
                                payload.slot(),
                                currentStack
                        );
                    }

                    /*
                     * 理论上原槽位空间不足时，继续寻找空槽位放回。
                     */
                    if (!takenStack.isEmpty()) {
                        for (
                                int i = 0;
                                i < container.getContainerSize();
                                i++
                        ) {
                            if (container.getItem(i).isEmpty()) {
                                container.setItem(
                                        i,
                                        takenStack.copy()
                                );

                                takenStack.setCount(0);
                                break;
                            }
                        }
                    }
                }

                blockEntity.setChanged();
                player.getInventory().setChanged();

                ResourceLocation itemId =
                        BuiltInRegistries.ITEM.getKey(
                                chestStack.getItem()
                        );

                int remainingCount =
                        container.getItem(payload.slot()).getCount();

                JsonObject data = new JsonObject();

                data.addProperty(
                        "player_id",
                        player.getId()
                );

                data.addProperty(
                        "slot",
                        payload.slot()
                );

                data.addProperty(
                        "item",
                        itemId.toString()
                );

                data.addProperty(
                        "requested_count",
                        payload.count()
                );

                data.addProperty(
                        "fully_added",
                        fullyAdded
                );

                data.addProperty(
                        "remaining_in_slot",
                        remainingCount
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