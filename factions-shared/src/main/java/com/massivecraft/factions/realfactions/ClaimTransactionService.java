package com.massivecraft.factions.realfactions;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import org.bukkit.World;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The single controlled entry point for board/claim mutations.
 *
 * <p>After migration, no code should mutate the {@link Board} for a claim/unclaim outside this
 * service. Every operation runs through {@link FactionOperationExecutor#runClaimWrite}, which
 * serializes it onto the model's single-writer thread. This removes the write/write races that
 * make the raw board unsafe on Folia while preserving the existing claim storage format and the
 * existing validation/economy/event logic inside {@code attemptClaim}/{@code attemptUnclaim}.
 *
 * <p>Two flavours are provided:
 * <ul>
 *   <li>{@code claim}/{@code unclaim}/{@code unclaimAll} - asynchronous; schedule the write and
 *       deliver the result through a callback on the model thread. Safe to call from any thread
 *       (e.g. a command running on a region thread under Folia).</li>
 *   <li>{@code claimNow}/{@code unclaimNow} - synchronous; assume the caller is already on the
 *       model thread (for example inside a marked {@code SpiralTask} batch) and return the result
 *       directly.</li>
 * </ul>
 */
public final class ClaimTransactionService {

    private final FactionOperationExecutor executor;

    public ClaimTransactionService(FactionOperationExecutor executor) {
        this.executor = executor;
    }

    public void claim(FPlayer fplayer, Faction forFaction, FLocation flocation, boolean notifyFailure, Consumer<Boolean> callback) {
        executor.runClaimWrite(() -> {
            boolean result = fplayer.attemptClaim(forFaction, flocation, notifyFailure);
            if (callback != null) {
                callback.accept(result);
            }
        });
    }

    public void unclaim(FPlayer fplayer, Faction forFaction, FLocation flocation, boolean notifyFailure, Consumer<Boolean> callback) {
        executor.runClaimWrite(() -> {
            boolean result = fplayer.attemptUnclaim(forFaction, flocation, notifyFailure);
            if (callback != null) {
                callback.accept(result);
            }
        });
    }

    /**
     * Claim a single chunk assuming the caller already holds the model thread. Returns the
     * result directly so callers such as {@code SpiralTask} can drive their batch loop.
     */
    public boolean claimNow(FPlayer fplayer, Faction forFaction, FLocation flocation, boolean notifyFailure) {
        return fplayer.attemptClaim(forFaction, flocation, notifyFailure);
    }

    /**
     * Unclaim a single chunk assuming the caller already holds the model thread.
     */
    public boolean unclaimNow(FPlayer fplayer, Faction forFaction, FLocation flocation, boolean notifyFailure) {
        return fplayer.attemptUnclaim(forFaction, flocation, notifyFailure);
    }

    /**
     * Remove a single chunk claim directly (no permission/economy checks), assuming the caller
     * already holds the model thread. Used by fill-style commands that have already validated and
     * are running inside a {@link #runTransaction} body.
     */
    public void removeAtNow(FLocation flocation) {
        Board.getInstance().removeAt(flocation);
    }

    public void unclaimAll(Faction faction, Runnable afterCommit) {
        unclaimAll(faction.getId(), afterCommit);
    }

    /**
     * Unclaim every chunk owned by {@code factionId} as one serialized board write.
     */
    public void unclaimAll(String factionId, Runnable afterCommit) {
        executor.runClaimWrite(() -> {
            Board.getInstance().unclaimAll(factionId);
            if (afterCommit != null) {
                afterCommit.run();
            }
        });
    }

    /**
     * Unclaim every chunk owned by {@code factionId}, but only if {@code precondition} (evaluated on
     * the model thread, e.g. economy refund + event firing/cancellation) returns true. The board
     * mutation and the precondition run atomically on the single-writer thread, so the refund/event
     * and the unclaim cannot be interleaved by another model write.
     */
    public void unclaimAll(String factionId, BooleanSupplier precondition, Runnable afterCommit) {
        executor.runClaimWrite(() -> {
            if (precondition != null && !precondition.getAsBoolean()) {
                return;
            }
            Board.getInstance().unclaimAll(factionId);
            if (afterCommit != null) {
                afterCommit.run();
            }
        });
    }

    /**
     * Unclaim every chunk owned by {@code factionId} within {@code world} as one serialized board write.
     */
    public void unclaimAllInWorld(String factionId, World world, Runnable afterCommit) {
        executor.runClaimWrite(() -> {
            Board.getInstance().unclaimAllInWorld(factionId, world);
            if (afterCommit != null) {
                afterCommit.run();
            }
        });
    }

    /**
     * Run an arbitrary claim/board transaction body as one serialized model write. Used by the
     * claim commands to keep their multi-step decision logic (system factions, radius claim
     * messaging, logging) inside a single controlled write.
     */
    public void runTransaction(Runnable body) {
        executor.runClaimWrite(body);
    }

    public FactionOperationExecutor executor() {
        return executor;
    }
}
