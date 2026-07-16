package io.github.sweetzonzi.py_port.client.gui.screen;

import io.github.sweetzonzi.py_port.common.shop.SellableItem;
import io.github.sweetzonzi.py_port.common.shop.ShopData;
import io.github.sweetzonzi.py_port.common.shop.ShopItem;
import io.github.sweetzonzi.py_port.network.java.payload.BuyItemPayload;
import io.github.sweetzonzi.py_port.network.java.payload.SellItemPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ShopScreen extends Screen {
    private static final int TITLE_Y = 28;
    private static final int LIST_TOP = 52;
    private static final int LIST_BOTTOM_OFFSET = 45;
    private static final int ENTRY_HEIGHT = 28;
    private static final int ENTRY_GAP = 2;
    private static final int ICON_SIZE = 18;

    private boolean sellMode = false;
    private int scrollOffset;
    // tab 区域坐标（在 init 中计算）
    private int tabBuyX, tabBuyY, tabSellX, tabSellY;
    private static final int TAB_W = 62;
    private static final int TAB_H = 16;

    public ShopScreen() {
        super(Component.literal("装备商店"));
        this.scrollOffset = 0;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        tabBuyX = centerX - TAB_W - 4;
        tabBuyY = 5;
        tabSellX = centerX + 4;
        tabSellY = 5;

        addRenderableWidget(Button.builder(
                CommonComponents.GUI_CANCEL,
                btn -> onClose()
        ).bounds(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // ====== 模式切换 Tab ======
        renderTab(graphics, tabBuyX, tabBuyY, "⚔ 购买", !sellMode, mouseX, mouseY);
        renderTab(graphics, tabSellX, tabSellY, "💰 出售", sellMode, mouseX, mouseY);

        // 标题
        String title = sellMode ? "§l💰 装备商店 - 回收" : "§l⚔ 装备商店";
        graphics.drawCenteredString(font, title, width / 2, TITLE_Y, 0xFFAA00);

        // 提示
        String hint = sellMode ? "§7点击物品即可出售" : "§7点击商品即可购买";
        graphics.drawCenteredString(font, hint, width / 2, TITLE_Y + 11, 0x888888);

        // 欢迎语
        graphics.drawCenteredString(font, "§a欢迎来到商店，退出请按ESC", width / 2, TITLE_Y + 22, 0x00AA00);

        // ====== 渲染列表 ======
        int listBottom = height - LIST_BOTTOM_OFFSET;
        int y = LIST_TOP - scrollOffset;

        if (sellMode) {
            renderSellItems(graphics, mouseX, mouseY, y, listBottom);
        } else {
            renderBuyItems(graphics, mouseX, mouseY, y, listBottom);
        }
    }

    private void renderTab(GuiGraphics graphics, int x, int y, String text, boolean active, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + TAB_W && mouseY >= y && mouseY <= y + TAB_H;
        int bgColor;
        if (active) {
            bgColor = 0xFFAA00;       // 金色 = 选中
        } else if (hovered) {
            bgColor = 0x80555555;
        } else {
            bgColor = 0x80444444;
        }
        graphics.fill(x, y, x + TAB_W, y + TAB_H, bgColor);
        int textColor = active ? 0xFFFFFFFF : 0xFFAAAAAA;
        graphics.drawCenteredString(font, text, x + TAB_W / 2, y + (TAB_H - font.lineHeight) / 2, textColor);
    }

    private void renderBuyItems(GuiGraphics graphics, int mouseX, int mouseY, int y, int listBottom) {
        List<ShopItem> items = ShopData.getAll();
        for (ShopItem item : items) {
            int entryTop = y;
            int entryBottom = y + ENTRY_HEIGHT;
            boolean visible = entryBottom >= LIST_TOP && entryTop <= listBottom;

            if (visible) {
                boolean hovered = mouseX >= 20 && mouseX <= width - 20
                        && mouseY >= entryTop && mouseY <= entryBottom;

                int bgColor = hovered ? 0x80555555 : 0x80333333;
                graphics.fill(20, entryTop, width - 20, entryBottom, bgColor);

                int iconX = 24;
                int iconY = entryTop + (ENTRY_HEIGHT - ICON_SIZE) / 2;
                graphics.renderItem(item.icon(), iconX, iconY);

                int textX = iconX + 22;
                int textY = entryTop + (ENTRY_HEIGHT - font.lineHeight) / 2;
                graphics.drawString(font, item.name(), textX, textY, 0xFFFFFF, false);

                String rightText;
                int rightColor;
                if (hovered) {
                    rightText = "§e点击购买";
                    rightColor = 0xFFFF55;
                } else {
                    rightText = "§6" + item.price() + " G";
                    rightColor = 0xFFAA00;
                }
                int rightWidth = font.width(rightText);
                graphics.drawString(font, rightText, width - 24 - rightWidth, textY, rightColor, false);
            }
            y += ENTRY_HEIGHT + ENTRY_GAP;
        }
    }

    private void renderSellItems(GuiGraphics graphics, int mouseX, int mouseY, int y, int listBottom) {
        List<SellableItem> items = ShopData.getSellableItems();
        for (SellableItem item : items) {
            int entryTop = y;
            int entryBottom = y + ENTRY_HEIGHT;
            boolean visible = entryBottom >= LIST_TOP && entryTop <= listBottom;

            if (visible) {
                boolean hovered = mouseX >= 20 && mouseX <= width - 20
                        && mouseY >= entryTop && mouseY <= entryBottom;

                int bgColor = hovered ? 0x80555555 : 0x80333333;
                graphics.fill(20, entryTop, width - 20, entryBottom, bgColor);

                int iconX = 24;
                int iconY = entryTop + (ENTRY_HEIGHT - ICON_SIZE) / 2;
                graphics.renderItem(item.item(), iconX, iconY);

                int textX = iconX + 22;
                int textY = entryTop + (ENTRY_HEIGHT - font.lineHeight) / 2;
                graphics.drawString(font, item.name(), textX, textY, 0xFFFFFF, false);

                String rightText;
                int rightColor;
                if (hovered) {
                    rightText = "§e点击出售";
                    rightColor = 0x55FF55;
                } else {
                    rightText = "§6+" + item.price() + " G";
                    rightColor = 0xFFAA00;
                }
                int rightWidth = font.width(rightText);
                graphics.drawString(font, rightText, width - 24 - rightWidth, textY, rightColor, false);
            }
            y += ENTRY_HEIGHT + ENTRY_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // 检查 Tab 点击
            if (mouseX >= tabBuyX && mouseX <= tabBuyX + TAB_W && mouseY >= tabBuyY && mouseY <= tabBuyY + TAB_H) {
                if (sellMode) {
                    sellMode = false;
                    scrollOffset = 0;
                }
                return true;
            }
            if (mouseX >= tabSellX && mouseX <= tabSellX + TAB_W && mouseY >= tabSellY && mouseY <= tabSellY + TAB_H) {
                if (!sellMode) {
                    sellMode = true;
                    scrollOffset = 0;
                }
                return true;
            }

            // 列表点击
            int listBottom = height - LIST_BOTTOM_OFFSET;
            int y = LIST_TOP - scrollOffset;

            if (sellMode) {
                List<SellableItem> items = ShopData.getSellableItems();
                for (SellableItem item : items) {
                    int entryTop = y;
                    int entryBottom = y + ENTRY_HEIGHT;
                    if (entryBottom >= LIST_TOP && entryTop <= listBottom
                            && mouseX >= 20 && mouseX <= width - 20
                            && mouseY >= entryTop && mouseY <= entryBottom) {
                        PacketDistributor.sendToServer(new SellItemPayload(item.id()));
                        return true;
                    }
                    y += ENTRY_HEIGHT + ENTRY_GAP;
                }
            } else {
                List<ShopItem> items = ShopData.getAll();
                for (ShopItem item : items) {
                    int entryTop = y;
                    int entryBottom = y + ENTRY_HEIGHT;
                    if (entryBottom >= LIST_TOP && entryTop <= listBottom
                            && mouseX >= 20 && mouseX <= width - 20
                            && mouseY >= entryTop && mouseY <= entryBottom) {
                        PacketDistributor.sendToServer(new BuyItemPayload(item.id()));
                        return true;
                    }
                    y += ENTRY_HEIGHT + ENTRY_GAP;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listSize = sellMode ? ShopData.getSellableItems().size() : ShopData.getAll().size();
        int contentHeight = listSize * (ENTRY_HEIGHT + ENTRY_GAP);
        int visibleHeight = height - LIST_TOP - LIST_BOTTOM_OFFSET;
        int maxScroll = Math.max(0, contentHeight - visibleHeight);

        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}