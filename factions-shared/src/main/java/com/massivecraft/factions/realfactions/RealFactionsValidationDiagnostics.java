package com.massivecraft.factions.realfactions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lightweight runtime counters for Folia staging validation.
 *
 * <p>Enabled only when {@code realfactions.validation-diagnostics} is {@code true} in
 * {@code config.yml}. When disabled, all {@code record*} methods are no-ops with negligible cost
 * (a single volatile/ boolean check).
 *
 * <p>Dump a snapshot with {@code /f debug} while the flag is enabled, or call
 * {@link #formatSummaryLines(RealFactionsEconomyService)} from tests.
 */
public final class RealFactionsValidationDiagnostics {

    private final RealFactionsFlags flags;

    private final AtomicInteger pendingModelWrites = new AtomicInteger();
    private final LongAdder writesInline = new LongAdder();
    private final LongAdder writesScheduled = new LongAdder();
    private final LongAdder writeDurationTotalNs = new LongAdder();
    private final AtomicLong writeDurationMaxNs = new AtomicLong();

    private final LongAdder blockedWaitCount = new LongAdder();
    private final LongAdder blockedWaitTotalNs = new LongAdder();
    private final AtomicLong blockedWaitMaxNs = new AtomicLong();

    private final LongAdder claimOpCount = new LongAdder();
    private final LongAdder claimOpTotalNs = new LongAdder();
    private final AtomicLong claimOpMaxNs = new AtomicLong();
    private final ConcurrentHashMap<String, LongAdder> claimOpsByName = new ConcurrentHashMap<>();

    private final LongAdder saveAsyncCount = new LongAdder();
    private final LongAdder saveSerializeTotalNs = new LongAdder();
    private final LongAdder saveWriteTotalNs = new LongAdder();
    private final AtomicLong saveSerializeMaxNs = new AtomicLong();
    private final AtomicLong saveWriteMaxNs = new AtomicLong();
    private final LongAdder autoSaveSkippedDisabled = new LongAdder();
    private final LongAdder autoSaveSkippedOverlap = new LongAdder();

    private final LongAdder economyInvocations = new LongAdder();
    private final LongAdder economyStrictModeSkips = new LongAdder();
    private final LongAdder economyVaultErrors = new LongAdder();

    public RealFactionsValidationDiagnostics(RealFactionsFlags flags) {
        this.flags = flags;
    }

    public boolean isEnabled() {
        return flags.validationDiagnostics();
    }

    public void recordPendingModelWrite(int delta) {
        if (!isEnabled()) {
            return;
        }
        pendingModelWrites.addAndGet(delta);
    }

    public void recordWrite(boolean inline, long durationNs) {
        if (!isEnabled()) {
            return;
        }
        if (inline) {
            writesInline.increment();
        } else {
            writesScheduled.increment();
        }
        writeDurationTotalNs.add(durationNs);
        updateMax(writeDurationMaxNs, durationNs);
    }

    public void recordBlockedWait(long waitNs) {
        if (!isEnabled()) {
            return;
        }
        blockedWaitCount.increment();
        blockedWaitTotalNs.add(waitNs);
        updateMax(blockedWaitMaxNs, waitNs);
    }

    public void recordClaimOp(String op, long durationNs) {
        if (!isEnabled()) {
            return;
        }
        claimOpCount.increment();
        claimOpTotalNs.add(durationNs);
        updateMax(claimOpMaxNs, durationNs);
        claimOpsByName.computeIfAbsent(op, k -> new LongAdder()).increment();
    }

    public void recordSaveSerialize(long durationNs) {
        if (!isEnabled()) {
            return;
        }
        saveSerializeTotalNs.add(durationNs);
        updateMax(saveSerializeMaxNs, durationNs);
    }

    public void recordSaveWrite(long durationNs) {
        if (!isEnabled()) {
            return;
        }
        saveWriteTotalNs.add(durationNs);
        updateMax(saveWriteMaxNs, durationNs);
    }

    public void recordSaveAsyncCompleted() {
        if (!isEnabled()) {
            return;
        }
        saveAsyncCount.increment();
    }

    public void recordAutoSaveSkipped(String reason) {
        if (!isEnabled()) {
            return;
        }
        if ("disabled".equals(reason)) {
            autoSaveSkippedDisabled.increment();
        } else if ("overlap".equals(reason)) {
            autoSaveSkippedOverlap.increment();
        }
    }

    public void recordEconomyInvocation() {
        if (!isEnabled()) {
            return;
        }
        economyInvocations.increment();
    }

    public void recordEconomyStrictModeSkip() {
        if (!isEnabled()) {
            return;
        }
        economyStrictModeSkips.increment();
    }

    public void recordEconomyVaultError() {
        if (!isEnabled()) {
            return;
        }
        economyVaultErrors.increment();
    }

    public List<String> formatSummaryLines(RealFactionsEconomyService economy) {
        List<String> lines = new ArrayList<>();
        lines.add("validation-diagnostics: enabled");
        lines.add("folia: " + flags.isFolia() + ", foliaStrictMode: " + flags.foliaStrictMode());
        if (economy != null) {
            lines.add("economy provider: " + economy.providerName()
                    + " (known Folia-safe: " + economy.providerKnownSafe()
                    + ", disabledByStrictMode: " + economy.isDisabledByStrictMode() + ")");
        }

        lines.add("--- executor ---");
        lines.add("pendingModelWrites (approx queue depth): " + pendingModelWrites.get());
        lines.add("writes inline/scheduled: " + writesInline.sum() + " / " + writesScheduled.sum());
        lines.add("write latency avg/max ms: "
                + formatAvgMs(writeDurationTotalNs.sum(), writesInline.sum() + writesScheduled.sum())
                + " / " + formatMs(writeDurationMaxNs.get()));
        lines.add("blocked cross-thread waits count/avg/max ms: "
                + blockedWaitCount.sum() + " / "
                + formatAvgMs(blockedWaitTotalNs.sum(), blockedWaitCount.sum()) + " / "
                + formatMs(blockedWaitMaxNs.get()));

        lines.add("--- claims ---");
        lines.add("claim ops total: " + claimOpCount.sum()
                + " avg/max ms: " + formatAvgMs(claimOpTotalNs.sum(), claimOpCount.sum())
                + " / " + formatMs(claimOpMaxNs.get()));
        claimOpsByName.forEach((op, count) -> lines.add("  " + op + ": " + count.sum()));

        lines.add("--- persistence ---");
        lines.add("async saves completed: " + saveAsyncCount.sum());
        lines.add("serialize avg/max ms: "
                + formatAvgMs(saveSerializeTotalNs.sum(), saveAsyncCount.sum())
                + " / " + formatMs(saveSerializeMaxNs.get()));
        lines.add("disk write avg/max ms: "
                + formatAvgMs(saveWriteTotalNs.sum(), saveAsyncCount.sum())
                + " / " + formatMs(saveWriteMaxNs.get()));
        lines.add("autosave skipped (disabled/overlap): "
                + autoSaveSkippedDisabled.sum() + " / " + autoSaveSkippedOverlap.sum());

        lines.add("--- economy bridge ---");
        lines.add("vault invocations: " + economyInvocations.sum());
        lines.add("strict-mode skips: " + economyStrictModeSkips.sum());
        lines.add("vault errors: " + economyVaultErrors.sum());

        lines.add("--- static audit (run mvn test before staging) ---");
        lines.add("expected: command/board/createFaction/listener/econ backlog 0; raw scheduler 5/3 files");

        return lines;
    }

    private static void updateMax(AtomicLong maxHolder, long value) {
        long current;
        do {
            current = maxHolder.get();
            if (value <= current) {
                return;
            }
        } while (!maxHolder.compareAndSet(current, value));
    }

    private static String formatAvgMs(long totalNs, long count) {
        if (count <= 0L) {
            return "0.00";
        }
        return String.format(Locale.ROOT, "%.2f", totalNs / (count * 1_000_000.0));
    }

    private static String formatMs(long ns) {
        return String.format(Locale.ROOT, "%.2f", ns / 1_000_000.0);
    }
}
