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

    public static final int MAX_PLAYER_NAME_LENGTH = 16;
    public static final int MAX_TRACK_TEXT_LENGTH = 160;

    public static final CustomPacketPayload.Type<SpotiMCSongPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("spotimc", "song_update"));

    public static final StreamCodec<FriendlyByteBuf, SpotiMCSongPayload> CODEC = CustomPacketPayload.codec(
            (payload, buf) -> {
                buf.writeUUID(payload.playerUuid());
                buf.writeUtf(payload.playerName() != null ? payload.playerName() : "", MAX_PLAYER_NAME_LENGTH);
                buf.writeUtf(payload.trackName() != null ? payload.trackName() : "", MAX_TRACK_TEXT_LENGTH);
                buf.writeUtf(payload.artistName() != null ? payload.artistName() : "", MAX_TRACK_TEXT_LENGTH);
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

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
