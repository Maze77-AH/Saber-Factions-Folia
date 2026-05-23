package com.massivecraft.factions.util;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.zcore.util.TagUtil;
import com.massivecraft.factions.zcore.util.TextUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

public class TitleUtil {

    // One-shot warning latch so a runtime without a working native title API does not spam
    // the console on every claim/unclaim. Resets only on plugin reload.
    private static final AtomicBoolean unsupportedWarned = new AtomicBoolean(false);

    public static void sendFactionChangeTitle(FPlayer me, Faction faction) {
        if (me == null) return;
        int version = FactionsPlugin.getInstance().version;
        if (version != 7) {
            FileConfiguration config = FactionsPlugin.getInstance().getConfig();

            String title = parseAllPlaceholders(TextUtil.replace(config.getString("Title.Format.Title"), "{Faction}", faction.getColorTo(me) + faction.getTag()), faction, me.getPlayer());
            String subTitle = parseAllPlaceholders(TextUtil.replace(config.getString("Title.Format.Subtitle"), "{Description}", faction.getDescription()).replace("{Faction}", faction.getColorTo(me) + faction.getTag()), faction, me.getPlayer());
            int fadeIn = config.getInt("Title.Options.FadeInTime");
            int stay = config.getInt("Title.Options.ShowTime");
            int fadeOut = config.getInt("Title.Options.FadeOutTime");
            boolean withFadeTimes = version != 8;

            Player titlePlayer = me.getPlayer();
            if (titlePlayer != null) {
                // Title display + metadata are Bukkit entity operations; run them on the player's
                // own entity scheduler (region-safe on Folia).
                FactionsPlugin.getInstance().getFactionScheduler().runForEntityLater(titlePlayer, () -> {
                    sendTitleSafely(titlePlayer, TextUtil.parse(title), TextUtil.parse(subTitle), fadeIn, stay, fadeOut, withFadeTimes);
                    titlePlayer.removeMetadata("showFactionTitle", FactionsPlugin.getInstance());
                }, 5);
            }
        }
    }

    /**
     * Sends the resolved title via Bukkit's native {@code Player.sendTitle} API (available
     * since 1.11) instead of shaded XSeries {@code Titles}, whose {@code <clinit>} fails on
     * MC 26 / Folia with {@code Failed to parse server version}. If the native call also
     * fails on an exotic runtime, log once and silently no-op on subsequent calls so the
     * claim/unclaim path does not spam exceptions every region change.
     */
    @SuppressWarnings("deprecation")
    private static void sendTitleSafely(Player player, String title, String subTitle, int fadeIn, int stay, int fadeOut, boolean withFadeTimes) {
        try {
            if (withFadeTimes) {
                player.sendTitle(title, subTitle, fadeIn, stay, fadeOut);
            } else {
                player.sendTitle(title, subTitle);
            }
        } catch (Throwable t) {
            if (unsupportedWarned.compareAndSet(false, true)) {
                Logger.print(
                        "Native title API unavailable on this runtime; faction change titles will be skipped for the rest of this session. Root cause: "
                                + t.getClass().getSimpleName() + ": " + t.getMessage(),
                        Logger.PrefixType.WARNING);
            }
        }
    }


    public static String parseAllPlaceholders(String string, Faction faction, Player player) {
        string = TagUtil.parsePlaceholders(player, string);

        string = TextUtil.replace(TextUtil.replace(TextUtil.replace(TextUtil.replace(TextUtil.replace(string,
                                                "{faction}", faction.getTag()),
                                        "{online}", Integer.toString(faction.getOnlinePlayers().size())),
                                "{offline}", Integer.toString(faction.getFPlayers().size() - faction.getOnlinePlayers().size())),
                        "{chunks}", Integer.toString(faction.getAllClaims().size())),
                "{power}", Double.toString(faction.getPower()));
        return string;
    }
}
