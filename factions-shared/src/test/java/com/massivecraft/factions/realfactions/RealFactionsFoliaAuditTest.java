package com.massivecraft.factions.realfactions;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Static Folia-safety audit over the source tree.
 *
 * <p>This is the {@code RealFactionsFoliaAudit} tool/test from the Phase 4 plan. It prints the
 * remaining migration backlog (raw Bukkit scheduler usage and direct board mutations) so the
 * audit document can stay accurate, and it asserts that the already-migrated core files stay
 * clean so the foundation does not silently regress.
 */
class RealFactionsFoliaAuditTest {

    /** Raw Bukkit scheduler usage that must live only inside the scheduler abstraction. */
    private static final Pattern RAW_SCHEDULER = Pattern.compile(
            "Bukkit\\.getScheduler\\(|getServer\\(\\)\\.getScheduler\\(|new\\s+BukkitRunnable"
                    + "|scheduleSyncRepeatingTask|scheduleSyncDelayedTask|scheduleAsyncRepeatingTask"
                    + "|runTaskTimerAsynchronously|runTaskLaterAsynchronously|runTaskAsynchronously");

    /** Direct board claim mutations that should go through {@code ClaimTransactionService}. */
    private static final Pattern DIRECT_BOARD_WRITE = Pattern.compile(
            "Board\\.getInstance\\(\\)\\.(setFactionAt|setIdAt|removeAt|unclaimAll|unclaimAllInWorld)\\(");

    /** Direct faction creation that must go through {@code FactionCreationService}. */
    private static final Pattern DIRECT_CREATE_FACTION = Pattern.compile(
            "Factions\\.getInstance\\(\\)\\.createFaction\\(\\)");

