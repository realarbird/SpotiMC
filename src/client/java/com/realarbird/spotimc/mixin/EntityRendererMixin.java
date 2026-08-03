package com.realarbird.spotimc.mixin;

import com.realarbird.spotimc.SpotiMCConfig;
import com.realarbird.spotimc.social.ClientSongTracker;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(
            method = "extractNameTags(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void spotimc$renderPlayerSongOverhead(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        try {
            SpotiMCConfig config = SpotiMCConfig.getInstance();
            if (config == null || !config.showOthersListeningStats) {
                return;
            }

            if (entity instanceof Player player) {
                ClientSongTracker.PlayerSongInfo songInfo = ClientSongTracker.getSong(player.getUUID());
                if (songInfo != null && songInfo.isPlaying()
                        && songInfo.trackName() != null && !songInfo.trackName().isEmpty()
                        && state.nameTag != null && state.nameTagAttachment != null) {
                    Component playerName = state.nameTag;
                    state.scoreText = state.scoreText == null
                            ? playerName
                            : Component.empty().append(state.scoreText).append(" ").append(playerName);
                    state.nameTag = Component.literal(formatSong(songInfo)).withColor(0x1DB954);
                }
            }
        } catch (Exception e) {
            // Silently ignore mixin errors to avoid crashing the renderer
        }
    }

    private static String formatSong(ClientSongTracker.PlayerSongInfo songInfo) {
        String track = abbreviate(songInfo.trackName(), 38);
        String artist = abbreviate(songInfo.artistName(), 26);
        return artist.isEmpty() ? "♫ " + track : "♫ " + track + " — " + artist;
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.isEmpty()) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }
}

