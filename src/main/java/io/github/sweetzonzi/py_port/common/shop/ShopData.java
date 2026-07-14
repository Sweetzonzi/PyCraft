package io.github.sweetzonzi.py_port.common.shop;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.List;

public class ShopData {

    public static List<ShopItem> getAll() {
        return List.of(
                // 近战武器
                WOODEN_SWORD,
                STONE_SWORD,
                IRON_SWORD,
                DIAMOND_SWORD,
                // 远程武器
                BOW,
                CROSSBOW,
                // ====== 弹药 ======
                ARROW_SINGLE,
                ARROW_PACK,
                // ====== 斧 ======
                WOODEN_AXE,
                STONE_AXE,
                IRON_AXE,
                DIAMOND_AXE,
                // ====== 皮革护甲 ======
                LEATHER_HELMET,
                LEATHER_CHESTPLATE,
                LEATHER_LEGGINGS,
                LEATHER_BOOTS,
                // ====== 铁护甲 ======
                IRON_HELMET,
                IRON_CHESTPLATE,
                IRON_LEGGINGS,
                IRON_BOOTS,
                // ====== 钻石护甲 ======
                DIAMOND_HELMET,
                DIAMOND_CHESTPLATE,
                DIAMOND_LEGGINGS,
                DIAMOND_BOOTS,
                // ====== 恢复药水 ======
                HEALING_POTION,
                STRONG_HEALING_POTION
        );
    }

    public static ShopItem getById(String id) {
        return getAll().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    // ====== 近战武器 ======

    private static final ShopItem WOODEN_SWORD = new ShopItem(
            "wooden_sword",
            "木剑",
            20,
            new ItemStack(Items.WOODEN_SWORD),
            new ItemStack(Items.WOODEN_SWORD)
    );

    private static final ShopItem STONE_SWORD = new ShopItem(
            "stone_sword",
            "石剑",
            40,
            new ItemStack(Items.STONE_SWORD),
            new ItemStack(Items.STONE_SWORD)
    );

    private static final ShopItem IRON_SWORD = new ShopItem(
            "iron_sword",
            "铁剑",
            80,
            new ItemStack(Items.IRON_SWORD),
            new ItemStack(Items.IRON_SWORD)
    );

    private static final ShopItem DIAMOND_SWORD = new ShopItem(
            "diamond_sword",
            "钻石剑",
            400,
            new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.DIAMOND_SWORD)
    );

    // ====== 远程武器 ======

    private static final ShopItem BOW = new ShopItem(
            "bow",
            "弓",
            60,
            new ItemStack(Items.BOW),
            new ItemStack(Items.BOW)
    );

    private static final ShopItem CROSSBOW = new ShopItem(
            "crossbow",
            "弩",
            200,
            new ItemStack(Items.CROSSBOW),
            new ItemStack(Items.CROSSBOW)
    );

    // ====== 弹药 ======

    private static final ShopItem ARROW_SINGLE = new ShopItem(
            "arrow_single",
            "箭矢",
            1,
            new ItemStack(Items.ARROW),
            new ItemStack(Items.ARROW)
    );

    private static final ShopItem ARROW_PACK = new ShopItem(
            "arrow_pack",
            "箭矢（16支装）",
            16,
            new ItemStack(Items.ARROW, 16),
            new ItemStack(Items.ARROW, 16)
    );

    // ====== 斧（伤害更高，兼顾工具用途）======

    private static final ShopItem WOODEN_AXE = new ShopItem(
            "wooden_axe",
            "木斧",
            25,
            new ItemStack(Items.WOODEN_AXE),
            new ItemStack(Items.WOODEN_AXE)
    );

    private static final ShopItem STONE_AXE = new ShopItem(
            "stone_axe",
            "石斧",
            50,
            new ItemStack(Items.STONE_AXE),
            new ItemStack(Items.STONE_AXE)
    );

    private static final ShopItem IRON_AXE = new ShopItem(
            "iron_axe",
            "铁斧",
            100,
            new ItemStack(Items.IRON_AXE),
            new ItemStack(Items.IRON_AXE)
    );

    private static final ShopItem DIAMOND_AXE = new ShopItem(
            "diamond_axe",
            "钻石斧",
            500,
            new ItemStack(Items.DIAMOND_AXE),
            new ItemStack(Items.DIAMOND_AXE)
    );

    // ====== 皮革护甲 ======

