package com.massivecraft.factions.realfactions;

import com.massivecraft.factions.Conf;
import com.massivecraft.factions.scheduler.FactionScheduler;
import com.massivecraft.factions.scheduler.FactionSchedulers;
import com.massivecraft.factions.util.Logger;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Container for the RealFactions Folia-first core services.
 *
 * <p>This is the entry point for the new architecture. The legacy SaberFactions model is still
 * present underneath, but after migration all model writes flow through the
 * {@link FactionOperationExecutor} (single-writer), claims flow through the
 * {@link ClaimTransactionService}, saves flow through the {@link PersistenceSnapshotService}, and
 * async chat reads come from the {@link ChatDisplayCache}.
 *
 * <p>Created once during {@code onEnable}; held by {@code FactionsPlugin}.
 */
public final class RealFactionsServices {

    private final FactionScheduler scheduler;
    private final RealFactionsFlags flags;
    private final FactionOperationExecutor executor;
    private final ClaimTransactionService claims;
    private final PersistenceSnapshotService persistence;
    private final ChatDisplayCache chatCache;
    private final RealFactionsEconomyService economy;
    private final FactionCreationService factionCreation;
    private final RealFactionsValidationDiagnostics diagnostics;

    public RealFactionsServices(FactionScheduler scheduler, FileConfiguration config) {
        boolean folia = FactionSchedulers.isFolia();
        this.scheduler = scheduler;
        this.flags = RealFactionsFlags.from(config, folia);
        this.diagnostics = new RealFactionsValidationDiagnostics(flags);
        this.executor = new FactionOperationExecutor(scheduler, folia, diagnostics);
        this.claims = new ClaimTransactionService(executor, diagnostics);
        this.persistence = new PersistenceSnapshotService(executor, scheduler, diagnostics);
        this.chatCache = new ChatDisplayCache();
        this.economy = new RealFactionsEconomyService(executor, flags, diagnostics);
        this.factionCreation = new FactionCreationService(executor, economy);
    }

    public RealFactionsFlags flags() {
        return flags;
    }

    public FactionOperationExecutor executor() {
        return executor;
    }

    public ClaimTransactionService claims() {
        return claims;
    }

    public PersistenceSnapshotService persistence() {
        return persistence;
    }

    public ChatDisplayCache chatCache() {
        return chatCache;
    }

    public RealFactionsEconomyService economy() {
        return economy;
    }

    public FactionCreationService factionCreation() {
        return factionCreation;
    }

    public RealFactionsValidationDiagnostics diagnostics() {
        return diagnostics;
    }

    /**
     * Detect the Vault economy provider and apply Folia strict-mode economy gating. Must be called
     * after {@code Econ.setup()} has run during startup.
     */
    public void detectEconomyProvider() {
        economy.detectProvider();
    }

    /**
     * Apply Folia strict-mode safety overrides. Must be called after {@code Conf.load()} so the
     * overrides take precedence over the loaded configuration.
     */
    public void applyStrictModeOverrides() {
        if (!flags.disableUnsafeIntegrations()) {
            return;
        }
        // WorldGuard chunk inspection during a claim touches an arbitrary chunk from the model
        // thread, which is unsafe on Folia (and WorldGuard itself is not Folia-compatible).
        if (Conf.worldGuardChecking) {
            Conf.worldGuardChecking = false;
            Logger.print("[RealFactions] foliaStrictMode: disabled WorldGuard claim checking (not Folia-safe).", Logger.PrefixType.WARNING);
        }
    }

    /**
     * Start background model-thread tasks. Call after the model has been loaded.
     */
    public void startBackgroundTasks() {
        // Refresh the chat display cache on the model thread (every 2s) so AsyncPlayerChatEvent
        // can read immutable snapshots instead of traversing the live model off-thread.
        scheduler.runGlobalTimer(() -> executor.runMarked(chatCache::refreshOnline), 40L, 40L);
        if (flags.validationDiagnostics()) {
            Logger.print("[RealFactions] validation-diagnostics enabled — use /f debug for runtime counters.",
                    Logger.PrefixType.DEFAULT);
        }
    }
}
