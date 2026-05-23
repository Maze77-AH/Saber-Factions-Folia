# RealFactions Phase 1 Audit

Date: 2026-05-21

Scope: audit SaberFactions as fork target, then apply only compile-first modernization for a RealFiction-owned RealFactions plugin. This pass intentionally does not claim Folia runtime safety yet.

Reference docs:

- Paper project setup: https://docs.papermc.io/paper/dev/project-setup/
- Paper Folia support: https://docs.papermc.io/paper/dev/folia-support/

## Build And Project Structure

This is a Maven multi-module plugin:

- Root POM: aggregator and shared dependency/repository properties.
- `factions-shared`: primary Bukkit plugin code, `plugin.yml`, commands, listeners, persistence, integrations, GUI code, CoreX addons.
- `factions-modern`: modern compatibility module.
- `factions-legacy`: legacy compatibility module.
- `factions-plugin`: shaded plugin jar assembly.

Before modernization, the root coordinates were `com.massivecraft:Factions:1.6.9.5-4.2.5` and the jar name was `SaberFactions`. The plugin descriptor registered `name: Factions` and `prefix: SaberFactions`.

## Dependency And Integration Inventory

Build dependencies include Spigot API, VaultAPI, PlaceholderAPI, MVdWPlaceholderAPI, WorldGuard-Bridge, WorldEdit Bukkit 6.x, EssentialsX, EssentialsXChat, dynmap, RoseStacker, WildStackerAPI, XSeries, item-nbt-api, Kyori Adventure, bStats, Mojang authlib, json-simple, Guava, and Apache Commons Lang.

Runtime integrations found:

- Vault economy and Vault permissions are required/used.
- PlaceholderAPI and MVdWPlaceholderAPI are soft hooks.
- WorldGuard/WorldEdit checks are used around territory/build rules.
- dynmap integration renders faction territory.
- EssentialsX/EssentialsXChat are compile-time provided hooks.
- RoseStacker/WildStacker are used by spawner upgrade code.
- No direct LuckPerms API usage was found.

## Storage Inventory

Storage is file-backed, not SQL-backed:

- `players.json`, `factions.json`, and `board.json` through Gson-backed JSON persistence.
- Claim data is stored as world plus chunk coordinate mappings, so SaberFactions claim files should remain migration-compatible.
- Additional faction state lives under `faction-data/<id>.yml`.
- Other YAML/config state lives under `configuration/`, `data/`, and `corex/`.
- Legacy name-to-UUID conversion uses Mojang HTTP lookups.

Risk: saves and faction-data loads currently happen from async executors while still holding mutable faction/plugin objects. File IO itself is fine async, but Bukkit object access and shared plugin state must be snapshotted on the owning thread before async IO.

## Scheduler And Async Audit

No existing Folia scheduler API usage was present before this pass. The codebase uses Bukkit global scheduler calls throughout:

- Startup and periodic tasks in `FactionsPlugin`, `MPlugin`, `StartupParameter`, `TimerManager`, `EngineDynmap`, `AsyncPlayerMap`, scoreboards, GUIs, missions, and CoreX addons.
- `TaskRunner` wraps `BukkitScheduler` directly.
- `SpiralTask` uses a single global sync repeating task for chunk claim/unclaim batches.
- Warmups use a global delayed task and store Bukkit task IDs in player state.

Unsafe async Bukkit API paths found:

- `MPlugin.handleCommand(..., async=true)` can execute arbitrary faction commands from async scheduler threads.
- `MPlugin.preEnable()` schedules `SaveTask` asynchronously, which can traverse plugin/faction state.
- Command handlers such as `CmdSethome`, `CmdJoin`, `CmdTag`, `CmdDescription`, `CmdDeinvite`, `CmdUnclaimall`, `CmdSafeunclaimall`, `CmdWarunclaimall`, and `CmdShow` use async scheduler paths while touching factions, players, messages, events, worlds, or commands.
- `FactionsChatListener` handles `AsyncPlayerChatEvent` and can call faction/player APIs, warmups, and teleports.
- `MissionHandler` schedules async mission expiry while mutating faction mission state and messaging factions.
- `StartupParameter` runs `CheckTask` asynchronously, and `CheckTask` touches faction/player messaging before switching only some work back to sync.
- `AutoLeaveProcessTask` and `MemoryFPlayer` use async scheduler calls for messages/logging and faction state paths.
- `AntiRedstoneOnTrapdoorCrash` maintains redstone state from an async timer.

## Bukkit World, Chunk, Entity, Block, Inventory Access

High-touch areas:

- `FactionsPlayerListener`: player movement, territory transitions, auto-claim/auto-unclaim, inventory events, respawn home selection, bucket/place/interact checks.
- `FactionsBlockListener`: block place/break/fade/piston checks, territory checks, and block mutation.
- `FactionsEntityListener`: combat, explosions, mob spawning, entity targeting, projectile/entity interaction, TNT/water behavior, block breaking from explosion handling.
- `UpgradesListener`: spawner delay edits, crop growth/block data mutation, stacked spawner integrations.
- CoreX addons under `org.saberdev.corex.addons`: inventory mutation, nearby entity scans, world border checks, breakNaturally calls, enchantment inventory edits, redstone trapdoor handling.
- GUI/menu code: creates and opens inventories, schedules refreshes, and mutates inventory contents.

These are expected for a Factions plugin, but on Folia they must run on the owning region or entity scheduler. Global scheduler calls are not a safe substitute.

## Teleport, Warp, Home, Claim, Explosion, Combat, Command Paths

Teleport and warmup paths:

- `/f home`: `CmdHome` uses `WarmUpUtil` and then `Player#teleport`.
- `/f warp`: `CmdFWarp`, `CmdAllyFWarp`, `FactionWarpsFrame`, and chat password flow call `Player#teleport`.
- `/f checkpoint`: `CmdCheckpoint` teleports after warmup.
- `/f stuck`: `CmdStuck` uses a global delayed task, scans for a safe location, calls `World#getHighestBlockYAt`, then teleports.
- `/f ahome`: `CmdAHome` directly teleports another player.
- Respawn home path uses `PlayerRespawnEvent#setRespawnLocation`.

Claim paths:

- `/f claim`, `/f unclaim`, claim fill, unclaim fill, corner claim, auto-claim, and auto-unclaim all mutate `Board` and faction state.
- Radius claim/unclaim runs through `SpiralTask`, which is currently a global scheduler loop.
- Spawner chunk checks use actual loaded chunk/tile entity access through `FastChunk#getChunk()` and `ChunkReference#getSpawnerCount`.

Explosion and combat paths:

- `FactionsEntityListener` handles TNT/fireball/creeper/explosion block lists, damage rules, combat flight cooldown cleanup, Enderman block changes, mob spawning/targeting, and entity interaction.
- Combat tag cleanup uses global delayed scheduler tasks.
- Explosion block edits such as `breakNaturally()` must remain on the owning region.

Command paths:

- Most commands run through Bukkit command execution. The dangerous path is the framework-level ability to execute commands asynchronously plus individual commands that schedule async bodies and then touch Bukkit/faction state.

## NMS, Reflection, Version-Specific Code

Reflection/version-specific areas:

- `ReflectionUtils.PackageType` builds legacy `net.minecraft.server.<version>` and `org.bukkit.craftbukkit.<version>` package names.
- Inventory title compatibility uses reflection in GUI listeners.
- `UpgradeManager` reflects the `profile` field on item meta.
- `UpgradesListener` reflects old crop/block-data APIs.
- `MiscUtil` strips `Craft` from implementation class names.
- `VersionProtocol` previously depended on CraftBukkit package parsing and now prefers `Bukkit#getBukkitVersion()`, falling back to the old parser.

These should be isolated behind modern API compatibility modules during later refactors. Broken legacy NMS assumptions should be removed where the modern Paper API has direct support.

## Highest-Risk Folia Violations