    private static final ShopItem LEATHER_HELMET = new ShopItem(
            "leather_helmet",
            "皮革头盔",
            15,
            new ItemStack(Items.LEATHER_HELMET),
            new ItemStack(Items.LEATHER_HELMET)
    );

    private static final ShopItem LEATHER_CHESTPLATE = new ShopItem(
            "leather_chestplate",
            "皮革胸甲",
            25,
            new ItemStack(Items.LEATHER_CHESTPLATE),
            new ItemStack(Items.LEATHER_CHESTPLATE)
    );

    private static final ShopItem LEATHER_LEGGINGS = new ShopItem(
            "leather_leggings",
            "皮革护腿",
            20,
            new ItemStack(Items.LEATHER_LEGGINGS),
            new ItemStack(Items.LEATHER_LEGGINGS)
    );

    private static final ShopItem LEATHER_BOOTS = new ShopItem(
            "leather_boots",
            "皮革靴子",
            15,
            new ItemStack(Items.LEATHER_BOOTS),
            new ItemStack(Items.LEATHER_BOOTS)
    );

    // ====== 铁护甲 ======

    private static final ShopItem IRON_HELMET = new ShopItem(
            "iron_helmet",
            "铁头盔",
            50,
            new ItemStack(Items.IRON_HELMET),
            new ItemStack(Items.IRON_HELMET)
    );

    private static final ShopItem IRON_CHESTPLATE = new ShopItem(
            "iron_chestplate",
            "铁胸甲",
            100,
            new ItemStack(Items.IRON_CHESTPLATE),
            new ItemStack(Items.IRON_CHESTPLATE)
    );

    private static final ShopItem IRON_LEGGINGS = new ShopItem(
            "iron_leggings",
            "铁护腿",
            80,
            new ItemStack(Items.IRON_LEGGINGS),
            new ItemStack(Items.IRON_LEGGINGS)
    );

    private static final ShopItem IRON_BOOTS = new ShopItem(
            "iron_boots",
            "铁靴子",
            45,
            new ItemStack(Items.IRON_BOOTS),
            new ItemStack(Items.IRON_BOOTS)
    );

    // ====== 钻石护甲 ======

    private static final ShopItem DIAMOND_HELMET = new ShopItem(
            "diamond_helmet",
            "钻石头盔",
            250,
            new ItemStack(Items.DIAMOND_HELMET),
            new ItemStack(Items.DIAMOND_HELMET)
    );

    private static final ShopItem DIAMOND_CHESTPLATE = new ShopItem(
            "diamond_chestplate",
            "钻石胸甲",
            500,
            new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_CHESTPLATE)
    );

    private static final ShopItem DIAMOND_LEGGINGS = new ShopItem(
            "diamond_leggings",
            "钻石护腿",
            400,
            new ItemStack(Items.DIAMOND_LEGGINGS),
            new ItemStack(Items.DIAMOND_LEGGINGS)
    );

    private static final ShopItem DIAMOND_BOOTS = new ShopItem(
            "diamond_boots",
            "钻石靴子",
            220,
            new ItemStack(Items.DIAMOND_BOOTS),
            new ItemStack(Items.DIAMOND_BOOTS)
    );

    // ====== 恢复药水 ======

    private static final ShopItem HEALING_POTION = new ShopItem(
            "healing_potion",
            "治疗药水",
            25,
            createPotionStack(Potions.HEALING),
            createPotionStack(Potions.HEALING)
    );

    private static final ShopItem STRONG_HEALING_POTION = new ShopItem(
            "strong_healing_potion",
            "治疗药水 II",
            50,
            createPotionStack(Potions.STRONG_HEALING),
            createPotionStack(Potions.STRONG_HEALING)
    );

    // ====== 可出售物品 ======

    public static List<SellableItem> getSellableItems() {
        return List.of(
                GOLD_INGOT,
                IRON_INGOT
        );
    }

    public static SellableItem getSellableById(String id) {
        return getSellableItems().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private static final SellableItem GOLD_INGOT = new SellableItem(
            "gold_ingot",
            "金锭",
            20,
            new ItemStack(Items.GOLD_INGOT)
    );

    private static final SellableItem IRON_INGOT = new SellableItem(
            "iron_ingot",
            "铁锭",
            10,
            new ItemStack(Items.IRON_INGOT)
    );

    // ====== 工具方法 ======

    private static ItemStack createPotionStack(Holder<Potion> potion) {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return stack;
    }
}