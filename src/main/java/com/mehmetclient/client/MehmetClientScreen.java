package com.mehmetclient.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Purple/black module menu. Right Shift toggles this screen. */
public final class MehmetClientScreen extends Screen {
    private String selectedCategory = "Render";
    private static final int PURPLE = 0xFF9B5CFF;
    private static final int PANEL = 0xF0121222;
    private static final int ROW = 0xFF202035;
    private static final int ROW_HOVER = 0xFF302449;
    private static final int TEXT = 0xFFF1EDFF;
    private static final int MUTED = 0xFFAAA5BC;
    private int panelX, panelY, panelW, panelH;
    private int listY, listBottom;

    public MehmetClientScreen() {
        super(Component.literal("Mehmet Client"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0x88050012);
        panelW = Math.min(580, Math.max(300, width - 24));
        panelH = Math.min(440, Math.max(330, height - 24));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 3, PURPLE);
        graphics.drawString(font, Component.literal("MEHMET CLIENT"), panelX + 18, panelY + 12, TEXT);
        graphics.drawString(font, Component.literal("Minecraft 1.21.11 | Fabric | v1.3.1"), panelX + 18, panelY + 27, MUTED);
        graphics.drawString(font, Component.literal("RIGHT SHIFT / ESC"), panelX + panelW - 125, panelY + 14, MUTED);

        int tabY = panelY + 49;
        int tabX = panelX + 16;
        for (String category : ClientModules.CATEGORIES) {
            int tabW = category.equals("BaseFinding") ? 112 : 92;
            boolean active = category.equals(selectedCategory);
            graphics.fill(tabX, tabY, tabX + tabW, tabY + 23, active ? 0xFF39245A : ROW);
            if (active) graphics.fill(tabX, tabY + 20, tabX + tabW, tabY + 23, PURPLE);
            graphics.drawCenteredString(font, Component.literal(category), tabX + tabW / 2, tabY + 7, active ? TEXT : MUTED);
            tabX += tabW + 7;
        }

        int listX = panelX + 16;
        listY = tabY + 30;
        listBottom = panelY + panelH - 32;
        int rowW = panelW - 32;
        int rowH = 20;
        List<String> modules = ClientModules.modules(selectedCategory);
        int visible = Math.max(1, (listBottom - listY) / rowH);

        graphics.enableScissor(listX, listY, listX + rowW, listBottom);
        for (int i = 0; i < Math.min(visible, modules.size()); i++) {
            String module = modules.get(i);
            int y = listY + i * rowH;
            boolean hover = mouseX >= listX && mouseX <= listX + rowW && mouseY >= y && mouseY <= y + rowH - 1;
            boolean implemented = ClientModules.hasBehavior(module);
            boolean enabled = ClientModules.isEnabled(module);
            graphics.fill(listX, y, listX + rowW, y + rowH - 1, hover ? ROW_HOVER : ROW);
            graphics.drawString(font, Component.literal(module), listX + 9, y + 5, implemented ? TEXT : MUTED);
            if (selectedCategory.equals("Render") && module.equals("Block ESP")) {
                graphics.drawString(font, Component.literal("[ SELECT BLOCKS ]"), listX + rowW - 122, y + 5, PURPLE);
            }
            String status = !implemented ? "SOON" : (enabled ? "ON" : "OFF");
            int statusColor = !implemented ? 0xFF6F6A7D : (enabled ? 0xFFB78BFF : 0xFF777386);
            graphics.drawString(font, Component.literal(status), listX + rowW - 38, y + 5, statusColor);
        }
        graphics.disableScissor();

        int footerY = panelY + panelH - 20;
        graphics.fill(panelX + 16, footerY - 7, panelX + panelW - 16, footerY - 6, 0xFF353047);
        graphics.drawString(font, Component.literal("Enabled: " + ClientModules.enabledCount()), panelX + 18, footerY, MUTED);
        graphics.drawString(font, Component.literal("Right-click Block ESP to select blocks"), panelX + panelW - 224, footerY, MUTED);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == 1) {
            int listX = panelX + 16;
            int rowW = panelW - 32;
            int rowH = 20;
            if (mouseX >= listX && mouseX <= listX + rowW && mouseY >= listY && mouseY < listBottom) {
                int index = (int) ((mouseY - listY) / rowH);
                List<String> modules = ClientModules.modules(selectedCategory);
                int visible = Math.max(1, (listBottom - listY) / rowH);
                if (selectedCategory.equals("Render") && index >= 0 && index < Math.min(visible, modules.size())
                        && modules.get(index).equals("Block ESP")) {
                    minecraft.setScreen(new BlockEspSettingsScreen(this));
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);

        int tabY = panelY + 49;
        int tabX = panelX + 16;
        for (String category : ClientModules.CATEGORIES) {
            int tabW = category.equals("BaseFinding") ? 112 : 92;
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + 23) {
                selectedCategory = category;
                return true;
            }
            tabX += tabW + 7;
        }

        int listX = panelX + 16;
        int rowW = panelW - 32;
        int rowH = 20;
        if (mouseX >= listX && mouseX <= listX + rowW && mouseY >= listY && mouseY < listBottom) {
            int index = (int) ((mouseY - listY) / rowH);
            List<String> modules = ClientModules.modules(selectedCategory);
            int visible = Math.max(1, (listBottom - listY) / rowH);
            if (index >= 0 && index < Math.min(visible, modules.size())) {
                String module = modules.get(index);
                if (selectedCategory.equals("Render") && module.equals("Block ESP")
                        && mouseX >= listX + rowW - 132) {
                    minecraft.setScreen(new BlockEspSettingsScreen(this));
                } else {
                    ClientModules.toggle(module);
                }
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
