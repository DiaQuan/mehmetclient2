package com.mehmetclient.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side scanner for the currently loaded area only. It never requests chunks,
 * sends server commands, reads server files, or claims to recover a world seed.
 * Base-finding results are heuristics and can produce false positives.
 */
public final class WorldScanner {
    private WorldScanner() {}

    public record Marker(String module, BlockPos pos, String label, int color, boolean chunkHint) {}
    private record ChunkKey(int x, int z) {}

    private static final class ChunkStats {
        int blockEntities;
        int containers;
        int suspiciousBlocks;
        int ores;
        BlockPos anchor;
    }

    private static volatile List<Marker> markers = List.of();
    private static int ticksUntilScan;
    private static final Set<BlockPos> notifiedBlocks = new HashSet<>();
    private static final int HORIZONTAL_RADIUS = 16;
    private static final int RTP_WAIT_TICKS = 160;
    private static boolean rtpActive;
    private static int rtpWaitTicks;
    private static boolean selectedBlockFound;
    private static final int MAX_MARKERS = 1400;

    public static void registerRenderEvents() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldScanner::renderWorld);
    }

    public static List<Marker> visibleMarkers() {
        return markers.stream()
                .filter(marker -> ClientModules.isEnabled(marker.module()))
                .sorted(Comparator.comparing(Marker::chunkHint).thenComparing(Marker::label))
                .toList();
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            markers = List.of();
            notifiedBlocks.clear();
            ticksUntilScan = 0;
            rtpActive = false;
            rtpWaitTicks = 0;
            selectedBlockFound = false;
            return;
        }
        boolean rtpEnabled = ClientModules.isEnabled("RTP Base Finder");
        if (rtpEnabled && !rtpActive) {
            rtpActive = true;
            rtpWaitTicks = RTP_WAIT_TICKS;
            markers = List.of();
            sendRtpCommand(client);
            client.player.displayClientMessage(Component.literal("§5[Mehmet] §dRTP Base Finder: teleport requested; scanning loaded area..."), false);
            return;
        }
        if (!rtpEnabled) {
            rtpActive = false;
            rtpWaitTicks = 0;
        } else if (rtpWaitTicks > 0) {
            rtpWaitTicks--;
            if (rtpWaitTicks == 0) {
                scanLoadedArea(client);
                if (selectedBlockFound) {
                    ClientModules.setEnabled("RTP Base Finder", false);
                    rtpActive = false;
                    client.player.displayClientMessage(Component.literal("§5[Mehmet] §aTESPIT EDILDI! Block ESP target found; automatic RTP stopped."), false);
                    return;
                }
                sendRtpCommand(client);
                rtpWaitTicks = RTP_WAIT_TICKS;
                client.player.displayClientMessage(Component.literal("§5[Mehmet] §7No selected target in loaded area; trying RTP again..."), true);
            }
            return;
        }

        if (!ClientModules.anyScanningEnabled()) {
            markers = List.of();
            notifiedBlocks.clear();
            return;
        }
        if (--ticksUntilScan > 0) return;
        ticksUntilScan = 40;
        scanLoadedArea(client);
    }

    private static void sendRtpCommand(Minecraft client) {
        if (client.player == null || client.getConnection() == null) return;
        // Server-specific command; only works where this command is supported.
        client.getConnection().sendCommand("rtpanadünya");
    }

    private static void scanLoadedArea(Minecraft client) {
        var level = client.level;
        var player = client.player;
        BlockPos origin = player.blockPosition();
        int centerY = origin.getY();
        Map<ChunkKey, ChunkStats> chunks = new LinkedHashMap<>();
        List<Marker> next = new ArrayList<>();
        Set<String> duplicate = new LinkedHashSet<>();
        Set<BlockPos> oresSeenThisScan = new HashSet<>();

        selectedBlockFound = false;
        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
            for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                int x = origin.getX() + dx;
                int z = origin.getZ() + dz;
                // Search the entire vertical range of already-loaded chunks, not just nearby Y levels.
                for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);

                    if (state.isAir()) {
                        if (ClientModules.isEnabled("Hole ESP") && isHole(level, pos, centerY)) {
                            add(next, duplicate, "Hole ESP", pos, "HOLE", 0xFF58D5FF, false);
                        }
                        if (ClientModules.isEnabled("Tunnel Base Finder") && isTunnelStart(level, pos, centerY)) {
                            add(next, duplicate, "Tunnel Base Finder", pos, "TUNNEL", 0xFFFFA45B, false);
                        }
                        continue;
                    }
                    if (state.is(Blocks.BEDROCK)) continue;

                    String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    String path = pathOf(blockId);
                    boolean ore = isOre(path);
                    boolean container = isContainerId(path);
                    boolean valuable = isValuableId(path);
                    boolean suspicious = isSuspiciousId(path);
                    BlockEntity blockEntity = level.getBlockEntity(pos);

                    if (ore) {
                        oresSeenThisScan.add(pos.immutable());
                        if (ClientModules.isEnabled("Ore Sim")) {
                            add(next, duplicate, "Ore Sim", pos, "ORE: " + shortName(path), 0xFF50C8FF, false);
                        }
                        if (ClientModules.isEnabled("Netherite Finder") && path.equals("ancient_debris")) {
                            add(next, duplicate, "Netherite Finder", pos, "ANCIENT DEBRIS", 0xFFFF8A50, false);
                        }
                    }
                    if (ClientModules.isBlockEspTarget(path)) {
                        if (ClientModules.isEnabled("RTP Base Finder")) selectedBlockFound = true;
                        if (ClientModules.isEnabled("Block ESP")) {
                            add(next, duplicate, "Block ESP", pos, shortName(path), 0xFF50E780, false);
                        }
                    }
                    if (ClientModules.isEnabled("Storage ESP") && container) {
                        add(next, duplicate, "Storage ESP", pos, shortName(path), 0xFFFFD65A, false);
                    }
                    if (ClientModules.isEnabled("Suspicious ESP") && suspicious) {
                        add(next, duplicate, "Suspicious ESP", pos, shortName(path), 0xFFFF795C, false);
                    }
                    if (ClientModules.isEnabled("Light Finder") && state.getLightEmission() > 0) {
                        add(next, duplicate, "Light Finder", pos, "LIGHT " + state.getLightEmission(), 0xFFFFE66D, false);
                    }
                    if (ClientModules.isEnabled("Block Entity Debug") && blockEntity != null) {
                        String type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString();
                        add(next, duplicate, "Block Entity Debug", pos, "BE " + pathOf(type), 0xFF8CC8FF, false);
                    }

                    ChunkKey chunkKey = new ChunkKey(x >> 4, z >> 4);
                    ChunkStats stats = chunks.computeIfAbsent(chunkKey, ignored -> new ChunkStats());
                    if (blockEntity != null) {
                        stats.blockEntities++;
                        if (stats.anchor == null) stats.anchor = pos.immutable();
                    }
                    if (container) {
                        stats.containers++;
                        if (stats.anchor == null) stats.anchor = pos.immutable();
                    }
                    if (suspicious) {
                        stats.suspiciousBlocks++;
                        if (stats.anchor == null) stats.anchor = pos.immutable();
                    }
                    if (ore) stats.ores++;
                }
            }
        }

        // Chunk findings are deliberately evidence-based heuristics from client-loaded blocks.
        for (Map.Entry<ChunkKey, ChunkStats> entry : chunks.entrySet()) {
            ChunkStats stats = entry.getValue();
            if (stats.anchor == null) continue;
            if (ClientModules.isEnabled("Prime Chunk Finder")
                    && (stats.containers >= 2 || stats.blockEntities >= 3)) {
                add(next, duplicate, "Prime Chunk Finder", stats.anchor, "PRIME CHUNK", 0xFFBA8CFF, true);
            }
            if (ClientModules.isEnabled("RTP Base Finder")
                    && stats.containers >= 1 && stats.suspiciousBlocks >= 2) {
                add(next, duplicate, "RTP Base Finder", stats.anchor, "RTP BASE?", 0xFFFF9C66, true);
            }
            if (ClientModules.isEnabled("Sus Chunk Finder")
                    && (stats.blockEntities >= 2 || stats.suspiciousBlocks >= 3)) {
                add(next, duplicate, "Sus Chunk Finder", stats.anchor, "SUSPICIOUS CHUNK", 0xFFFF5F70, true);
            }
            if (ClientModules.isEnabled("Seed Chunk Finder")
                    && stats.containers >= 1 && (stats.blockEntities >= 2 || stats.suspiciousBlocks >= 3)) {
                add(next, duplicate, "Seed Chunk Finder", stats.anchor, "SEED-CHUNK HINT", 0xFFDB8CFF, true);
            }
            if (ClientModules.isEnabled("Chunk Finder") && (stats.containers >= 1 || stats.ores >= 4)) {
                add(next, duplicate, "Chunk Finder", stats.anchor, "CHUNK SIGNAL", 0xFF76D8FF, true);
            }
        }

        if (ClientModules.isEnabled("Block Notifier")) {
            for (BlockPos pos : oresSeenThisScan) {
                if (!notifiedBlocks.contains(pos)) {
                    String path = pathOf(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
                    double distance = Math.sqrt(pos.getCenter().distanceToSqr(player.position()));
                    if (distance <= 18.0) {
                        player.displayClientMessage(Component.literal("§b[Mehmet] §f" + shortName(path)
                                + " nearby: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
                    }
                }
            }
            notifiedBlocks.addAll(oresSeenThisScan);
            if (notifiedBlocks.size() > 7000) notifiedBlocks.clear();
        } else {
            notifiedBlocks.clear();
        }

        if (next.size() > MAX_MARKERS) next = new ArrayList<>(next.subList(0, MAX_MARKERS));
        markers = List.copyOf(next);
    }

    private static void add(List<Marker> target, Set<String> seen, String module, BlockPos pos,
                            String label, int color, boolean chunkHint) {
        String key = module + "@" + pos.asLong();
        if (seen.add(key)) target.add(new Marker(module, pos.immutable(), label, color, chunkHint));
    }

    private static boolean isHole(LevelReader level, BlockPos pos, int centerY) {
        if (Math.abs(pos.getY() - centerY) > 3) return false;
        if (!level.getBlockState(pos.above()).isAir()) return false;
        if (!level.getBlockState(pos.below()).blocksMotion()) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (!level.getBlockState(pos.relative(direction)).blocksMotion()) return false;
        }
        return true;
    }

    private static boolean isTunnelStart(LevelReader level, BlockPos pos, int centerY) {
        if (Math.abs(pos.getY() - centerY) > 3 || !level.getBlockState(pos.above()).isAir()) return false;
        if (!level.getBlockState(pos.below()).blocksMotion()) return false;
        // Scan east/south corridor directions to reduce duplicate markers. Only already-loaded blocks are read.
        for (Direction direction : List.of(Direction.EAST, Direction.SOUTH)) {
            boolean corridor = true;
            for (int step = 0; step < 5; step++) {
                BlockPos point = pos.relative(direction, step);
                if (!level.hasChunkAt(point) || !level.hasChunkAt(point.above(2))) {
                    corridor = false;
                    break;
                }
                if (!level.getBlockState(point).isAir() || !level.getBlockState(point.above()).isAir()
                        || !level.getBlockState(point.below()).blocksMotion()
                        || !level.getBlockState(point.above(2)).blocksMotion()
                        || !level.getBlockState(point.relative(direction.getClockWise())).blocksMotion()
                        || !level.getBlockState(point.relative(direction.getCounterClockWise())).blocksMotion()) {
                    corridor = false;
                    break;
                }
            }
            if (corridor) return true;
        }
        return false;
    }

    private static boolean isOre(String path) {
        return path.endsWith("_ore") || path.equals("ancient_debris") || path.equals("nether_gold_ore");
    }

    private static boolean isContainerId(String path) {
        return path.contains("chest") || path.equals("barrel") || path.endsWith("shulker_box")
                || path.equals("hopper") || path.equals("furnace") || path.equals("smoker")
                || path.equals("blast_furnace") || path.equals("dispenser") || path.equals("dropper")
                || path.equals("spawner") || path.equals("trial_spawner") || path.equals("vault")
                || path.equals("crafter") || path.equals("brewing_stand");
    }

    private static boolean isValuableId(String path) {
        return path.equals("ancient_debris") || path.equals("diamond_block") || path.equals("emerald_block")
                || path.equals("gold_block") || path.equals("iron_block") || path.equals("beacon")
                || path.equals("respawn_anchor") || path.equals("end_portal_frame");
    }

    private static boolean isSuspiciousId(String path) {
        return isContainerId(path) || path.equals("obsidian") || path.equals("crying_obsidian")
                || path.equals("diamond_block") || path.equals("emerald_block") || path.equals("beacon")
                || path.equals("end_portal_frame") || path.equals("ancient_debris");
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static String shortName(String id) {
        String path = pathOf(id).replace('_', ' ').toUpperCase();
        return path.length() > 22 ? path.substring(0, 22) : path;
    }

    private static void renderWorld(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        List<ThroughWallEspRenderer.Box> boxes = new ArrayList<>();

        boolean markerRendering = ClientModules.isEnabled("Block ESP") || ClientModules.isEnabled("Ore Sim")
                || ClientModules.isEnabled("Netherite Finder") || ClientModules.isEnabled("Storage ESP")
                || ClientModules.isEnabled("Hole ESP") || ClientModules.isEnabled("Light Finder")
                || ClientModules.isEnabled("Block Entity Debug") || ClientModules.isEnabled("Suspicious ESP")
                || ClientModules.isEnabled("Tunnel Base Finder") || ClientModules.isEnabled("Prime Chunk Finder")
                || ClientModules.isEnabled("RTP Base Finder") || ClientModules.isEnabled("Sus Chunk Finder")
                || ClientModules.isEnabled("Seed Chunk Finder") || ClientModules.isEnabled("Chunk Finder");
        if (markerRendering) {
            for (Marker marker : markers) {
                if (!ClientModules.isEnabled(marker.module())) continue;
                if (marker.pos().getCenter().distanceToSqr(client.player.position()) > 64.0 * 64.0) continue;
                boxes.add(ThroughWallEspRenderer.block(marker.pos().getX(), marker.pos().getY(), marker.pos().getZ(), marker.color()));
            }
        }

        boolean entityRendering = ClientModules.isEnabled("Player ESP") || ClientModules.isEnabled("Mob ESP")
                || ClientModules.isEnabled("RealHitBox");
        if (entityRendering) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity == client.player || !entity.isAlive()) continue;
                if (entity.distanceToSqr(client.player) > 64.0 * 64.0) continue;
                boolean isPlayer = entity instanceof Player;
                boolean isMob = entity instanceof Mob && !isPlayer;
                boolean usePlayer = ClientModules.isEnabled("Player ESP") && isPlayer;
                boolean useMob = ClientModules.isEnabled("Mob ESP") && isMob;
                boolean useHitbox = ClientModules.isEnabled("RealHitBox");
                if (!usePlayer && !useMob && !useHitbox) continue;
                int color = isPlayer ? 0xFFFF5DA8 : (isMob ? 0xFFFF9A4D : 0xFF8CC8FF);
                boxes.add(ThroughWallEspRenderer.entity(entity.getBoundingBox().inflate(0.04), color));
            }
        }
        if (boxes.isEmpty()) return;
        PoseStack pose = context.matrices();
        Vec3 camera = context.worldState().cameraRenderState.pos;
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        ThroughWallEspRenderer.draw(client, pose, boxes);
        pose.popPose();
    }
}
