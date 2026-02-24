package io.github.thebrightmountain.tippytap.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public class HudRenderer implements HudElement {

    /**
     * Static reference used by the mixin to fire events into this renderer.
     */
    public static volatile HudRenderer INSTANCE = null;

    private final TippytapConfig config;

    public HudRenderer(TippytapConfig config) {
        this.config = config;
    }

    // FPS counter
    private long lastFpsTime = System.nanoTime();
    private int frameCount = 0;
    private int currentFps = 0;

    // Key tile gap (fixed; size comes from config)
    private static final int KEY_GAP = 2;

    // Outgoing hit state — unified for mele and range (most recent replaces the other)
    private volatile long hitFlashUntil = 0;     // PRI key flash (mele only, 300 ms)
    private volatile long hitDistUntil = 0;
    private volatile float lastHitDist = 0f;
    private volatile boolean lastHitWasCrit = false;
    private volatile boolean lastHitWasRanged = false;
    private volatile long hitDmgUntil = 0;
    private volatile float lastHitDmg = 0f;

    // Hit target tracking — polled each render frame to detect health change
    private volatile LivingEntity hitTarget = null;
    private volatile float hitTargetHealth = 0f;
    private volatile long hitTargetExpiry = 0L;

    // Incoming damage state — set by ClientPacketListenerMixin
    private volatile long dmgInfoUntil = 0;
    private volatile float lastDmgDistance = 0f;   // negative = no entity source
    private volatile String lastDmgType = null; // null = entity source
    private volatile long tknInfoUntil = 0;
    private volatile float lastTknDamage = 0f;

    // Colors
    private static final int COLOR_BG_PANEL = 0x80000000;
    private static final int COLOR_KEY_ACTIVE = 0xCCFFFFFF;
    private static final int COLOR_KEY_IDLE = 0x80222222;
    private static final int COLOR_KEY_HIT = 0xCCFF8800; // orange hit flash
    private static final int COLOR_KEY_CRIT = 0xCCFFDD00; // gold crit flash
    private static final int COLOR_KEY_SPRINT = 0xCC44FF44; // green sprint
    private static final int COLOR_BORDER_ACTIVE = 0xFFFFFFFF;
    private static final int COLOR_BORDER_IDLE = 0x80666666;
    private static final int COLOR_BORDER_HIT = 0xFFFFAA00;
    private static final int COLOR_BORDER_CRIT = 0xFFFFEE00;
    private static final int COLOR_BORDER_SPRINT = 0xFF00DD00;
    private static final int COLOR_TEXT_ACTIVE = 0xFF111111;
    private static final int COLOR_TEXT_IDLE = 0xFFAAAAAA;
    private static final int COLOR_TEXT_HIT = 0xFF331A00;
    private static final int COLOR_TEXT_CRIT = 0xFF332A00;
    private static final int COLOR_TEXT_SPRINT = 0xFF003300;
    private static final int COLOR_TEXT_INFO = 0xFFFFFFFF;

    /**
     * Called from AttackEntityCallback when the player hits an entity.
     */
    public void onMeleHit(float distance, boolean crit) {
        long now = System.nanoTime();
        hitFlashUntil = now + 300_000_000L;
        hitDistUntil = now + 3_000_000_000L;
        lastHitDist = distance;
        lastHitWasCrit = crit;
        lastHitWasRanged = false;
    }

    /**
     * Called from ClientPacketListenerMixin when the player's projectile hits another entity.
     */
    public void onRangeHit(float distance, LivingEntity target) {
        long now = System.nanoTime();
        hitDistUntil = now + 3_000_000_000L;
        lastHitDist = distance;
        lastHitWasCrit = false;
        lastHitWasRanged = true;
        hitTarget = target;
        hitTargetHealth = target.getHealth();
        hitTargetExpiry = now + 2_000_000_000L; // 2 s — arrows can be slow
    }

    /**
     * Called from AttackEntityCallback to track a living entity's health for damage dealt.
     */
    public void recordMeleTarget(LivingEntity target) {
        hitTarget = target;
        hitTargetHealth = target.getHealth();
        hitTargetExpiry = System.nanoTime() + 1_000_000_000L;
    }

    /**
     * Called from the mixin when the local player receives damage.
     *
     * @param distance distance to cause entity, or {@code -1} if no entity source
     * @param type     damage type msgId when no entity source (e.g. "fall", "fire"), or {@code null}
     */
    public void onHitReceived(float distance, String type) {
        dmgInfoUntil = System.nanoTime() + 3_000_000_000L;
        lastDmgDistance = distance;
        lastDmgType = type;
    }

    /**
     * Called from the mixin when the player's health decreases (damage taken).
     */
    public void onDmgTaken(float amount) {
        tknInfoUntil = System.nanoTime() + 3_000_000_000L;
        lastTknDamage = amount;
    }

    @Override
    public void render(@NonNull GuiGraphics gfx, @NonNull DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        tickFps();
        pollHitTarget();
        renderInfoPanel(gfx, client);
        renderKeystrokes(gfx, client);
    }

    // -------------------------------------------------------------------------
    // Hit target health polling (damage dealt detection)
    // -------------------------------------------------------------------------

    private void pollHitTarget() {
        LivingEntity target = hitTarget;
        if (target == null) return;

        float currentHealth = target.getHealth();
        float dealt = hitTargetHealth - currentHealth;
        boolean expired = System.nanoTime() > hitTargetExpiry || !target.isAlive();

        if (dealt > 0.001f) {
            hitDmgUntil = System.nanoTime() + 3_000_000_000L;
            lastHitDmg = dealt;
            hitTarget = null;
        } else if (expired) {
            hitTarget = null;
        }
    }

    // -------------------------------------------------------------------------
    // FPS tracking
    // -------------------------------------------------------------------------

    private void tickFps() {
        frameCount++;
        long now = System.nanoTime();
        if (now - lastFpsTime >= 1_000_000_000L) {
            currentFps = frameCount;
            frameCount = 0;
            lastFpsTime = now;
        }
    }

    // -------------------------------------------------------------------------
    // Info panel (top-left)
    // -------------------------------------------------------------------------

    private void renderInfoPanel(GuiGraphics gfx, Minecraft client) {
        long now = System.nanoTime();
        boolean showHitDist = now < hitDistUntil && (lastHitWasRanged ? config.showRangeHit : config.showMeleDist);
        boolean showHitDmg = now < hitDmgUntil && (lastHitWasRanged ? config.showRangeDmg : config.showMeleDmg);
        boolean showHitLine = showHitDist || showHitDmg;
        boolean showDmg = now < dmgInfoUntil && config.showHitReceived;
        boolean showTkn = now < tknInfoUntil && config.showDmgTaken;

        int staticLines = config.infoLines != null ? config.infoLines.size() : 0;
        int extras = (showHitLine ? 1 : 0) + (showDmg ? 1 : 0) + (showTkn ? 1 : 0);

        if (staticLines + extras == 0) return;

        int lineH = 10;
        int padX = 4, padY = 3;
        int margin = 4;
        int panelW = config.infoPanelWidth;
        int panelH = (staticLines + extras) * lineH + padY * 2;
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();
        String ipos = config.infoPanelPosition != null ? config.infoPanelPosition : "top_left";
        int ox = ipos.contains("right") ? sw - panelW - margin : margin;
        int oy = ipos.contains("bottom") ? sh - panelH - margin : margin;

        gfx.fill(ox, oy, ox + panelW, oy + panelH, COLOR_BG_PANEL);

        int ty = oy + padY;

        // ── Compose static info lines ─────────────────────────────────────────
        if (config.infoLines != null) {
            double x = client.player.getX();
            double y = client.player.getY();
            double z = client.player.getZ();
            Vec3 vel = client.player.getDeltaMovement();
            double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0;
            float yaw = client.player.getYRot();
            float pitch = client.player.getXRot();
            boolean sprinting = client.player.isSprinting();
            float charge = client.player.getAttackStrengthScale(0f);
            int chargePct = Math.round(charge * 100);

            // Player stats
            double health = client.player.getHealth();
            double maxHealth = client.player.getMaxHealth();
            int food = client.player.getFoodData().getFoodLevel();
            int armor = client.player.getArmorValue();

            // World / level data
            var blockPos = client.player.blockPosition();
            int cx = blockPos.getX() >> 4;
            int cz = blockPos.getZ() >> 4;
            int blockLight = client.level != null ? client.level.getBrightness(LightLayer.BLOCK, blockPos) : 0;
            int skyLight = client.level != null ? client.level.getBrightness(LightLayer.SKY, blockPos) : 0;
            long dayTime = client.level != null ? client.level.getDayTime() % 24000L : 0L;
            String biome = "?";
            String dim = "?";
            if (client.level != null) {
                biome = client.level.getBiome(blockPos).unwrapKey()
                        .map(HudRenderer::resourceKeyPath).orElse("?");
                dim = resourceKeyPath(client.level.dimension());
            }

            // Network ping (–1 in singleplayer)
            PlayerInfo pInfo = client.player.connection.getPlayerInfo(client.player.getUUID());
            int ping = pInfo != null ? pInfo.getLatency() : -1;

            // {sprint} and {cooldown} embed §-colour codes so they render coloured
            // regardless of which line the user places them on.
            String sprintVal = sprinting ? "\u00a7aON\u00a7r" : "OFF";
            String chargeVal = charge >= 1.0f
                    ? "\u00a7a" + chargePct + "\u00a7r"
                    : "\u00a76" + chargePct + "\u00a7r";

            for (String tmpl : config.infoLines) {
                String line = TippytapConfig.applyInt(tmpl, "fps", currentFps);
                line = TippytapConfig.applyDouble(line, "x", x);
                line = TippytapConfig.applyDouble(line, "y", y);
                line = TippytapConfig.applyDouble(line, "z", z);
                line = TippytapConfig.applyDouble(line, "speed", hSpeed);
                line = TippytapConfig.applyDouble(line, "yaw", yaw);
                line = TippytapConfig.applyDouble(line, "pitch", pitch);
                line = TippytapConfig.applyDouble(line, "health", health);
                line = TippytapConfig.applyDouble(line, "maxhealth", maxHealth);
                line = TippytapConfig.applyInt(line, "food", food);
                line = TippytapConfig.applyInt(line, "armor", armor);
                line = TippytapConfig.applyInt(line, "blocklight", blockLight);
                line = TippytapConfig.applyInt(line, "skylight", skyLight);
                line = TippytapConfig.applyInt(line, "time", (int) dayTime);
                line = TippytapConfig.applyInt(line, "cx", cx);
                line = TippytapConfig.applyInt(line, "cz", cz);
                line = TippytapConfig.applyInt(line, "ping", ping);
                line = TippytapConfig.applyString(line, "biome", biome);
                line = TippytapConfig.applyString(line, "dim", dim);
                line = TippytapConfig.applyString(line, "dir", getDirection(yaw));
                line = line.replace("{sprint}", sprintVal);
                line = line.replace("{cooldown}", chargeVal);
                gfx.drawString(client.font, line, ox + padX, ty, COLOR_TEXT_INFO);
                ty += lineH;
            }
        }

        // ── Combat event lines (conditional, always same format) ──────────────
        if (showHitLine) {
            StringBuilder hit = new StringBuilder();
            if (showHitDmg) hit.append(String.format("Dmg %.1f", lastHitDmg));
            if (showHitDist) {
                if (hit.length() > 0) hit.append("  ");
                hit.append(String.format("Dist %.1f", lastHitDist));
            }
            int hitColor = lastHitWasRanged ? 0xFF55DDFF : (lastHitWasCrit ? 0xFFFFDD00 : 0xFFFF8800);
            gfx.drawString(client.font, hit.toString(), ox + padX, ty, hitColor);
            ty += lineH;
        }
        if (showDmg) {
            String dmgText = lastDmgDistance >= 0
                    ? String.format("Rcv  %.1fm", lastDmgDistance)
                    : "Rcv  " + lastDmgType;
            gfx.drawString(client.font, dmgText, ox + padX, ty, 0xFFFF4444);
            ty += lineH;
        }
        if (showTkn) {
            gfx.drawString(client.font,
                    String.format("Tkn  %.1f", lastTknDamage), ox + padX, ty, 0xFFFF4444);
        }
    }

    // -------------------------------------------------------------------------
    // Keystroke panel (bottom-right)
    // -------------------------------------------------------------------------

    private void renderKeystrokes(GuiGraphics gfx, Minecraft client) {
        if (!config.showKeystrokes) return;

        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();

        boolean kUp = client.options.keyUp.isDown();
        boolean kLeft = client.options.keyLeft.isDown();
        boolean kDown = client.options.keyDown.isDown();
        boolean kRight = client.options.keyRight.isDown();
        boolean kJump = client.options.keyJump.isDown();
        boolean kPrimary = client.options.keyAttack.isDown();
        boolean kSecondary = client.options.keyUse.isDown();
        boolean kSneak = client.options.keyShift.isDown();

        // 3 columns × 4 rows  (rows 2–3 use shorter action-key height)
        int ks = config.keystrokeKeySize;
        int ah = config.keystrokeActionHeight;
        int margin = 4;
        int totalW = ks * 3 + KEY_GAP * 2;
        int totalH = ks * 2 + ah * 2 + KEY_GAP * 3;
        String kpos = config.keystrokesPosition != null ? config.keystrokesPosition : "bottom_right";
        int bx = kpos.contains("right") ? sw - totalW - margin : margin;
        int by = kpos.contains("bottom") ? sh - totalH - margin : margin;

        int col0 = bx;
        int col1 = bx + ks + KEY_GAP;
        int col2 = bx + (ks + KEY_GAP) * 2;
        int row0 = by;
        int row1 = by + ks + KEY_GAP;
        int row2 = by + ks * 2 + KEY_GAP * 2;
        int row3 = by + ks * 2 + ah + KEY_GAP * 3;

        // Row 0: [ ] [W] [ ]
        boolean sprinting = client.player.isSprinting();
        drawKeyFwd(gfx, client, col1, row0, ks, ks, "△", kUp, sprinting);

        // Row 1: [A] [S] [D]
        drawKey(gfx, client, col0, row1, ks, ks, "◁", kLeft);
        drawKey(gfx, client, col1, row1, ks, ks, "▽", kDown);
        drawKey(gfx, client, col2, row1, ks, ks, "▷", kRight);

        int halfW = (totalW - KEY_GAP) / 2;

        // Row 2: [Crouch] [Jump]
        drawKey(gfx, client, col0, row2, halfW, ah, "SNEAK", kSneak);
        drawKey(gfx, client, col0 + halfW + KEY_GAP, row2, halfW, ah, "JUMP", kJump);

        // Row 3: [Pri] [Sec]
        boolean hitting = System.nanoTime() < hitFlashUntil;
        boolean wasCrit = hitting && lastHitWasCrit;
        drawKeyPri(gfx, client, col0, row3, halfW, ah, "PRI", kPrimary, hitting, wasCrit);
        drawKey(gfx, client, col0 + halfW + KEY_GAP, row3, halfW, ah, "SEC", kSecondary);
    }

    // -------------------------------------------------------------------------
    // Drawing helpers
    // -------------------------------------------------------------------------

    private void drawKey(GuiGraphics gfx, Minecraft client,
                         int x, int y, int w, int h, String label, boolean active) {
        // Background
        gfx.fill(x, y, x + w, y + h, active ? COLOR_KEY_ACTIVE : COLOR_KEY_IDLE);

        // 1-px border
        int border = active ? COLOR_BORDER_ACTIVE : COLOR_BORDER_IDLE;
        gfx.fill(x, y, x + w, y + 1, border); // top
        gfx.fill(x, y + h - 1, x + w, y + h, border); // bottom
        gfx.fill(x, y, x + 1, y + h, border); // left
        gfx.fill(x + w - 1, y, x + w, y + h, border); // right

        // Centered label (no drop shadow so active state text stays crisp on white)
        int textColor = active ? COLOR_TEXT_ACTIVE : COLOR_TEXT_IDLE;
        int tw = client.font.width(label);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - 8) / 2;
        gfx.drawString(client.font, label, tx, ty, textColor, false);
    }

    /**
     * Like drawKey but turns green when the player is sprinting.
     */
    private void drawKeyFwd(GuiGraphics gfx, Minecraft client,
                            int x, int y, int w, int h, String label, boolean active, boolean sprint) {
        int bg = sprint ? COLOR_KEY_SPRINT : (active ? COLOR_KEY_ACTIVE : COLOR_KEY_IDLE);
        int border = sprint ? COLOR_BORDER_SPRINT : (active ? COLOR_BORDER_ACTIVE : COLOR_BORDER_IDLE);
        int text = sprint ? COLOR_TEXT_SPRINT : (active ? COLOR_TEXT_ACTIVE : COLOR_TEXT_IDLE);

        gfx.fill(x, y, x + w, y + h, bg);
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);

        int tw = client.font.width(label);
        gfx.drawString(client.font, label, x + (w - tw) / 2, y + (h - 8) / 2, text, false);
    }

    /**
     * Like drawKey but with hit-flash and crit states that override active colors.
     */
    private void drawKeyPri(GuiGraphics gfx, Minecraft client,
                            int x, int y, int w, int h, String label, boolean active, boolean hit, boolean crit) {
        int bg = crit ? COLOR_KEY_CRIT : (hit ? COLOR_KEY_HIT : (active ? COLOR_KEY_ACTIVE : COLOR_KEY_IDLE));
        int border = crit ? COLOR_BORDER_CRIT : (hit ? COLOR_BORDER_HIT : (active ? COLOR_BORDER_ACTIVE : COLOR_BORDER_IDLE));
        int text = crit ? COLOR_TEXT_CRIT : (hit ? COLOR_TEXT_HIT : (active ? COLOR_TEXT_ACTIVE : COLOR_TEXT_IDLE));

        gfx.fill(x, y, x + w, y + h, bg);
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);

        int tw = client.font.width(label);
        gfx.drawString(client.font, label, x + (w - tw) / 2, y + (h - 8) / 2, text, false);
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Extracts the path segment from a ResourceKey by parsing its toString().
     * e.g. "ResourceKey[minecraft:worldgen/biome / minecraft:plains]" → "plains"
     * Avoids calling location()/id() whose name varies across MC versions.
     */
    private static String resourceKeyPath(net.minecraft.resources.ResourceKey<?> key) {
        String s = key.toString();
        int sep = s.indexOf(" / ");
        if (sep < 0) return "?";
        String tail = s.substring(sep + 3).replace("]", "");
        int colon = tail.indexOf(':');
        return colon >= 0 ? tail.substring(colon + 1) : tail;
    }

    private String getDirection(float yaw) {
        yaw = ((yaw % 360) + 360) % 360;
        if (yaw < 22.5 || yaw >= 337.5) return "S";
        if (yaw < 67.5) return "SW";
        if (yaw < 112.5) return "W";
        if (yaw < 157.5) return "NW";
        if (yaw < 202.5) return "N";
        if (yaw < 247.5) return "NE";
        if (yaw < 292.5) return "E";
        return "SE";
    }
}