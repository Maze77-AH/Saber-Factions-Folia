package com.massivecraft.factions.util.flight;

import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.listeners.FactionsEntityListener;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * SaberFactions - Developed by Driftay.
 * All rights reserved 2020.
 * Creation Date: 9/15/2020
 */
public class FlightEnhance implements Runnable {

    @Override
    public void run() {
        // Dispatch each player's flight check to that player's own region/entity scheduler. The
        // body reads the player's location, scans nearby entities, and toggles flight - all Bukkit
        // entity operations that must run on the owning region thread under Folia. The outer loop
        // only enumerates online players.
        for (FPlayer player : FPlayers.getInstance().getOnlinePlayers()) {
            Player p = player.getPlayer();
            if (p == null) {
                continue;
            }
            FactionsPlugin.getInstance().getFactionScheduler().runForEntity(p, () -> checkPlayer(player));
        }
    }

    private void checkPlayer(FPlayer player) {
        if (shouldSkipPlayer(player)) {
            return;
        }

        FLocation fLocation = FLocation.wrap(player.getPlayer().getLocation());
        player.checkIfNearbyEnemies();

        if (!player.hasEnemiesNearby()) {
            handleFlightStatusForPlayer(player, fLocation);
        }
    }

    private boolean shouldSkipPlayer(FPlayer player) {
        Player p = player.getPlayer();

        return player.isAdminBypassing()
                || p == null
                || p.isOp()
                || p.getGameMode() == GameMode.CREATIVE
                || p.getGameMode() == GameMode.SPECTATOR;
    }

    private void handleFlightStatusForPlayer(FPlayer player, FLocation fLocation) {
        if (player.isFlying() && !player.canFlyAtLocation(fLocation)) {
            player.setFlying(false, false);
            return;
        }

        if (!player.isFlying()
                && player.canFlyAtLocation()
                && FactionsPlugin.getInstance().getConfig().getBoolean("ffly.AutoEnable")
                && !FactionsEntityListener.combatList.contains(player.getPlayer().getUniqueId())) {
            player.setFlying(true);
        }
    }
}