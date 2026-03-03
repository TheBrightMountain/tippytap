package io.github.thebrightmountain.tippytap.mixin.client;

import io.github.thebrightmountain.tippytap.client.HudRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleDamageEvent", at = @At("HEAD"))
    private void onHandleDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        if (HudRenderer.INSTANCE == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        DamageSource source = packet.getSource(client.level);

        if (packet.entityId() == client.player.getId()) {
            // Local player received damage
            Entity cause = source.getEntity();
            if (cause != null) {
                HudRenderer.INSTANCE.onHitReceived(client.player.distanceTo(cause), null);
            } else {
                HudRenderer.INSTANCE.onHitReceived(-1f, source.getMsgId());
            }
        } else {
            // Some other entity was damaged — check if the local player caused it indirectly
            // (ranged: arrow/trident/etc. — direct entity is the projectile, not the player)
            Entity attacker = source.getEntity();
            Entity direct = source.getDirectEntity();
            if (attacker == client.player && direct != client.player) {
                Entity hit = client.level.getEntity(packet.entityId());
                if (hit instanceof LivingEntity living) {
                    HudRenderer.INSTANCE.onRangeHit((float) client.player.distanceTo(hit), living);
                }
            }
        }
    }

    @Inject(method = "handleSetHealth", at = @At("HEAD"))
    private void onHandleSetHealth(ClientboundSetHealthPacket packet, CallbackInfo ci) {
        if (HudRenderer.INSTANCE == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        float delta = client.player.getHealth() - packet.getHealth();
        if (delta > 0.001f) {
            HudRenderer.INSTANCE.onDmgTaken(delta);
        } else if (delta < -0.001f) {
            HudRenderer.INSTANCE.onHealReceived(-delta);
        }
    }
}