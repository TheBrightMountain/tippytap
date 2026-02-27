package io.github.thebrightmountain.tippytap.client;

import io.github.thebrightmountain.tippytap.mixin.client.InventoryAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.waypoints.PartialTickSupplier;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.jspecify.annotations.NonNull;

public class HudRenderer implements HudElement {

    /** Static reference used by mixins to fire events into this renderer. */
    public static volatile HudRenderer INSTANCE = null;

    private final TippytapConfig config;

    public HudRenderer(TippytapConfig config) { this.config = config; }
    public TippytapConfig getConfig()         { return config; }

    // =========================================================================
    // Constants — layout & style
    // =========================================================================

    // Layout
    private static final int    MARGIN        = 8;
    private static final int    KEY_GAP       = 2;
    private static final int    LOCATOR_H     = 5;
    private static final double LOCATOR_FOV   = 60.0;

    // Shared
    private static final int C_BG_PANEL       = 0x40000000;
    private static final int C_DIM            = 0x40000000;

    // Key states  (background / border / text)
    private static final int C_KEY_IDLE_BG    = 0x40222222;
    private static final int C_KEY_ACTIVE_BG  = 0x40555555;
    private static final int C_KEY_SPRINT_BG  = 0x40446644;
    private static final int C_KEY_HIT_BG     = 0x40FF8800;
    private static final int C_KEY_CRIT_BG    = 0x40FFDD00;

    private static final int C_KEY_IDLE_BD    = 0x40666666;
    private static final int C_KEY_ACTIVE_BD  = 0xFFFFFFFF;
    private static final int C_KEY_SPRINT_BD  = 0xFF44EE44;
    private static final int C_KEY_HIT_BD     = 0xFFFFAA00;
    private static final int C_KEY_CRIT_BD    = 0xFFFFEE00;

    private static final int C_KEY_IDLE_TXT   = 0xFFAAAAAA;
    private static final int C_KEY_ACTIVE_TXT = 0xFFFFFFFF;
    private static final int C_KEY_SPRINT_TXT = 0xFF88CC88;
    private static final int C_KEY_HIT_TXT    = 0xFF331A00;
    private static final int C_KEY_CRIT_TXT   = 0xFF332A00;

    private static final int C_TEXT_INFO      = 0xFFFFFFFF;

    // Hotbar slots
    private static final int C_SLOT_SEL_BG    = 0x40555555;
    private static final int C_SLOT_IDLE_BG   = 0x40222222;
    private static final int C_SLOT_SEL_BD    = 0xFFFFFFFF;
    private static final int C_SLOT_IDLE_BD   = 0x40666666;
    private static final int C_KEY_LABEL_SEL  = 0xFFFFFFFF;
    private static final int C_KEY_LABEL_IDLE = 0xFF888888;

    // Status bars — shared gradient: green (>60 %) → orange (>30 %) → red (≤30 %)
    private static final int C_BAR_TRACK      = 0x80333333;
    private static final int C_GRAD_HI        = 0xCC55DD55;
    private static final int C_GRAD_MID       = 0xCCFF8800;
    private static final int C_GRAD_LO        = 0xCCFF4444;
    private static final int C_BAR_XP         = 0xCC55FF55;
    private static final int C_XP_LEVEL_TXT   = 0xFFCCCCCC;

    // Locator bar
    private static final int C_LOCATOR_TICK   = 0x40FFFFFF;

    // Saturation bar
    private static final int C_BAR_SAT        = 0x80FFCC33;

    // Combat event text colors
    private static final int C_HIT_RANGE      = 0xFF55DDFF;
    private static final int C_HIT_CRIT       = 0xFFFFDD00;
    private static final int C_HIT_MELEE      = 0xFFFF8800;
    private static final int C_HIT_RECV       = 0xFFFF4444;

    // =========================================================================
    // State — FPS, speed, combat events
    // =========================================================================

    private long   lastFpsTime    = System.nanoTime();
    private int    frameCount     = 0;
    private int    currentFps     = 0;

    private volatile double  tickSpeed     = 0;
    private double prevTickX = 0, prevTickY = 0, prevTickZ = 0;
    private boolean hasPrevTickPos = false;

