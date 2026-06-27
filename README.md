# VaultOP Tournaments Client Mod

A premium client-side Minecraft mod for the VaultOP Esports tournament platform. Features an integrated tournament dashboard, secure authentication, registration tracker, and real-time tournament details directly inside Minecraft's GUI.

## Features
* 🎮 **Interactive Tournament List**: Browse active, upcoming, and completed tournaments.
* 🔒 **Protected Mode**: Ensures competitive integrity by verifying client-side integrity (mods/resource packs).
* ↻ **Real-Time Sync**: Instant tournament registration status updates and manual force-refresh.
* 👤 **Competitor Profile**: Displays Discord verification, premium/offline client type, and registered events.
* 🏆 **Multi-Version Architecture**: Dedicated structures configured for clean builds across Minecraft releases.

## Repository Structure
Each directory is a self-contained Gradle project configured for a specific Minecraft version:
* `/1.21.5` - Minecraft 1.21.5 Client Mod
* `/1.21.6` - Minecraft 1.21.6 Client Mod
* `/1.21.7` - Minecraft 1.21.7 Client Mod
* `/1.21.8` - Minecraft 1.21.8 Client Mod
* `/1.21.9` - Minecraft 1.21.9 Client Mod
* `/1.21.10` - Minecraft 1.21.10 Client Mod

## Build Instructions
To build the mod for a specific version:
1. Navigate to the desired version folder (e.g., `cd 1.21.6`).
2. Run the Gradle build command:
   ```bash
   ./gradlew :fabric:assemble
   ```
3. The compiled Fabric mod `.jar` will be available in the `fabric/build/libs/` directory.

## License
This project is licensed under the MIT License.