1. Arbitrary async command execution in `MPlugin.handleCommand(..., async=true)`.
2. Async command bodies that touch players, faction state, Bukkit events, Bukkit worlds, or messages.
3. Chat password and faction chat logic running directly from `AsyncPlayerChatEvent`.
4. Global scheduler warmups followed by direct `Player#teleport` for homes, warps, checkpoints, and stuck handling.
5. Claim/unclaim and board mutation from global tasks or async command bodies, especially radius claims and unclaim-all commands.
6. Explosion/block/entity mutation paths that must stay on the event region and must not defer to global scheduler tasks.
7. Inventory GUI creation/open/refresh code scheduled globally instead of on the player scheduler.
8. Async persistence that can walk mutable faction/player/plugin state without region/global ownership boundaries.
9. CoreX addon code with direct block breaks, nearby entity scans, redstone state mutation, and inventory mutation.
10. Legacy reflection and version parsing that assumes CraftBukkit package version naming.

## Compile-First Modernization Applied

Changes made in this pass:

- Renamed Maven root coordinates to `com.realfiction:RealFactions:1.6.9.5-4.2.5-realfactions-SNAPSHOT`.
- Renamed plugin metadata to `RealFactions`.
- Preserved existing `/factions` and `/f` commands and all `factions.*` permissions.
- Added `provides: [Factions, SaberFactions]` to reduce breakage for plugins expecting the old names.
- Updated plugin `api-version` to `26.1`.
- Did not add `folia-supported: true`; the audit shows the plugin is not Folia-safe yet.
- Added a `paper26-java25` Maven profile that switches modern API coordinates to Paper and sets compiler release 25.
- Kept the default profile buildable in this workspace because Maven is running under Java 21 and no JDK 25 is installed locally.
- Centralized modern Maven plugin versions for compiler, surefire, and shade.
- Updated `maven-shade-plugin` to `3.6.2` so the shade step can process modern class files.
- Added `FactionScheduler` abstraction with `PaperScheduler`, reflective `FoliaScheduler`, and task handles.
- Instantiated the scheduler abstraction during plugin enable for detection/logging, without rewiring gameplay paths yet.
- Added a first-start data folder migrator from `plugins/Factions` or `plugins/SaberFactions` into `plugins/RealFactions`.
- Updated the console log prefix to `RealFactions`.
- Updated version parsing to prefer Bukkit version strings such as `1.21.11-*` or `26.1.2-*`.
- Added a focused JUnit test for the pure version parser.

## Phase 2 Runtime-Safety Work Applied

Changes made in Phase 2:

- Removed framework-level async command execution in `MPlugin.handleCommand(..., async=true)`. Async command requests now re-enter through `FactionScheduler`: player senders use entity/player scheduling, console senders use the global scheduler.
- Moved periodic autosave scheduling away from async traversal of live faction/player/board state. The existing save task now runs through the global scheduler instead of `runTaskTimerAsynchronously`.
- Reworked warmups to start and complete on the player/entity scheduler. Warmup cancellation now stores `ScheduledTaskHandle` instead of raw Bukkit task IDs.
- Added `TeleportUtil`, which attempts Paper `teleportAsync` reflectively and falls back to Bukkit teleport only after the caller has entered the scheduled player context.
- Routed `/f home`, `/f warp`, ally warps, GUI faction warps, chat-password faction warps, `/f checkpoint`, `/f stuck`, and `/f ahome` through scheduled teleport helper paths.
- Changed home smoke effects to schedule per-location region work instead of playing every effect from the player task.
- Changed stuck warmup delay to a player-scheduled task and changed stuck task cancellation to cancel a scheduler handle.
- Moved common GUI open/reopen/refresh/delayed-close flows in `SaberGUI` and `GUIMenu` onto player/entity scheduling.
- Removed explicit async bodies from high-risk command paths: `/f sethome`, `/f join`, `/f tag`, `/f desc`, `/f deinvite`, `/f unclaimall`, `/f safeunclaimall`, `/f warunclaimall`, and `/f show`.
- Fixed `/f unclaimall` event ordering so `LandUnclaimAllEvent` is called before checking cancellation and before mutating the board.
- Replaced mission deadline async scheduling with global scheduler handles.
- Replaced async faction conversion and async logger/message scheduling paths with scheduler-managed global/player work.
- Replaced the CoreX trapdoor crash cleanup async timer with a scheduler-managed global timer.
- Replaced combat-flight cleanup delayed tasks with entity scheduler delayed tasks.
- Replaced login unread-announcement delayed task with player scheduler delayed task.

Phase 2 fixed risks:

- Arbitrary faction command bodies are no longer executed on Bukkit async worker threads by the command framework.
- The most visible teleport paths no longer call direct teleport from warmup/global async paths.
- Warmup lifecycle cancellation no longer depends on global Bukkit task IDs.
- Common inventory opening/refreshing paths are no longer globally scheduled.
- Major claim/unclaim-all mutations no longer run from async command callbacks.
- Autosave no longer asynchronously walks live faction/player/board state.
- Mission deadlines and conversion tasks no longer mutate faction state from async scheduler callbacks.

## Remaining Folia Blockers After Phase 2

Folia support is still blocked. Do not add `folia-supported: true` yet.

Remaining high-risk areas:

- `AsyncPlayerChatEvent` still reads and mutates faction/player/chat state in the async event path. The warp password teleport now enters scheduled warmup/teleport code, but the chat handlers themselves still need a full sync handoff design.
- Radius claim/unclaim, fill operations, `SpiralTask`, corner claim, auto-claim, and auto-unclaim still need a region-aware claim coordinator.
- Some claim checks still load chunks or inspect tile entities, such as spawner chunk checks through `FastChunk#getChunk()` and `ChunkReference#getSpawnerCount`.
- Explosion/block/entity listeners mostly run from Bukkit events, but delayed work and all block/entity mutations still need current-region verification and explicit region scheduling where work crosses regions.
- Many startup/scoreboard/dynmap/timer/addon paths still use raw Bukkit scheduler calls and need migration to `FactionScheduler`.
- GUI code outside the two common wrappers still contains direct delayed scheduling and needs a second pass.
- Persistence is safer because autosave is no longer async, but true async IO still needs immutable DTO snapshots before disk writes.
- `TaskRunner` still exposes raw Bukkit async helpers. It appears unused, but it should be removed or rewritten before claiming Folia support.
- Legacy reflection and CraftBukkit/NMS assumptions remain isolated only partially.

## Phase 3 Runtime-Safety Work Applied

Phase 3 targeted the highest-traffic remaining Folia blockers with focused, reviewable changes rather than a model-layer rewrite.

Changes made in Phase 3:

- Removed the dead `TaskRunner` helper. It was only self-referenced and exposed raw Bukkit sync/async scheduler wrappers (`runTaskAsynchronously`, `runTaskTimerAsynchronously`, and friends). Deleting it removes the last raw-async scheduler helper class from the codebase.
- Migrated `SpiralTask` — the radius claim/unclaim and stuck-scan engine — from a raw `Bukkit.getScheduler().runTaskTimer` plus integer task id to `FactionScheduler.runGlobalTimer` plus a `ScheduledTaskHandle`. Board state is global model state, so the spiral now runs on the global region scheduler: the main thread on Paper, the global region thread on Folia (where the old call would have thrown).
- Moved the `MemoryFPlayer.setFlying` fall-damage cooldown off a raw `BukkitRunnable.runTaskLater` onto `FactionScheduler.runForEntityLater` for the affected player. This per-player delayed task is now entity-scheduled, which is required on Folia and unchanged in effect on Paper. The now-unused `BukkitRunnable` import was removed.
- Reworked `MemoryBoard.removeAt` (called on every unclaimed chunk) so the per-online-player Bukkit work — reading the player location, toggling flight, cancelling warmups, messaging — is scheduled on each player's own entity scheduler instead of being called inline. Previously this read locations and called `Player#setFlying` for arbitrary online players from whatever thread performed the unclaim, which is cross-region access on Folia. The standing-chunk check is re-evaluated when the per-player task runs, so a player who moved away is correctly skipped.
- Restructured `/f stuck` so the world-sensitive part of the wilderness scan runs in the correct region. The board scan (a global-model read) still runs on the spiral's global thread, but `World#getHighestBlockYAt` for the chosen chunk now runs via `FactionScheduler.runAt(targetLocation, ...)` on the region that owns that chunk, and the resulting teleport runs on the player's entity scheduler. The stuck warmup delay was already moved to the entity scheduler in Phase 2.
- Moved the `/f warp` chat-password flow off the async chat thread. `AsyncPlayerChatEvent` is delivered off-thread on both Paper and Folia; the handler now keeps only the event cancellation and the censored echo on the async thread and hands warp-password validation, the warmup trigger, and the `setEnteringPassword` flag mutation to the player's entity scheduler.

