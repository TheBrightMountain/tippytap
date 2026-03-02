package io.github.thebrightmountain.tippytap.client.hud;

import io.github.thebrightmountain.tippytap.client.TippytapConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LightLayer;

public final class InfoPanelRenderer {
    private InfoPanelRenderer() {}

    public static void render(GuiGraphics gfx, Minecraft client, TippytapConfig config,
                               int fps, double speed, CombatInfo combat, boolean dimmed) {
        int staticLineCount = config.infoLines != null ? config.infoLines.size() : 0;
        int extraLineCount  = (combat.showHitDistance() || combat.showHitDamage() ? 1 : 0)
                            + (combat.showReceivedInfo() ? 1 : 0)
                            + (combat.showDamageTaken()  ? 1 : 0);
        if (staticLineCount + extraLineCount == 0) return;

        int lineHeight  = 10;
        int padX = 4, padY = 3;
        int panelWidth  = config.infoPanelWidth;
        int panelHeight = (staticLineCount + extraLineCount) * lineHeight + padY * 2;

        int screenWidth  = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        String position  = config.infoPanelPosition != null ? config.infoPanelPosition : "top_left";
        int panelLeft = position.contains("right")  ? screenWidth  - panelWidth  - HudConst.MARGIN : HudConst.MARGIN;
        int panelTop  = position.contains("bottom") ? screenHeight - panelHeight - HudConst.MARGIN : HudConst.MARGIN;

        gfx.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, HudConst.C_PANEL_BG);

        int textY = panelTop + padY;
        if (config.infoLines != null) {
            textY = renderStaticLines(gfx, client, config, fps, speed, panelLeft + padX, textY, lineHeight);
        }
        renderCombatLines(gfx, client, panelLeft + padX, textY, lineHeight, combat);

