package com.massivecraft.factions.realfactions;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Folia-first feature flags for RealFactions. Resolved once at enable time.
 *
 * <ul>
 *   <li>{@code legacyCompatibilityMode} - keep SaberFactions behaviours that are only safe on
 *       a single main thread (defaults on when not running Folia).</li>
 *   <li>{@code foliaStrictMode} - refuse to run integrations and code paths that are not yet
 *       proven Folia-safe; disable them automatically rather than risk corruption (defaults on
 *       when running Folia).</li>
 *   <li>{@code validationDiagnostics} - collect lightweight runtime counters for Folia staging
 *       validation (defaults off; enable only on test servers).</li>
 * </ul>
 *
 * <p>The defaults intentionally keep Paper/Purpur behaviour unchanged while making Folia fail
 * safe instead of silently corrupting state. Both flags can be overridden in {@code config.yml}
 * under the {@code realfactions} section.
 */
public final class RealFactionsFlags {

    private final boolean folia;
    private final boolean legacyCompatibilityMode;
    private final boolean foliaStrictMode;
    private final boolean validationDiagnostics;

    private RealFactionsFlags(boolean folia, boolean legacyCompatibilityMode, boolean foliaStrictMode,
                                boolean validationDiagnostics) {
        this.folia = folia;
        this.legacyCompatibilityMode = legacyCompatibilityMode;
        this.foliaStrictMode = foliaStrictMode;
        this.validationDiagnostics = validationDiagnostics;
    }

    public static RealFactionsFlags from(FileConfiguration config, boolean folia) {
        boolean legacy = config.getBoolean("realfactions.legacy-compatibility-mode", !folia);
        boolean strict = config.getBoolean("realfactions.folia-strict-mode", folia);
        boolean diagnostics = config.getBoolean("realfactions.validation-diagnostics", false);
        return new RealFactionsFlags(folia, legacy, strict, diagnostics);
    }

    public boolean isFolia() {
        return folia;
    }

    public boolean legacyCompatibilityMode() {
        return legacyCompatibilityMode;
    }

    public boolean foliaStrictMode() {
        return foliaStrictMode;
    }

    /**
     * @return true when integrations that are known to be unsafe off the owning region thread
     * (for example WorldGuard chunk inspection during a claim) should be disabled automatically.
     */
    public boolean disableUnsafeIntegrations() {
        return foliaStrictMode;
    }

    /**
     * @return true when lightweight runtime validation counters should be collected (staging/test only).
     */
    public boolean validationDiagnostics() {
        return validationDiagnostics;
    }
}