Phase 3 fixed risks:

- No raw-async Bukkit scheduler helper class remains (`TaskRunner` deleted).
- The claim/unclaim/stuck spiral no longer uses a raw global Bukkit repeating task and no longer stores a raw Bukkit task id.
- Flight toggling and its fall-damage cooldown no longer schedule raw global tasks and no longer touch `Player` flight state from non-owning threads during unclaim.
- `/f stuck` no longer calls `World#getHighestBlockYAt` from a global/non-owning thread.
- The chat warp-password path no longer mutates `FPlayer` state from the async chat thread.

## Areas Audited And Confirmed Already Safe Or Acceptable In Phase 3

- The block listener (`FactionsBlockListener`) performs no async or global scheduling. All of its checks and cancellations run inside the originating block event, which Folia delivers on the owning region thread.
- The entity/explosion listener (`FactionsEntityListener`) performs all block/entity mutations (`breakNaturally`, `setIntensity`, flight cancel, event cancel) synchronously inside the originating event on the owning region thread. The deferred combat-tag cleanup was already moved to the entity scheduler in Phase 2. One theoretical edge remains: the TNT-waterlog workaround reads six relative blocks around the explosion, which could touch an adjacent region exactly at a region boundary; this is low-risk and noted rather than fixed.
- Autosave persistence serializes a snapshot to a JSON string on the calling (global scheduler) thread before any disk write; `DiscUtil.writeCatch` then performs the write synchronously under a per-file lock. The async save path no longer traverses live collections off-thread. The residual concern is the model-mutation race described below, not the IO itself.

## Remaining Folia Blockers After Phase 3

Folia support is still blocked. Do not add `folia-supported: true` yet.

The single fundamental blocker:

- The faction/player/board model (`MemoryFactions`, `MemoryFPlayers`, `MemoryBoard`, and the per-faction and per-player nested collections) is plain, unsynchronized in-memory state designed for a single main thread. On Folia it is read and mutated from many region threads, entity threads, and the global thread with no locking or single-writer discipline. Phase 3 routed the acute "Bukkit API from the wrong thread" calls onto the correct schedulers, but the underlying data structures are still shared mutable state. This can corrupt state or throw `ConcurrentModificationException`, including during the global-thread autosave serialization, which traverses live faction objects while region threads may be mutating them. Resolving this correctly requires either a single-writer model executor or comprehensive locking / immutable deep snapshots — a model-layer redesign that is intentionally out of scope for these focused changes.

Secondary remaining blockers:

- Many subsystems still call raw Bukkit schedulers and must move to `FactionScheduler` (entity/region/global as appropriate) before Folia is safe: scoreboards (`FScoreboard`, `FTeamWrapper`), dynmap (`EngineDynmap`), GUIs/menus (`MissionGUI`, `FAuditMenu`, `FLogManager`), timers (`TimerManager`, `AsyncPlayerMap`), the AutoLeave repeating task and `FlightEnhance` timer in `FactionsPlugin`, several command cooldowns (`CmdDisband`, `FCmdRoot`, `LogoutHandler`, `CmdMod`, `CmdAdmin`, `CmdSeeChunk`, `CmdTntFill`, `CmdCorner`), `UpgradesListener`, `CheckTask`, `Metrics`, `TitleUtil`, and CoreX addons (`GlobalGamemode`, `AutoRespawn`).
- `AsyncPlayerChatEvent` still reads model display data (chat mode, faction tags, relations, recipient lists) from the async thread in the faction/alliance/mod/truce chat handlers and the tag-insertion handler. This pattern predates Folia and is also present on stock Paper, but it remains an unsynchronized read of shared state and is part of the model-layer blocker above.
- `attemptClaim` still accesses the live Bukkit chunk via WorldGuard checking (`hasRegionsInChunk(flocation.getChunk())`) when `Conf.worldGuardChecking` is enabled, and fires `LandClaimEvent` synchronously from the spiral's global thread. WorldGuard is not Folia-compatible regardless.
- `/f stuck` still reads the world border (`isOutsideWorldBorder`) on the spiral's global thread; only the highest-block lookup was moved to the owning region.
- Legacy reflection and CraftBukkit/NMS version assumptions remain only partially isolated.

## Phase 4: Folia-First Core Architecture (Direction Change)

Phases 1-3 were a patch-based hardening of SaberFactions. Phase 4 begins a Folia-first
architecture: RealFactions must be a genuinely Folia-safe Factions plugin, and where preserving
SaberFactions internals conflicts with Folia correctness, Folia correctness wins. This phase adds
the new core foundation and migrates the first important paths onto it. `folia-supported` is still
NOT enabled.

### Why the patch-based approach is insufficient

Phases 2-3 removed the acute "Bukkit API called from the wrong thread" sites. They could not make
the design safe, because the root problem is the shared mutable model itself. Fixing call sites one
by one reduces the crash surface but never establishes ownership. A correct Folia design needs:
explicit ownership per state, single-writer model writes, immutable snapshots for cross-thread
reads, and snapshot-based persistence. That is an architecture, not a patch set.

### 1. Shared mutable model inventory

- Board / claims: `MemoryBoard.flocationIds` (world to chunk to factionId) and the inner faction
  to land index; per-faction claim ownership maps.
- Faction state: `MemoryFaction` (tag, description, power, bank, open flag, peaceful, permissions).
- FPlayer state: `MemoryFPlayer` (power, role, factionId, title, `lastStoodAt`, chat mode).
- warps / homes / checkpoints: per-faction warp map and home `LazyLocation`s.
- invites / relations / permissions: per-faction invite sets, relation wishes, permission maps.
- mission state: per-faction mission progress and deadlines.
- flight / combat state: `MemoryFPlayer` flight flags plus the static `combatList` in the entity listener.
- map / cache / display state: scoreboards, GUIs, and the new chat display cache.

### 2. Ownership classification

- global / model state (Board, Factions, FPlayers and all nested faction/player collections): owned
  by the model single-writer, which is the global region scheduler.
- player-owned Bukkit operations (teleport, flight toggle, inventory, titles, per-player messages):
  owned by that player's entity scheduler.
- chunk / claim-owned Bukkit operations (`getChunk`, `getHighestBlockYAt`, `breakNaturally`,
  world border): owned by the region scheduler for that location.
- faction-owned model fields: currently part of global model state (no per-faction thread).
- read-only snapshot / cache (snapshot records, `ChatDisplayCache`): immutable, safe on any thread.
- persistence-only (serialized JSON strings): produced on the model thread, written on the async scheduler.

### 3. Folia-safe architecture (target)

- region-thread Bukkit operations only on the owning region/entity scheduler;
- model writes through a single-writer service (`FactionOperationExecutor`);
- reads from async/global/other-region threads use immutable snapshots;
- saves persist immutable snapshots, never live mutable collections;
- commands/events enqueue operations into the correct ownership context;
- claim/unclaim go through one atomic claim transaction API;
- teleport/warp/home use the player scheduler plus `teleportAsync` (done in Phase 2 + `TeleportUtil`);
- explosion/block/entity events stay region-local (confirmed in Phase 3);
- chat formatting uses cached snapshots, not live faction traversal on `AsyncPlayerChatEvent`.

### 4. Decision: retrofit (A) vs new core layer (B)

Decision: B - a new RealFactions core layer in front of the legacy model, not a deep retrofit of
every mutation site.

Rationale: B preserves the SaberFactions data and config formats, all command names and
`factions.*` permissions, and the Vault / PlaceholderAPI integrations, while letting the migration
proceed incrementally. The legacy `Memory*` classes remain the storage; a controlled access layer
(executor + transaction services + snapshots) provides single-writer discipline in front of them.
The model becomes logically owned by the global region scheduler, writes funnel through the
executor, and cross-thread reads use snapshots. This converges on full safety path by path and is
far lower risk than rewriting hundreds of in-place mutations at once. Option A (deep retrofit) would
touch every call site simultaneously with high regression risk and is rejected.

### Phase 4 foundation implemented

New package `com.massivecraft.factions.realfactions`:

