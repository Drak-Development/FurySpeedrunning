# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FurySpeedrunning is a Minecraft Spigot plugin (1.21.11) for cooperative speedrunning. Players share inventory and health while trying to beat the Ender Dragon. Spectators watch invisibly in creative mode. The plugin depends on **BukkitOfUtils** as its core framework.

## Build Commands

```bash
# Build the shadow JAR (output: target/libs/FurySpeedrunning-<version>.jar)
./gradlew shadowJar

# Clean build
./gradlew clean shadowJar
```

No tests exist. No linter configured.

## Architecture

### Core Flow
`FurySpeedrunning` (main plugin) → initializes config, GUI API, lobby world, listeners, commands.

### Game States (`enums/GameState`)
- **LOBBY**: Players in void world, select roles via GUI, start game
- **PLAYING**: Active speedrun with shared inventory/health, portal linking, dragon kill detection

### Player Roles (`enums/PlayerRole`)
- **PLAYER**: Survival mode, shared inventory + health + XP with all other players
- **SPECTATOR**: Creative mode, hidden from players via `hidePlayer()`, teleport GUI via Nether Star

### Key Managers
- **`GameManager`**: Controls game lifecycle (start/stop), role setup, spectator visibility
- **`PlayerManager`**: Static `ConcurrentHashMap<UUID, PlayerData>` registry
- **`InventorySyncManager`**: Syncs inventory/health/XP across PLAYER-role players with `syncing` guard flag to prevent infinite loops
- **`WorldManager`**: Creates overworld/nether/end with same seed, handles chunk pre-generation and async world deletion
- **`LobbyManager`**: Void lobby world with spawn platform, hotbar item management, GUI item marker detection

### Event Listeners (all extend `AbstractConglomerate`)
- **`MainListener`**: Join/quit handling with state-aware logic (lobby vs reconnect during game)
- **`ItemProtectionListener`**: Prevents GUI items from being dropped/moved; opens GUI on right-click
- **`SharedInventoryListener`**: Syncs inventory on click/pickup/drop (MONITOR priority, next-tick scheduling)
- **`SharedHealthListener`**: Syncs health/food on damage/regen/death; all players die together
- **`SpectatorListener`**: Blocks spectator interaction with game world
- **`PortalListener`**: Redirects nether/end portals to custom game worlds
- **`DragonListener`**: Detects Ender Dragon death → triggers game completion

### GUI (ObliviateInvs via BukkitOfUtils)
- **`LobbyGui`**: 3-row chest — role selection (slots 11, 15) + start game (slot 13)
- **`SpectatorGui`**: 3-row chest — player heads for teleportation

### Commands
- `/managegame <start|stop>` — requires `furyspeedrunning.manage` permission
- `/playas <player|spectator>` — lobby only

## Key Dependencies

- **Spigot 1.21.11** — server API (compileOnly). Note: GameRule constants were renamed (e.g., `ADVANCE_TIME` not `DO_DAYLIGHT_CYCLE`, `SPAWN_MOBS` not `DO_MOB_SPAWNING`, `SHOW_ADVANCEMENT_MESSAGES` not `ANNOUNCE_ADVANCEMENTS`)
- **BukkitOfUtils 1.18.0** — `BetterPlugin`, `SimpleConfiguration`, `ListenerConglomerate`
- **ObliviateInvs** (via BukkitOfUtils) — GUI framework: extend `Gui`, use `Icon` for items
- **Adventure API 4.17.0** — required on classpath for ObliviateInvs `Component` constructor resolution
- **Lombok** — `@Getter`/`@Setter` throughout
- **JitPack** repo required in build.gradle for BukkitOfUtils transitive deps (TheBase, UniversalScheduler)

## Build System Notes

- Dependencies in `dependencies.gradle`. Folia API was removed to avoid `GameRule` classpath conflict.
- `plugin.yml` uses resource expansion from `gradle.properties`
- Shadow JAR is minimized
- Config values use `getOrSetDefault(path, default)` pattern with `reloadResource()` before reads

## GUI Item Marker

GUI items (lobby Nether Star, spectator Nether Star) are identified by a hidden lore marker string (`§r§0§f§s§g`). `LobbyManager.isGuiItem()` checks for this marker.
