package com.massivecraft.factions.realfactions.snapshot;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.struct.Role;

/**
 * Immutable snapshot of the display/read fields of an {@link FPlayer}.
 *
 * <p>Build instances with {@link #of(FPlayer)} on the model thread. Once created the snapshot
 * is safe to read from any thread because it holds only immutable values copied out of the
 * live model.
 */
public record FPlayerSnapshot(
        String id,
        String name,
        String factionId,
        String title,
        Role role,
        double power
) {

    public static FPlayerSnapshot of(FPlayer fplayer) {
        return new FPlayerSnapshot(
                fplayer.getId(),
                fplayer.getName(),
                fplayer.getFactionId(),
                fplayer.getTitle(),
                fplayer.getRole(),
                fplayer.getPower()
        );
    }
}