- `RealFactionsServices` - the service container. Created in `FactionsPlugin.onEnable`, holds the
  executor, flags, claim service, persistence service, and chat cache. Applies strict-mode
  overrides after `Conf.load()` and starts background model-thread tasks after the model is loaded.
- `FactionOperationExecutor` - the single-writer model access layer over the global region
  scheduler. `isOnModelThread()` uses a `ThreadLocal` marker plus `Bukkit.isPrimaryThread()` on
  Paper. `runWrite` / `runFactionWrite` / `runPlayerWrite` / `runClaimWrite` execute inline when
  already on the model thread (so Paper command behaviour is unchanged) and otherwise schedule onto
  the global region scheduler (so Folia region-thread callers are serialized). `runSnapshotRead`
  produces a snapshot on the model thread; `runMarked` lets already-global work (timers) run as
  model-thread work.
- `RealFactionsFlags` - `legacyCompatibilityMode` (default on when not Folia) and `foliaStrictMode`
  (default on when Folia). Strict mode auto-disables integrations that are not Folia-safe; it
  currently disables WorldGuard claim checking (whose `getChunk` is unsafe off-region and which is
  not Folia-compatible anyway).
- Immutable snapshots (`record`s, built on the model thread): `FactionSnapshot`, `FPlayerSnapshot`,
  `ClaimSnapshot`, `ChatPlayerSnapshot`.
- `ClaimTransactionService` - the single controlled board/claim mutation API. `claim`/`unclaim`
  schedule the write and deliver the result via callback; `claimNow`/`unclaimNow` run synchronously
  for callers already on the model thread (the radius spiral batches); `unclaimAll` and
  `runTransaction` cover bulk and multi-step transactions. Every method routes through
  `executor.runClaimWrite`. The existing `attemptClaim`/`attemptUnclaim` validation, economy, and
  event logic is reused unchanged, so the claim storage format is preserved.
- `PersistenceSnapshotService` - `saveAllAsync` serializes immutable JSON snapshot strings on the
  model thread, then writes them on the async scheduler; `saveAllSync` is the shutdown path. Backed
  by a new `serializeToJson()` / `writeJson()` split added to `Board`/`Factions`/`FPlayers` and their
  JSON implementations. The on-disk format is byte-for-byte the same; only the thread boundaries changed.
- `ChatDisplayCache` - a concurrent per-player cache of immutable `ChatPlayerSnapshot`s, refreshed
  every 2 seconds on the model thread via `runMarked`. `AsyncPlayerChatEvent` reads from it.
- `RealFactionsFoliaAuditTest` - the audit tool, implemented as a JUnit test. It scans
  `src/main/java`, prints the remaining raw Bukkit scheduler usage and direct board-write call sites
  (the migration backlog), and asserts the already-migrated files and the entire `realfactions` core
  package stay clean so the foundation cannot silently regress.

### Paths migrated onto the new core in Phase 4

- `/f claim` (single and radius) - all board writes now go through `ClaimTransactionService`.
  Region-sensitive values (origin location, origin chunk) are captured on the command thread before
  the transaction runs on the model thread.
- `/f unclaim` (single and radius) - routed through `ClaimTransactionService`.
- Autosave (`SaveTask`) - now uses `PersistenceSnapshotService.saveAllAsync`: snapshot on the model
  thread, write async, release the running guard on completion.
- Chat tag insertion (`onPlayerChat`) - reads the cached title and chat tag from `ChatDisplayCache`,
  falling back to a live read only on a cache miss.

### Remaining command/model paths to migrate

- Claim family not yet migrated: `CmdClaimFill`, `CmdUnclaimfill`, `CmdCorner`, auto-claim, and the
  unclaim-all family (`CmdUnclaimall`, `CmdSafeunclaimall`, `CmdWarunclaimall`). The audit test flags
  four of these as still writing the board directly.
- `MemoryBoardMap.removeFaction` (used by `unclaimAll`) still calls `setFlying` cross-region; it needs
  the same per-player entity handoff `removeAt` received in Phase 3.
- All non-claim model mutations (join/leave/kick, role/relation/invite changes, sethome, desc, tag,
  power, bank, missions) still mutate the model directly from command/event threads and must route
  through the executor before the model is truly single-writer.
- The ~40 raw Bukkit scheduler call sites listed under the Phase 3 blockers.
- The faction/alliance/mod/truce chat handlers still traverse live member collections; they need
  cached recipient lists, and relation-colored tag insertion still reads live per-viewer data.

### Explicit blockers before `folia-supported: true`

1. Every model write funnels through `FactionOperationExecutor` (single-writer). Today only
   claim/unclaim do.
2. Every raw Bukkit scheduler call site is migrated to `FactionScheduler` (audit-test count reaches 0).
3. Every cross-thread model read uses an immutable snapshot (chat handlers, PlaceholderAPI, scoreboards).
4. Every persistence path serializes a snapshot on the model thread before async IO (done for
   autosave; shutdown `forceSave` is synchronous on the model thread and acceptable).
5. The full set is verified on a real Folia server under load.

## Phase 5: Continued Model-Write Migration

Phase 5 continues moving high-risk model writes into the Folia-first core: the unclaim-all family,
common command mutations, and the flight task. `folia-supported` remains disabled.

### Board/claim writes migrated (zero direct board writes remain in commands)

- `/f unclaimall` (both branches) routes the board write through `ClaimTransactionService.unclaimAll`;
  economy/spawner/event validation stays on the command thread, and the player location and claim
  count are captured up front for the deferred log/message.
- `/f safeunclaimall` and `/f warunclaimall` route through `unclaimAll` / `unclaimAllInWorld`.
- `/f unclaimfill` runs its entire flood-fill scan and every chunk removal inside one
  `runTransaction`; removals use `ClaimTransactionService.removeAtNow`.
- `ClaimTransactionService` gained `unclaimAll(String, Runnable)`,
  `unclaimAll(String, BooleanSupplier, Runnable)`, `unclaimAllInWorld(String, World, Runnable)`, and
  `removeAtNow(FLocation)`.
- `MemoryBoardMap.removeFaction` (invoked by `unclaimAll`) now hands its per-player flight/warmup
  Bukkit work to each player's entity scheduler, matching the Phase 3 `removeAt` fix; the
  standing-chunk test uses cached `lastStoodAt` data on the model thread.

### Faction/player command model writes routed through FactionOperationExecutor

- `/f desc` - `setDescription` plus broadcast.
- `/f tag` - `setTag`, inform loop, and scoreboard prefix refresh (validation/event/payment stay on
  the command thread).
- `/f sethome` - location captured on the command thread; `setHome` plus messages on the model thread.
- `/f deinvite` - invite-list write plus messages.
- `/f join` - membership writes (`resetFactionData`, `setFaction`, `setRole`, `deinvite`) were
  confirmed Bukkit-free and routed through the model thread; validation, the join event, and payment
  stay on the command thread.

### Combat/flight

- `FlightEnhance` was a single global task that read every player's location, scanned nearby
  entities, and toggled flight from one thread - fully cross-region on Folia. It now runs on the
  global scheduler only as a cadence driver and dispatches each player's check to that player's own
  entity scheduler, so flight/location/entity Bukkit access is region-local. Its scheduling moved
  from the raw Bukkit scheduler to `FactionScheduler.runGlobalTimer`.
- Combat-tag cleanup (`combatList`) and the flight fall-damage cooldown were already entity-scheduled
  in Phase 3; the remaining `setFlying` call sites are all in region-thread event handlers, the
  issuing player's own `/f fly` command, or the now-entity-scheduled board cleanup.

### Audit test extended

`RealFactionsFoliaAuditTest` now also reports unmigrated command classes with direct model mutations
and asserts that the Phase 5 migrated commands route through the core. It asserts that the entire
claim/unclaim command family performs no direct `Board.getInstance()` writes.

Current audit-scanner counts after Phase 5:

- Direct `Board.getInstance()` write call sites outside the model/service: 0.
- Raw Bukkit scheduler usages outside the scheduler abstraction: 41 across 24 files.
- Unmigrated command classes with direct model mutations: 15.

### Remaining unsafe command/model paths

- Command model writes not yet migrated: `/f leave`, `/f kick`, `/f promote`/`/f demote` (role),
  `/f invite`, `/f open`, `/f peaceful`, bank commands, and the rest of the ~15 command classes the
  audit test lists. They follow the established pattern (wrap the model write in
  `executor.runFactionWrite`/`runPlayerWrite`, capture Bukkit data up front) and were deferred to keep
  this pass focused.