    private volatile long    hitFlashUntil = 0;
    private volatile long    hitDistUntil  = 0;
    private volatile float   lastHitDist   = 0f;
    private volatile boolean lastHitWasCrit   = false;
    private volatile boolean lastHitWasRanged = false;
    private volatile long    hitDmgUntil   = 0;
    private volatile float   lastHitDmg    = 0f;

    private volatile LivingEntity hitTarget       = null;
    private volatile float        hitTargetHealth = 0f;
    private volatile long         hitTargetExpiry = 0L;

    private volatile long   dmgInfoUntil   = 0;
    private volatile float  lastDmgDistance = 0f;
    private volatile String lastDmgType    = null;
    private volatile long   tknInfoUntil   = 0;
    private volatile float  lastTknDamage  = 0f;

    // =========================================================================
    // Public event API (called from mixins / callbacks)
    // =========================================================================

    /** Called every game tick to compute speed from position delta. */
    public void onGameTick(double x, double y, double z) {
        if (hasPrevTickPos) {
            double dx = x - prevTickX, dy = y - prevTickY, dz = z - prevTickZ;
            tickSpeed = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
        }
        prevTickX = x; prevTickY = y; prevTickZ = z;
        hasPrevTickPos = true;
    }

    /** Called from AttackEntityCallback when the player hits an entity. */
    public void onMeleHit(float distance, boolean crit) {
        long now = System.nanoTime();
        hitFlashUntil    = now + 300_000_000L;
        hitDistUntil     = now + 3_000_000_000L;
        lastHitDist      = distance;
        lastHitWasCrit   = crit;
        lastHitWasRanged = false;
    }

    /** Called from ClientPacketListenerMixin when a projectile hits an entity. */
    public void onRangeHit(float distance, LivingEntity target) {
        long now = System.nanoTime();
        hitDistUntil     = now + 3_000_000_000L;
        lastHitDist      = distance;
        lastHitWasCrit   = false;
        lastHitWasRanged = true;
        hitTarget        = target;
        hitTargetHealth  = target.getHealth();
        hitTargetExpiry  = now + 2_000_000_000L;
    }

    /** Called from AttackEntityCallback to track target health before a melee hit. */
    public void recordMeleTarget(LivingEntity target) {
        hitTarget       = target;
        hitTargetHealth = target.getHealth();
        hitTargetExpiry = System.nanoTime() + 1_000_000_000L;
    }

    /** Called from the mixin when the local player receives damage. */
    public void onHitReceived(float distance, String type) {
        dmgInfoUntil    = System.nanoTime() + 3_000_000_000L;
        lastDmgDistance = distance;
        lastDmgType     = type;
    }

    /** Called from the mixin when the player's health decreases. */
    public void onDmgTaken(float amount) {
        tknInfoUntil  = System.nanoTime() + 3_000_000_000L;
        lastTknDamage = amount;
    }

    // =========================================================================
    // Render entry point
    // =========================================================================

    @Override
    public void render(@NonNull GuiGraphics gfx, @NonNull DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        // Container screens (inventory, chest…) → dim the HUD behind them.
        boolean inContainer = client.screen instanceof AbstractContainerScreen<?>;

        boolean inDebug = client.gui.getDebugOverlay().showDebugScreen();

        tickFps();
        pollHitTarget();
        if (!inDebug || !config.hideInfoPanelInDebug)
            renderInfoPanel(gfx, client, inContainer);
        renderStatusBars(gfx, client, inContainer);
        renderLocatorBar(gfx, client, delta, inContainer);
        renderCustomHotbar(gfx, client, inContainer);
        renderKeystrokes(gfx, client, inContainer);
    }

    // =========================================================================
    // Internal tick helpers
    // =========================================================================

    private void tickFps() {
        frameCount++;
        long now = System.nanoTime();
        if (now - lastFpsTime >= 1_000_000_000L) {
            currentFps  = frameCount;
            frameCount  = 0;
            lastFpsTime = now;
        }
    }

