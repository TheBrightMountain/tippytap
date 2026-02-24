package io.github.thebrightmountain.tippytap.mixin.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Player.class)
public interface PlayerInvoker {
    @Invoker("canCriticalAttack")
    boolean invokeCanCriticalAttack(Entity entity);
}