package com.massivecraft.factions.realfactions.snapshot;

import com.massivecraft.factions.Faction;

/**
 * Immutable snapshot of the display/read fields of a {@link Faction}.
 *
 * <p>Build instances with {@link #of(Faction)} on the model thread.
 */
public record FactionSnapshot(
        String id,
        String tag,
        boolean normal,
        int landCount,
        int onlineCount
) {

    public static FactionSnapshot of(Faction faction) {
        return new FactionSnapshot(
                faction.getId(),
                faction.getTag(),
                faction.isNormal(),
                faction.getLandRounded(),
                faction.getFPlayersWhereOnline(true).size()
        );
    }
}
