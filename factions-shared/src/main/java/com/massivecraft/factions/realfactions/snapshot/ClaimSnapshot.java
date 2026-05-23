package com.massivecraft.factions.realfactions.snapshot;

import com.massivecraft.factions.FLocation;

/**
 * Immutable snapshot of a single board claim (one chunk owned by one faction).
 *
 * <p>The board itself is a large mutable map; a {@code ClaimSnapshot} captures a single
 * coordinate-to-faction mapping in immutable form so it can be passed across threads without
 * exposing the live board.
 */
public record ClaimSnapshot(
        String worldName,
        int x,
        int z,
        String factionId
) {

    public static ClaimSnapshot of(FLocation location, String factionId) {
        return new ClaimSnapshot(location.getWorldName(), location.getIntX(), location.getIntZ(), factionId);
    }
}
