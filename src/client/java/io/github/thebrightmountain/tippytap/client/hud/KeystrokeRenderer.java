package io.github.thebrightmountain.tippytap.client.hud;

import io.github.thebrightmountain.tippytap.client.TippytapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class KeystrokeRenderer {
    private KeystrokeRenderer() {}

    public static void render(GuiGraphics gfx, Minecraft client, TippytapConfig config,
                               boolean hitFlashActive, boolean hitWasCrit, boolean dimmed) {
        if (!config.showKeystrokes) return;

        int screenWidth  = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int keySize      = config.keystrokeKeySize;
        int actionHeight = config.keystrokeActionHeight;
        int panelWidth   = keySize * 3 + HudConst.KEY_GAP * 2;
        int panelHeight  = keySize * 2 + actionHeight + HudConst.KEY_GAP * 2;

        String position  = config.keystrokesPosition != null ? config.keystrokesPosition : "bottom_right";
        int panelLeft    = position.contains("right")  ? screenWidth  - panelWidth  - HudConst.MARGIN : HudConst.MARGIN;
        // Bottom edge of the panel background aligns with the hotbar background bottom
        int panelTop     = position.contains("bottom") ? screenHeight - config.hotbarOffsetY - panelHeight : HudConst.MARGIN;

        gfx.fill(panelLeft - HudConst.BG_PAD, panelTop - HudConst.BG_PAD,
                 panelLeft + panelWidth  + HudConst.BG_PAD,
                 panelTop  + panelHeight + HudConst.BG_PAD, HudConst.C_PANEL_BG);

        int col0 = panelLeft;
        int col1 = panelLeft + keySize + HudConst.KEY_GAP;
        int col2 = panelLeft + (keySize + HudConst.KEY_GAP) * 2;
        int row1 = panelTop  + keySize + HudConst.KEY_GAP;
        int row2 = panelTop  + keySize * 2 + HudConst.KEY_GAP * 2;
        int halfWidth = (panelWidth - HudConst.KEY_GAP) / 2;

        boolean sprinting = client.player.isSprinting();

        if (config.showKeystrokePri)
            drawKeyAttack(gfx, client, col0, panelTop, keySize, keySize,
                          "PRI", client.options.keyAttack.isDown(), hitFlashActive, hitWasCrit);
        drawKeyForward(gfx, client, col1, panelTop,  keySize, keySize,
                       "\u25b3", client.options.keyUp.isDown(), sprinting);
        if (config.showKeystrokeSec)
            drawKey(gfx, client, col2, panelTop, keySize, keySize,
                    "SEC", client.options.keyUse.isDown());

        drawKey(gfx, client, col0, row1, keySize, keySize, "\u25c1", client.options.keyLeft.isDown());
        drawKey(gfx, client, col1, row1, keySize, keySize, "\u25bd", client.options.keyDown.isDown());
        drawKey(gfx, client, col2, row1, keySize, keySize, "\u25b7", client.options.keyRight.isDown());

        drawKey(gfx, client, col0,                            row2, halfWidth, actionHeight, "SNEAK", client.options.keyShift.isDown());
        drawKey(gfx, client, col0 + halfWidth + HudConst.KEY_GAP, row2, halfWidth, actionHeight, "JUMP",  client.options.keyJump.isDown());

        if (dimmed) gfx.fill(panelLeft - HudConst.BG_PAD, panelTop - HudConst.BG_PAD,
                             panelLeft + panelWidth  + HudConst.BG_PAD,
                             panelTop  + panelHeight + HudConst.BG_PAD, HudConst.C_PANEL_DIM);
    }

    private static void drawKey(GuiGraphics gfx, Minecraft client,
                                 int x, int y, int w, int h, String label, boolean active) {
        gfx.fill(x, y, x + w, y + h, active ? HudConst.C_KEY_ACTIVE_BG : HudConst.C_KEY_IDLE_BG);
        DrawUtils.drawBorder(gfx, x, y, w, h, active ? HudConst.C_KEY_ACTIVE_BORDER : HudConst.C_KEY_IDLE_BORDER);
        DrawUtils.drawCenteredLabel(gfx, client, x, y, w, h, label,
                                    active ? HudConst.C_KEY_ACTIVE_TEXT : HudConst.C_KEY_IDLE_TEXT);
    }

    private static void drawKeyForward(GuiGraphics gfx, Minecraft client,
                                        int x, int y, int w, int h, String label,
                                        boolean active, boolean sprinting) {
        int bg     = sprinting ? HudConst.C_KEY_SPRINT_BG     : active ? HudConst.C_KEY_ACTIVE_BG     : HudConst.C_KEY_IDLE_BG;
        int border = sprinting ? HudConst.C_KEY_SPRINT_BORDER : active ? HudConst.C_KEY_ACTIVE_BORDER : HudConst.C_KEY_IDLE_BORDER;
        int text   = sprinting ? HudConst.C_KEY_SPRINT_TEXT   : active ? HudConst.C_KEY_ACTIVE_TEXT   : HudConst.C_KEY_IDLE_TEXT;
        gfx.fill(x, y, x + w, y + h, bg);
        DrawUtils.drawBorder(gfx, x, y, w, h, border);
        DrawUtils.drawCenteredLabel(gfx, client, x, y, w, h, label, text);
    }

    private static void drawKeyAttack(GuiGraphics gfx, Minecraft client,
                                       int x, int y, int w, int h, String label,
                                       boolean active, boolean hit, boolean crit) {
        int bg     = crit ? HudConst.C_KEY_CRIT_BG     : hit ? HudConst.C_KEY_HIT_BG     : active ? HudConst.C_KEY_ACTIVE_BG     : HudConst.C_KEY_IDLE_BG;
        int border = crit ? HudConst.C_KEY_CRIT_BORDER : hit ? HudConst.C_KEY_HIT_BORDER : active ? HudConst.C_KEY_ACTIVE_BORDER : HudConst.C_KEY_IDLE_BORDER;
        int text   = crit ? HudConst.C_KEY_CRIT_TEXT   : hit ? HudConst.C_KEY_HIT_TEXT   : active ? HudConst.C_KEY_ACTIVE_TEXT   : HudConst.C_KEY_IDLE_TEXT;
        gfx.fill(x, y, x + w, y + h, bg);
        DrawUtils.drawBorder(gfx, x, y, w, h, border);
        DrawUtils.drawCenteredLabel(gfx, client, x, y, w, h, label, text);
    }
}
