package com.massivecraft.factions.realfactions.snapshot;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.struct.ChatMode;

import java.util.UUID;

/**
 * Immutable snapshot of the chat-display fields of an {@link FPlayer}.
 *
 * <p>{@code AsyncPlayerChatEvent} is delivered off the model thread on both Paper and Folia.
 * Reading the live faction/player model from that thread is an unsynchronized read of shared
 * state. {@link com.massivecraft.factions.realfactions.ChatDisplayCache} keeps one of these
 * per online player, refreshed on the model thread, so the chat handler can format messages
 * from immutable cached values instead.
 */
public record ChatPlayerSnapshot(
        UUID uuid,
        String chatTag,
        String nameAndTag,
        String title,
        String factionId,
        ChatMode chatMode
) {

    public static ChatPlayerSnapshot of(FPlayer fplayer) {
        UUID uuid;
        try {
            uuid = UUID.fromString(fplayer.getId());
        } catch (IllegalArgumentException ex) {
            uuid = null;
        }
        return new ChatPlayerSnapshot(
                uuid,
                fplayer.getChatTag(),
                fplayer.getNameAndTag(),
                fplayer.getTitle(),
                fplayer.getFactionId(),
                fplayer.getChatMode()
        );
    }
}
