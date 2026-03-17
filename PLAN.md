# FurySpeedrunning Implementation Plan

## Phase 0: Documentation & API Reference

### Verified APIs

**BukkitOfUtils (1.18.0)**:
- `BetterPlugin`: lifecycle via `onBaseEnabled()`/`onBaseDisable()`, `logInfo()`, `sync()`, `registerFactory()`
- `SimpleConfiguration`: `getOrSetDefault(path, default)`, `reloadResource()`, `setData(path, value)`
- `ListenerConglomerate`: combined Bukkit `@EventHandler` + BOU `@BaseProcessor` listener
- `CommandBuilder`: fluent command registration — `new CommandBuilder("name", plugin).setExecutionHandler(ctx -> ...).build()`
- `CommandContext`: `getArgCount()`, `getArg(index).getContent()`, `getPlayer()`, `sendMessage()`, `isPlayer()`
- `CommandResult`: `SUCCESS`, `FAILURE`
- `TaskManager`: `runTask()`, `runTaskLater(runnable, ticks)`, `runTaskTimer(runnable, delay, period)`

**ObliviateInvs (4.3.0 via BOU)**:
- `Gui`: extend, pass `(player, id, title, rows)`, override `onOpen(InventoryOpenEvent)`, `onClick(InventoryClickEvent)` (return false = cancel clicks)
- `Icon`: `new Icon(Material)`, `.setName(str)`, `.setLore(str...)`, `.onClick(consumer)`, `.getItem()`
- `InventoryAPI`: `new InventoryAPI(plugin).init()` — required in onEnable
- GUI auto-cancels clicks unless `onClick` returns true

**Spigot 1.21.11**:
- `WorldCreator`: `.seed(long)`, `.environment(Environment)`, `.generator(ChunkGenerator)`, `.createWorld()`
- `ChunkGenerator`: override `shouldGenerateNoise()` etc. returning false for void world
- `Player.hidePlayer(Plugin, Player)` / `showPlayer(Plugin, Player)` for visibility
- `Player.setGameMode(GameMode)`, `Player.setHealth(double)`, `Player.getInventory()`
- `Bukkit.unloadWorld(world, save)` + recursive file delete for world cleanup

### Anti-Patterns to Avoid
- Do NOT call `InventoryAPI.init()` more than once
- Do NOT use deprecated `Player.hidePlayer(Player)` — use `hidePlayer(Plugin, Player)`
- Do NOT call `player.damage()` inside `EntityDamageEvent` handler on the same player (infinite loop) — use a guard flag
- Do NOT delete world folders on the main thread — use async
- Do NOT use `Bukkit.getScheduler()` directly — use `TaskManager` for Folia compatibility

---

## Phase 1: Foundation — Enums, Data Classes, Config

**Goal**: Establish core data model and configuration.

### Files to Create

1. **`enums/GameState.java`** — `LOBBY`, `PLAYING`
2. **`enums/PlayerRole.java`** — `PLAYER`, `SPECTATOR`
3. **`data/PlayerData.java`** — per-player data: UUID, role, reference to Bukkit Player
4. **`data/PlayerManager.java`** — static registry: `Map<UUID, PlayerData>`, `getOrCreatePlayer()`, `removePlayer()`, `getPlayersByRole()`
5. **`data/GameManager.java`** — singleton managing current `GameState`, active world names, game lifecycle methods

### Files to Modify

6. **`config/MainConfig.java`** — Add config getters:
   - `getSeeds()` → `List<Long>` from `seeds` list
   - `getLobbySpawnX/Y/Z()` → lobby spawn coordinates
   - `getMaxPlayers()` → int
   - `getPreGenerateRadius()` → int (chunk radius)
   - `getWorldPrefix()` → string prefix for generated worlds
   - `isAutoStartEnabled()` → boolean

### Verification
- [ ] `GameState` and `PlayerRole` enums compile
- [ ] `PlayerManager.getOrCreatePlayer(player)` returns PlayerData with default PLAYER role
- [ ] `MainConfig` generates `config.yml` with all default values on first load
- [ ] `GameManager.getState()` returns `LOBBY` by default
- [ ] Build succeeds: `./gradlew shadowJar`

---

## Phase 2: Lobby System — Void World, Hotbar Items, Item Protection

**Goal**: Create void lobby world, give hotbar items, prevent item manipulation.

### Files to Create

7. **`world/VoidChunkGenerator.java`** — extends `ChunkGenerator`, all `shouldGenerate*()` return false
8. **`world/LobbyManager.java`** — creates/manages void lobby world with spawn platform (small stone platform at y=64), teleports players to lobby
9. **`events/ItemProtectionListener.java`** — extends `AbstractConglomerate`:
   - Cancel `InventoryClickEvent` for GUI items (check for custom NBT/name marker)
   - Cancel `PlayerDropItemEvent` for GUI items
   - Cancel `PlayerInteractEvent` on protected items (open GUI instead)