    private void pollHitTarget() {
        LivingEntity target = hitTarget;
        if (target == null) return;
        float dealt   = hitTargetHealth - target.getHealth();
        boolean expired = System.nanoTime() > hitTargetExpiry || !target.isAlive();
        if (dealt > 0.001f) {
            hitDmgUntil = System.nanoTime() + 3_000_000_000L;
            lastHitDmg  = dealt;
            hitTarget   = null;
        } else if (expired) {
            hitTarget = null;
        }
    }

    // =========================================================================
    // Info panel
    // =========================================================================

    private void renderInfoPanel(GuiGraphics gfx, Minecraft client, boolean dimmed) {
        long now = System.nanoTime();
        boolean showHitDist = now < hitDistUntil && (lastHitWasRanged ? config.showRangeHit  : config.showMeleDist);
        boolean showHitDmg  = now < hitDmgUntil  && (lastHitWasRanged ? config.showRangeDmg  : config.showMeleDmg);
        boolean showHitLine = showHitDist || showHitDmg;
        boolean showDmg     = now < dmgInfoUntil  && config.showHitReceived;
        boolean showTkn     = now < tknInfoUntil  && config.showDmgTaken;

        int staticLines = config.infoLines != null ? config.infoLines.size() : 0;
        int extras = (showHitLine ? 1 : 0) + (showDmg ? 1 : 0) + (showTkn ? 1 : 0);
        if (staticLines + extras == 0) return;

        int lineH  = 10;
        int padX   = 4, padY = 3;
        int panelW = config.infoPanelWidth;
        int panelH = (staticLines + extras) * lineH + padY * 2;
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        String pos = config.infoPanelPosition != null ? config.infoPanelPosition : "top_left";
        int ox = pos.contains("right")  ? sw - panelW - MARGIN : MARGIN;
        int oy = pos.contains("bottom") ? sh - panelH - MARGIN : MARGIN;

        gfx.fill(ox, oy, ox + panelW, oy + panelH, C_BG_PANEL);

        int ty = oy + padY;

        if (config.infoLines != null) {
            double x  = client.player.getX();
            double y  = client.player.getY();
            double z  = client.player.getZ();
            float  yaw   = client.player.getYRot();
            float  pitch = client.player.getXRot();
            boolean sprinting = client.player.isSprinting();
            float charge    = client.player.getAttackStrengthScale(0f);
            int   chargePct = Math.round(charge * 100);

            double health    = client.player.getHealth();
            double maxHealth = client.player.getMaxHealth();
            int    food      = client.player.getFoodData().getFoodLevel();
            int    armor     = client.player.getArmorValue();

            var blockPos  = client.player.blockPosition();
            int cx        = blockPos.getX() >> 4;
            int cz        = blockPos.getZ() >> 4;
            int blockLight = client.level != null ? client.level.getBrightness(LightLayer.BLOCK, blockPos) : 0;
            int skyLight   = client.level != null ? client.level.getBrightness(LightLayer.SKY,   blockPos) : 0;
            long dayTime   = client.level != null ? client.level.getDayTime() % 24000L : 0L;
            String biome   = "?", dim = "?";
            if (client.level != null) {
                biome = client.level.getBiome(blockPos).unwrapKey().map(HudRenderer::resourceKeyPath).orElse("?");
                dim   = resourceKeyPath(client.level.dimension());
            }

            PlayerInfo pInfo = client.player.connection.getPlayerInfo(client.player.getUUID());
            int ping = pInfo != null ? pInfo.getLatency() : -1;

            String sprintVal = sprinting ? "\u00a7aON\u00a7r" : "OFF";
            String chargeVal = charge >= 1.0f ? "\u00a7a" + chargePct + "\u00a7r"
                                              : "\u00a76" + chargePct + "\u00a7r";

            for (String tmpl : config.infoLines) {
                String line = TippytapConfig.applyInt(tmpl, "fps", currentFps);
                line = TippytapConfig.applyDouble(line, "x", x);
                line = TippytapConfig.applyDouble(line, "y", y);
                line = TippytapConfig.applyDouble(line, "z", z);
                line = TippytapConfig.applyDouble(line, "speed",     tickSpeed);
                line = TippytapConfig.applyDouble(line, "yaw",       yaw);
                line = TippytapConfig.applyDouble(line, "pitch",     pitch);
                line = TippytapConfig.applyDouble(line, "health",    health);
                line = TippytapConfig.applyDouble(line, "maxhealth", maxHealth);
                line = TippytapConfig.applyInt(line, "food",       food);
                line = TippytapConfig.applyInt(line, "armor",      armor);
                line = TippytapConfig.applyInt(line, "blocklight", blockLight);
                line = TippytapConfig.applyInt(line, "skylight",   skyLight);
                line = TippytapConfig.applyInt(line, "time",  (int) dayTime);
                line = TippytapConfig.applyInt(line, "cx", cx);
                line = TippytapConfig.applyInt(line, "cz", cz);
                line = TippytapConfig.applyInt(line, "ping", ping);
                line = TippytapConfig.applyString(line, "biome", biome);
                line = TippytapConfig.applyString(line, "dim",   dim);
                line = TippytapConfig.applyString(line, "dir",   getDirection(yaw));
                line = line.replace("{sprint}",   sprintVal);
                line = line.replace("{cooldown}", chargeVal);
                gfx.drawString(client.font, line, ox + padX, ty, C_TEXT_INFO);
                ty += lineH;
            }
        }

        if (showHitLine) {
            StringBuilder hit = new StringBuilder();
            if (showHitDmg)  hit.append(String.format("Dmg %.1f", lastHitDmg));
            if (showHitDist) {
                if (!hit.isEmpty()) hit.append("  ");
                hit.append(String.format("Dist %.1f", lastHitDist));
            }
            int hitColor = lastHitWasRanged ? C_HIT_RANGE : (lastHitWasCrit ? C_HIT_CRIT : C_HIT_MELEE);
            gfx.drawString(client.font, hit.toString(), ox + padX, ty, hitColor);
            ty += lineH;
        }
        if (showDmg) {
            String txt = lastDmgDistance >= 0 ? String.format("Rcv  %.1fm", lastDmgDistance)
                                              : "Rcv  " + lastDmgType;
            gfx.drawString(client.font, txt, ox + padX, ty, C_HIT_RECV);
            ty += lineH;
        }
        if (showTkn) {
            gfx.drawString(client.font, String.format("Tkn  %.1f", lastTknDamage), ox + padX, ty, C_HIT_RECV);
        }
        if (dimmed) gfx.fill(ox, oy, ox + panelW, oy + panelH, C_DIM);
    }

