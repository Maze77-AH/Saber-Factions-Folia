package com.massivecraft.factions;

import com.massivecraft.factions.zcore.persist.json.JSONFactions;

import java.util.ArrayList;
import java.util.Set;
import java.util.function.Consumer;

public abstract class Factions {
    protected static Factions instance = getFactionsImpl();

    public static Factions getInstance() {
        return instance;
    }

    private static Factions getFactionsImpl() {
        return new JSONFactions();
    }

    public abstract Faction getFactionById(String id);

    public abstract Faction getByTag(String str);

    public abstract Faction getBestTagMatch(String start);

    public abstract boolean isTagTaken(String str);

    public abstract boolean isValidFactionId(String id);

    public abstract Faction createFaction();

    public abstract void removeFaction(String id);

    public abstract Set<String> getFactionTags();

    public abstract ArrayList<Faction> getAllFactions();

    public abstract ArrayList<Faction> getAllNormalFactions();

    @Deprecated
    public abstract Faction getNone();

    public abstract Faction getWilderness();

    public abstract Faction getSafeZone();

    public abstract Faction getWarZone();

    public abstract void forceSave();

    public abstract void forceSave(boolean sync);

    /**
     * Serialize all factions to their on-disk JSON form. MUST be called on the model thread;
     * the returned string is an immutable snapshot safe to write from any thread.
     */
    public abstract String serializeToJson();

    /**
     * Write a previously produced JSON snapshot to disk. Safe to call off the model thread.
     */
    public abstract void writeJson(String json, boolean sync);

    public abstract void load(Consumer<Boolean> success);
}
