package io.github.sweetzonzi.py_port.client.gui.screen;

import io.github.sweetzonzi.py_port.common.teach.AlgorithmData;
import io.github.sweetzonzi.py_port.common.teach.AlgorithmEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AlgorithmListScreen extends Screen {
    private static final int TITLE_Y = 20;
    private static final int LIST_TOP = 50;
    private static final int LIST_BOTTOM_OFFSET = 40;
    private static final int ENTRY_HEIGHT = 36;
    private static final int ENTRY_GAP = 4;

    private final List<AlgorithmEntry> entries;
    private int scrollOffset;

    public AlgorithmListScreen() {
        super(Component.translatable("screen.py_port.algorithm_list.title"));
        this.entries = AlgorithmData.getAll();
        this.scrollOffset = 0;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(
                CommonComponents.GUI_CANCEL,
                btn -> onClose()
        ).bounds(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Title
        graphics.drawCenteredString(font, title, width / 2, TITLE_Y, 0xFFFFFF);

        // Render entries
        int listBottom = height - LIST_BOTTOM_OFFSET;
        int y = LIST_TOP - scrollOffset;

        for (AlgorithmEntry entry : entries) {
            int entryTop = y;
            int entryBottom = y + ENTRY_HEIGHT;

            // Check if entry is visible
            if (entryBottom < LIST_TOP || entryTop > listBottom) {
                y += ENTRY_HEIGHT + ENTRY_GAP;
                continue;
            }

            boolean hovered = mouseX >= 20 && mouseX <= width - 20
                    && mouseY >= entryTop && mouseY <= entryBottom;

            // Background
            int bgColor = hovered ? 0x80555555 : 0x80333333;
            graphics.fill(20, entryTop, width - 20, entryBottom, bgColor);

            // Name
            graphics.drawString(font, entry.name(), 28, entryTop + 4, 0xFFFFAA, false);

            // Summary
            graphics.drawString(font, entry.summary(), 28, entryTop + 18, 0xAAAAAA, false);

            y += ENTRY_HEIGHT + ENTRY_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int listBottom = height - LIST_BOTTOM_OFFSET;
            int y = LIST_TOP - scrollOffset;
            int index = 0;

            for (AlgorithmEntry entry : entries) {
                int entryTop = y;
                int entryBottom = y + ENTRY_HEIGHT;

                if (entryBottom >= LIST_TOP && entryTop <= listBottom) {
                    if (mouseX >= 20 && mouseX <= width - 20
                            && mouseY >= entryTop && mouseY <= entryBottom) {
                        minecraft.setScreen(new AlgorithmDetailScreen(entry));
                        return true;
                    }
                }
                y += ENTRY_HEIGHT + ENTRY_GAP;
                index++;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentHeight = entries.size() * (ENTRY_HEIGHT + ENTRY_GAP);
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