package io.github.thebrightmountain.tippytap.mixin.client;

import io.github.thebrightmountain.tippytap.client.HudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.LocatorBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocatorBarRenderer.class)
public class LocatorBarRendererMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void tippytap$cancelLocatorBg(GuiGraphics gfx, DeltaTracker delta, CallbackInfo ci) {
        HudRenderer hud = HudRenderer.INSTANCE;
        if (hud != null && hud.getConfig().showLocatorBar) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void tippytap$cancelLocatorBar(GuiGraphics gfx, DeltaTracker delta, CallbackInfo ci) {
        HudRenderer hud = HudRenderer.INSTANCE;
        if (hud != null && hud.getConfig().showLocatorBar) {
            ci.cancel();
        }
    }
}