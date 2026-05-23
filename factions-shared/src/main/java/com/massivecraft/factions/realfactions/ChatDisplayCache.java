package com.massivecraft.factions.realfactions;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.realfactions.snapshot.ChatPlayerSnapshot;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe cache of per-player chat display data.
 *
 * <p>{@code AsyncPlayerChatEvent} runs off the model thread. Instead of traversing the live
 * faction/player model from that thread, the chat handler reads immutable
 * {@link ChatPlayerSnapshot}s from this cache. The cache is refreshed on the model thread by
 * {@link #refreshOnline()} (driven by a periodic global-scheduler task) so the snapshots stay
 * current within a small window without ever exposing the live model off-thread.
 */
public final class ChatDisplayCache {

    private final ConcurrentMap<UUID, ChatPlayerSnapshot> byPlayer = new ConcurrentHashMap<>();

    /**
     * Rebuild the cache from the currently online players. MUST be called on the model thread
     * (it reads the live faction/player model).
     */
    public void refreshOnline() {
        for (FPlayer fplayer : FPlayers.getInstance().getOnlinePlayers()) {
            ChatPlayerSnapshot snapshot = ChatPlayerSnapshot.of(fplayer);
            if (snapshot.uuid() != null) {
                byPlayer.put(snapshot.uuid(), snapshot);
            }
        }
    }

    /**
     * Update a single player's snapshot. MUST be called on the model thread.
     */
    public void update(FPlayer fplayer) {
        ChatPlayerSnapshot snapshot = ChatPlayerSnapshot.of(fplayer);
        if (snapshot.uuid() != null) {
            byPlayer.put(snapshot.uuid(), snapshot);
        }
    }

    /**
     * @return the cached snapshot for the player, or {@code null} if not cached yet. Safe to
     * call from any thread.
     */
    public ChatPlayerSnapshot get(UUID uuid) {
        return byPlayer.get(uuid);
    }

    public void remove(UUID uuid) {
        byPlayer.remove(uuid);
    }

    public void clear() {
        byPlayer.clear();
    }
}
