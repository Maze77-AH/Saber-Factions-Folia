package com.massivecraft.factions.realfactions;

import com.massivecraft.factions.scheduler.FactionScheduler;
import org.bukkit.Bukkit;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Single-writer access layer for the shared faction/player/board model.
 *
 * <p>The legacy SaberFactions model ({@code Factions}, {@code FPlayers}, {@code Board} and
 * their nested collections) is plain, unsynchronized in-memory state designed for a single
 * main thread. On Folia it would otherwise be read and written from many region and entity
 * threads concurrently, which can corrupt state or throw
 * {@link java.util.ConcurrentModificationException}.
 *
 * <p>This executor funnels every model write onto one serialized execution context: the
 * global region scheduler. That context is the main thread on Paper and the single global
 * region thread on Folia. Because all writes run on that one thread, there is no write/write
 * race, and reads taken on that thread (snapshots) never observe a torn state.
 *
 * <p>Conventions enforced by review and by {@code RealFactionsFoliaAuditTest}:
 * <ul>
 *   <li>model writes go through {@link #runWrite}, {@link #runFactionWrite},
 *       {@link #runPlayerWrite} or {@link #runClaimWrite};</li>
 *   <li>snapshot reads go through {@link #runSnapshotRead};</li>
 *   <li>Bukkit entity/region operations are NOT performed here. Schedule those on the owning
 *       entity/region scheduler instead.</li>
 * </ul>
 */
public final class FactionOperationExecutor {

    private final FactionScheduler scheduler;
    private final boolean folia;
    private final RealFactionsValidationDiagnostics diagnostics;
    private final ThreadLocal<Boolean> onModelThread = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public FactionOperationExecutor(FactionScheduler scheduler, boolean folia,
                                      RealFactionsValidationDiagnostics diagnostics) {
        this.scheduler = scheduler;
        this.folia = folia;
        this.diagnostics = diagnostics;
    }

    /**
     * @return true if the calling thread is the model's single-writer thread.
     */
    public boolean isOnModelThread() {
        if (onModelThread.get()) {
            return true;
        }
        // On Paper the global scheduler runs on the primary thread, which is the model thread.
        // On Folia we only trust the explicit marker, which is set whenever we enter the
        // global region scheduler through this executor (or through marked SpiralTask work).
        return !folia && Bukkit.isPrimaryThread();
    }

    /**
     * Run a model write. Executes inline when already on the model thread (so writes issued
     * from a command running on the model thread keep their original ordering), otherwise
     * schedules the write onto the global region scheduler.
     */
    public void runWrite(Runnable task) {
        if (isOnModelThread()) {
            if (diagnostics != null && diagnostics.isEnabled()) {
                long start = System.nanoTime();
                try {
                    task.run();
                } finally {
                    diagnostics.recordWrite(true, System.nanoTime() - start);
                }
            } else {
                task.run();
            }
        } else if (diagnostics != null && diagnostics.isEnabled()) {
            diagnostics.recordPendingModelWrite(1);
            scheduler.runGlobal(() -> runMarked(() -> {
                long start = System.nanoTime();
                try {
                    task.run();
                } finally {
                    diagnostics.recordWrite(false, System.nanoTime() - start);
                    diagnostics.recordPendingModelWrite(-1);
                }
            }));
        } else {
            scheduler.runGlobal(() -> runMarked(task));
        }
    }

    /**
     * Run a model-thread task and return its result. When not already on the model thread, blocks
     * until the global scheduler has executed the task. Intended for controlled economy bridges that
     * must not call Vault from region threads.
     */
    public <T> T runWriteForResult(Supplier<T> task) {
        if (isOnModelThread()) {
            return task.get();
        }
        java.util.concurrent.atomic.AtomicReference<T> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        long waitStart = diagnostics != null && diagnostics.isEnabled() ? System.nanoTime() : 0L;
        scheduler.runGlobal(() -> runMarked(() -> {
            try {
                result.set(task.get());
            } finally {
                latch.countDown();
            }
        }));
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for model-thread economy work", e);
        }
        if (diagnostics != null && diagnostics.isEnabled()) {
            diagnostics.recordBlockedWait(System.nanoTime() - waitStart);
        }
        return result.get();
    }

    public void runFactionWrite(Runnable task) {
        runWrite(task);
    }

    public void runPlayerWrite(Runnable task) {
        runWrite(task);
    }

    public void runClaimWrite(Runnable task) {
        runWrite(task);
    }

    /**
     * Produce an immutable snapshot on the model thread and deliver it to {@code consumer}
     * (also on the model thread). Only the immutable result should escape the supplier; the
     * live model must not be captured by reference and used off-thread.
     */
    public <T> void runSnapshotRead(Supplier<T> snapshot, Consumer<T> consumer) {
        runWrite(() -> consumer.accept(snapshot.get()));
    }

    /**
     * Execute {@code task} while marking the current thread as the model thread, restoring the
     * previous marker afterwards. Used by work that is already running on the global region
     * scheduler (for example {@code SpiralTask} batches) so that nested {@link #runWrite} calls
     * execute inline instead of re-scheduling and reordering.
     */
    public void runMarked(Runnable task) {
        boolean previous = onModelThread.get();
        onModelThread.set(Boolean.TRUE);
        try {
            task.run();
        } finally {
            onModelThread.set(previous);
        }
    }

    public boolean isFolia() {
        return folia;
    }
}