    private Path sourceRoot() {
        for (String candidate : new String[]{"src/main/java", "factions-shared/src/main/java"}) {
            Path p = Paths.get(candidate);
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        throw new IllegalStateException("Could not locate src/main/java from " + Paths.get("").toAbsolutePath());
    }

    private List<Path> javaFiles(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> out = new ArrayList<>();
            stream.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int countMatches(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    void reportRawSchedulerUsageAndProtectMigratedFiles() {
        Path root = sourceRoot();
        Map<String, Integer> offenders = new LinkedHashMap<>();
        int total = 0;

        for (Path file : javaFiles(root)) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            // The scheduler abstraction is the one place allowed to touch the Bukkit scheduler.
            if (rel.startsWith("com/massivecraft/factions/scheduler/")) {
                continue;
            }
            int n = countMatches(RAW_SCHEDULER, read(file));
            if (n > 0) {
                offenders.put(rel, n);
                total += n;
            }
        }

        System.out.println("[RealFactions Folia audit] remaining raw Bukkit scheduler usages: "
                + total + " across " + offenders.size() + " files (migration backlog)");
        offenders.forEach((f, n) -> System.out.println("    " + n + "  " + f));

        // Migrated files must contain zero raw scheduler usage.
        List<String> mustBeClean = List.of(
                "com/massivecraft/factions/util/spiral/SpiralTask.java",
                "com/massivecraft/factions/cmd/claim/CmdClaim.java",
                "com/massivecraft/factions/cmd/claim/CmdUnclaim.java",
                // Phase 9
                "com/massivecraft/factions/missions/MissionGUI.java",
                "com/massivecraft/factions/cmd/check/CheckTask.java",
                "com/massivecraft/factions/zcore/frame/fupgrades/UpgradesListener.java",
                "com/massivecraft/factions/zcore/persist/MemoryFPlayer.java",
                "org/saberdev/corex/addons/AutoRespawn.java",
                "org/saberdev/corex/addons/GlobalGamemode.java"
        );
        for (String clean : mustBeClean) {
            assertTrue(!offenders.containsKey(clean),
                    clean + " must route scheduling through FactionScheduler, not the raw Bukkit scheduler.");
        }

        // The entire RealFactions core package must be clean.
        offenders.keySet().stream()
                .filter(f -> f.startsWith("com/massivecraft/factions/realfactions/"))
                .findAny()
                .ifPresent(f -> fail("RealFactions core must not use the raw Bukkit scheduler: " + f));
    }

    @Test
    void deadTaskRunnerIsRemoved() {
        Path root = sourceRoot();
        assertTrue(Files.notExists(root.resolve("com/massivecraft/factions/util/TaskRunner.java")),
                "Legacy TaskRunner must stay removed.");
    }

    @Test
    void reportDirectBoardWritesAndProtectClaimCommands() {
        Path root = sourceRoot();
        Map<String, Integer> offenders = new LinkedHashMap<>();

        for (Path file : javaFiles(root)) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            // The model implementation and the transaction service legitimately mutate the board.
            if (rel.startsWith("com/massivecraft/factions/zcore/persist/")
                    || rel.startsWith("com/massivecraft/factions/realfactions/")) {
                continue;
            }
            int n = countMatches(DIRECT_BOARD_WRITE, read(file));
            if (n > 0) {
                offenders.put(rel, n);
            }
        }

        System.out.println("[RealFactions Folia audit] direct Board write call sites outside the model/service: "
                + offenders.size() + " (claim/unclaim migration backlog)");
        offenders.forEach((f, n) -> System.out.println("    " + n + "  " + f));

        // The migrated claim/unclaim commands must not write the board directly anymore.
        List<String> migratedClaimCommands = List.of(
                "com/massivecraft/factions/cmd/claim/CmdClaim.java",
                "com/massivecraft/factions/cmd/claim/CmdUnclaim.java",
                "com/massivecraft/factions/cmd/claim/CmdUnclaimall.java",
                "com/massivecraft/factions/cmd/claim/CmdSafeunclaimall.java",
                "com/massivecraft/factions/cmd/claim/CmdWarunclaimall.java",
                "com/massivecraft/factions/cmd/claim/CmdUnclaimfill.java"
        );
        for (String cmd : migratedClaimCommands) {
            assertTrue(!offenders.containsKey(cmd),
                    cmd + " must mutate the board through ClaimTransactionService, not Board.getInstance() directly.");
        }
    }

    @Test
    void reportCommandModelMutationsAndProtectMigratedCommands() {
        Path root = sourceRoot();

        // Heuristic: setter-style faction/player model mutations invoked directly in command classes.
        Pattern mutators = Pattern.compile(
                "\\.(setFaction|setRole|setTag|setDescription|setHome|deinvite|resetFactionData|setTitle"
                        + "|setOpen|setPeaceful|setPowerBoost|setAlt)\\(");

        Map<String, Integer> offenders = new LinkedHashMap<>();
        for (Path file : javaFiles(root)) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            if (!rel.startsWith("com/massivecraft/factions/cmd/")) {
                continue;
            }
            String content = read(file);
            // Commands that route through the RealFactions core are considered migrated.
            if (content.contains("getRealFactionsServices()")) {
                continue;
            }
            int n = countMatches(mutators, content);
            if (n > 0) {
                offenders.put(rel, n);
            }
        }

        System.out.println("[RealFactions Folia audit] unmigrated command classes with direct model mutations: "
                + offenders.size() + " (model-write migration backlog)");
        offenders.forEach((f, n) -> System.out.println("    " + n + "  " + f));

        // Commands migrated in Phases 5-6 must route their model writes through the core.
        List<String> migratedCommands = List.of(
                // Phase 5
                "com/massivecraft/factions/cmd/CmdDescription.java",
                "com/massivecraft/factions/cmd/CmdTag.java",
                "com/massivecraft/factions/cmd/CmdSethome.java",
                "com/massivecraft/factions/cmd/CmdDeinvite.java",
                "com/massivecraft/factions/cmd/CmdJoin.java",
                // Phase 6
                "com/massivecraft/factions/cmd/CmdLeave.java",
                "com/massivecraft/factions/cmd/CmdKick.java",
                "com/massivecraft/factions/cmd/CmdInvite.java",
                "com/massivecraft/factions/cmd/CmdOpen.java",
                "com/massivecraft/factions/cmd/CmdPeaceful.java",
                "com/massivecraft/factions/cmd/CmdColeader.java",
                "com/massivecraft/factions/cmd/CmdMod.java",
                "com/massivecraft/factions/cmd/CmdAdmin.java",
                "com/massivecraft/factions/cmd/roles/FPromoteCommand.java",
                // Phase 7
                "com/massivecraft/factions/cmd/CmdTitle.java",
                "com/massivecraft/factions/cmd/CmdPowerBoost.java",
                "com/massivecraft/factions/cmd/CmdBan.java",
                "com/massivecraft/factions/cmd/CmdAdminFaction.java",
                "com/massivecraft/factions/cmd/alts/CmdInviteAlt.java",
                "com/massivecraft/factions/cmd/alts/CmdKickAlt.java",
                // Phase 8
                "com/massivecraft/factions/cmd/CmdCreate.java",
                "com/massivecraft/factions/cmd/CmdCreateAdmin.java"
        );
        for (String cmd : migratedCommands) {
            String content = read(root.resolve(cmd));
            assertTrue(content.contains("getRealFactionsServices()"),
                    cmd + " must route its model writes through the RealFactions core (FactionOperationExecutor).");
        }
    }