### Files to Modify

10. **`events/MainListener.java`** — on join: teleport to lobby, give hotbar items, set role to PLAYER; on quit: cleanup PlayerData
11. **`FurySpeedrunning.java`** — `onBaseEnabled`: init `InventoryAPI`, create lobby world; `onBaseDisable`: cleanup

### Hotbar Item Design
- **Slot 4**: Nether Star named `&a&lGame Menu` — right-click opens main GUI
- Item has a custom model data or lore marker to identify it as a GUI item

### Verification
- [ ] Void world generates with only a small spawn platform
- [ ] Players join into lobby world at spawn platform
- [ ] Nether Star appears in slot 4 on join
- [ ] Items cannot be dropped, moved, or modified
- [ ] Right-clicking Nether Star opens GUI (Phase 3)

---

## Phase 3: GUI System — Role Selection & Game Start

**Goal**: Implement ObliviateInvs GUIs for lobby interaction.

### Files to Create

12. **`gui/LobbyGui.java`** — extends `Gui`:
    - 3-row chest GUI titled `&6&lGame Menu`
    - Slot 11: Player role button (Diamond Sword) — sets role to PLAYER, updates icon
    - Slot 13: Start Game button (Emerald Block) — calls `GameManager.startGame()` (permission check)
    - Slot 15: Spectator role button (Ender Eye) — sets role to SPECTATOR, updates icon
    - Currently selected role highlighted with enchant glow
    - `onClick()` returns false (cancel all clicks by default)

### Verification
- [ ] Right-clicking hotbar item opens LobbyGui
- [ ] Clicking Player/Spectator changes role in PlayerData
- [ ] Selected role has enchant glow indicator
- [ ] Start button triggers game start (stub in Phase 4)
- [ ] No item duplication or desync

---

## Phase 4: World Generation & Game Start

**Goal**: Generate overworld/nether/end worlds and start the game.

### Files to Create

13. **`world/WorldManager.java`** — handles:
    - `createGameWorlds(long seed)`: creates 3 worlds with same seed (overworld, nether, end) using `WorldCreator`
    - `deleteGameWorlds()`: unloads + async deletes all 3 world folders
    - `getOverworld()` / `getNether()` / `getEnd()` getters
    - World names: `{prefix}_overworld`, `{prefix}_nether`, `{prefix}_end`
14. **`world/ChunkPreGenerator.java`** — pre-generates chunks in a radius around spawn:
    - Uses `world.getChunkAtAsync()` for non-blocking chunk loading
    - Tracks progress, fires callback when complete
    - Configurable radius from MainConfig

### Files to Modify

15. **`data/GameManager.java`** — implement `startGame()`:
    1. Pick random seed from config
    2. Create worlds via WorldManager
    3. Pre-generate chunks
    4. On complete: set state to PLAYING, teleport players, apply roles
16. **`data/GameManager.java`** — implement `stopGame()`:
    1. Teleport all players to lobby
    2. Reset inventories, health, food
    3. Delete game worlds
    4. Set state to LOBBY
    5. Re-give hotbar items

### Verification
- [ ] `/managegame start` creates 3 worlds with random seed from config
- [ ] Chunk pre-generation completes before teleport
- [ ] Nether portals link to the custom nether world (same seed)
- [ ] End portals link to the custom end world
- [ ] `/managegame stop` cleans up all worlds and returns players to lobby
- [ ] No leftover world folders after stop

---

## Phase 5: Player Role — Shared Inventory & Health

**Goal**: Synchronize inventory and health across all PLAYER-role participants.

### Files to Create

17. **`events/SharedInventoryListener.java`** — extends `AbstractConglomerate`:
    - On `InventoryClickEvent` / `InventoryOpenEvent` / `InventoryCloseEvent`: sync inventory to all PLAYER-role players
    - On pickup/drop events: sync
    - Debounce sync to avoid cascading (use a boolean `syncing` flag)
18. **`events/SharedHealthListener.java`** — extends `AbstractConglomerate`:
    - On `EntityDamageEvent` where entity is a PLAYER-role player: apply same final damage to all other PLAYER-role players (skip if already syncing)
    - On `EntityRegainHealthEvent`: sync healing to all
    - On player death: all PLAYER-role players die (game over)
    - Guard against infinite loops with `isSyncing` flag
19. **`managers/InventorySyncManager.java`** — static utility:
    - `syncInventory(Player source)`: copies source inventory contents + armor + offhand to all other PLAYER-role players
    - `syncHealth(Player source)`: sets all PLAYER-role players to same health
    - Thread-safe syncing flag