        if (dimmed) gfx.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, HudConst.C_PANEL_DIM);
    }

    private static int renderStaticLines(GuiGraphics gfx, Minecraft client, TippytapConfig config,
                                          int fps, double speed,
                                          int textX, int textY, int lineHeight) {
        double playerX   = client.player.getX();
        double playerY   = client.player.getY();
        double playerZ   = client.player.getZ();
        float  yaw       = client.player.getYRot();
        float  pitch     = client.player.getXRot();
        boolean sprinting = client.player.isSprinting();
        float  charge    = client.player.getAttackStrengthScale(0f);
        int    chargePct = Math.round(charge * 100);

        double health    = client.player.getHealth();
        double maxHealth = client.player.getMaxHealth();
        int    food      = client.player.getFoodData().getFoodLevel();
        int    armor     = client.player.getArmorValue();

        var blockPos   = client.player.blockPosition();
        int chunkX     = blockPos.getX() >> 4;
        int chunkZ     = blockPos.getZ() >> 4;
        int blockLight = client.level != null ? client.level.getBrightness(LightLayer.BLOCK, blockPos) : 0;
        int skyLight   = client.level != null ? client.level.getBrightness(LightLayer.SKY,   blockPos) : 0;
        long dayTime   = client.level != null ? client.level.getDayTime() % 24000L : 0L;

        String biome     = "?", dimension = "?";
        if (client.level != null) {
            biome     = client.level.getBiome(blockPos).unwrapKey().map(InfoPanelRenderer::resourcePath).orElse("?");
            dimension = resourcePath(client.level.dimension());
        }

        PlayerInfo playerInfo = client.player.connection.getPlayerInfo(client.player.getUUID());
        int ping = playerInfo != null ? playerInfo.getLatency() : -1;

        String sprintText = sprinting ? "\u00a7aON\u00a7r" : "OFF";
        String chargeText = charge >= 1.0f ? "\u00a7a" + chargePct + "\u00a7r"
                                           : "\u00a76" + chargePct + "\u00a7r";

        for (String template : config.infoLines) {
            String line = TippytapConfig.applyInt   (template, "fps",        fps);
            line = TippytapConfig.applyDouble(line, "x",          playerX);
            line = TippytapConfig.applyDouble(line, "y",          playerY);
            line = TippytapConfig.applyDouble(line, "z",          playerZ);
            line = TippytapConfig.applyDouble(line, "speed",      speed);
            line = TippytapConfig.applyDouble(line, "yaw",        yaw);
            line = TippytapConfig.applyDouble(line, "pitch",      pitch);
            line = TippytapConfig.applyDouble(line, "health",     health);
            line = TippytapConfig.applyDouble(line, "maxhealth",  maxHealth);
            line = TippytapConfig.applyInt   (line, "food",       food);
            line = TippytapConfig.applyInt   (line, "armor",      armor);
            line = TippytapConfig.applyInt   (line, "blocklight", blockLight);
            line = TippytapConfig.applyInt   (line, "skylight",   skyLight);
            line = TippytapConfig.applyInt   (line, "time",       (int) dayTime);
            line = TippytapConfig.applyInt   (line, "cx",         chunkX);
            line = TippytapConfig.applyInt   (line, "cz",         chunkZ);
            line = TippytapConfig.applyInt   (line, "ping",       ping);
            line = TippytapConfig.applyString(line, "biome",      biome);
            line = TippytapConfig.applyString(line, "dim",        dimension);
            line = TippytapConfig.applyString(line, "dir",        cardinalDirection(yaw));
            line = line.replace("{sprint}",   sprintText);
            line = line.replace("{cooldown}", chargeText);
            gfx.drawString(client.font, line, textX, textY, HudConst.C_TEXT_WHITE);
            textY += lineHeight;
        }
        return textY;
    }

    private static void renderCombatLines(GuiGraphics gfx, Minecraft client,
                                           int textX, int textY, int lineHeight, CombatInfo combat) {
        if (combat.showHitDistance() || combat.showHitDamage()) {
            StringBuilder line = new StringBuilder();
            if (combat.showHitDamage())   line.append(String.format("Dmg %.1f", combat.hitDamage()));
            if (combat.showHitDistance()) {
                if (!line.isEmpty()) line.append("  ");
                line.append(String.format("Dist %.1f", combat.hitDistance()));
            }
            int color = combat.hitWasRanged() ? HudConst.C_HIT_RANGE
                      : combat.hitWasCrit()   ? HudConst.C_HIT_CRIT
                      :                         HudConst.C_HIT_MELEE;
            gfx.drawString(client.font, line.toString(), textX, textY, color);
            textY += lineHeight;
        }
        if (combat.showReceivedInfo()) {
            String text = combat.receivedDistance() >= 0
                ? String.format("Rcv  %.1fm", combat.receivedDistance())
                : "Rcv  " + combat.receivedType();
            gfx.drawString(client.font, text, textX, textY, HudConst.C_HIT_RECV);
            textY += lineHeight;
        }
        if (combat.showDamageTaken()) {
            gfx.drawString(client.font, String.format("Tkn  %.1f", combat.damageTaken()),
                           textX, textY, HudConst.C_HIT_RECV);
        }
    }

    /** Returns the last path segment of a ResourceKey (e.g. "plains", "the_nether"). */
    private static String resourcePath(ResourceKey<?> key) {
        String s     = key.toString();
        int    sep   = s.indexOf(" / ");
        if (sep < 0) return "?";
        String tail  = s.substring(sep + 3).replace("]", "");
        int    colon = tail.indexOf(':');
        return colon >= 0 ? tail.substring(colon + 1) : tail;
    }

    /** Converts a yaw angle (degrees) to an 8-point cardinal label. */
    static String cardinalDirection(float yaw) {
        yaw = ((yaw % 360) + 360) % 360;
        if (yaw < 22.5  || yaw >= 337.5) return "S";
        if (yaw < 67.5)                  return "SW";
        if (yaw < 112.5)                 return "W";
        if (yaw < 157.5)                 return "NW";
        if (yaw < 202.5)                 return "N";
        if (yaw < 247.5)                 return "NE";
        if (yaw < 292.5)                 return "E";
        return "SE";
    }
}
