package com.massivecraft.factions.scheduler;

import org.bukkit.plugin.Plugin;

public final class FactionSchedulers {

    private FactionSchedulers() {
    }

    public static FactionScheduler create(Plugin plugin) {
        if (isFolia()) {
            return new FoliaScheduler(plugin);
        }

        return new PaperScheduler(plugin);
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
