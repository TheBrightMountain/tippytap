package io.github.thebrightmountain.tippytap.mixin.client;

import io.github.thebrightmountain.tippytap.client.HudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceBarRenderer.class)
public class ExperienceBarRendererMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void tippytap$cancelXpBg(GuiGraphics gfx, DeltaTracker delta, CallbackInfo ci) {
        HudRenderer hud = HudRenderer.INSTANCE;
        if (hud != null && hud.getConfig().showXpBar) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void tippytap$cancelXpBar(GuiGraphics gfx, DeltaTracker delta, CallbackInfo ci) {
        HudRenderer hud = HudRenderer.INSTANCE;
        if (hud != null && hud.getConfig().showXpBar) {
            ci.cancel();
        }
    }
}