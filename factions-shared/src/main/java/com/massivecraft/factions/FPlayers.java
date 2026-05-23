package com.massivecraft.factions;

import com.massivecraft.factions.zcore.persist.json.JSONFPlayers;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

public abstract class FPlayers {
    protected static FPlayers instance = getFPlayersImpl();

    public static FPlayers getInstance() {
        return instance;
    }

    private static FPlayers getFPlayersImpl() {
        return new JSONFPlayers();
    }

    public abstract void clean();

    public abstract Set<FPlayer> getOnlinePlayers();

    public abstract FPlayer getByPlayer(Player player);

    public abstract Collection<FPlayer> getAllFPlayers();

    public abstract void forceSave();

    public abstract void forceSave(boolean sync);

    /**
     * Serialize all saveable players to their on-disk JSON form. MUST be called on the model
     * thread; the returned string is an immutable snapshot safe to write from any thread.
     */
    public abstract String serializeToJson();

    /**
     * Write a previously produced JSON snapshot to disk. Safe to call off the model thread.
     */
    public abstract void writeJson(String json, boolean sync);

    public abstract FPlayer getByOfflinePlayer(OfflinePlayer player);

    public abstract FPlayer getById(String string);

    public abstract void load(Consumer<Boolean> finish);
}
