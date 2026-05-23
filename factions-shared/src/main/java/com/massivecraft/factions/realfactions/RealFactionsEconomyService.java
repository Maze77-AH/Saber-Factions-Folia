package com.massivecraft.factions.realfactions;

import com.massivecraft.factions.Conf;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.iface.EconomyParticipator;
import com.massivecraft.factions.integration.Econ;
import com.massivecraft.factions.util.Logger;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Folia-first economy bridge.
 *
 * <p>Vault economy providers (EssentialsX, CMI, etc.) are written for a single main thread and are
 * generally not Folia-aware. RealFactions must therefore not call Vault from arbitrary region
 * threads. This service is the controlled entry point for economy operations:
 *
 * <ul>
 *   <li>It detects the Vault provider and reports whether it is on the known-Folia-safe allowlist.</li>
 *   <li>It serializes Vault work onto the model/global thread via {@link FactionOperationExecutor}.</li>
 *   <li>Under {@code foliaStrictMode}, when the provider is not known Folia-safe, it disables economy
 *       so costs/refunds are skipped instead of issuing unsafe cross-thread Vault calls.</li>
 * </ul>
 *
 * <p><b>Vault is not assumed Folia-safe.</b> Treat every provider as single-thread-only unless it is
 * explicitly allowlisted in {@link #KNOWN_FOLIA_SAFE_PROVIDERS}. Legacy call sites not yet routed
 * through this service remain a Folia risk and are tracked in the audit document.
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

    public boolean hasAtLeast(EconomyParticipator ep, double delta, String toDoThis) {
        if (!applyCommandEconomyCosts()) {
            return true;
        }
        return runEconomy(() -> Econ.hasAtLeast(ep, delta, toDoThis), true);
    }

    public boolean modifyMoney(EconomyParticipator ep, double delta, String toDoThis, String forDoingThis) {
        if (!applyCommandEconomyCosts()) {
            return true;
        }
        return runEconomy(() -> Econ.modifyMoney(ep, delta, toDoThis, forDoingThis), true);
    }

    public boolean transferMoney(FPlayer invoker, EconomyParticipator from, EconomyParticipator to, double amount) {
        return transferMoney(invoker, from, to, amount, true);
    }

    public boolean transferMoney(FPlayer invoker, EconomyParticipator from, EconomyParticipator to, double amount,
                                 boolean notify) {
        if (!isEconomyEnabled()) {
            return false;
        }
        return runEconomy(() -> Econ.transferMoney(invoker, from, to, amount, notify), false);
    }

    public boolean payCommandCost(FPlayer player, Faction faction, double cost, String toDoThis, String forDoingThis) {
        if (player == null || cost == 0.0 || player.isAdminBypassing()) {
            return true;
        }
        if (!applyCommandEconomyCosts()) {
            return true;
        }
        if (Conf.bankEnabled && Conf.bankFactionPaysCosts && player.hasFaction()) {
            return modifyMoney(faction, -cost, toDoThis, forDoingThis);
        }
        return modifyMoney(player, -cost, toDoThis, forDoingThis);
    }

    public boolean canAffordCommandCost(FPlayer player, Faction faction, double cost, String toDoThis) {
        if (player == null || cost == 0.0 || player.isAdminBypassing()) {
            return true;
        }
        if (!applyCommandEconomyCosts()) {
            return true;
        }
        if (Conf.bankEnabled && Conf.bankFactionPaysCosts && player.hasFaction()) {
            return hasAtLeast(faction, cost, toDoThis);
        }
        return hasAtLeast(player, cost, toDoThis);
    }

    /**
     * Set a newly created faction's starting balance. Intended to be called on the model thread
     * (for example from inside a {@link FactionCreationService} transaction).
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
