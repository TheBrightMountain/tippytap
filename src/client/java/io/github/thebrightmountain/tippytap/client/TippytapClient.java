package io.github.thebrightmountain.tippytap.client;

import io.github.thebrightmountain.tippytap.mixin.client.PlayerInvoker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

public class TippytapClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TippytapConfig config = TippytapConfig.load();
        HudRenderer hud = new HudRenderer(config);
        HudRenderer.INSTANCE = hud;

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("tippytap", "hud"),
                hud
        );

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide()) {
                boolean isCrit = ((PlayerInvoker) player).invokeCanCriticalAttack(entity);
                hud.onMeleHit(player.distanceTo(entity), isCrit);
                if (entity instanceof LivingEntity living) {
                    hud.recordMeleTarget(living);
                }
            }
            return InteractionResult.PASS;
        });
    }
}

