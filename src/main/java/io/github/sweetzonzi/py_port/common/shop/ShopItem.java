package io.github.sweetzonzi.py_port.common.shop;

import net.minecraft.world.item.ItemStack;

public record ShopItem(
        String id,
        String name,
        int price,
        ItemStack icon,
        ItemStack result
) {
    public ShopItem {
        icon = icon.copy();
        result = result.copy();
    }
}