- Listener-driven model writes (auto-claim/auto-unclaim in `FactionsPlayerListener`, power loss on
  death) still mutate the model from event threads and should route through the executor or claim
  service.
- The 41 raw Bukkit scheduler call sites (scoreboards, dynmap, GUIs, timers, command cooldowns,
  addons) remain.
- `/f unclaimall`'s spawner-chunk pre-check still inspects Bukkit chunks across regions on the
  command thread when `Conf.userSpawnerChunkSystem` is enabled; this is gated and unchanged.
- The model is still not globally single-writer: claim/unclaim and the migrated commands are, but the
  bulk of the model surface is not, so the global-thread persistence snapshot can still race with
  unmigrated writers.

### Folia verdict (unchanged)

Folia production remains UNSAFE and `folia-supported` stays disabled. Phase 5 reduced the model-write
race surface (all board writes plus several common commands are now single-writer) and made the
flight task region-correct, but a majority of model writers and roughly forty raw scheduler call
sites remain. Paper/Purpur staging remains safe.

## Phase 6: Membership/Permission Commands and High-Risk Listeners

Phase 6 migrates the highest-impact remaining command and listener model writers into the Folia-first
core. `folia-supported` remains disabled.

### Membership/role commands migrated (model writes via FactionOperationExecutor)

- `/f leave` routes `FPlayer.leave(true)` through the executor. `leave()` now schedules its flight
  toggle on the leaving player's entity scheduler, so the model writes run single-writer while the
  Bukkit flight effect stays region-local. The two other `leave()` callers (`AutoLeaveProcessTask`
  and the ban path in `FactionsPlayerListener`) were also routed through the executor.
- `/f kick` runs its model writes (`promoteNewLeader`, `deinvite`, `resetFactionData`) and messages
  inside the executor; validation, the leave event, and payment stay on the command thread.
- `/f promote` and `/f demote` (`FPromoteCommand`) run the `setRole` write and messages in the executor.
- `/f coleader` routes the role grant/revoke through the executor.
- `/f mod` and `/f admin` had their role/leader helpers changed from raw
  `Bukkit.getScheduler().runTask` to `executor.runFactionWrite`, removing three raw scheduler call
  sites and routing the writes through the model thread.

### Invite/permission commands migrated

- `/f invite` (invite-list write plus notification), `/f open` (open flag plus inform loop), and
  `/f peaceful` (peaceful flag plus inform loop).

### High-risk listeners migrated

- Auto-claim / auto-unclaim in `FactionsPlayerListener` (PlayerMoveEvent) now go through
  `ClaimTransactionService.claim`/`unclaim` instead of mutating the board from the move-event thread.
- Power loss on death in `FactionsEntityListener` (`onDeath`/`alterPower` plus the power-dependent
  message) is routed through `executor.runPlayerWrite`; the diedToPlayer metadata stays on the
  region/event thread.
- The ban-cleanup path (`onPlayerKick`) and the inactivity auto-leave task (`AutoLeaveProcessTask`)
  route their leadership/leave/remove model writes through the executor; local list cleanup stays on
  the task thread.

### Bank/economy (audited, not migrated)

Per the task scope this was audited only:

- `Econ` delegates monetary operations to Vault (`withdrawPlayer`/`depositPlayer`/`getBalance`), which
  forward to the backing economy plugin (EssentialsX, CMI, etc.). Those are generally written for a
  single main thread and are not Folia-aware, so the calls are not guaranteed safe off the main
  thread. This is a cross-cutting risk independent of the faction model.
- `Econ` also uses `Bukkit.getOfflinePlayer(String)`, which can block on a name lookup and must not
  run on the model/global thread.
- Faction-balance mutations (`modifyFactionBalance` and friends) are model writes that should route
  through the executor, but they are interleaved with the Vault calls, so they cannot simply be
  wrapped until the economy-provider thread model is decided.
- Recommendation (deferred): pin economy operations to a single consistent thread and require a
  Folia-safe economy provider, or gate economy under `foliaStrictMode`. Bank commands were left unchanged.

### Audit test extended

`RealFactionsFoliaAuditTest` now protects the Phase 5-6 migrated commands (it fails if any regresses to
a direct model write outside the core) and adds an informational listener/task model-mutation report.

Current audit-scanner counts after Phase 6:

- Direct `Board.getInstance()` write call sites outside the model/service: 0.
- Raw Bukkit scheduler usages outside the scheduler abstraction: 38 across 22 files (down from 41).
- Unmigrated command classes with direct model mutations: 8 (down from 15).
- Listener/task model-mutation call sites (upper bound): 3 files.

### Remaining unsafe command/listener writers

- Command model writes still direct: `/f create`, `/f createadmin`, `/f ban`, the alt commands
  (`CmdKickAlt`, `CmdInviteAlt`), disband, and permission/GUI editors - the remaining 8 command
  classes the audit lists.
- Listener writes still partially direct: per-player field updates in `FactionsPlayerListener` (for
  example `setLastStoodAt`) and some region-safe event-handler writes (included in the upper-bound report).
- The 38 raw Bukkit scheduler call sites (scoreboards, dynmap, GUIs, timers, remaining command
  cooldowns, addons).
- Bank/economy as described above.

### Folia verdict (unchanged)

Folia production remains UNSAFE and `folia-supported` stays disabled. Phase 6 brought all board writes
plus the membership, role, invite, and open/peaceful commands, and the highest-risk listeners
(auto-claim/unclaim, power-on-death, ban/auto-leave), onto the single-writer model. But several
command and listener writers, the economy layer, and roughly forty raw scheduler call sites remain.
Paper/Purpur staging remains safe.

## Phase 7: Remaining Commands, Listener Residue, Scheduler Reduction, Economy Strategy

Phase 7 reduces the remaining model-write and raw-scheduler backlog with focused changes and sets a
concrete economy/Vault strategy. `folia-supported` remains disabled.

### Remaining command model writes migrated

- `/f title` - `setTitle` routed through `runPlayerWrite`.
- `/f powerboost` - player and faction `setPowerBoost` routed through the executor.
- `/f ban` - ban/deinvite/removeFPlayer/resetFactionData and the leave event routed through `runFactionWrite`.
- `/f invitealt` - alt invite-list write routed through `runFactionWrite`.
- `/f kickalt` - promoteNewLeader/removeAltPlayer/deinvite/resetFactionData routed through `runFactionWrite`.
- `/f adminfaction` - all four sub-actions (tag, description, permission, sethome) routed through the executor.

### Deferred command writes (mix Vault with model creation)

- `/f create` and `/f createadmin` interleave `Factions.createFaction()` plus
  `setTag`/`setFaction`/`setRole`/`setPeaceful` with `Econ.setBalance(...)` (a Vault call). The model
  portion cannot be cleanly isolated from the Vault call without the economy strategy below, so these
  two remain unmigrated (the only remaining unmigrated command classes).

### Listener residue classification

The three listener/task files the scanner flagged were classified; all of their flagged mutations are
already routed through the core (Phase 6) and are false positives in the raw text scan:

- `AutoLeaveProcessTask` - `promoteNewLeader`/`leave` inside `runFactionWrite`.
- `FactionsEntityListener` - `onDeath`/`alterPower` inside `runPlayerWrite`.
- `FactionsPlayerListener` - ban-path `promoteNewLeader`/`leave` inside `runFactionWrite`; auto-claim
  via `ClaimTransactionService`.

No remaining high-risk faction/model writes exist in listeners. The audit test now allowlists these
three files (with per-file comments) and fails if any NEW unrouted listener/task mutation appears.

### Raw scheduler sites migrated (highest-risk)

- `TitleUtil` - delayed faction-change title to the player's entity scheduler.
- `LogoutHandler` - delayed logout kick to the player's entity scheduler.
- `FScoreboard` - the per-player sidebar refresh timer and the temporary-sidebar expiry to the
  player's entity scheduler (handle-based self-cancel).
- `CmdSeeChunk` - the visualizer was one global timer touching every player's world/blocks; it now
  runs a global cadence and dispatches each player's border render to that player's entity scheduler.
