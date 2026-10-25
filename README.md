# Mehmet Client v1.3.0

Minecraft Java 1.21.11 / Fabric / Java 21.

## Implemented in this version
- Right Shift menu, module toggles, HUD and Fullbright.
- Block ESP target selector: open the main menu, choose Render, then **right-click Block ESP**.
- Block ESP uses a custom line pipeline with depth testing disabled, so target block outlines render through walls.
- Player ESP and Mob ESP draw bounding-box outlines through walls.
- Netherite Finder highlights Ancient Debris.
- Block scanning checks the full vertical build range of client-loaded chunks, so a player at Y=40 can find a selected block at Y=-55 if its chunk is loaded.
- BaseFinding heuristics inspect only client-loaded blocks and may report false positives.
- RTP Base Finder sends `/rtpanadünya` (without slash through the client command API) and repeats after a wait while no selected Block ESP target is found. It stops and reports `TESPIT EDILDI` when a selected target is detected in the loaded scan area. This command is server-specific and only works if the server supports it.

## Limits
The client cannot inspect chunks that the server has not sent/loaded on the client. The vertical scan is full-height but horizontal range is 16 blocks from the player (roughly the immediate nearby chunk area). Increase this radius only if you accept the CPU cost. Server plugins can reject or rate-limit automated RTP commands; use only where permitted by the server rules.

## Build via GitHub Actions
Push the repository and open Actions. The workflow builds the mod and uploads one runnable `MehmetClient.jar` artifact. The `-sources.jar` is not the installable mod.

The source has not been built in this environment; use the GitHub Actions result to confirm compilation for your current repository.
