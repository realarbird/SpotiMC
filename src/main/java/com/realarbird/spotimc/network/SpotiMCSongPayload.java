package com.realarbird.spotimc.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

/**
 * Custom packet payload used to sync currently playing song status between players.
 */
public record SpotiMCSongPayload(
        UUID playerUuid,
        String playerName,
        String trackName,
        String artistName,
        boolean isPlaying
) implements CustomPacketPayload {

    public static final int MAX_PLAYER_NAME_LENGTH = 64;
    public static final int MAX_TRACK_TEXT_LENGTH = 256;

    public static final CustomPacketPayload.Type<SpotiMCSongPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("spotimc", "song_update"));

    public static final StreamCodec<FriendlyByteBuf, SpotiMCSongPayload> CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeUUID(payload.playerUuid());
                buf.writeUtf(sanitize(payload.playerName(), MAX_PLAYER_NAME_LENGTH), MAX_PLAYER_NAME_LENGTH);
                buf.writeUtf(sanitize(payload.trackName(), MAX_TRACK_TEXT_LENGTH), MAX_TRACK_TEXT_LENGTH);
                buf.writeUtf(sanitize(payload.artistName(), MAX_TRACK_TEXT_LENGTH), MAX_TRACK_TEXT_LENGTH);
                buf.writeBoolean(payload.isPlaying());
            },
            buf -> new SpotiMCSongPayload(
                    buf.readUUID(),
                    buf.readUtf(MAX_PLAYER_NAME_LENGTH),
                    buf.readUtf(MAX_TRACK_TEXT_LENGTH),
                    buf.readUtf(MAX_TRACK_TEXT_LENGTH),
                    buf.readBoolean()
            )
    );

    private static String sanitize(String str, int maxLen) {
        if (str == null) return "";
        return str.length() <= maxLen ? str : str.substring(0, maxLen);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

