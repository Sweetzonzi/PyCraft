package io.github.sweetzonzi.py_port.common.shop;

import net.minecraft.world.item.ItemStack;

public record SellableItem(
        String id,
        String name,
        int price,
        ItemStack item
) {
    public SellableItem {
        item = item.copy();
    }
}