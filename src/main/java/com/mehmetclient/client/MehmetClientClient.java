package com.mehmetclient.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class MehmetClientClient implements ClientModInitializer {
    private static KeyMapping openMenuKey;
    private static final Set<Entity> GLOWING_ENTITIES = Collections.newSetFromMap(new IdentityHashMap<>());
    private static Double originalGamma;
    private static boolean appliedFullbright;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("mehmetclient", "main"));
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.mehmetclient.open_menu", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT, category));

        WorldScanner.registerRenderEvents();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new MehmetClientScreen());
                else if (client.screen instanceof MehmetClientScreen) client.setScreen(null);
            }
            updateFullbright(client);
            updateEntityEsp(client);
            WorldScanner.tick(client);
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("mehmetclient", "client_hud"),
                (graphics, deltaTracker) -> renderHud(graphics, Minecraft.getInstance()));
    }

    private static void renderHud(net.minecraft.client.gui.GuiGraphics graphics, Minecraft client) {
        if (client.player == null || client.options.hideGui) return;

        int x = 8;
        int y = 8;
        int width = 232;
        int baseHeight = 58;
        int listY = y + baseHeight + 5;
        int shown = 0;

        if (ClientModules.isEnabled("HUD")) {
            graphics.fill(x - 4, y - 4, x + width, y + baseHeight, 0xB8101020);
            graphics.fill(x - 4, y - 4, x + width, y - 2, 0xFF9B5CFF);
            graphics.drawString(client.font, Component.literal("MEHMET CLIENT v1.2.1"), x, y, 0xFFD9B8FF);
            graphics.drawString(client.font, Component.literal("XYZ: " + (int) client.player.getX() + " / "
                    + (int) client.player.getY() + " / " + (int) client.player.getZ()), x, y + 13, 0xFFFFFFFF);
            graphics.drawString(client.font, Component.literal("Active modules: " + ClientModules.enabledCount()), x, y + 26, 0xFFB8B8C8);
            graphics.drawString(client.font, Component.literal("Markers: " + WorldScanner.visibleMarkers().size()), x, y + 39, 0xFFB8B8C8);

            List<WorldScanner.Marker> hudMarkers = WorldScanner.visibleMarkers().stream()
                    .filter(marker -> marker.pos().distToCenterSqr(client.player.position()) <= 40.0 * 40.0)
                    .sorted((a, b) -> Double.compare(
                            a.pos().distToCenterSqr(client.player.position()),
                            b.pos().distToCenterSqr(client.player.position())))
                    .limit(5)
                    .toList();
            for (WorldScanner.Marker marker : hudMarkers) {
                String text = marker.label() + "  " + marker.pos().getX() + " " + marker.pos().getY() + " " + marker.pos().getZ();
                graphics.fill(x - 4, listY + shown * 12 - 2, x + width, listY + shown * 12 + 10, 0xA8101020);
                graphics.drawString(client.font, Component.literal(text), x, listY + shown * 12, marker.color());
                shown++;
            }
        }

        // Name Tags is independent of the main HUD toggle.
        if (ClientModules.isEnabled("Name Tags") && client.level != null) {
            int playerY = ClientModules.isEnabled("HUD") ? listY + shown * 12 + (shown > 0 ? 4 : 0) : y;
            int count = 0;
            for (Player player : client.level.players()) {
                if (player == client.player || count >= 4) continue;
                double distance = Math.sqrt(player.distanceToSqr(client.player));
                if (distance > 48.0) continue;
                graphics.fill(x - 4, playerY + count * 12 - 2, x + width, playerY + count * 12 + 10, 0xA8101020);
                graphics.drawString(client.font, Component.literal(player.getName().getString()
                        + "  " + (int) distance + "m"), x, playerY + count * 12, 0xFFFF80C0);
                count++;
            }
        }
    }

    private static void updateFullbright(Minecraft client) {
        boolean wanted = ClientModules.isEnabled("Fullbright");
        if (client.options == null) return;
        if (wanted && !appliedFullbright) {
            originalGamma = client.options.gamma().get();
            client.options.gamma().set(1.0D);
            appliedFullbright = true;
        } else if (!wanted && appliedFullbright) {
            if (originalGamma != null) client.options.gamma().set(originalGamma);
            originalGamma = null;
            appliedFullbright = false;
        }
    }

    private static void updateEntityEsp(Minecraft client) {
        boolean playerEsp = ClientModules.isEnabled("Player ESP");
        boolean mobEsp = ClientModules.isEnabled("Mob ESP");
        if (client.level == null || client.player == null) {
            clearGlow();
            return;
        }
        Set<Entity> desired = Collections.newSetFromMap(new IdentityHashMap<>());
        if (playerEsp || mobEsp) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity == client.player || !entity.isAlive()) continue;
                boolean match = (playerEsp && entity instanceof Player)
                        || (mobEsp && entity instanceof Mob && !(entity instanceof Player));
                if (!match) continue;

                // Track only glow flags this module set, so we don't undo server/other-mod glow state.
                if (GLOWING_ENTITIES.contains(entity)) {
                    desired.add(entity);
                } else if (!entity.isCurrentlyGlowing()) {
                    entity.setGlowingTag(true);
                    desired.add(entity);
                }
            }
        }
        for (Entity old : GLOWING_ENTITIES) {
            if (!desired.contains(old)) old.setGlowingTag(false);
        }
        GLOWING_ENTITIES.clear();
        GLOWING_ENTITIES.addAll(desired);
    }

    private static void clearGlow() {
        for (Entity entity : GLOWING_ENTITIES) entity.setGlowingTag(false);
        GLOWING_ENTITIES.clear();
    }
}
