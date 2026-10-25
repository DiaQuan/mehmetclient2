package com.mehmetclient.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry of implemented client-side modules. Unsupported modules stay visibly unavailable. */
public final class ClientModules {
    private ClientModules() {}

    public static final List<String> CATEGORIES = List.of("General", "BaseFinding", "Render");

    private static final Map<String, List<String>> MODULES = new LinkedHashMap<>();
    private static final Map<String, Boolean> ENABLED = new LinkedHashMap<>();

    private static final Set<String> IMPLEMENTED = Set.of(
            // ESP / block render
            "Block ESP", "Block Notifier", "Fullbright", "HUD", "Mob ESP", "Name Tags",
            "Ore Sim", "Player ESP", "RealHitBox", "Storage ESP", "Netherite Finder",
            // BaseFinding
            "Block Entity Debug", "Hole ESP", "Light Finder", "Prime Chunk Finder",
            "RTP Base Finder", "Sus Chunk Finder", "Suspicious ESP", "Tunnel Base Finder",
            "Seed Chunk Finder", "Chunk Finder"
    );

    private static final Set<String> SCANNING = Set.of(
            "Block ESP", "Block Notifier", "Ore Sim", "Netherite Finder", "Storage ESP",
            "Block Entity Debug", "Hole ESP", "Light Finder", "Prime Chunk Finder",
            "RTP Base Finder", "Sus Chunk Finder", "Suspicious ESP", "Tunnel Base Finder",
            "Seed Chunk Finder", "Chunk Finder"
    );

    static {
        MODULES.put("General", List.of(
                "Anti Trap", "Auto Sell", "Auto Spawner Sell", "Chunk Finder", "Fake Pay",
                "Fake Stats", "Item Dropper", "Netherite Finder", "Player Chunks", "Spawner Protect"));
        MODULES.put("BaseFinding", List.of(
                "Block Entity Debug", "Hole ESP", "Light Finder", "Prime Chunk Finder", "RTP Base Finder",
                "Sus Chunk Finder", "Suspicious ESP", "Tunnel Base Finder", "Seed Chunk Finder"));
        MODULES.put("Render", List.of(
                "Block ESP", "Block Notifier", "Free Look", "Fullbright", "HUD", "Jump Circles",
                "Mob ESP", "Name Tags", "Ore Sim", "Pearl Trajectory", "Player ESP", "RealHitBox",
                "Music HUD", "Storage ESP", "SwingSpeed"));

        MODULES.values().forEach(list -> list.forEach(name -> ENABLED.put(name, false)));
        ENABLED.put("HUD", true);
    }

    public static List<String> modules(String category) {
        return MODULES.getOrDefault(category, List.of());
    }

    public static boolean isEnabled(String module) {
        return ENABLED.getOrDefault(module, false);
    }

    public static boolean hasBehavior(String module) {
        return IMPLEMENTED.contains(module);
    }

    public static void toggle(String module) {
        if (hasBehavior(module) && ENABLED.containsKey(module)) {
            ENABLED.put(module, !ENABLED.get(module));
        }
    }

    public static int enabledCount() {
        return (int) ENABLED.entrySet().stream()
                .filter(entry -> entry.getValue() && hasBehavior(entry.getKey()))
                .count();
    }

    public static boolean anyScanningEnabled() {
        return SCANNING.stream().anyMatch(ClientModules::isEnabled);
    }
}