    // =========================================================================
    // Status bars (health / food / XP)
    // =========================================================================

    private void renderStatusBars(GuiGraphics gfx, Minecraft client, boolean dimmed) {
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        int sz     = config.hotbarSlotSize;
        int hbGap  = config.hotbarGap;
        int totalW = 9 * sz + 8 * hbGap;
        int ox     = (sw - totalW) / 2;
        int barH   = 5, padX = 4, padV = 2;
        int rowH   = barH + padV * 2;
        int rowGap = 1;
        int base   = sh - config.hotbarOffsetY - sz - 2;  // top of hotbar outer bg

        // ── Row: XP | Food | Saturation ──────────────────────────────────────
        int row3Y  = base - rowGap - rowH;
        int colGap = 4;
        int colW   = (totalW - 2 * colGap) / 3;
        gfx.fill(ox, row3Y, ox + totalW, row3Y + rowH, C_BG_PANEL);

        if (config.showXpBar) {
            drawFillBar(gfx, ox + padX, row3Y + padV, colW - padX * 2, barH,
                        client.player.experienceProgress, C_BAR_XP);
            drawCenteredLabel(gfx, client, ox, row3Y, colW, rowH,
                              String.valueOf(client.player.experienceLevel), C_XP_LEVEL_TXT);
        }

        if (config.showFoodBar) {
            int foodX = ox + colW + colGap;
            float pct = client.player.getFoodData().getFoodLevel() / 20f;
            int color = pct > 0.6f ? C_GRAD_HI : (pct > 0.3f ? C_GRAD_MID : C_GRAD_LO);
            drawFillBar(gfx, foodX + padX, row3Y + padV, colW - padX * 2, barH, pct, color);
            drawCenteredLabel(gfx, client, foodX, row3Y, colW, rowH,
                              String.valueOf(client.player.getFoodData().getFoodLevel()), C_TEXT_INFO);

            int satX = ox + (colW + colGap) * 2;
            float sat = client.player.getFoodData().getSaturationLevel();
            drawFillBar(gfx, satX + padX, row3Y + padV, colW - padX * 2, barH,
                        Math.min(1f, sat / 20f), C_BAR_SAT);
            drawCenteredLabel(gfx, client, satX, row3Y, colW, rowH,
                              String.format("%.0f", sat), C_TEXT_INFO);
        }

        if (dimmed) gfx.fill(ox, row3Y, ox + totalW, row3Y + rowH, C_DIM);

        // ── Row: Health ───────────────────────────────────────────────────────
        if (config.showHealthBar) {
            int row2Y = row3Y - rowGap - rowH;
            gfx.fill(ox, row2Y, ox + totalW, row2Y + rowH, C_BG_PANEL);
            float h = client.player.getHealth(), mh = client.player.getMaxHealth();
            float pct = mh > 0 ? h / mh : 0f;
            int color = pct > 0.6f ? C_GRAD_HI : (pct > 0.3f ? C_GRAD_MID : C_GRAD_LO);
            drawFillBar(gfx, ox + padX, row2Y + padV, totalW - padX * 2, barH, pct, color);
            if (dimmed) gfx.fill(ox, row2Y, ox + totalW, row2Y + rowH, C_DIM);
        }
    }

