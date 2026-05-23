package com.massivecraft.factions.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Folia's global, region, entity, and async schedulers all reject initial delays
 * and periods that are {@code <= 0} with {@code IllegalArgumentException}. Several
 * call sites in this codebase pass {@code 0L} (the Bukkit "next tick" sentinel).
 * {@link FoliaScheduler#sanitizeTicks(long)} normalizes any non-positive value to
 * {@code 1L} so the abstraction is safe to call uniformly from Paper and Folia.
 */
class FoliaSchedulerSanitizationTest {

    @Test
    void zeroBecomesOneTick() {
        assertEquals(1L, FoliaScheduler.sanitizeTicks(0L));
    }

    @Test
    void negativeBecomesOneTick() {
        assertEquals(1L, FoliaScheduler.sanitizeTicks(-1L));
        assertEquals(1L, FoliaScheduler.sanitizeTicks(Long.MIN_VALUE));
    }

    @Test
    void positiveTicksArePreserved() {
        assertEquals(1L, FoliaScheduler.sanitizeTicks(1L));
        assertEquals(20L, FoliaScheduler.sanitizeTicks(20L));
        assertEquals(1200L, FoliaScheduler.sanitizeTicks(1200L));
        assertEquals(Long.MAX_VALUE, FoliaScheduler.sanitizeTicks(Long.MAX_VALUE));
    }
}
