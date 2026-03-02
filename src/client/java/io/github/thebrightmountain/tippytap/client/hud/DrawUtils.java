package io.github.thebrightmountain.tippytap.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Static drawing helpers shared by all HUD renderers. */
public final class DrawUtils {
    private DrawUtils() {}

    /** Draws a 1-px border around a rectangle. */
    public static void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x,         y,         x + w, y + 1, color);  // top
        gfx.fill(x,         y + h - 1, x + w, y + h, color);  // bottom
        gfx.fill(x,         y,         x + 1, y + h, color);  // left
        gfx.fill(x + w - 1, y,         x + w, y + h, color);  // right
    }

    /**
     * Draws a left-to-right fill bar with a dark empty track behind it.
     *
     * @param fill 0.0–1.0 fraction
     */
    public static void drawFillBar(GuiGraphics gfx, int x, int y, int w, int h, float fill, int fillColor) {
        gfx.fill(x, y, x + w, y + h, HudConst.C_BAR_TRACK);
        int fillWidth = (int) (w * Math.min(1f, Math.max(0f, fill)));
        if (fillWidth > 0) gfx.fill(x, y, x + fillWidth, y + h, fillColor);
    }

    /** Draws text centred inside a rectangle at 0.5× scale with drop shadow. */
    public static void drawCenteredLabel(GuiGraphics gfx, Minecraft client,
                                         int x, int y, int w, int h, String text, int color) {
        float scale      = 0.5f;
        float textWidth  = client.font.width(text) * scale;
        float textHeight = client.font.lineHeight * scale;
        // Round to nearest pixel to prevent floor-truncation shifting the text up/left
        float tx = Math.round(x + (w - textWidth)  / 2f);
        float ty = Math.round(y + (h - textHeight) / 2f);
        gfx.pose().pushMatrix();
        gfx.pose().translate(tx, ty);
        gfx.pose().scale(scale, scale);
        gfx.drawString(client.font, text, 0, 0, color, true);
        gfx.pose().popMatrix();
    }

    /** Draws a small label near the top-left corner of a slot at 0.5× scale with drop shadow. */
    public static void drawSmallLabel(GuiGraphics gfx, Minecraft client,
                                      float tx, float ty, String text, int color) {
        gfx.pose().pushMatrix();
        gfx.pose().translate(tx, ty);
        gfx.pose().scale(0.5f, 0.5f);
        gfx.drawString(client.font, text, 0, 0, color, true);
        gfx.pose().popMatrix();
    }

    /**
     * Anti-aliased circular arc progress bar. Fills clockwise from the top.
     * Ring geometry is taken from {@link HudConst#RING_R} / {@link HudConst#RING_R_IN}.
     *
     * @param fill 0.0–1.0 fraction
     */
    public static void drawCircularBar(GuiGraphics gfx, int centerX, int centerY,
                                       float fill, int fillColor) {
        drawMergedCircularBar(gfx, centerX, centerY, fill, fillColor, 0f, fillColor);
    }

    /**
     * Two-color circular arc in a single pass. {@code secondaryFill} is drawn on top of
     * {@code primaryFill} (e.g. saturation over food), with the dark track filling the rest.
     */
    public static void drawMergedCircularBar(GuiGraphics gfx, int centerX, int centerY,
                                             float primaryFill,   int primaryColor,
                                             float secondaryFill, int secondaryColor) {
        final int    outerR = HudConst.RING_R;
        final int    innerR = HudConst.RING_R_IN;
        final double twoPi  = Math.PI * 2;
        final double halfPi = Math.PI / 2;

        float primary   = Math.max(0f, Math.min(1f, primaryFill));
        float secondary = Math.max(0f, Math.min(primary, secondaryFill));

        for (int dy = -(outerR + 1); dy <= outerR + 1; dy++) {
            for (int dx = -(outerR + 1); dx <= outerR + 1; dx++) {
                double dist  = Math.sqrt(dx * dx + dy * dy);
                float  alpha = Math.min((float)(dist - (innerR - 0.5)),
                                        (float)(outerR + 0.5 - dist));
                alpha = Math.max(0f, Math.min(1f, alpha));
                if (alpha <= 0) continue;

                double angle     = (Math.atan2(dy, dx) + halfPi + twoPi) % twoPi / twoPi;
                int    baseColor = angle <= secondary ? secondaryColor
                                 : angle <= primary   ? primaryColor
                                 :                      HudConst.C_BAR_TRACK;
                int srcAlpha = (baseColor >>> 24) & 0xFF;
                int color    = (baseColor & 0x00FFFFFF) | ((int)(srcAlpha * alpha) << 24);
                gfx.fill(centerX + dx, centerY + dy,
                         centerX + dx + 1, centerY + dy + 1, color);
            }
        }
    }
}
