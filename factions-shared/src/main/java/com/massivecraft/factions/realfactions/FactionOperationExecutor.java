package com.massivecraft.factions.realfactions;

import com.massivecraft.factions.scheduler.FactionScheduler;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

    /**
     * Upper bound on how long {@link #runWriteForResult} will block a calling (region) thread while
     * waiting for the global region scheduler to run the task. A generous bound that should never be
     * hit in normal operation; its only purpose is to convert a permanent hang (for example if the
     * scheduler stops making progress during shutdown) into a fast, diagnosable failure.
     */
    private static final long BLOCKING_RESULT_TIMEOUT_SECONDS = 10L;

    private final FactionScheduler scheduler;
    private final boolean folia;
    private final RealFactionsValidationDiagnostics diagnostics;
    private final ThreadLocal<Boolean> onModelThread = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Reflective handle for Folia's Server#isGlobalTickThread(), resolved lazily and cached. The
    // global tick thread is the model's single-writer thread on Folia, but a task dispatched there
    // by the platform (for example console command execution) carries no ThreadLocal marker, so we
    // must be able to recognise it directly to avoid re-scheduling/blocking against our own thread.
    private volatile Method foliaGlobalTickThreadMethod;
    private volatile boolean foliaGlobalTickThreadUnavailable;

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
        if (!folia) {
            // On Paper the global scheduler runs on the primary thread, which is the model thread.
            return Bukkit.isPrimaryThread();
        }
        // On Folia the model thread is the global tick thread. Recognise it directly so platform-
        // dispatched work that runs there without our marker (e.g. console commands) is treated as
        // on-thread instead of re-scheduling onto, or blocking against, the same thread.
        return isFoliaGlobalTickThread();
    }

    private boolean isFoliaGlobalTickThread() {
        if (foliaGlobalTickThreadUnavailable) {
            return false;
        }
        try {
            Method method = foliaGlobalTickThreadMethod;
            if (method == null) {
                method = Bukkit.getServer().getClass().getMethod("isGlobalTickThread");
                foliaGlobalTickThreadMethod = method;
            }
            Object result = method.invoke(Bukkit.getServer());
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            // Older Folia builds may not expose this method; fall back to marker-only detection.
            foliaGlobalTickThreadUnavailable = true;
            return false;
        }
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
     * the calling thread until the global scheduler has executed the task. Intended for controlled
     * economy bridges that must not call Vault from region threads.
     *
     * <p>WARNING: this blocks a region thread on Folia and must never be called from the global
     * region thread itself (that would deadlock waiting for the scheduler to run a task behind the
     * current one). Callers already on the model thread run inline and are safe.
     *
     * <p>The wait is bounded by {@link #BLOCKING_RESULT_TIMEOUT_SECONDS}: a timeout throws rather
     * than hanging forever, and any exception thrown by {@code task} is propagated to the caller
     * instead of being swallowed (which previously returned {@code null} and could NPE on unboxing).
     */
    public <T> T runWriteForResult(Supplier<T> task) {
        if (isOnModelThread()) {
            return task.get();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        long waitStart = diagnostics != null && diagnostics.isEnabled() ? System.nanoTime() : 0L;
        scheduler.runGlobal(() -> runMarked(() -> {
            try {
                result.set(task.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        }));
        boolean completed;
        try {
            completed = latch.await(BLOCKING_RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for model-thread work", e);
        }
        if (diagnostics != null && diagnostics.isEnabled()) {
            diagnostics.recordBlockedWait(System.nanoTime() - waitStart);
        }
        if (!completed) {
            throw new IllegalStateException("Timed out after " + BLOCKING_RESULT_TIMEOUT_SECONDS
                    + "s waiting for the model thread to run a result-returning task. The global region"
                    + " scheduler was not making progress (for example during shutdown).");
        }
        Throwable t = error.get();
        if (t != null) {
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new IllegalStateException("Model-thread task failed", t);
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
