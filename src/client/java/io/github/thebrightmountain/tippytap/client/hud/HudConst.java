package io.github.thebrightmountain.tippytap.client.hud;

/** Shared layout metrics and color palette for all HUD renderers. */
public final class HudConst {
    private HudConst() {}

    // ── Layout ────────────────────────────────────────────────────────────────

    /** Screen-edge margin used by panels that snap to a corner (px). */
    public static final int    MARGIN      = 8;
    /** Background fill extension around panel content (px). */
    public static final int    BG_PAD      = 2;
    /** Gap between the main hotbar edge and offhand / armor slots (px). */
    public static final int    OFFHAND_GAP = 12;
    /** Standard Minecraft item icon size (px). */
    public static final int    ITEM_SIZE   = 16;
    /** Gap between individual keystroke keys (px). */
    public static final int    KEY_GAP     = 2;
    /** Height of each locator bar tick mark (px). */
    public static final int    LOCATOR_H   = 5;
    /** Half-angle of the locator bar field of view (degrees). */
    public static final double LOCATOR_FOV = 60.0;

    // ── Circular status rings ─────────────────────────────────────────────────

    /** Outer radius of a status ring (px). */
    public static final int RING_R    = 9;
    /** Inner radius — ring thickness = RING_R − RING_R_IN = 2 px. */
    public static final int RING_R_IN = 7;
    /** Gap between adjacent rings (px). */
    public static final int RING_GAP  = 5;
    /** Padding inside the ring panel background (px). */
    public static final int RING_PAD  = 4;

    // ── Panel backgrounds ─────────────────────────────────────────────────────

    public static final int C_PANEL_BG  = 0x40000000;
    public static final int C_PANEL_DIM = 0x40000000;

    // ── Keystroke key states  (background / border / text) ───────────────────

    public static final int C_KEY_IDLE_BG      = 0x40222222;
    public static final int C_KEY_ACTIVE_BG    = 0x40555555;
    public static final int C_KEY_SPRINT_BG    = 0x40446644;
    public static final int C_KEY_HIT_BG       = 0x40FF8800;
    public static final int C_KEY_CRIT_BG      = 0x40FFDD00;

    public static final int C_KEY_IDLE_BORDER   = 0x40666666;
    public static final int C_KEY_ACTIVE_BORDER = 0xFFFFFFFF;
    public static final int C_KEY_SPRINT_BORDER = 0xFF44EE44;
    public static final int C_KEY_HIT_BORDER    = 0xFFFFAA00;
    public static final int C_KEY_CRIT_BORDER   = 0xFFFFEE00;

    public static final int C_KEY_IDLE_TEXT    = 0xFFAAAAAA;
    public static final int C_KEY_ACTIVE_TEXT  = 0xFFFFFFFF;
    public static final int C_KEY_SPRINT_TEXT  = 0xFF88CC88;
    public static final int C_KEY_HIT_TEXT     = 0xFF331A00;
    public static final int C_KEY_CRIT_TEXT    = 0xFF332A00;

    // ── Hotbar slots ──────────────────────────────────────────────────────────

    public static final int C_SLOT_SELECTED_BG     = 0x40555555;
    public static final int C_SLOT_IDLE_BG         = 0x40222222;
    public static final int C_SLOT_SELECTED_BORDER = 0xFFFFFFFF;
    public static final int C_SLOT_IDLE_BORDER     = 0x40666666;
    public static final int C_SLOT_LABEL_SELECTED  = 0xFFFFFFFF;
    public static final int C_SLOT_LABEL_IDLE      = 0xFF888888;

    // ── Status bars ───────────────────────────────────────────────────────────

    public static final int C_BAR_TRACK  = 0x80333333;
    public static final int C_HEALTH_HI  = 0xCC44BB44;  // > 60 %
    public static final int C_HEALTH_MID = 0xCCDD8822;  // > 30 %
    public static final int C_HEALTH_LO  = 0xCCDD3333;  // ≤ 30 %
    public static final int C_XP_FILL    = 0xCC33AA33;
    public static final int C_FOOD_FILL  = 0xCCCC8822;
    public static final int C_SAT_FILL   = 0xCCBBAA33;
    public static final int C_XP_LEVEL   = 0xFFCCCCCC;

    // ── Locator bar ───────────────────────────────────────────────────────────

    public static final int C_LOCATOR_TICK = 0x40FFFFFF;

    // ── Info panel / combat text ──────────────────────────────────────────────

    public static final int C_TEXT_WHITE = 0xFFFFFFFF;
    public static final int C_HIT_RANGE  = 0xFF55DDFF;
    public static final int C_HIT_CRIT   = 0xFFFFDD00;
    public static final int C_HIT_MELEE  = 0xFFFF8800;
    public static final int C_HIT_RECV   = 0xFFFF4444;
}
