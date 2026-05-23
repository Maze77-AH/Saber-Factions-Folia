package com.massivecraft.factions.realfactions;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.scheduler.FactionScheduler;

/**
 * Folia-safe persistence.
 *
 * <p>The legacy save path serialized the live faction/player/board collections and wrote them to
 * disk on whatever thread invoked the save. On Folia that traversal can race with region-thread
 * mutations. This service splits the work into two phases:
 *
 * <ol>
 *   <li>on the model thread, serialize each model to an immutable JSON snapshot string (no other
 *       thread mutates the model concurrently because all writes are single-writer);</li>
 *   <li>on the async scheduler, write those immutable snapshot strings to disk.</li>
 * </ol>
 *
 * The live mutable collections are therefore never traversed off the model thread.
 */
public final class PersistenceSnapshotService {

    private final FactionOperationExecutor executor;
    private final FactionScheduler scheduler;
    private final RealFactionsValidationDiagnostics diagnostics;

    public PersistenceSnapshotService(FactionOperationExecutor executor, FactionScheduler scheduler,
                                      RealFactionsValidationDiagnostics diagnostics) {
        this.executor = executor;
        this.scheduler = scheduler;
        this.diagnostics = diagnostics;
    }

    public void saveAllAsync() {
        saveAllAsync(null);
    }

    /**
     * Serialize immutable snapshots on the model thread, then write them on the async scheduler.
     *
     * @param onComplete optional callback run on the async thread after the write finishes.
     */
    public void saveAllAsync(Runnable onComplete) {
        executor.runWrite(() -> {
            long serializeStart = diagnostics != null && diagnostics.isEnabled() ? System.nanoTime() : 0L;
            final String factionsJson = Factions.getInstance().serializeToJson();
            final String playersJson = FPlayers.getInstance().serializeToJson();
            final String boardJson = Board.getInstance().serializeToJson();
            if (diagnostics != null && diagnostics.isEnabled()) {
                diagnostics.recordSaveSerialize(System.nanoTime() - serializeStart);
            }
            scheduler.runAsync(() -> {
                long writeStart = diagnostics != null && diagnostics.isEnabled() ? System.nanoTime() : 0L;
                try {
                    Factions.getInstance().writeJson(factionsJson, true);
                    FPlayers.getInstance().writeJson(playersJson, true);
                    Board.getInstance().writeJson(boardJson, true);
                } finally {
                    if (diagnostics != null && diagnostics.isEnabled()) {
                        diagnostics.recordSaveWrite(System.nanoTime() - writeStart);
                        diagnostics.recordSaveAsyncCompleted();
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            });
        });
    }

    /**
     * Serialize and write synchronously on the model thread. Used on shutdown, where async tasks
     * are not guaranteed to run.
     */
    public void saveAllSync() {
        executor.runWrite(() -> {
            Factions.getInstance().forceSave(true);
            FPlayers.getInstance().forceSave(true);
            Board.getInstance().forceSave(true);
        });
    }
}