    // =========================================================================
    // Locator bar
    // =========================================================================

    private void renderLocatorBar(GuiGraphics gfx, Minecraft client, DeltaTracker delta, boolean dimmed) {
        if (!config.showLocatorBar) return;
        if (client.player == null || client.level == null) return;
        var wpMgr = client.player.connection.getWaypointManager();
        if (!wpMgr.hasWaypoints()) return;

        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        int sz      = config.hotbarSlotSize;
        int hbGap   = config.hotbarGap;
        int totalW  = 9 * sz + 8 * hbGap;
        int ox      = (sw - totalW) / 2;
        int barH    = 5, padV = 2, rowH = barH + padV * 2, rowGap = 1;
        int base    = sh - config.hotbarOffsetY - sz - 2;
        int row3Y   = base - rowGap - rowH;
        int row2Y   = row3Y - rowGap - rowH;
        int locRowH = LOCATOR_H + padV * 2;
        int locRowY = row2Y - rowGap - locRowH;
        int oy      = locRowY + padV;

        gfx.fill(ox, locRowY, ox + totalW, locRowY + locRowH, C_BG_PANEL);

        // Centre tick
        int cx = ox + totalW / 2;
        gfx.fill(cx, oy, cx + 1, oy + LOCATOR_H, C_LOCATOR_TICK);

        PartialTickSupplier pts      = entity -> delta.getGameTimeDeltaPartialTick(true);
        var                  camera  = client.gameRenderer.getMainCamera();
        var                  proj    = client.gameRenderer;

        wpMgr.forEachWaypoint(client.player, (TrackedWaypoint wp) -> {
            double yaw = wp.yawAngleToCamera(client.level, camera, pts);
            if (yaw < -LOCATOR_FOV || yaw > LOCATOR_FOV) return;

            int dotX = Math.max(ox, Math.min(ox + totalW - 3,
                    ox + (int) ((yaw + LOCATOR_FOV) / (LOCATOR_FOV * 2) * totalW)));

            var wpId  = wp.id();
            int hash  = wpId.left().map(java.util.UUID::hashCode)
                            .orElseGet(() -> wpId.right().map(String::hashCode).orElse(0));
            int color = (wp.icon().color.orElse(0xFF000000 | (Math.abs(hash) % 0xFFFFFF + 1)) & 0x00FFFFFF) | 0xFF000000;

            gfx.fill(dotX, oy, dotX + 3, oy + LOCATOR_H, color);

            switch (wp.pitchDirectionToCamera(client.level, proj, pts)) {
                case UP   -> gfx.fill(dotX + 1, oy - 2,          dotX + 2, oy,              color);
                case DOWN -> gfx.fill(dotX + 1, oy + LOCATOR_H,  dotX + 2, oy + LOCATOR_H + 2, color);
                default   -> {}
            }
        });

        if (dimmed) gfx.fill(ox, locRowY, ox + totalW, locRowY + locRowH, C_DIM);
    }