### Verification
- [ ] Picking up an item appears in all PLAYER inventories
- [ ] Taking damage applies to all PLAYER-role players
- [ ] Healing syncs across all players
- [ ] One player dying kills all PLAYER-role players
- [ ] No infinite loop on damage/inventory sync
- [ ] Inventory sync doesn't affect SPECTATOR-role players

---

## Phase 6: Spectator Role — Invisibility & Teleport GUI

**Goal**: Make spectators fully invisible and provide teleport GUI.

### Files to Modify

20. **`data/GameManager.java`** — on game start, for SPECTATOR-role players:
    - Set `GameMode.CREATIVE`
    - Hide from all PLAYER-role players via `player.hidePlayer(plugin, spectator)`
    - Give Nether Star in slot 4 for spectator GUI

### Files to Create

21. **`gui/SpectatorGui.java`** — extends `Gui`:
    - Lists all PLAYER-role players as skull icons
    - Clicking a player head teleports spectator to that player
    - Auto-refreshes player list on open
22. **`events/SpectatorListener.java`** — extends `AbstractConglomerate`:
    - On right-click Nether Star (spectator): open SpectatorGui
    - On SPECTATOR join during PLAYING state: re-hide, re-apply creative, re-give nether star
    - Prevent spectators from interacting with blocks/entities (cancel relevant events)

### Packet-Level Invisibility Notes
- Use `Player.hidePlayer(Plugin, Player)` as primary mechanism — this removes the player from the tab list and prevents entity packets from being sent, which is effectively packet-level
- Additionally set spectators invisible with potion effects as backup
- Cancel all spectator damage/interaction events

### Verification
- [ ] Spectators are invisible to PLAYER-role players (not in tab list, not visible)
- [ ] Spectators can see all players
- [ ] Nether Star opens SpectatorGui with player heads
- [ ] Clicking player head teleports spectator
- [ ] Spectators cannot interact with/affect the game world
- [ ] Spectators cannot take or deal damage

---

## Phase 7: Commands

**Goal**: Register `/managegame` and `/playas` commands.

### Files to Create

23. **`commands/ManageGameCommand.java`** — uses `CommandBuilder`:
    - `/managegame start` — calls `GameManager.startGame()`, requires permission `furyspeedrunning.manage`
    - `/managegame stop` — calls `GameManager.stopGame()`
    - Tab completion: `["start", "stop"]`
24. **`commands/PlayAsCommand.java`** — uses `CommandBuilder`:
    - `/playas player` — sets role to PLAYER (only in LOBBY state)
    - `/playas spectator` — sets role to SPECTATOR (only in LOBBY state)
    - Tab completion: `["player", "spectator"]`

### Files to Modify

25. **`plugin.yml`** — add command definitions for `managegame` and `playas`
26. **`FurySpeedrunning.java`** — register commands in `onBaseEnabled()`

### Verification
- [ ] `/managegame start` starts the game
- [ ] `/managegame stop` stops and cleans up
- [ ] `/playas spectator` changes role in lobby
- [ ] `/playas player` changes role in lobby
- [ ] Commands fail gracefully outside valid state
- [ ] Tab completion works
- [ ] Permission checks work

---

## Phase 8: Dragon Kill Detection & Game Completion

**Goal**: Detect Ender Dragon death and end the game.

### Files to Modify

27. **`events/MainListener.java`** — add `EntityDeathEvent` handler:
    - If `EnderDragon` dies in the game's end world → game won
    - Announce victory, record time
    - After delay: call `GameManager.stopGame()`

### Verification
- [ ] Killing the Ender Dragon triggers game completion
- [ ] Victory message broadcast to all players
- [ ] Game returns to lobby after delay
- [ ] Works only for the game's custom end world (not other end worlds)

---

## Phase 9: Polish & Edge Cases

**Goal**: Handle edge cases and finalize.

### Items to Address
- Player disconnect/reconnect during PLAYING state — restore role, inventory, teleport to game world
- Server shutdown during PLAYING state — clean up worlds on next start
- Prevent players from leaving game worlds (world border or teleport back)
- Food level sync for PLAYER-role (alongside health)
- Clear inventories on game start for PLAYER-role
- Portal linking: handle `PlayerPortalEvent` to redirect to custom nether/end worlds
- Prevent SPECTATOR from picking up items
- Handle player death respawn — respawn in game world, not default world

### Verification
- [ ] Reconnecting player gets correct state
- [ ] Stale world folders cleaned on startup
- [ ] Portals work correctly between custom worlds
- [ ] Full game loop: lobby → start → play → kill dragon → lobby
- [ ] Build succeeds: `./gradlew shadowJar`
