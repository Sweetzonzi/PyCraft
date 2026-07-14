package io.github.sweetzonzi.py_port.network.java.payload;

import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.shop.ShopData;
import io.github.sweetzonzi.py_port.common.shop.ShopEventHandler;
import io.github.sweetzonzi.py_port.common.shop.ShopItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record BuyItemPayload(String itemId) implements CustomPacketPayload {
    public static final Type<BuyItemPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PyCraft.MOD_ID, "buy_item")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BuyItemPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, BuyItemPayload::itemId,
                    BuyItemPayload::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final BuyItemPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            // 查找商品
            ShopItem shopItem = ShopData.getById(payload.itemId);
            if (shopItem == null) {
                serverPlayer.sendSystemMessage(Component.literal("§c商品不存在！"));
                return;
            }

            // 检查背包空间
            var inventory = serverPlayer.getInventory();
            if (inventory.getFreeSlot() == -1) {
                serverPlayer.sendSystemMessage(Component.literal("§c背包已满！"));
                return;
            }

            // 扣款
            if (!ShopEventHandler.deductGold(serverPlayer, shopItem.price())) {
                serverPlayer.sendSystemMessage(Component.literal("§c金币不足！需要 " + shopItem.price() + " G，当前拥有 "
                        + ShopEventHandler.getGold(serverPlayer) + " G"));
                return;
            }

            // 发放物品
            var resultStack = shopItem.result().copy();
            inventory.add(resultStack);

            // 通知玩家
            serverPlayer.sendSystemMessage(Component.literal("§a成功购买 " + shopItem.name()
                    + "，花费 " + shopItem.price() + " G，剩余 "
                    + ShopEventHandler.getGold(serverPlayer) + " G"));
        });
    }
}