    // =========================================================================
    // Custom hotbar
    // =========================================================================

    private void renderCustomHotbar(GuiGraphics gfx, Minecraft client, boolean dimmed) {
        if (!config.showCustomHotbar) return;

        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        int sz  = config.hotbarSlotSize;
        int gap = config.hotbarGap;
        int totalW  = 9 * sz + 8 * gap;
        int ox = (sw - totalW) / 2;
        int oy = sh - config.hotbarOffsetY - sz;

        // Main hotbar background + slots
        gfx.fill(ox - 2, oy - 2, ox + totalW + 2, oy + sz + 2, C_BG_PANEL);

        int selected = ((InventoryAccessor) client.player.getInventory()).getSelected();

        for (int i = 0; i < 9; i++) {
            int x   = ox + i * (sz + gap);
            boolean sel = i == selected;

            gfx.fill(x, oy, x + sz, oy + sz, sel ? C_SLOT_SEL_BG : C_SLOT_IDLE_BG);
            drawBorder(gfx, x, oy, sz, sz, sel ? C_SLOT_SEL_BD : C_SLOT_IDLE_BD);

            ItemStack stack = client.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                int ix = x + (sz - 16) / 2, iy = oy + (sz - 16) / 2;
                gfx.renderItem(stack, ix, iy);
                gfx.renderItemDecorations(client.font, stack, ix, iy);
            }

            drawSmallLabel(gfx, client, x + 1.5f, oy + 1.5f,
                    keyLabel(client.options.keyHotbarSlots[i]),
                    sel ? C_KEY_LABEL_SEL : C_KEY_LABEL_IDLE);
        }

        if (dimmed) gfx.fill(ox - 2, oy - 2, ox + totalW + 2, oy + sz + 2, C_DIM);

        // Armor bar — right of main hotbar, only occupied pieces in order
        if (config.showArmorBar) {
            EquipmentSlot[] armorSlots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
            java.util.List<ItemStack> worn = new java.util.ArrayList<>();
            for (EquipmentSlot slot : armorSlots) {
                ItemStack s = client.player.getItemBySlot(slot);
                if (!s.isEmpty()) worn.add(s);
            }
            if (!worn.isEmpty()) {
                int armorX = ox + totalW + 12;
                int armorW = worn.size() * sz + (worn.size() - 1) * gap;
                gfx.fill(armorX - 2, oy - 2, armorX + armorW + 2, oy + sz + 2, C_BG_PANEL);
                for (int i = 0; i < worn.size(); i++) {
                    int x = armorX + i * (sz + gap);
                    gfx.fill(x, oy, x + sz, oy + sz, C_SLOT_IDLE_BG);
                    drawBorder(gfx, x, oy, sz, sz, C_SLOT_IDLE_BD);
                    int ix = x + (sz - 16) / 2, iy = oy + (sz - 16) / 2;
                    gfx.renderItem(worn.get(i), ix, iy);
                    gfx.renderItemDecorations(client.font, worn.get(i), ix, iy);
                }
                if (dimmed) gfx.fill(armorX - 2, oy - 2, armorX + armorW + 2, oy + sz + 2, C_DIM);
            }
        }

