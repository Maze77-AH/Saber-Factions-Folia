package com.massivecraft.factions.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.lang.reflect.Method;

public final class TeleportUtil {

    private TeleportUtil() {
    }

    public static void teleport(Player player, Location location) {
        teleport(player, location, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public static void teleport(Player player, Location location, PlayerTeleportEvent.TeleportCause cause) {
        if (player == null || location == null || !player.isOnline()) {
            return;
        }

        if (tryTeleportAsync(player, location, cause)) {
            return;
        }

        player.teleport(location, cause);
    }

    private static boolean tryTeleportAsync(Player player, Location location, PlayerTeleportEvent.TeleportCause cause) {
        if (invokeTeleportAsync(player, location, cause, true)) {
            return true;
        }

        return invokeTeleportAsync(player, location, cause, false);
    }

    private static boolean invokeTeleportAsync(Player player, Location location, PlayerTeleportEvent.TeleportCause cause, boolean includeCause) {
        try {
            Method method;
            Object result;
            if (includeCause) {
                method = player.getClass().getMethod("teleportAsync", Location.class, PlayerTeleportEvent.TeleportCause.class);
                result = method.invoke(player, location, cause);
            } else {
                method = player.getClass().getMethod("teleportAsync", Location.class);
                result = method.invoke(player, location);
            }
            return result != null;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
