package io.github.thebrightmountain.tippytap.client.hud;

import io.github.thebrightmountain.tippytap.client.TippytapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Renders the XP and food/saturation circular rings near the offhand slot. */
public final class SecondaryStatsRenderer {
    private SecondaryStatsRenderer() {}

    private static final ItemStack FOOD_ICON = new ItemStack(Items.BREAD);

    public static void render(GuiGraphics gfx, Minecraft client,
                               TippytapConfig config, boolean dimmed) {
        boolean showXp   = config.showXpBar;
        boolean showFood = config.showFoodBar;
        if (!showXp && !showFood) return;

        int screenWidth  = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int slotSize     = config.hotbarSlotSize;
        int slotGap      = config.hotbarGap;
        int hotbarWidth  = 9 * slotSize + 8 * slotGap;
        int hotbarLeft   = (screenWidth - hotbarWidth) / 2;

        int ringDiam  = HudConst.RING_R * 2;
        int ringCount = (showXp ? 1 : 0) + (showFood ? 1 : 0);  // food+sat share one ring

        int panelWidth  = HudConst.RING_PAD * 2 + ringCount * ringDiam + (ringCount - 1) * HudConst.RING_GAP;
        int panelHeight = HudConst.RING_PAD * 2 + ringDiam;

        // Bottom-aligned with the hotbar background, to the left of the offhand slot
        int hotbarBottom = screenHeight - config.hotbarOffsetY + HudConst.BG_PAD;
        int panelLeft    = hotbarLeft - HudConst.OFFHAND_GAP - slotSize - HudConst.OFFHAND_GAP - panelWidth;
        int panelTop     = hotbarBottom - panelHeight;

        gfx.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, HudConst.C_PANEL_BG);

        int ringCenterX = panelLeft + HudConst.RING_PAD + HudConst.RING_R;
        int ringCenterY = panelTop  + HudConst.RING_PAD + HudConst.RING_R;

        if (showXp) {
            DrawUtils.drawCircularBar(gfx, ringCenterX, ringCenterY,
                                      client.player.experienceProgress, HudConst.C_XP_FILL);
            DrawUtils.drawCenteredLabel(gfx, client,
                                        ringCenterX - HudConst.RING_R_IN, ringCenterY - HudConst.RING_R_IN,
                                        HudConst.RING_R_IN * 2, HudConst.RING_R_IN * 2,
                                        String.valueOf(client.player.experienceLevel),
                                        HudConst.C_XP_LEVEL);
            ringCenterX += ringDiam + HudConst.RING_GAP;
        }

        if (showFood) {
            float foodFill = client.player.getFoodData().getFoodLevel() / 20f;
            float satFill  = Math.min(foodFill, client.player.getFoodData().getSaturationLevel() / 20f);
            DrawUtils.drawMergedCircularBar(gfx, ringCenterX, ringCenterY,
                                            foodFill, HudConst.C_FOOD_FILL,
                                            satFill,  HudConst.C_SAT_FILL);
            float iconScale = 0.55f;
            gfx.pose().pushMatrix();
            gfx.pose().translate(ringCenterX - HudConst.ITEM_SIZE * iconScale * 0.5f,
                                 ringCenterY  - HudConst.ITEM_SIZE * iconScale * 0.5f);
            gfx.pose().scale(iconScale, iconScale);
            gfx.renderItem(FOOD_ICON, 0, 0);
            gfx.pose().popMatrix();
        }

        if (dimmed) gfx.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, HudConst.C_PANEL_DIM);
    }
}