    @Test
    void reportListenerAndTaskModelMutations() {
        Path root = sourceRoot();

        // Same mutator heuristic as for commands, applied to listener and background-task classes.
        Pattern mutators = Pattern.compile(
                "\\.(setFaction|setRole|setTag|setDescription|setHome|invite|deinvite|resetFactionData"
                        + "|setOpen|setPeaceful|onDeath|alterPower|promoteNewLeader|\\bleave)\\(");

        // Listener/task files whose model mutations are already routed through the executor or the
        // claim service (verified by review in Phases 6-7): their mutator-shaped calls live inside
        // runFactionWrite/runPlayerWrite blocks, so the raw text count is a known-safe false positive.
        Set<String> knownSafeResidue = Set.of(
                "com/massivecraft/factions/util/AutoLeaveProcessTask.java",        // promoteNewLeader/leave inside runFactionWrite
                "com/massivecraft/factions/listeners/FactionsEntityListener.java", // onDeath/alterPower inside runPlayerWrite
                "com/massivecraft/factions/listeners/FactionsPlayerListener.java"  // ban-path promoteNewLeader/leave inside runFactionWrite; auto-claim via ClaimTransactionService
        );

        Map<String, Integer> allowlisted = new LinkedHashMap<>();
        Map<String, Integer> backlog = new LinkedHashMap<>();
        for (Path file : javaFiles(root)) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            boolean isListenerOrTask = rel.startsWith("com/massivecraft/factions/listeners/")
                    || rel.endsWith("AutoLeaveProcessTask.java")
                    || rel.endsWith("AutoLeaveTask.java");
            if (!isListenerOrTask) {
                continue;
            }
            int n = countMatches(mutators, read(file));
            if (n == 0) {
                continue;
            }
            if (knownSafeResidue.contains(rel)) {
                allowlisted.put(rel, n);
            } else {
                backlog.put(rel, n);
            }
        }

        System.out.println("[RealFactions Folia audit] listener/task model mutations - known-safe (executor-routed): "
                + allowlisted.size() + " files");
        allowlisted.forEach((f, n) -> System.out.println("    " + n + "  " + f + "  [allowlisted]"));
        System.out.println("[RealFactions Folia audit] listener/task model mutations - backlog (unrouted): "
                + backlog.size() + " files");
        backlog.forEach((f, n) -> System.out.println("    " + n + "  " + f));

