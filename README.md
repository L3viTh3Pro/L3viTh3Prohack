# ChunkScout – Fabric 1.21.11

Client-side Fabric mod project for Minecraft 1.21.11.

## Features in this build

- Right Ctrl opens the ChunkScout settings GUI.
- F6 toggles freecam; W/A/S/D move, Space/Shift move vertically, Left Ctrl speeds up.
- Storage Finder ESP:
  - chest = yellow
  - shulker = pink
  - spawner = black
- Suspicious chunk highlighting when a loaded chunk contains at least 5 chests, hoppers, or shulker boxes.
- Respawn Anchor highlighting through the debug-style overlay.
- Anchor radius is configurable from 0 to 1000 blocks.
- Each feature can be toggled in the GUI.

## Build on Windows

1. Install Java 21.
2. Open this folder.
3. Double-click `build.bat`, or run it from CMD.
4. The finished JAR is placed in `build\libs\`.

The build uses Fabric Loom 1.14.5, which is the Loom generation intended for obfuscated Minecraft 1.21.11.

## Important

This is a client-side mod. The rendering/scan features operate on chunks and block data that are available to the client. Large Anchor radii can require more scanning work, so the anchor scanner processes loaded chunks incrementally rather than blocking the game in one large scan.
