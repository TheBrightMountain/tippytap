package io.github.thebrightmountain.tippytap.mixin.client;

import io.github.thebrightmountain.tippytap.client.HudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "renderItemHotbar", at = @At("HEAD"), cancellable = true, require = 0)
    private void tippytap$cancelHotbar(GuiGraphics gfx, DeltaTracker delta, CallbackInfo ci) {
        HudRenderer hud = HudRenderer.INSTANCE;
        if (hud != null && hud.getConfig().showCustomHotbar) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true, require = 0)
    private void tippytap$cancelHealthHearts(GuiGraphics gfx, CallbackInfo ci) {
        HudRenderer hud = HudRenderer.INSTANCE;
        if (hud != null && hud.getConfig().showHealthBar) {
            ci.cancel();
        }
    }

    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true, require = 0)
    private void tippytap$cancelFoodBar(GuiGraphics gfx, Player player, int x, int y, CallbackInfo ci) {
        HudRenderer hud = HudRenderer.INSTANCE;
        if (hud != null && hud.getConfig().showFoodBar) {
            ci.cancel();
        }
    }
}