- `FAuditMenu` - two GUI opens to the player's entity scheduler.
- `CmdDisband` - the confirmation-map cleanup to the global scheduler (pure data).
- `FCmdRoot` - the command-registry edit to the global scheduler (pure data).
- `FTeamWrapper` - the scoreboard-update dispatch via the scheduler abstraction (does not throw on
  Folia; the team-prefix internals still touch per-player scoreboards and remain on the scoreboard backlog).

This took the scanner count from 38 raw scheduler usages across 22 files to 24 across 14 files.

### Remaining raw scheduler backlog (24 across 14 files)

Mostly startup, integration, timer, GUI, and addon paths: `FactionsPlugin` startup tasks, `MissionGUI`
(GUI task fields), `EngineDynmap`, `StartupParameter`, `CheckTask`, `FLogManager`, `TimerManager`,
`AsyncPlayerMap`, `Metrics`, `UpgradesListener`, `MemoryFPlayer`, `JSONFPlayers`, and the CoreX
`AutoRespawn`/`GlobalGamemode` addons. These are lower per-call risk or need a dedicated subsystem pass.

### Economy / Vault Folia strategy

Economy was audited, not migrated. The concrete strategy for a future phase:

1. Vault calls run on one fixed thread. `Economy` operations (`withdrawPlayer`/`depositPlayer`/
   `getBalance`) must not run from arbitrary region threads. Route them through a single economy
   executor - in practice the global region scheduler (the model thread) - so all Vault access is
   serialized on one thread, matching how Vault providers expect to be called.
2. Faction bank writes become model-thread-only. The in-memory faction balance is model state and
   must be mutated only on the model thread (via the executor), exactly like other faction state. The
   external Vault transaction stays isolated on the economy thread; the two are sequenced, not interleaved.
3. RealFactions should require a Folia-safe economy provider. Most Vault providers (EssentialsX, CMI)
   are not Folia-aware. The plugin should detect the provider and, when it is not known Folia-safe,
   warn at enable.
4. Under `foliaStrictMode`, gate economy. When running Folia strict and the provider is not known
   Folia-safe, disable economy-dependent command costs/refunds (treat as free) or disable the economy
   integration entirely, and log it - rather than make unsafe cross-thread Vault calls. This mirrors
   the existing strict-mode WorldGuard disable.
5. Avoid blocking lookups on the model thread. Replace `Bukkit.getOfflinePlayer(String name)` (which
   can block on a name lookup) with UUID-based lookups, or perform name resolution on the async scheduler.

No economy code was changed in Phase 7; `/f create`, `/f createadmin`, and the bank commands stay on
the legacy path until this strategy is implemented.

### Counts after Phase 7

- Direct `Board.getInstance()` write call sites outside the model/service: 0.
- Unmigrated command classes with direct model mutations: 2 (`/f create`, `/f createadmin`; down from 8).
- Raw Bukkit scheduler usages outside the scheduler abstraction: 24 across 14 files (down from 38).
- Listener/task model-mutation backlog: 0 (3 files allowlisted as executor-routed).

### Folia verdict (unchanged)

Folia production remains UNSAFE and `folia-supported` stays disabled. Phase 7 cut the unmigrated
command writers to two (both economy-entangled) and the raw scheduler sites to 24, and confirmed the
listener residue is already routed through the core. But the economy/Vault layer is still unsafe under
Folia, ~24 raw scheduler sites remain (including startup, dynmap, and GUI paths), and the model is not
yet provably single-writer end to end. Paper/Purpur staging remains safe.

## Phase 8: Faction Creation and Economy Bridge

Phase 8 completes the last command model-write blockers and implements the foundational economy/Vault
bridge. `folia-supported` remains disabled.

### Faction creation migrated

- `/f create` - validation, the create event, and `payForCommand` stay on the command thread. Model
  creation (`createFaction`, `setTag`, `setFaction`, `setRole`, peaceful defaults) runs through
  `FactionCreationService` on the model thread. Starting balance setup runs via
  `RealFactionsEconomyService.initStartingBalance`; on failure the service rolls back the faction and
  refunds the create cost.
- `/f createadmin` - same pattern: validation/event on the command thread, model writes through
  `FactionCreationService`, starting balance through the economy bridge, rollback on balance failure.

### Economy / Vault bridge implemented

New `RealFactionsEconomyService` (wired in `RealFactionsServices`, provider detected after
`Econ.setup()`):

1. Detects the Vault provider name via `Econ.getProviderName()` and logs known-Folia-safe status.
2. Documents and treats Vault as not assumed Folia-safe (`KNOWN_FOLIA_SAFE_PROVIDERS` is empty).
3. Serializes new-path Vault work onto the model/global thread via `FactionOperationExecutor`.
4. Under `foliaStrictMode` on Folia, when the provider is not allowlisted, disables economy so
   command costs/refunds and starting-balance setup are skipped instead of making unsafe Vault calls.
5. Exposes `applyCommandEconomyCosts()` for gated command affordability/payment in migrated paths.
6. Avoids new `Bukkit.getOfflinePlayer(String)` usage; legacy `Econ` call sites are unchanged.

`FactionCreationService` is the single entry point for `Factions.createFaction()` from commands.
`FactionOperationExecutor` gained `runWriteForResult` for controlled blocking economy bridges.

Legacy `Econ.*` call sites (bank commands, `payForCommand`, claim costs in `MemoryFPlayer`, GUI
upgrades, missions) are **not** yet routed through the bridge.

### Remaining Econ / Vault risks (audited)

| Area | Risk | Why not migrated in Phase 8 |
|------|------|-----------------------------|
| `CommandContext.payForCommand` / `canAffordCommand` | Calls `Econ.modifyMoney` directly from command threads | Broad cross-cutting change; create uses bridge gating only |
| Bank commands (`CmdMoneyDeposit`, `CmdMoneyWithdraw`, transfer commands) | Vault + in-memory faction balance interleaved | Needs sequenced model-thread bank writes + economy thread Vault calls |
| `MemoryFPlayer.attemptClaim` / leave / unclaim | Vault costs mixed with model/event logic | Deep model paths; separate pass |
| `Econ.setBalance` / `modifyBalance` for player names | `Bukkit.getOfflinePlayer(String)` can block | Name resolution should move async or UUID-only |
| `Econ.transferMoney` / faction bank mutations | Model writes inside Vault transaction flow | Requires executor sequencing like Phase 7 strategy |
| Faction upgrade / warp / mission GUIs | Vault from GUI click handlers | GUI subsystem pass |

Under Folia strict mode, create/createadmin costs are skipped when the provider is not known safe.
Other commands still use legacy `Econ` and remain unsafe off the main/global thread.

### Audit test extended

`RealFactionsFoliaAuditTest` now:

- Asserts zero direct `Factions.getInstance().createFaction()` calls outside
  `FactionCreationService` / the model layer.
- Protects `/f create` and `/f createadmin` from regression.
- Reports **0** unmigrated command classes with direct model mutations (down from 2).

Current audit-scanner counts after Phase 8:

- Direct `Board.getInstance()` write call sites outside the model/service: **0**.
- Direct `Factions.createFaction()` call sites outside the creation service: **0**.
- Unmigrated command classes with direct model mutations: **0** (down from 2).
- Raw Bukkit scheduler usages outside the scheduler abstraction: **24** across **14** files (unchanged).
- Listener/task model-mutation backlog: **0** (3 files allowlisted).

### Folia verdict (unchanged)

Folia production remains **UNSAFE** and `folia-supported` stays disabled. Phase 8 eliminated the last
command model-write blockers and established the economy bridge for faction creation, but the bulk of
Vault usage still flows through legacy `Econ`, ~24 raw scheduler sites remain, and the model is not
yet provably single-writer end to end for economy-interleaved paths. Paper/Purpur staging remains safe.

## Phase 9: Economy Containment and High-Risk Scheduler Reduction

Phase 9 extends the economy bridge to common command/GUI/model paths and migrates high-risk raw
scheduler sites. `folia-supported` remains disabled.

### Legacy Econ/Vault containment

Extended `RealFactionsEconomyService` with `hasAtLeast`, `modifyMoney`, `transferMoney`,
`payCommandCost`, and `canAffordCommandCost` — all serialized on the model/global thread and gated
under `foliaStrictMode`.

Routed through the bridge in Phase 9:

