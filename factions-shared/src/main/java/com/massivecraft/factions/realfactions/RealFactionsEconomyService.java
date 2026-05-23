package com.massivecraft.factions.realfactions;

import com.massivecraft.factions.Conf;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.integration.Econ;
import com.massivecraft.factions.util.Logger;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Folia-first economy bridge.
 *
 * <p>Vault economy providers (EssentialsX, CMI, etc.) are written for a single main thread and are
 * generally not Folia-aware. RealFactions must therefore not call Vault from arbitrary region
 * threads. This service is the controlled entry point for the new code paths:
 *
 * <ul>
 *   <li>It detects the Vault provider and reports whether it is on the known-Folia-safe allowlist.</li>
 *   <li>It serializes economy work onto the designated economy thread - the model/global thread via
 *       {@link FactionOperationExecutor} - so Vault is never touched from a region thread.</li>
 *   <li>Under {@code foliaStrictMode}, when the provider is not known Folia-safe, it disables economy
 *       so costs/refunds are skipped instead of issuing unsafe cross-thread Vault calls.</li>
 * </ul>
 *
 * <p>This is the foundational bridge: it gates and routes the NEW economy paths (faction creation).
 * The legacy {@code Econ.*} call sites in other commands (cost/refund via {@code payForCommand}) are
 * not yet routed through it; that broader migration is tracked in the audit document.
 *
 * <p><b>Vault is not assumed Folia-safe.</b> Treat every provider as single-thread-only unless it is
 * explicitly allowlisted in {@link #KNOWN_FOLIA_SAFE_PROVIDERS}.
 */
public final class RealFactionsEconomyService {

    // No mainstream Vault economy provider is currently confirmed Folia-safe. Add names here once a
    // provider has been verified to be safe under regionized threading.
    private static final Set<String> KNOWN_FOLIA_SAFE_PROVIDERS = Set.of();

    private final FactionOperationExecutor executor;
    private final boolean folia;
    private final boolean foliaStrictMode;

    private String providerName = "none";
    private boolean providerKnownSafe = false;
    private boolean disabledByStrictMode = false;

    public RealFactionsEconomyService(FactionOperationExecutor executor, RealFactionsFlags flags) {
        this.executor = executor;
        this.folia = flags.isFolia();
        this.foliaStrictMode = flags.foliaStrictMode();
    }

    /**
     * Detect the Vault provider and decide whether economy is safe to use. Must be called after
     * {@code Econ.setup()} has run.
     */
    public void detectProvider() {
        this.providerName = Econ.getProviderName();
        this.providerKnownSafe = KNOWN_FOLIA_SAFE_PROVIDERS.contains(providerName.toLowerCase());

        if (folia && foliaStrictMode && Econ.isSetup() && !providerKnownSafe) {
            this.disabledByStrictMode = true;
            Logger.print("[RealFactions] foliaStrictMode: economy provider '" + providerName
                    + "' is not known Folia-safe; economy features are disabled. Costs and refunds will be skipped.",
                    Logger.PrefixType.WARNING);
        } else if (Econ.isSetup()) {
            Logger.print("[RealFactions] Economy provider: '" + providerName + "' (known Folia-safe: "
                    + providerKnownSafe + ").", Logger.PrefixType.DEFAULT);
        }
    }

    /**
     * @return true if command costs/refunds should run: Vault is set up and active, and economy has
     * not been disabled by Folia strict mode.
     */
    public boolean applyCommandEconomyCosts() {
        return !disabledByStrictMode && Econ.shouldBeUsed();
    }

    /**
     * @return true if economy operations should run: Vault is set up and active, and economy has not
     * been disabled by Folia strict mode.
     */
    public boolean isEconomyEnabled() {
        return applyCommandEconomyCosts();
    }

    public boolean isDisabledByStrictMode() {
        return disabledByStrictMode;
    }

    public String providerName() {
        return providerName;
    }

    public boolean providerKnownSafe() {
        return providerKnownSafe;
    }

    public boolean isFolia() {
        return folia;
    }

    /**
     * Run a Vault operation on the designated economy thread (the model/global thread), serialized.
     * No-op when economy is disabled (including strict-mode gating). Safe to call from any thread.
     */
    public void runEconomy(Runnable vaultWork) {
        if (!isEconomyEnabled()) {
            return;
        }
        executor.runWrite(vaultWork);
    }

    /**
     * Run a Vault operation on the economy thread and return its result. When economy is disabled,
     * returns {@code disabledResult} without touching Vault.
     */
    public <T> T runEconomy(Supplier<T> vaultWork, T disabledResult) {
        if (!isEconomyEnabled()) {
            return disabledResult;
        }
        if (executor.isOnModelThread()) {
            return vaultWork.get();
        }
        return executor.runWriteForResult(vaultWork);
    }

    /**
     * Set a newly created faction's starting balance. Intended to be called on the model thread
     * (for example from inside a {@link FactionCreationService} transaction). Matches legacy
     * {@code /f create} behaviour: only runs when {@code Conf.econEnabled} is true. When Folia
     * strict mode has disabled economy, the balance step is skipped and treated as success.
     *
     * @return false only when a Vault balance setup was attempted and failed
     */
    public boolean initStartingBalance(Faction faction) {
        if (!Conf.econEnabled) {
            return true;
        }
        if (disabledByStrictMode) {
            return true;
        }
        try {
            return Econ.setBalance(faction.getAccountId(), Conf.econFactionStartingBalance);
        } catch (Throwable t) {
            Logger.print("[RealFactions] Failed to set starting balance for faction "
                    + faction.getTag() + ": " + t.getMessage(), Logger.PrefixType.WARNING);
            return false;
        }
    }

    /**
     * Refund a player command cost after a failed post-payment model write (for example faction
     * creation rollback). Runs on the economy thread when economy is active.
     */
    public void refundPlayerCost(FPlayer player, double amount, String forDoingThis) {
        if (!applyCommandEconomyCosts() || player == null || amount == 0.0D || player.isAdminBypassing()) {
            return;
        }
        runEconomy(() -> Econ.modifyMoney(player, amount, null, forDoingThis));
    }
}
