package com.mehmetclient.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Clickable block filter for Block ESP. Right-click Block ESP in the main menu to open. */
public final class BlockEspSettingsScreen extends Screen {
    private final Screen parent;
    private static final int PURPLE = 0xFF9B5CFF;
    private static final List<Target> TARGETS = List.of(
            new Target("diamond_ore", "Diamond Ore"), new Target("deepslate_diamond_ore", "Deepslate Diamond Ore"),
            new Target("ancient_debris", "Ancient Debris / Netherite"), new Target("chest", "Chest"),
            new Target("trapped_chest", "Trapped Chest"), new Target("ender_chest", "Ender Chest"),
            new Target("barrel", "Barrel"), new Target("spawner", "Spawner"),
            new Target("trial_spawner", "Trial Spawner"), new Target("vault", "Vault"),
            new Target("shulker_box", "Shulker Box"), new Target("obsidian", "Obsidian"),
            new Target("crying_obsidian", "Crying Obsidian"), new Target("beacon", "Beacon"),
            new Target("diamond_block", "Diamond Block"), new Target("emerald_block", "Emerald Block"),
            new Target("gold_block", "Gold Block"), new Target("iron_block", "Iron Block"),
            new Target("emerald_ore", "Emerald Ore"), new Target("deepslate_emerald_ore", "Deepslate Emerald Ore"),
            new Target("gold_ore", "Gold Ore"), new Target("deepslate_gold_ore", "Deepslate Gold Ore"),
            new Target("iron_ore", "Iron Ore"), new Target("deepslate_iron_ore", "Deepslate Iron Ore"),
            new Target("lapis_ore", "Lapis Ore"), new Target("deepslate_lapis_ore", "Deepslate Lapis Ore"),
            new Target("redstone_ore", "Redstone Ore"), new Target("deepslate_redstone_ore", "Deepslate Redstone Ore"),
            new Target("copper_ore", "Copper Ore"), new Target("deepslate_copper_ore", "Deepslate Copper Ore"),
            new Target("nether_gold_ore", "Nether Gold Ore")
    );
    private record Target(String id, String label) {}

    public BlockEspSettingsScreen(Screen parent) {
        super(Component.literal("Mehmet Client - Block ESP Targets"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int columns = 2;
        int rowH = 21;
        int gap = 6;
        int buttonW = Math.min(260, (width - 40) / columns - gap);
        int totalW = columns * buttonW + gap;
        int startX = (width - totalW) / 2;
        int startY = 54;
        int rows = (TARGETS.size() + columns - 1) / columns;
        for (int i = 0; i < TARGETS.size(); i++) {
            Target target = TARGETS.get(i);
            int col = i / rows;
            int row = i % rows;
            int x = startX + col * (buttonW + gap);
            int y = startY + row * rowH;
            addRenderableWidget(Button.builder(label(target), b -> {
                ClientModules.toggleBlockEspTarget(target.id());
                b.setMessage(label(target));
            }).bounds(x, y, buttonW, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(parent))
                .bounds(width / 2 - 70, height - 32, 140, 22).build());
    }

    private static Component label(Target target) {
        return Component.literal((ClientModules.hasBlockEspTarget(target.id()) ? "[ON] " : "[OFF] ") + target.label());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xEE080611);
        graphics.fill(0, 0, width, 3, PURPLE);
        graphics.drawCenteredString(font, "MEHMET CLIENT - BLOCK ESP", width / 2, 18, 0xFFF0E8FF);
        graphics.drawCenteredString(font, "Select which blocks get an outline through walls", width / 2, 34, 0xFFB7ADC9);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override public boolean isPauseScreen() { return false; }
}
