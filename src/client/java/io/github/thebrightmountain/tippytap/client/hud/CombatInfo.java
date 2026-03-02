package io.github.thebrightmountain.tippytap.client.hud;

/**
 * Immutable per-frame snapshot of combat event display state.
 * Built once per render call in HudRenderer and consumed by
 * {@link InfoPanelRenderer} and {@link KeystrokeRenderer}.
 */
public record CombatInfo(
    // Outgoing hit
    boolean showHitDistance,
    boolean showHitDamage,
    float   hitDistance,
    boolean hitWasCrit,
    boolean hitWasRanged,
    float   hitDamage,

    // Incoming hit
    boolean showReceivedInfo,
    float   receivedDistance,
    String  receivedType,
    boolean showDamageTaken,
    float   damageTaken,

    // Keystroke flash
    boolean hitFlashActive
) {}
