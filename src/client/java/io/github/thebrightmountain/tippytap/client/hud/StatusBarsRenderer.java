package io.github.thebrightmountain.tippytap.client.hud;

import io.github.thebrightmountain.tippytap.client.TippytapConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;

public final class StatusBarsRenderer {
    private StatusBarsRenderer() {}

    /** Renders the gradient health bar above the hotbar.
     *  @param dmgFlashT 0.0 = damage just taken (full red flash), 1.0 = no flash */
    public static void renderHealthBar(GuiGraphics gfx, Minecraft client,
                                        TippytapConfig config, boolean dimmed, float dmgFlashT) {
        if (!config.showHealthBar) return;

        int screenWidth  = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int slotSize     = config.hotbarSlotSize;
        int slotGap      = config.hotbarGap;
        int hotbarWidth  = 9 * slotSize + 8 * slotGap;
        int hotbarLeft   = (screenWidth - hotbarWidth) / 2;

        int barHeight = 6, padX = 4, padV = 3;
        int rowHeight = barHeight + padV * 2;
        int rowGap    = 1;
        int hotbarTop = screenHeight - config.hotbarOffsetY - slotSize - HudConst.BG_PAD;
        int rowTop    = hotbarTop - rowGap - rowHeight;

        gfx.fill(hotbarLeft, rowTop, hotbarLeft + hotbarWidth, rowTop + rowHeight, HudConst.C_PANEL_BG);

        float health    = client.player.getHealth();
        float maxHealth = client.player.getMaxHealth();
        float fill      = maxHealth > 0 ? health / maxHealth : 0f;
        int   color     = fill > 0.6f ? HudConst.C_HEALTH_HI
                        : fill > 0.3f ? HudConst.C_HEALTH_MID
                        :               HudConst.C_HEALTH_LO;

        DrawUtils.drawFillBar(gfx, hotbarLeft + padX, rowTop + padV,
                              hotbarWidth - padX * 2, barHeight, fill, color);
        DrawUtils.drawCenteredLabel(gfx, client, hotbarLeft, rowTop, hotbarWidth, rowHeight,
                                    String.format("%.1f / %.0f", health, maxHealth),
                                    HudConst.C_TEXT_WHITE);

        if (dmgFlashT < 1f) {
            int flashAlpha = (int)(0xCC * (1f - dmgFlashT) * (1f - dmgFlashT));
            gfx.fill(hotbarLeft, rowTop, hotbarLeft + hotbarWidth, rowTop + rowHeight,
                     (flashAlpha << 24) | 0xFF4444);
        }

        if (dimmed) gfx.fill(hotbarLeft, rowTop, hotbarLeft + hotbarWidth, rowTop + rowHeight, HudConst.C_PANEL_DIM);
    }

    /** Renders the waypoint locator bar above the health bar. */
    public static void renderLocatorBar(GuiGraphics gfx, Minecraft client,
                                         TippytapConfig config, DeltaTracker delta, boolean dimmed) {
        if (!config.showLocatorBar) return;
        if (client.player == null || client.level == null) return;
        var waypointManager = client.player.connection.getWaypointManager();
        if (!waypointManager.hasWaypoints()) return;

        int screenWidth  = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int slotSize     = config.hotbarSlotSize;
        int slotGap      = config.hotbarGap;
        int hotbarWidth  = 9 * slotSize + 8 * slotGap;
        int hotbarLeft   = (screenWidth - hotbarWidth) / 2;

        int barHeight      = 6, padV = 3;
        int rowHeight      = barHeight + padV * 2;
        int rowGap         = 1;
        int hotbarTop      = screenHeight - config.hotbarOffsetY - slotSize - HudConst.BG_PAD;
        int healthRowTop   = hotbarTop - rowGap - rowHeight;
        int locatorRowH    = HudConst.LOCATOR_H + padV * 2;
        int locatorRowTop  = healthRowTop - rowGap - locatorRowH;
        int tickTop        = locatorRowTop + padV;

        gfx.fill(hotbarLeft, locatorRowTop,
                 hotbarLeft + hotbarWidth, locatorRowTop + locatorRowH, HudConst.C_PANEL_BG);

        // Centre reference tick
        int midX = hotbarLeft + hotbarWidth / 2;
        gfx.fill(midX, tickTop, midX + 1, tickTop + HudConst.LOCATOR_H, HudConst.C_LOCATOR_TICK);

        PartialTickSupplier pts    = entity -> delta.getGameTimeDeltaPartialTick(true);
        var                  camera = client.gameRenderer.getMainCamera();
        var                  proj   = client.gameRenderer;

        waypointManager.forEachWaypoint(client.player, (TrackedWaypoint wp) -> {
            double yawOffset = wp.yawAngleToCamera(client.level, camera, pts);
            if (yawOffset < -HudConst.LOCATOR_FOV || yawOffset > HudConst.LOCATOR_FOV) return;

            int dotX = Math.max(hotbarLeft, Math.min(hotbarLeft + hotbarWidth - 3,
                    hotbarLeft + (int)((yawOffset + HudConst.LOCATOR_FOV)
                                       / (HudConst.LOCATOR_FOV * 2) * hotbarWidth)));

            var wpId  = wp.id();
            int hash  = wpId.left().map(java.util.UUID::hashCode)
                            .orElseGet(() -> wpId.right().map(String::hashCode).orElse(0));
            int color = (wp.icon().color.orElse(0xFF000000 | (Math.abs(hash) % 0xFFFFFF + 1))
                          & 0x00FFFFFF) | 0xFF000000;

            gfx.fill(dotX, tickTop, dotX + 3, tickTop + HudConst.LOCATOR_H, color);

            switch (wp.pitchDirectionToCamera(client.level, proj, pts)) {
                case UP   -> gfx.fill(dotX + 1, tickTop - 2,
                                      dotX + 2, tickTop,                           color);
                case DOWN -> gfx.fill(dotX + 1, tickTop + HudConst.LOCATOR_H,
                                      dotX + 2, tickTop + HudConst.LOCATOR_H + 2, color);
                default   -> {}
            }
        });

        if (dimmed) gfx.fill(hotbarLeft, locatorRowTop,
                             hotbarLeft + hotbarWidth, locatorRowTop + locatorRowH, HudConst.C_PANEL_DIM);
    }
}