        // Offhand slot — only shown when occupied
        ItemStack offhand = client.player.getOffhandItem();
        if (!offhand.isEmpty()) {
            int offhandX = ox - 12 - sz;
            gfx.fill(offhandX - 2, oy - 2, offhandX + sz + 2, oy + sz + 2, C_BG_PANEL);
            gfx.fill(offhandX, oy, offhandX + sz, oy + sz, C_SLOT_IDLE_BG);
            drawBorder(gfx, offhandX, oy, sz, sz, C_SLOT_IDLE_BD);
            int ix = offhandX + (sz - 16) / 2, iy = oy + (sz - 16) / 2;
            gfx.renderItem(offhand, ix, iy);
            gfx.renderItemDecorations(client.font, offhand, ix, iy);
            if (dimmed) gfx.fill(offhandX - 2, oy - 2, offhandX + sz + 2, oy + sz + 2, C_DIM);
        }
    }

    // =========================================================================
    // Keystroke panel
    // =========================================================================

    private void renderKeystrokes(GuiGraphics gfx, Minecraft client, boolean dimmed) {
        if (!config.showKeystrokes) return;

        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        int ks = config.keystrokeKeySize;
        int ah = config.keystrokeActionHeight;
        int totalW = ks * 3 + KEY_GAP * 2;
        int totalH = ks * 2 + ah + KEY_GAP * 2;
        String kpos = config.keystrokesPosition != null ? config.keystrokesPosition : "bottom_right";
        int bx = kpos.contains("right")  ? sw - totalW - MARGIN : MARGIN;
        int by = kpos.contains("bottom") ? sh - totalH - MARGIN : MARGIN;

        gfx.fill(bx - 2, by - 2, bx + totalW + 2, by + totalH + 2, C_BG_PANEL);

        int col1 = bx + ks + KEY_GAP;
        int col2 = bx + (ks + KEY_GAP) * 2;
        int row1 = by + ks + KEY_GAP;
        int row2 = by + ks * 2 + KEY_GAP * 2;
        int halfW = (totalW - KEY_GAP) / 2;

        boolean sprinting  = client.player.isSprinting();
        boolean hitting    = System.nanoTime() < hitFlashUntil;
        boolean wasCrit    = hitting && lastHitWasCrit;

        if (config.showKeystrokePri)
            drawKeyPri(gfx, client, bx,   by, ks, ks, "PRI", client.options.keyAttack.isDown(), hitting, wasCrit);
        drawKeyFwd(gfx, client, col1, by,   ks,    ks, "△",   client.options.keyUp.isDown(),     sprinting);
        if (config.showKeystrokeSec)
            drawKey   (gfx, client, col2, by, ks, ks, "SEC", client.options.keyUse.isDown());
        drawKey   (gfx, client, bx,   row1, ks,    ks, "◁",   client.options.keyLeft.isDown());
        drawKey   (gfx, client, col1, row1, ks,    ks, "▽",   client.options.keyDown.isDown());
        drawKey   (gfx, client, col2, row1, ks,    ks, "▷",   client.options.keyRight.isDown());
        drawKey   (gfx, client, bx,   row2, halfW, ah, "SNEAK", client.options.keyShift.isDown());
        drawKey   (gfx, client, bx + halfW + KEY_GAP, row2, halfW, ah, "JUMP", client.options.keyJump.isDown());

        if (dimmed) gfx.fill(bx - 2, by - 2, bx + totalW + 2, by + totalH + 2, C_DIM);
    }

    // =========================================================================
    // Drawing primitives
    // =========================================================================

    private void drawKey(GuiGraphics gfx, Minecraft client,
                         int x, int y, int w, int h, String label, boolean active) {
        gfx.fill(x, y, x + w, y + h, active ? C_KEY_ACTIVE_BG : C_KEY_IDLE_BG);
        drawBorder(gfx, x, y, w, h, active ? C_KEY_ACTIVE_BD : C_KEY_IDLE_BD);
        drawCenteredLabel(gfx, client, x, y, w, h, label, active ? C_KEY_ACTIVE_TXT : C_KEY_IDLE_TXT);
    }

    private void drawKeyFwd(GuiGraphics gfx, Minecraft client,
                            int x, int y, int w, int h, String label, boolean active, boolean sprint) {
        gfx.fill(x, y, x + w, y + h, sprint ? C_KEY_SPRINT_BG : (active ? C_KEY_ACTIVE_BG : C_KEY_IDLE_BG));
        drawBorder(gfx, x, y, w, h,   sprint ? C_KEY_SPRINT_BD : (active ? C_KEY_ACTIVE_BD : C_KEY_IDLE_BD));
        drawCenteredLabel(gfx, client, x, y, w, h, label,
                sprint ? C_KEY_SPRINT_TXT : (active ? C_KEY_ACTIVE_TXT : C_KEY_IDLE_TXT));
    }

    private void drawKeyPri(GuiGraphics gfx, Minecraft client,
                            int x, int y, int w, int h, String label, boolean active, boolean hit, boolean crit) {
        gfx.fill(x, y, x + w, y + h, crit ? C_KEY_CRIT_BG : (hit ? C_KEY_HIT_BG : (active ? C_KEY_ACTIVE_BG : C_KEY_IDLE_BG)));
        drawBorder(gfx, x, y, w, h,   crit ? C_KEY_CRIT_BD : (hit ? C_KEY_HIT_BD : (active ? C_KEY_ACTIVE_BD : C_KEY_IDLE_BD)));
        drawCenteredLabel(gfx, client, x, y, w, h, label,
                crit ? C_KEY_CRIT_TXT : (hit ? C_KEY_HIT_TXT : (active ? C_KEY_ACTIVE_TXT : C_KEY_IDLE_TXT)));
    }

    /** 1-px border around a rect. */
    private static void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x,         y,         x + w,     y + 1,     color);
        gfx.fill(x,         y + h - 1, x + w,     y + h,     color);
        gfx.fill(x,         y,         x + 1,     y + h,     color);
        gfx.fill(x + w - 1, y,         x + w,     y + h,     color);
    }

    /** Gradient fill bar with a dark empty track behind it. */
    private static void drawFillBar(GuiGraphics gfx, int x, int y, int w, int h, float pct, int fillColor) {
        gfx.fill(x, y, x + w, y + h, C_BAR_TRACK);
        int fillW = (int) (w * Math.min(1f, Math.max(0f, pct)));
        if (fillW > 0) gfx.fill(x, y, x + fillW, y + h, fillColor);
    }

    /** Text centred inside a rect at 0.5× scale. */
    private static void drawCenteredLabel(GuiGraphics gfx, Minecraft client,
                                          int x, int y, int w, int h, String label, int color) {
        float scale   = 0.5f;
        float scaledW = client.font.width(label) * scale;
        float tx = x + (w - scaledW) / 2f;
        float ty = y + (h - 4f * scale) / 2f;
        gfx.pose().pushMatrix();
        gfx.pose().translate(tx, ty);
        gfx.pose().scale(scale, scale);
        gfx.drawString(client.font, label, 0, 0, color, false);
        gfx.pose().popMatrix();
    }

    /** Small label in the top-left corner of a hotbar slot (0.5× scale). */
    private static void drawSmallLabel(GuiGraphics gfx, Minecraft client, float tx, float ty, String label, int color) {
        gfx.pose().pushMatrix();
        gfx.pose().translate(tx, ty);
        gfx.pose().scale(0.5f, 0.5f);
        gfx.drawString(client.font, label, 0, 0, color, false);
        gfx.pose().popMatrix();
    }

    // =========================================================================
    // Utilities
    // =========================================================================


    /** Extracts the last path segment of a KeyMapping's saveString(), max 3 chars, upper-cased. */
    private static String keyLabel(KeyMapping key) {
        String s = key.saveString();
        s = s.substring(s.lastIndexOf('.') + 1).toUpperCase();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }

    /**
     * Extracts the path segment from a ResourceKey by parsing its toString().
     * e.g. "ResourceKey[minecraft:worldgen/biome / minecraft:plains]" → "plains"
     */
    private static String resourceKeyPath(net.minecraft.resources.ResourceKey<?> key) {
        String s   = key.toString();
        int    sep = s.indexOf(" / ");
        if (sep < 0) return "?";
        String tail = s.substring(sep + 3).replace("]", "");
        int colon   = tail.indexOf(':');
        return colon >= 0 ? tail.substring(colon + 1) : tail;
    }

    private static String getDirection(float yaw) {
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
