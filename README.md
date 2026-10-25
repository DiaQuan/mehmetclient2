# Mehmet Client v1.2.0

A client-side Fabric mod for **Minecraft Java Edition 1.21.11**. Built with Java 21 and Mojang official mappings.

## What's implemented in this version

### Render / ESP
- **Block ESP**: marks ore, container, and selected valuable block positions in the client-loaded nearby area.
- **Ore Sim**: labels ore blocks already loaded by the client.
- **Netherite Finder**: marks loaded Ancient Debris blocks.
- **Storage ESP**: marks loaded chest/barrel/shulker/furnace and other container-type blocks.
- **Block Notifier**: action-bar notification for newly detected ore blocks within 18 blocks.
- **Player ESP**: client-side glow outline + box around player entities currently rendered by the client.
- **Mob ESP**: client-side glow outline + box around rendered mobs.
- **RealHitBox**: draws outlines for nearby entity hitboxes.
- **Name Tags**: displays nearby player names and distance in the HUD area.
- **Fullbright**: sets the vanilla gamma option to its maximum and restores the prior value when disabled.
- **HUD**: displays coordinates, active module count, and the closest scan markers.

### BaseFinding / world scan
- **Block Entity Debug**: marks loaded block entities with their registered type.
- **Hole ESP**: marks 1×1 holes with a solid floor and solid horizontal walls, near the player's Y level.
- **Light Finder**: marks loaded blocks with block light emission.
- **Prime Chunk Finder**: marks candidate chunks with multiple containers or block entities.
- **RTP Base Finder**: marks chunks with a combination of containers and suspicious blocks.
- **Sus Chunk Finder**: marks chunks with repeated block entities or suspicious block patterns.
- **Suspicious ESP**: marks containers, obsidian variants, Ancient Debris, beacons, and selected valuable blocks.
- **Tunnel Base Finder**: marks corridor-shaped tunnel segments around the player.
- **Seed Chunk Finder**: emits a **chunk heuristic only**; it does not recover or derive a world seed.
- **Chunk Finder**: marks chunks with container or ore signals.

All scans are limited to blocks/chunks the vanilla client has **already loaded** in a 33×33 horizontal area and a limited vertical band around the player. The mod does not request chunks, issue commands, inspect server files, or guarantee that a marked chunk contains a player base. Base-finding results are heuristic and can have false positives or misses.

## Controls
- **Right Shift**: open/close the menu.
- Click a category and click a module row to toggle implemented modules.
- Rows marked `SOON` are not implemented and cannot be toggled; this intentionally avoids pretending that unavailable modules work.

## Build with GitHub Actions

1. Create a GitHub repository and upload the contents of this project folder to its root (including `.github/workflows/build.yml`).
2. Open **Actions**. `Build Mehmet Client JAR` starts on push, or use **Run workflow**.
3. Open the completed workflow run and download the `MehmetClient-1.2.0-<commit>` artifact.
4. Put `mehmetclient-1.2.0.jar` from the artifact into the `.minecraft/mods` folder alongside Fabric Loader for Minecraft 1.21.11. Install the matching Fabric API version if required by the mod.

The workflow runs `gradle clean build` on Java 21 and uploads the remapped JAR only if the build succeeds.

## Important build verification note

This source package has not been executed by GitHub Actions from this environment. The build workflow is configured to perform the real compile; do not treat a ZIP link as proof that the resulting JAR has already compiled. If Actions reports another compiler error, use the exact log to target the next API mismatch.

## Mojang mappings notice

This project requests the official Mojang mappings during the build. Those mappings are provided under Microsoft's terms and at the user's risk, and may not be redistributed complete and unmodified. The project does not package a copy of the mappings. See the Minecraft EULA and the mapping notice presented by the build.
