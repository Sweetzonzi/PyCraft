package io.github.sweetzonzi.py_port.network.java.payload;

import io.github.sweetzonzi.py_port.PyCraft;
import io.github.sweetzonzi.py_port.common.shop.SellableItem;
import io.github.sweetzonzi.py_port.common.shop.ShopData;
import io.github.sweetzonzi.py_port.common.shop.ShopEventHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record SellItemPayload(String itemId) implements CustomPacketPayload {
    public static final Type<SellItemPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PyCraft.MOD_ID, "sell_item")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SellItemPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, SellItemPayload::itemId,
                    SellItemPayload::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final SellItemPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            // 查找可出售物品定义
            SellableItem sellItem = ShopData.getSellableById(payload.itemId);
            if (sellItem == null) {
                serverPlayer.sendSystemMessage(Component.literal("§c该物品无法出售！"));
                return;
            }

            // 遍历背包查找匹配物品
            var inventory = serverPlayer.getInventory();
            int slotIndex = -1;
            for (int i = 0; i < inventory.items.size(); i++) {
                ItemStack stack = inventory.items.get(i);
                if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, sellItem.item())) {
                    slotIndex = i;
                    break;
                }
            }

            if (slotIndex == -1) {
                serverPlayer.sendSystemMessage(Component.literal("§c背包中没有 " + sellItem.name() + "！"));
                return;
            }

            // 移除1个物品
            inventory.removeItem(slotIndex, 1);

            // 增加金币
            int currentGold = ShopEventHandler.getGold(serverPlayer);
            ShopEventHandler.setGold(serverPlayer, currentGold + sellItem.price());

            // 通知玩家
            serverPlayer.sendSystemMessage(Component.literal("§a成功出售 " + sellItem.name()
                    + "，获得 " + sellItem.price() + " G，当前持有 "
                    + (currentGold + sellItem.price()) + " G"));
        });
    }
}