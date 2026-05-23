package com.massivecraft.factions.util;

import com.massivecraft.factions.*;
import com.massivecraft.factions.struct.Role;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.ListIterator;

public class AutoLeaveProcessTask extends BukkitRunnable {

    private transient boolean readyToGo;
    private transient boolean finished;
    private transient ListIterator<FPlayer> iterator;
    private transient double toleranceMillis;

    public AutoLeaveProcessTask() {
        ArrayList<FPlayer> fplayers = (ArrayList<FPlayer>) FPlayers.getInstance().getAllFPlayers();
        this.iterator = fplayers.listIterator();
        this.toleranceMillis = Conf.autoLeaveAfterDaysOfInactivity * 24 * 60 * 60 * 1000;
        this.readyToGo = true;
        this.finished = false;
    }

    public void run() {
        if (Conf.autoLeaveAfterDaysOfInactivity <= 0.0 || Conf.autoLeaveRoutineMaxMillisecondsPerTick <= 0.0) {
            this.stop();
            return;
        }

        if (!readyToGo) {
            return;
        }
        // this is set so it only does one iteration at a time, no matter how frequently the timer fires
        readyToGo = false;
        // and this is tracked to keep one iteration from dragging on too long and possibly choking the system if there are a very large number of players to go through
        long loopStartTime = System.currentTimeMillis();

        while (iterator.hasNext()) {
            long now = System.currentTimeMillis();

            // if this iteration has been running for maximum time, stop to take a breather until next tick
            if (now > loopStartTime + Conf.autoLeaveRoutineMaxMillisecondsPerTick) {
                readyToGo = true;
                return;
            }

            FPlayer fplayer = iterator.next();

            // Check if they should be exempt from this.
            if (!fplayer.willAutoLeave()) {
                FactionsPlugin.getInstance().getFactionScheduler().runGlobal(() -> Logger.print(fplayer.getName() + " was going to be auto-removed but was set not to.", Logger.PrefixType.DEFAULT));
                continue;
            }
            if (fplayer.hasFaction() && fplayer.isOffline() && now - fplayer.getLastLoginTime() > toleranceMillis) {
                if (Conf.logFactionLeave || Conf.logFactionKick) {
                    FactionsPlugin.getInstance().getFactionScheduler().runGlobal(() -> Logger.print("Player " + fplayer.getName() + " was auto-removed due to inactivity.", Logger.PrefixType.DEFAULT));
                }

                // Leadership reassignment, leave, and removal are model writes; route them through
                // the single-writer model thread. The local-list cleanup stays on the task thread.
                final FPlayer toRemove = fplayer;
                FactionsPlugin.getInstance().getRealFactionsServices().executor().runFactionWrite(() -> {
                    if (toRemove.getRole() == Role.LEADER) {
                        Faction faction = toRemove.getFaction();
                        if (faction != null) {
                            toRemove.getFaction().promoteNewLeader(true);
                        }
                    }
                    toRemove.leave(false);
                    if (Conf.autoLeaveDeleteFPlayerData) {
                        toRemove.remove();
                    }
                });
                iterator.remove();  // go ahead and remove this list's link to the FPlayer object
            }
        }

        // looks like we've finished
        this.stop();
    }

    // we're done, shut down
    public void stop() {
        readyToGo = false;
        finished = true;

        this.cancel();
    }

    public boolean isFinished() {
        return finished;
    }
}