        // Regression guard: any NEW listener/task with unrouted model mutations must be migrated
        // through the core (or explicitly allowlisted after review).
        assertTrue(backlog.isEmpty(),
                "New listener/task model mutations must route through the RealFactions core (or be allowlisted): " + backlog.keySet());
    }

    @Test
    void reportDirectCreateFactionCallsAndProtectCreationService() {
        Path root = sourceRoot();
        Map<String, Integer> offenders = new LinkedHashMap<>();

        for (Path file : javaFiles(root)) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            // The model implementation and the creation service legitimately create factions.
            if (rel.equals("com/massivecraft/factions/realfactions/FactionCreationService.java")
                    || rel.startsWith("com/massivecraft/factions/zcore/persist/")) {
                continue;
            }
            int n = countMatches(DIRECT_CREATE_FACTION, read(file));
            if (n > 0) {
                offenders.put(rel, n);
            }
        }

        System.out.println("[RealFactions Folia audit] direct Factions.createFaction() call sites outside the creation service: "
                + offenders.size() + " (faction-creation migration backlog)");
        offenders.forEach((f, n) -> System.out.println("    " + n + "  " + f));

        assertTrue(offenders.isEmpty(),
                "Faction creation must route through FactionCreationService, not Factions.getInstance().createFaction(): "
                        + offenders.keySet());

        List<String> migratedCreateCommands = List.of(
                "com/massivecraft/factions/cmd/CmdCreate.java",
                "com/massivecraft/factions/cmd/CmdCreateAdmin.java"
        );
        for (String cmd : migratedCreateCommands) {
            String content = read(root.resolve(cmd));
            assertTrue(content.contains("getRealFactionsServices()"),
                    cmd + " must route faction creation through the RealFactions core (FactionCreationService).");
            assertTrue(content.contains("factionCreation()"),
                    cmd + " must call FactionCreationService instead of Factions.getInstance().createFaction().");
        }
    }

    /** Direct Vault/economy mutations outside the bridge and known-safe read-only paths. */
    private static final Pattern DIRECT_ECON_MUTATION = Pattern.compile(
            "Econ\\.(modifyMoney|transferMoney|setBalance|modifyBalance|withdraw|deposit)\\(");

    @Test
    void reportLegacyEconUsageAndProtectEconomyBridgeRouting() {
        Path root = sourceRoot();
        Set<String> allowlisted = Set.of(
                "com/massivecraft/factions/integration/Econ.java",
                "com/massivecraft/factions/realfactions/RealFactionsEconomyService.java",
                "com/massivecraft/factions/realfactions/FactionCreationService.java",
                // read-only / display / admin in-memory faction balance helpers
                "com/massivecraft/factions/tag/FactionTag.java",
                "com/massivecraft/factions/tag/PlayerTag.java",
                "com/massivecraft/factions/util/ClipPlaceholderAPIManager.java",
                "com/massivecraft/factions/zcore/util/TagReplacer.java",
                "com/massivecraft/factions/cmd/CmdHelp.java",
                "com/massivecraft/factions/cmd/CmdTop.java",
                "com/massivecraft/factions/zcore/MCommand.java",
                "com/massivecraft/factions/cmd/FCommand.java"
        );

        Map<String, Integer> backlog = new LinkedHashMap<>();
        int total = 0;
        for (Path file : javaFiles(root)) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            if (allowlisted.contains(rel)) {
                continue;
            }
            int n = countMatches(DIRECT_ECON_MUTATION, read(file));
            if (n > 0) {
                backlog.put(rel, n);
                total += n;
            }
        }

        System.out.println("[RealFactions Folia audit] legacy Econ mutation call sites outside the bridge: "
                + total + " across " + backlog.size() + " files (economy migration backlog)");
        backlog.forEach((f, n) -> System.out.println("    " + n + "  " + f));

        List<String> economyRouted = List.of(
                "com/massivecraft/factions/cmd/CommandContext.java",
                "com/massivecraft/factions/cmd/econ/CmdMoneyDeposit.java",
                "com/massivecraft/factions/cmd/econ/CmdMoneyWithdraw.java",
                "com/massivecraft/factions/cmd/econ/CmdMoneyTransferPf.java",
                "com/massivecraft/factions/cmd/econ/CmdMoneyTransferFp.java",
                "com/massivecraft/factions/cmd/econ/CmdMoneyTransferFf.java",
                "com/massivecraft/factions/cmd/claim/CmdUnclaimall.java",
                "com/massivecraft/factions/cmd/claim/CmdUnclaimfill.java",
                "com/massivecraft/factions/missions/MissionGUI.java",
                "com/massivecraft/factions/zcore/frame/fwarps/FactionWarpsFrame.java",
                "com/massivecraft/factions/zcore/frame/fupgrades/FactionUpgradeFrame.java"
        );
        for (String routed : economyRouted) {
            String content = read(root.resolve(routed));
            assertTrue(content.contains("getRealFactionsServices().economy()"),
                    routed + " must route economy mutations through RealFactionsEconomyService.");
        }

        // MemoryFPlayer claim/leave costs must use the bridge for Vault-touching paths.
        String memoryFPlayer = read(root.resolve("com/massivecraft/factions/zcore/persist/MemoryFPlayer.java"));
        assertTrue(memoryFPlayer.contains("getRealFactionsServices().economy()"),
                "MemoryFPlayer claim/leave economy paths must use RealFactionsEconomyService.");
    }
}
