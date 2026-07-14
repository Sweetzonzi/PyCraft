package io.github.sweetzonzi.py_port.client.gui.screen;

import io.github.sweetzonzi.py_port.common.teach.AlgorithmEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AlgorithmDetailScreen extends Screen {
    private static final int TITLE_Y = 20;
    private static final int INFO_Y = 42;
    private static final int DESC_TOP = 62;
    private static final int PSEUDOCODE_LABEL_Y_OFFSET = -20;
    private static final int SIDE_MARGIN = 25;

    private final AlgorithmEntry entry;
    private int scrollOffset;
    private int maxScroll;

    public AlgorithmDetailScreen(AlgorithmEntry entry) {
        super(Component.literal(entry.name()));
        this.entry = entry;
        this.scrollOffset = 0;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(Button.builder(
                Component.translatable("screen.py_port.back"),
                btn -> minecraft.setScreen(new AlgorithmListScreen())
        ).bounds(SIDE_MARGIN, height - 30, 80, 20).build());

        addRenderableWidget(Button.builder(
                CommonComponents.GUI_CANCEL,
                btn -> onClose()
        ).bounds(width - 80 - SIDE_MARGIN, height - 30, 80, 20).build());

        calculateMaxScroll();
    }

    private void calculateMaxScroll() {
        // Estimate total content height
        int descLineCount = 0;
        for (String line : entry.description().split("\n")) {
            if (line.isEmpty()) {
                descLineCount++;
            } else {
                descLineCount += (int) Math.ceil(
                        (double) font.width(line) / (width - SIDE_MARGIN * 2)
                );
            }
        }
        int descHeight = descLineCount * font.lineHeight + 10;

        int pseudoLineCount = 0;
        String[] pseudoLines = entry.pseudocode().split("\n");
        pseudoLineCount = pseudoLines.length;

        int pseudoHeight = pseudoLineCount * font.lineHeight + 20;

        int totalContentHeight = descHeight + pseudoHeight + 80;
        int visibleHeight = height - DESC_TOP - 40;

        maxScroll = Math.max(0, totalContentHeight - visibleHeight);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Title
        graphics.drawCenteredString(font, entry.name(), width / 2, TITLE_Y, 0xFFFFAA);

        // Category & Complexity tags
        String info = "§7分类: §f" + entry.category() + "    §7时间复杂度: §f" + entry.complexity();
        graphics.drawCenteredString(font, Component.nullToEmpty(info), width / 2, INFO_Y, 0xAAAAAA);

        // Scrollable content area
        int contentY = DESC_TOP - scrollOffset;
        int contentAreaBottom = height - 40;

        // Description section
        graphics.drawString(font, "§n--- 算法介绍 ---", SIDE_MARGIN, contentY, 0x88FF88, false);
        contentY += 12;

        for (String rawLine : entry.description().split("\n")) {
            if (contentY > contentAreaBottom + 20 || contentY < DESC_TOP - font.lineHeight * 2) {
                contentY += font.lineHeight;
                continue;
            }

            if (rawLine.isEmpty()) {
                contentY += 4;
                continue;
            }

            // Word wrap
            List<FormattedCharSequence> wrapped = font.split(Component.nullToEmpty(rawLine), width - SIDE_MARGIN * 2);
            for (FormattedCharSequence line : wrapped) {
                if (contentY >= DESC_TOP - font.lineHeight && contentY <= contentAreaBottom) {
                    graphics.drawString(font, line, SIDE_MARGIN, contentY, 0xFFFFFF, false);
                }
                contentY += font.lineHeight;
            }
        }

        contentY += 8;

        // Pseudocode section
        int pseudoY = contentY;
        if (pseudoY > DESC_TOP - scrollOffset) {
            graphics.drawString(font, "§n--- 伪代码 ---", SIDE_MARGIN, pseudoY, 0x88FF88, false);
        }
        pseudoY += 12;

        for (String line : entry.pseudocode().split("\n")) {
            if (pseudoY > contentAreaBottom + 20 || pseudoY < DESC_TOP - font.lineHeight) {
                pseudoY += font.lineHeight;
                continue;
            }
            if (pseudoY >= DESC_TOP && pseudoY <= contentAreaBottom) {
                graphics.drawString(font, line, SIDE_MARGIN + 8, pseudoY, 0x88CCFF, false);
            }
            pseudoY += font.lineHeight;
        }

        // Scroll hint
        if (maxScroll > 0) {
            String hint = "§7鼠标滚轮滚动";
            graphics.drawCenteredString(font, Component.nullToEmpty(hint), width / 2, height - 12, 0x555555);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int) (scrollY * 15);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}