- `CommandContext.payForCommand` / `canAffordCommand` (all command costs site-wide)
- Bank commands: deposit, withdraw, and all three transfer variants
- `/f unclaimall` refund, `/f unclaimfill` refund batch
- GUI: mission cancel cost, faction warp cost, upgrade purchase cost
- Model: `MemoryFPlayer.leave()` cost/transfer, `attemptClaim()` cost/reward, single-chunk unclaim refund

Still on legacy direct `Econ.*` (audited, not migrated):

- `MemoryFaction` disband bank payout (`transferMoney` interleaved with model teardown)
- `Econ.java` internals (`Bukkit.getOfflinePlayer(String)` for name-based accounts)
- Read-only display paths (tags, placeholders, `/f top`, help text)

Under Folia strict mode, routed paths treat economy as disabled (costs skipped or bank transfers
return false); legacy paths remain unsafe until migrated.

### Raw scheduler sites migrated (high-risk)

- `MissionGUI` — GUI close check and refresh timer to player entity scheduler
- `CheckTask` — faction check map writes to model executor (was raw `runTask`)
- `UpgradesListener` — spawner delay mutation to region scheduler at spawner location
- `AutoRespawn` / `GlobalGamemode` — delayed player effects to entity scheduler
- `MemoryFPlayer.updatePower` — off-thread regen to global scheduler
- `FactionsPlugin.startAutoLeaveTask` — auto-leave timer to `runGlobalTimer` with handle-based cancel

Intentionally deferred (low per-call risk or startup/file/metrics):

- `FactionsPlugin` startup `runTaskLater` (faction-data preload, addon registry)
- `Metrics`, `TimerManager`, `AsyncPlayerMap`, `EngineDynmap`, `FLogManager`, `JSONFPlayers`, commented
  startup paths in `StartupParameter`

### Audit test extended

`RealFactionsFoliaAuditTest` now also:

- Reports legacy `Econ.modifyMoney`/`transferMoney`/etc. call sites outside the bridge (informational backlog)
- Protects Phase 9 economy-routed files from regression
- Protects Phase 9 scheduler-migrated files from regression

Current audit-scanner counts after Phase 9:

- Direct `Board.getInstance()` write call sites outside the model/service: **0**.
- Direct `Factions.createFaction()` call sites outside the creation service: **0**.
- Unmigrated command model mutations: **0**.
- Raw Bukkit scheduler usages outside the scheduler abstraction: **12** across **8** files (down from 24).
- Legacy Econ mutation call sites outside the bridge: **2** in **1** file (`MemoryFaction` disband payout).
- Listener/task model-mutation backlog: **0**.

### Folia verdict (unchanged)

Folia production remains **UNSAFE** and `folia-supported` stays disabled. Phase 9 routed the
highest-traffic economy command paths through the bridge and halved the raw scheduler backlog, but
disband bank payout, name-based `getOfflinePlayer(String)` in `Econ`, and 12 startup/integration
scheduler sites remain. Paper/Purpur staging remains safe.

## Phase 10: MemoryFaction Economy and Remaining High-Risk Schedulers

Phase 10 clears the last legacy economy mutation sites and migrates the highest-value remaining
raw scheduler usages. `folia-supported` remains disabled.

### MemoryFaction economy strategy

`MemoryFaction.disband()` and `remove()` were the last direct `Econ.transferMoney` / `Econ.setBalance`
call sites outside the bridge.

- **Disband bank payout** — routed through `RealFactionsEconomyService.transferDisbandHoldings()`.
  Reads in-memory faction balance on the model thread, then delegates to `transferMoney()` on the
  economy bridge. Under `foliaStrictMode` with a non-allowlisted Vault provider, payout is skipped
  with a warning and disband still proceeds (matches legacy behaviour where transfer failure did not
  block teardown). Holdings chat/log messages still appear when economy is enabled and balance was
  &gt; 0 before payout, even if the transfer fails.
- **Remove balance cleanup** — routed through `RealFactionsEconomyService.clearFactionBalanceOnRemove()`,
  which clears in-memory faction bank balance only (legacy `Econ.setBalance` for faction accounts was
  already a no-op against Vault).
- **`/f disband`** — both console and player paths now run `faction.disband(...)` inside
  `FactionOperationExecutor.runFactionWrite()` so teardown and economy reads stay on the model thread.

### Raw scheduler sites migrated (Phase 10)

- `AsyncPlayerMap` — **unsafe** global timer touching all online players for titles/locations; migrated
  to global cadence + per-player entity scheduler (same pattern as `FlightEnhance`).
- `TimerManager` — grace/war timer tick loop to `runGlobalTimer` (time checks only, no world access).
- `FLogManager` — log timer maintenance to `runGlobalTimer` (pure in-memory map cleanup).
- `JSONFPlayers` — async-load thread hop to `runGlobal` when not on primary thread.

### Raw scheduler sites intentionally deferred

- `FactionsPlugin` (2) — startup-only `runTaskLater` for faction-data preload and addon registry; no
  player/world mutation during tick.
- `EngineDynmap` (2) — integration-specific dynmap marker refresh; deferred until dynmap/Folia path is
  validated separately.
- `Metrics` (1) — bStats submit hop to main thread; metrics-only.

After Phase 10 the audit scanner reports **5 raw scheduler usages across 3 files** (down from 12 / 8).

### Audit test extended

`RealFactionsFoliaAuditTest` now also:

- Asserts legacy Econ mutation backlog stays at **0** (Phase 10 MemoryFaction migration).
- Protects `MemoryFaction` from regressing to direct `Econ.transferMoney` / `Econ.setBalance`.
- Protects Phase 10 scheduler-migrated files from regression.

Current audit-scanner counts after Phase 10:

- Direct `Board.getInstance()` write call sites outside the model/service: **0**.
- Direct `Factions.createFaction()` call sites outside the creation service: **0**.
- Unmigrated command model mutations: **0**.
- Raw Bukkit scheduler usages outside the scheduler abstraction: **5** across **3** files (down from 12).
- Legacy Econ mutation call sites outside the bridge: **0** (down from 2 in `MemoryFaction`).
- Listener/task model-mutation backlog: **0**.

### Folia verdict (updated)

- **Paper/Purpur staging:** SAFE — unchanged; all Phase 10 changes preserve behaviour on single-thread Paper.
- **Folia staging:** REASONABLE for developer test servers — economy bridge now covers all audited
  mutation paths; high-risk player/entity scheduler sites (GUI, flight map, power regen, auto-leave) are
  migrated. Remaining raw scheduler sites are startup/metrics/dynmap-only. Still not player-ready:
  Vault provider Folia safety is not verified, dynmap integration uses raw scheduler, and end-to-end load
  testing on Folia has not been done.
- **Folia production:** UNSAFE — `folia-supported` stays disabled. Residual blockers: Vault/`Econ`
  internals (`getOfflinePlayer(String)`), dynmap raw scheduler, no production load verification, model
  is serialized through executor but not fully proven under concurrent region load.

## Build Results

Baseline before edits:

- `mvn -q -DskipTests package`
- Result: success.

After modernization:

- `mvn -q test`
- Result: success.

- `mvn -q clean package`
- Result: success.
- Jar: `factions-plugin/target/RealFactions.jar` at 9.6 MB.

After Phase 2:

- `mvn -q test`
- Result: success.

- `mvn -q clean package`
- Result: success.
- Jar: `factions-plugin/target/RealFactions.jar` at 9.6 MB.

After Phase 3:

- `mvn -q test`
- Result: success (exit code 0).

- `mvn -q clean package`
- Result: success (exit code 0).
- Jar: `factions-plugin/target/RealFactions.jar` at 10,050,838 bytes (~9.6 MB).

After Phase 4:

- `mvn -q test`
- Result: success (exit code 0). 6 tests pass, including the new `RealFactionsFoliaAuditTest`
  (3 tests) which reports 42 raw Bukkit scheduler usages across 24 files and 4 direct board-write
  call sites still outstanding, and asserts the migrated files and the `realfactions` core stay clean.

- `mvn -q clean package`
- Result: success (exit code 0).
- Jar: `factions-plugin/target/RealFactions.jar` at 10,067,373 bytes (~9.6 MB).

After Phase 5:

