package io.github.thebrightmountain.tippytap.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TippytapConfig {

    // ── Info panel ────────────────────────────────────────────────────────────
    //
    // Each entry in infoLines is one HUD line. Remove an entry to hide it.
    // Tokens can be freely mixed on the same line or split across multiple lines.
    //
    // ── Movement / position ───────────────────────────────────────────────────
    //   {fps}             frames per second
    //   {x:.Nf}           X coordinate   (e.g. {x:.1f} = 1 decimal, {x:.0f} = none)
    //   {y:.Nf}           Y coordinate
    //   {z:.Nf}           Z coordinate
    //   {speed:.Nf}       horizontal speed in m/s
    //   {yaw:.Nf}         exact yaw angle in degrees (raw, can exceed ±360)
    //   {pitch:.Nf}       pitch angle  (-90 = up, 0 = level, 90 = down)
    //   {dir}             cardinal direction  (N / NE / E / SE / S / SW / W / NW)
    //   {cx}              chunk X coordinate
    //   {cz}              chunk Z coordinate
    //
    // ── Player stats ──────────────────────────────────────────────────────────
    //   {health:.Nf}      current health  (0.0–20.0, or higher with modifiers)
    //   {maxhealth:.Nf}   max health
    //   {food}            food level  (0–20)
    //   {armor}           total armor value  (0–20)
    //   {sprint}          sprinting state — coloured green when ON
    //   {cooldown}        attack cooldown 0–100 — coloured green at 100, orange otherwise
    //
    // ── World ─────────────────────────────────────────────────────────────────
    //   {blocklight}      block-light level at feet  (0–15)
    //   {skylight}        sky-light level at feet    (0–15)
    //   {biome}           biome name  (e.g. "plains", "dark_forest")
    //   {dim}             dimension   (e.g. "overworld", "the_nether", "the_end")
    //   {time}            day time in ticks  (0–23999; 0 = sunrise, 6000 = noon)
    //
    // ── Network ───────────────────────────────────────────────────────────────
    //   {ping}            server ping in ms  (–1 in singleplayer)
    //
    // ── Examples ──────────────────────────────────────────────────────────────
    //   one coord line : "{x:.1f} {y:.1f} {z:.1f}"
    //   labeled coords : "x: {x:.2f}  y: {y:.2f}  z: {z:.2f}"
    //   no labels      : "{x:.0f} {y:.0f} {z:.0f}"
    //   health bar     : "HP {health:.1f}/{maxhealth:.0f}  Food {food}"
    //   world info     : "{biome}  {dim}  Light {blocklight}"

    public List<String> infoLines = new ArrayList<>(List.of(
            "FPS {fps}",
            "{x:.1f} {y:.1f} {z:.1f}",
            "Spd {speed:.2f} m/s",
            "Dir {dir}"
    ));

    // ── Combat event lines ────────────────────────────────────────────────────
    // These appear for ~3 s after the relevant event. Set false to hide.

    public boolean showMeleDist = true;   // melee hit distance + CRIT tag
    public boolean showMeleDmg = true;   // melee damage dealt
    public boolean showRangeHit = true;   // ranged hit distance
    public boolean showRangeDmg = true;   // ranged damage dealt
    public boolean showHitReceived = true;   // incoming hit source distance / type
    public boolean showDmgTaken = true;   // incoming damage amount

    // ── Keystroke overlay ─────────────────────────────────────────────────────

    public boolean showKeystrokes = true;

    // ── Positions ─────────────────────────────────────────────────────────────
    // "top_left" | "top_right" | "bottom_left" | "bottom_right"

    public String infoPanelPosition = "top_left";
    public String keystrokesPosition = "bottom_right";

    // ── Sizes ─────────────────────────────────────────────────────────────────

    public int infoPanelWidth = 112;  // info panel width in pixels
    public int keystrokeKeySize = 18;   // directional key tile size in pixels
    public int keystrokeActionHeight = 12;   // height of SNEAK / JUMP / PRI / SEC keys

    // ── Persistence ───────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("tippytap.json");
    }

    /**
     * Loads from disk, or writes and returns defaults if missing / corrupt.
     */
    public static TippytapConfig load() {
        Path path = configPath();
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                TippytapConfig cfg = GSON.fromJson(r, TippytapConfig.class);
                if (cfg != null) return cfg;
            } catch (IOException | com.google.gson.JsonParseException ignored) {
            }
        }
        TippytapConfig defaults = new TippytapConfig();
        defaults.save();
        return defaults;
    }

    /**
     * Saves this config to disk (best-effort).
     */
    public void save() {
        try (Writer w = Files.newBufferedWriter(configPath())) {
            GSON.toJson(this, w);
        } catch (IOException ignored) {
        }
    }

    // ── Token replacement ─────────────────────────────────────────────────────

    /**
     * Replaces {@code {token}} and {@code {token:spec}} in {@code template}.
     * The spec is passed to {@link String#format} as {@code %spec}
     * (e.g. {@code .1f}, {@code .2f}, {@code .0f}).
     * Bare {@code {token}} defaults to {@code %.1f}.
     * Malformed specs fall back to {@code %.1f}.
     */
    public static String applyDouble(String template, String token, double value) {
        if (template == null) return "";
        String prefix = "{" + token + ":";
        String plain = "{" + token + "}";

        int idx = template.indexOf(prefix);
        while (idx >= 0) {
            int end = template.indexOf('}', idx + prefix.length());
            if (end < 0) break;
            String spec = template.substring(idx + prefix.length(), end);
            String formatted;
            try {
                formatted = String.format("%" + spec, value);
            } catch (java.util.IllegalFormatException e) {
                formatted = String.format("%.1f", value);
            }
            template = template.substring(0, idx) + formatted + template.substring(end + 1);
            idx = template.indexOf(prefix, idx + formatted.length());
        }
        return template.replace(plain, String.format("%.1f", value));
    }

    public static String applyInt(String template, String token, int value) {
        if (template == null) return "";
        return template.replace("{" + token + "}", String.valueOf(value));
    }

    public static String applyString(String template, String token, String value) {
        if (template == null) return "";
        return template.replace("{" + token + "}", value != null ? value : "");
    }
}