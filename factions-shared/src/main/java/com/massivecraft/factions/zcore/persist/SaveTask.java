package com.massivecraft.factions.zcore.persist;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.FactionsPlugin;
import com.massivecraft.factions.realfactions.RealFactionsServices;
import com.massivecraft.factions.zcore.MPlugin;

public class SaveTask implements Runnable {

    private static volatile boolean running = false;

    MPlugin p;

    public SaveTask(MPlugin p) {
        this.p = p;
    }

    public void run() {
        if (!p.getAutoSave() || running) return;
        running = true;
        p.preAutoSave();

        RealFactionsServices services = FactionsPlugin.getInstance().getRealFactionsServices();
        if (services != null) {
            // Folia-safe autosave: serialize immutable snapshots on the model thread, write the
            // snapshot strings on the async scheduler, and release the guard once writing finishes.
            services.persistence().saveAllAsync(() -> running = false);
            p.postAutoSave();
        } else {
            Factions.getInstance().forceSave(false);
            FPlayers.getInstance().forceSave(false);
            Board.getInstance().forceSave(false);
            p.postAutoSave();
            running = false;
        }
    }
}