- `mvn -q test`
- Result: success (exit code 0). 7 tests pass, including `RealFactionsFoliaAuditTest` (4 tests). The
  scanner reports 41 raw Bukkit scheduler usages across 24 files, 0 direct board-write call sites in
  command classes, and 15 unmigrated command classes with direct model mutations.

- `mvn -q clean package`
- Result: success (exit code 0).
- Jar: `factions-plugin/target/RealFactions.jar` at 10,071,803 bytes (~9.6 MB).

After Phase 6:

- `mvn -q test`
- Result: success (exit code 0). 8 tests pass, including `RealFactionsFoliaAuditTest` (5 tests). The
  scanner reports 38 raw Bukkit scheduler usages across 22 files, 0 direct board-write call sites in
  command classes, 8 unmigrated command classes with direct model mutations, and 3 listener/task
  files with model-mutation call sites (upper bound).

- `mvn -q clean package`
- Result: success (exit code 0).
- Jar: `factions-plugin/target/RealFactions.jar` at 10,075,046 bytes (~9.6 MB).

After Phase 7:

- `mvn -q test`
- Result: success (exit code 0). 8 tests pass, including `RealFactionsFoliaAuditTest` (5 tests). The
  scanner reports 24 raw Bukkit scheduler usages across 14 files, 0 direct board-write call sites in
  command classes, 2 unmigrated command classes (`/f create`, `/f createadmin`), and 0 listener/task
  model-mutation backlog (3 files allowlisted as executor-routed).

- `mvn -q clean package`
- Result: success (exit code 0).
- Jar: `factions-plugin/target/RealFactions.jar` at 10,076,427 bytes (~9.6 MB).

After Phase 8:

- `mvn -q test`
- Result: success (exit code 0). 9 tests pass, including `RealFactionsFoliaAuditTest` (6 tests). The
  scanner reports 24 raw Bukkit scheduler usages across 14 files, 0 direct board-write call sites, 0
  direct `createFaction()` call sites outside the creation service, 0 unmigrated command classes, and
  0 listener/task model-mutation backlog.

- `mvn -q clean package`
- Result: success (exit code 0).

After Phase 9:

- `mvn -q test`
- Result: success (exit code 0). 10 tests pass, including `RealFactionsFoliaAuditTest` (7 tests). The
  scanner reports 12 raw Bukkit scheduler usages across 8 files, 0 unmigrated command classes, 2 legacy
  Econ mutation sites in `MemoryFaction`, and 0 listener mutation backlog.

- `mvn -q clean package`
- Result: success (exit code 0).

After Phase 10:

- `mvn -q test`
- Result: success (exit code 0). Audit scanner reports 5 raw Bukkit scheduler usages across 3 files, 0
  legacy Econ mutation sites outside the bridge, and 0 unmigrated command/listener/board/createFaction
  backlog.

- `mvn -q clean package`
- Result: success (exit code 0).

Target Java 25/Paper 26 profile check:

- `mvn -q -Ppaper26-java25 -DskipTests clean package`
- Result: failed because Maven is running on Java 21 and this machine has no JDK 25.
- Exact Maven error: `org.apache.maven.plugins:maven-compiler-plugin:3.14.1:compile ... Fatal error compiling: error: release version 25 not supported`.

Local Java state:

- `java -version`: OpenJDK 21.0.6.
- `mvn -version`: Maven 3.9.9 using Java 21.0.6.
- `/usr/libexec/java_home -V`: highest installed JDK is 24.0.2; JDK 25 is not installed.

## Staging And Production Safety Assessment

This assessment reflects the state after Phase 10. Phase 9 routed command costs, bank commands,
claim/leave/unclaim economy paths, and key GUI costs through `RealFactionsEconomyService`, and migrated
high-risk GUI/player/region scheduler sites (raw scheduler count 24 → 12). Phase 10 migrated the last
legacy Econ mutations in `MemoryFaction` and the highest-value remaining unsafe scheduler sites (raw
scheduler count 12 → 5). After Phase 10: 0 command/board/createFaction/listener/economy mutation backlog,
5 raw scheduler sites (startup/metrics/dynmap only). Folia staging is now reasonable for dev/test;
Folia production stays unsafe; `folia-supported` stays off.

Paper / Purpur staging: SAFE. Recommended target.

- The plugin builds and packages cleanly, preserves the SaberFactions data and config formats, and keeps all `/f` and `/factions` commands and `factions.*` permissions, plus the `Factions` / `SaberFactions` provides aliases.
- Vault, PlaceholderAPI, and the other integrations are unchanged.
- All Phase 1-3 changes are behavior-preserving on Paper: the scheduler abstraction maps to the Bukkit main-thread scheduler. The only observable differences are a few operations that now complete on the next tick instead of inline (flight fall-damage cooldown, per-player unclaim cleanup, stuck highest-block lookup, chat-password handling). These are imperceptible in normal play.

Folia staging (developer test server only): REASONABLE FOR DEV/TEST, NOT RECOMMENDED FOR PLAYERS.

- The plugin loads and the scheduler abstraction detects Folia and uses the global/region/entity/async
  schedulers. Phases 2–10 fixed acute crash paths (commands, warmups/teleports, claims, flight map,
  GUI timers, economy bridge for all audited mutation paths).
- Remaining raw scheduler sites (5 in 3 files) are startup preload, bStats metrics, and dynmap refresh —
  not per-tick player/world mutation loops.
- Vault Folia safety is still unverified, dynmap uses raw scheduler, and concurrent region load has not
  been end-to-end tested. Suitable for throwaway developer Folia servers continuing validation; not for
  player-facing staging yet.

Folia production: UNSAFE. DO NOT USE.

- All audited economy mutations now route through `RealFactionsEconomyService`, but Vault/`Econ`
  internals (`getOfflinePlayer(String)`) and dynmap integration remain unverified on Folia. Five raw
  Bukkit scheduler call sites remain (startup/metrics/dynmap). Model writes are executor-serialized but
  not proven under real concurrent region load.

Exact reason `folia-supported: true` remains disabled:

- The faction/player/board model is unsynchronized shared mutable state read and written from multiple Folia threads, and many subsystems still use raw Bukkit schedulers that throw under Folia. Setting `folia-supported: true` tells Folia the plugin is safe to run regionized; that is not true today and would invite data corruption and crashes. Per project policy we do not fake Folia support, so the flag stays off until the model-layer redesign and the remaining scheduler migrations are complete and verified on a real Folia server.

## Staged Migration Plan

Stage 1, scheduler migration:

- Replace direct Bukkit scheduler calls with `FactionScheduler`.
- Remove framework-level async command execution.
- Define explicit execution domains: global state, player/entity scheduler, region scheduler, and pure async IO.
- Add guard helpers for current-region checks where Paper/Folia exposes them.

Stage 2, teleports and warmups:

- Rewrite `WarmUpUtil` to schedule by player/entity ownership.
- Use Folia-compatible teleport patterns and never call direct teleports from async/global scheduler contexts.
- Refactor `/f home`, `/f warp`, `/f checkpoint`, `/f stuck`, chat warp password flow, and `/f ahome`.

Stage 3, claims and territory:

- Make Board/faction state mutations serialized through a clear global or region-safe coordinator.
- Refactor radius claim/unclaim and fill operations so each chunk operation runs in the appropriate region context.
- Snapshot only pure data for async persistence.

Stage 4, combat/explosions/block/entity:

- Keep event-thread region operations local to their region.
- Replace delayed global cleanup/mutation tasks with entity or region scheduled tasks.
- Audit every `breakNaturally`, `setType`, `setBlockData`, entity scan, and inventory update.

Stage 5, storage:

- Split pure serialization/IO from Bukkit object access.
- Use immutable DTO snapshots for async writes.
- Load disk/network data async, then publish state changes back on the correct scheduler.

Stage 6, integrations and compatibility:

- Validate Vault, PlaceholderAPI, WorldGuard, dynmap, stacker, Essentials, and addon hooks on Paper/Purpur/Folia.
- Isolate or remove legacy CraftBukkit/NMS reflection.
- Add Paper/Purpur/Folia integration test harnesses where practical.

Stage 7, Folia declaration:

- Add `folia-supported: true` only after the scheduler migration, teleport paths, claim paths, block/entity mutation paths, GUI paths, and storage publication paths are verified on Folia.
