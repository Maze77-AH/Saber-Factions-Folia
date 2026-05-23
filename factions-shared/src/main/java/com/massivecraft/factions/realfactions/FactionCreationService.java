package com.massivecraft.factions.realfactions;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;

import java.util.function.Consumer;

/**
 * The single controlled entry point for creating a faction.
 *
 * <p>{@code Factions.createFaction()} generates a new faction id and registers it in the global
 * factions map - a model write that must be single-writer. After migration no command should call
 * {@code Factions.getInstance().createFaction()} directly; instead they call {@link #create}, which
 * runs the creation and the caller's setup as one serialized transaction on the model thread.
 *
 * <p>The {@code onCreated} body runs on the model thread with the freshly created faction. It should
 * perform the faction setup (tag, flags), the starting-balance call via
 * {@link RealFactionsEconomyService}, and any messaging. Because economy work routes through the
 * economy service (which targets this same thread and gates under strict mode), Vault calls stay
 * isolated and never run on a region thread.
 */
public final class FactionCreationService {

    private final FactionOperationExecutor executor;
    private final RealFactionsEconomyService economy;

    public FactionCreationService(FactionOperationExecutor executor, RealFactionsEconomyService economy) {
        this.executor = executor;
        this.economy = economy;
    }

    /**
     * Create a faction as one serialized model-thread transaction.
     *
     * @param onCreated applied to the new faction on the model thread (setup + balance + messaging)
     * @param onFailure run on the model thread if {@code createFaction()} returns null
     */
    public void create(Consumer<Faction> onCreated, Runnable onFailure) {
        executor.runFactionWrite(() -> {
            Faction faction = Factions.getInstance().createFaction();
            if (faction == null) {
                if (onFailure != null) {
                    onFailure.run();
                }
                return;
            }
            onCreated.accept(faction);
        });
    }

    /**
     * Roll back a partially created faction when post-creation setup fails (for example starting
     * balance setup). Must be called on the model thread.
     */
    public void rollbackCreation(Faction faction, FPlayer creator, double refundAmount, String refundReason) {
        if (creator != null) {
            creator.resetFactionData();
        }
        if (faction != null) {
            Factions.getInstance().removeFaction(faction.getId());
        }
        if (creator != null && refundAmount != 0.0D) {
            economy.refundPlayerCost(creator, refundAmount, refundReason);
        }
    }

    /**
     * Apply the legacy starting-balance step for a newly created faction. Returns false when balance
     * setup was attempted and failed; callers should {@link #rollbackCreation} in that case.
     */
    public boolean applyStartingBalance(Faction faction) {
        return economy.initStartingBalance(faction);
    }

    public RealFactionsEconomyService economy() {
        return economy;
    }
}
