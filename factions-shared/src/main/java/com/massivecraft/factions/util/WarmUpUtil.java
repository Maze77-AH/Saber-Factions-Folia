package com.massivecraft.factions.util;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.scheduler.ScheduledTaskHandle;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.entity.Player;

public class WarmUpUtil {

    /**
     * @param player         The player to notify.
     * @param translationKey The translation key used for notifying.
     * @param action         The action, inserted into the notification message.
     * @param runnable       The task to run after the delay. If the delay is 0, the task is instantly ran.
     * @param delay          The time used, in seconds, for the delay.
     *                       <p>
     *                       note: for translations: %s = action, %d = delay
     */
    public static void process(final FPlayer player, Warmup warmup, TL translationKey, String action, final Runnable runnable, long delay) {
        Player bukkitPlayer = player.getPlayer();
        if (bukkitPlayer == null || !bukkitPlayer.isOnline()) {
            return;
        }

        FactionsPlugin.getInstance().getFactionScheduler().runForEntity(bukkitPlayer, () -> processOnPlayerScheduler(player, bukkitPlayer, warmup, translationKey, action, runnable, delay));
    }

    private static void processOnPlayerScheduler(final FPlayer player, Player bukkitPlayer, Warmup warmup, TL translationKey, String action, final Runnable runnable, long delay) {
        if (delay > 0) {
            if (player.isWarmingUp()) {
                player.msg(TL.WARMUPS_ALREADY);
                return;
            }

            player.msg(translationKey.format(action, delay));
            ScheduledTaskHandle handle = FactionsPlugin.getInstance().getFactionScheduler().runForEntityLater(bukkitPlayer, () -> {
                player.stopWarmup();
                runnable.run();
            }, delay * 20);
            player.addWarmup(warmup, handle);
        } else {
            player.stopWarmup();
            runnable.run();
        }
    }

    public enum Warmup {
        HOME, WARP, FLIGHT, BANNER, CHECKPOINT, WILD
    }

}
