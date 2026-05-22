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

Target Java 25/Paper 26 profile check:

- `mvn -q -Ppaper26-java25 -DskipTests clean package`
- Result: failed because Maven is running on Java 21 and this machine has no JDK 25.
- Exact Maven error: `org.apache.maven.plugins:maven-compiler-plugin:3.14.1:compile ... Fatal error compiling: error: release version 25 not supported`.

Local Java state:

- `java -version`: OpenJDK 21.0.6.
- `mvn -version`: Maven 3.9.9 using Java 21.0.6.
- `/usr/libexec/java_home -V`: highest installed JDK is 24.0.2; JDK 25 is not installed.

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
