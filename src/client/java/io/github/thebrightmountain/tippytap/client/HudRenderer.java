package io.github.thebrightmountain.tippytap.client;

import io.github.thebrightmountain.tippytap.client.hud.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

/**
 * Main HUD orchestrator. Tracks FPS, player speed, and combat events,
 * then delegates each render concern to a focused sub-renderer.
 */
public class HudRenderer implements HudElement {

    /** Accessed by mixins to fire events into the active renderer. */
    public static volatile HudRenderer INSTANCE = null;

    private final TippytapConfig config;

    public HudRenderer(TippytapConfig config) { this.config = config; }
    public TippytapConfig getConfig()         { return config; }

    // =========================================================================
    // FPS tracking
    // =========================================================================

    private long lastFpsTime = System.nanoTime();
    private int  frameCount  = 0;
    private int  currentFps  = 0;

    // =========================================================================
    // Speed tracking
    // =========================================================================

    private volatile double  tickSpeed  = 0;
    private double  prevX = 0, prevY = 0, prevZ = 0;
    private boolean hasPrevPos = false;

    // =========================================================================
    // Combat event state
    // =========================================================================

    private volatile long    hitFlashExpiry   = 0;
    private volatile long    hitDistExpiry    = 0;
    private volatile float   lastHitDistance  = 0f;
    private volatile boolean lastHitWasCrit   = false;
    private volatile boolean lastHitWasRanged = false;
    private volatile long    hitDmgExpiry     = 0;
    private volatile float   lastHitDamage    = 0f;

    private volatile LivingEntity hitTarget        = null;
    private volatile float        hitTargetHealth  = 0f;
    private volatile long         hitTargetExpiry  = 0L;

    private volatile long   receivedInfoExpiry = 0;
    private volatile float  receivedDistance   = 0f;
    private volatile String receivedType       = null;
    private volatile long   damageTakenExpiry  = 0;
    private volatile float  damageTaken        = 0f;
    private volatile long   damageTakenStart   = 0;

    private static final long DMG_FLASH_NS = 600_000_000L; // 600 ms

    // =========================================================================
    // Public event API (called from mixins)
    // =========================================================================

    /** Called every game tick to compute speed from position delta. */
    public void onGameTick(double x, double y, double z) {
        if (hasPrevPos) {
            double dx = x - prevX, dy = y - prevY, dz = z - prevZ;
            tickSpeed = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
        }
        prevX = x; prevY = y; prevZ = z;
        hasPrevPos = true;
    }

    /** Called when the player lands a melee hit. */
    public void onMeleHit(float distance, boolean crit) {
        long now = System.nanoTime();
        hitFlashExpiry   = now + 300_000_000L;
        hitDistExpiry    = now + 3_000_000_000L;
        lastHitDistance  = distance;
        lastHitWasCrit   = crit;
        lastHitWasRanged = false;
    }

    /** Called when a player-fired projectile hits an entity. */
    public void onRangeHit(float distance, LivingEntity target) {
        long now = System.nanoTime();
        hitDistExpiry    = now + 3_000_000_000L;
        lastHitDistance  = distance;
        lastHitWasCrit   = false;
        lastHitWasRanged = true;
        hitTarget        = target;
        hitTargetHealth  = target.getHealth();
        hitTargetExpiry  = now + 2_000_000_000L;
    }

    /** Called just before a melee hit to snapshot the target's health. */
    public void recordMeleTarget(LivingEntity target) {
        hitTarget       = target;
        hitTargetHealth = target.getHealth();
        hitTargetExpiry = System.nanoTime() + 1_000_000_000L;
    }

    /** Called when the local player takes a hit. */
    public void onHitReceived(float distance, String type) {
        receivedInfoExpiry = System.nanoTime() + 3_000_000_000L;
        receivedDistance   = distance;
        receivedType       = type;
    }

    /** Called when the local player's health decreases. */
    public void onDmgTaken(float amount) {
        long now          = System.nanoTime();
        damageTakenExpiry = now + 3_000_000_000L;
        damageTaken       = amount;
        damageTakenStart  = now;
    }

    // =========================================================================
    // Render entry point
    // =========================================================================

    @Override
    public void render(@NonNull GuiGraphics gfx, @NonNull DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        boolean inContainer = client.screen instanceof AbstractContainerScreen<?>;
        boolean inDebug     = client.gui.getDebugOverlay().showDebugScreen();

        tickFps();
        pollHitTarget();
        CombatInfo combat = buildCombatInfo();

        if (!inDebug || !config.hideInfoPanelInDebug)
            InfoPanelRenderer.render(gfx, client, config, currentFps, tickSpeed, combat, inContainer);

        float dmgFlashT = damageTakenStart > 0
                ? Math.min(1f, (float)(System.nanoTime() - damageTakenStart) / DMG_FLASH_NS)
                : 1f;
        StatusBarsRenderer.renderHealthBar(gfx, client, config, inContainer, dmgFlashT);
        SecondaryStatsRenderer.render(gfx, client, config, inContainer);
        StatusBarsRenderer.renderLocatorBar(gfx, client, config, delta, inContainer);
        HotbarRenderer.render(gfx, client, config, inContainer);
        KeystrokeRenderer.render(gfx, client, config, combat.hitFlashActive(), combat.hitWasCrit(), inContainer);
    }

    // =========================================================================
    // Private helpers
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
        float damageDealt = hitTargetHealth - target.getHealth();
        boolean expired   = System.nanoTime() > hitTargetExpiry || !target.isAlive();
        if (damageDealt > 0.001f) {
            hitDmgExpiry  = System.nanoTime() + 3_000_000_000L;
            lastHitDamage = damageDealt;
            hitTarget     = null;
        } else if (expired) {
            hitTarget = null;
        }
    }

    private CombatInfo buildCombatInfo() {
        long now = System.nanoTime();
        boolean hitDistVisible = now < hitDistExpiry;
        boolean hitDmgVisible  = now < hitDmgExpiry;
        return new CombatInfo(
            hitDistVisible && (lastHitWasRanged ? config.showRangeHit  : config.showMeleDist),
            hitDmgVisible  && (lastHitWasRanged ? config.showRangeDmg  : config.showMeleDmg),
            lastHitDistance, lastHitWasCrit, lastHitWasRanged, lastHitDamage,
            now < receivedInfoExpiry && config.showHitReceived,
            receivedDistance, receivedType,
            now < damageTakenExpiry && config.showDmgTaken, damageTaken,
            now < hitFlashExpiry
        );
    }
}
