package com.realarbird.spotimc.mixin;

import com.realarbird.spotimc.social.ClientSongTracker;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(
            method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void spotimc$renderPlayerSongOverhead(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (entity instanceof Player player) {
            ClientSongTracker.PlayerSongInfo songInfo = ClientSongTracker.getSong(player.getUUID());
            if (songInfo != null && songInfo.isPlaying() && songInfo.trackName() != null && !songInfo.trackName().isEmpty()) {
                Component songText = Component.literal("\n🎵 " + songInfo.trackName() + " - " + songInfo.artistName()).withColor(0x1DB954);
                if (state.nameTag == null) {
                    state.nameTag = Component.empty().append(player.getDisplayName()).append(songText);
                } else {
                    state.nameTag = Component.empty().append(state.nameTag).append(songText);
                }
            }
        }
    }
}
