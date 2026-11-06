package com.lyricsflow26.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lyricsflow26.LyricsFlowMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Persistent settings, stored as JSON in the Fabric config directory. */
public final class LyricsConfig {
    public enum DisplayMode { HUD, WORLD }
    public enum Align { LEFT, CENTER, RIGHT }
    public enum HudAnchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT, MIDDLE_LEFT, MIDDLE, MIDDLE_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;
        public int row() { return ordinal() / 3; } // 0 top, 1 middle, 2 bottom
        public int col() { return ordinal() % 3; } // 0 left, 1 center, 2 right
    }
    public enum FontStyle { NORMAL, BOLD, ITALIC, BOLD_ITALIC }
    public enum Transition { NONE, FADE, SLIDE }
    public enum WordEffect { OFF, HIGHLIGHT, REVEAL }
    public enum FloatDirection { UP, DOWN, DRIFT }
    public enum WorldAnchor { FOLLOW_VIEW, WORLD_AT_SPAWN, FIXED_POSITION }

    // ---- General ----
    public boolean enabled = true;
    public DisplayMode mode = DisplayMode.HUD;
    public int visibleLines = 3;
    public Transition transition = Transition.SLIDE;
    public int transitionMs = 300;
    public float animSpeed = 1.0f;
    public int lyricsOffsetMs = 0;
    public boolean showTitleArtist = false;

    // ---- Text ----
    public String textColor = "#FFFFFF";
    public FontStyle fontStyle = FontStyle.NORMAL;
    public float thickness = 0f;
    public float scale = 1.5f;
    public float textOpacity = 1f;
    public Align align = Align.CENTER;
    public int fadeInMs = 250;
    public int fadeOutMs = 400;

    // ---- Glow / shadow / outline ----
    public boolean glowEnabled = true;
    public String glowColor = "#7FD7FF";
    public float glowIntensity = 0.6f;
    public float glowRadius = 2.0f;
    public boolean shadowEnabled = true;
    public String shadowColor = "#000000";
    public float shadowOpacity = 0.6f;
    public float shadowOffset = 1f;
    public boolean outlineEnabled = false;
    public String outlineColor = "#000000";
    public float outlineWidth = 1f;

    // ---- Words / background ----
    public WordEffect wordEffect = WordEffect.HIGHLIGHT;
    public String highlightColor = "#FFE066";
    public float unsungOpacity = 0.55f;
    public float dimOpacity = 0.45f;
    public boolean bgEnabled = false;
    public String bgColor = "#000000";
    public float bgOpacity = 0.35f;
    public int bgPadding = 4;

    // ---- HUD placement ----
    public HudAnchor hudAnchor = HudAnchor.BOTTOM_CENTER;
    public int hudOffsetX = 0;
    public int hudOffsetY = -60;
    public int lineSpacing = 4;

    // ---- Floating world lyrics ----
    public WorldAnchor worldAnchor = WorldAnchor.FOLLOW_VIEW;
    public FloatDirection floatDirection = FloatDirection.UP;
    public float floatSpeed = 0.35f;        // blocks per second
    public float worldDistance = 3.0f;      // blocks in front of the camera
    public float worldScale = 0.45f;        // blocks tall per text line
    public float worldVerticalOffset = 0.35f;
    public int worldFadeInMs = 400;
    public int worldFadeOutMs = 1400;
    public float worldOpacity = 0.9f;
    public boolean worldAvoidCrosshair = true;
    public int worldMaxLines = 4;
    public float worldDriftAmount = 0.15f;
    public double worldFixedX = 0, worldFixedY = 70, worldFixedZ = 0;

    // ---- Spotify ----
    public String spotifyClientId = "";
    public int spotifyPort = 8888;
    public int pollIntervalMs = 1000;
    public boolean showStatusMessages = true;

    // ------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("lyricsflow26.json");
    }

    public static LyricsConfig load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                try (Reader r = Files.newBufferedReader(p)) {
                    LyricsConfig c = GSON.fromJson(r, LyricsConfig.class);
                    if (c != null) { c.sanitize(); return c; }
                }
            }
        } catch (Exception e) {
            LyricsFlowMod.LOG.warn("Could not read config, using defaults", e);
        }
        LyricsConfig c = new LyricsConfig();
        c.save();
        return c;
    }

    public synchronized void save() {
        sanitize();
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            LyricsFlowMod.LOG.warn("Could not save config", e);
        }
    }

    /** Replaces unknown enum values / garbage with sane defaults. */
    public void sanitize() {
        if (mode == null) mode = DisplayMode.HUD;
        if (align == null) align = Align.CENTER;
        if (hudAnchor == null) hudAnchor = HudAnchor.BOTTOM_CENTER;
        if (fontStyle == null) fontStyle = FontStyle.NORMAL;
        if (transition == null) transition = Transition.SLIDE;
        if (wordEffect == null) wordEffect = WordEffect.HIGHLIGHT;
        if (floatDirection == null) floatDirection = FloatDirection.UP;
        if (worldAnchor == null) worldAnchor = WorldAnchor.FOLLOW_VIEW;
        if (spotifyClientId == null) spotifyClientId = "";
        textColor = fixColor(textColor, "#FFFFFF");
        glowColor = fixColor(glowColor, "#7FD7FF");
        shadowColor = fixColor(shadowColor, "#000000");
        outlineColor = fixColor(outlineColor, "#000000");
        highlightColor = fixColor(highlightColor, "#FFE066");
        bgColor = fixColor(bgColor, "#000000");

        visibleLines = cl(visibleLines, 1, 7);
        transitionMs = cl(transitionMs, 50, 2000);
        animSpeed = cl(animSpeed, 0.25f, 4f);
        lyricsOffsetMs = cl(lyricsOffsetMs, -5000, 5000);
        thickness = cl(thickness, 0f, 2f);
        scale = cl(scale, 0.5f, 5f);
        textOpacity = cl(textOpacity, 0.05f, 1f);
        fadeInMs = cl(fadeInMs, 0, 3000);
        fadeOutMs = cl(fadeOutMs, 0, 3000);
        glowIntensity = cl(glowIntensity, 0f, 1.5f);
        glowRadius = cl(glowRadius, 0f, 6f);
        shadowOpacity = cl(shadowOpacity, 0f, 1f);
        shadowOffset = cl(shadowOffset, 0f, 4f);
        outlineWidth = cl(outlineWidth, 0.5f, 3f);
        unsungOpacity = cl(unsungOpacity, 0f, 1f);
        dimOpacity = cl(dimOpacity, 0f, 1f);
        bgOpacity = cl(bgOpacity, 0f, 1f);
        bgPadding = cl(bgPadding, 0, 20);
        hudOffsetX = cl(hudOffsetX, -2000, 2000);
        hudOffsetY = cl(hudOffsetY, -2000, 2000);
        lineSpacing = cl(lineSpacing, 0, 30);
        floatSpeed = cl(floatSpeed, 0f, 3f);
        worldDistance = cl(worldDistance, 1f, 12f);
        worldScale = cl(worldScale, 0.1f, 2f);
        worldVerticalOffset = cl(worldVerticalOffset, -3f, 3f);
        worldFadeInMs = cl(worldFadeInMs, 0, 4000);
        worldFadeOutMs = cl(worldFadeOutMs, 100, 6000);
        worldOpacity = cl(worldOpacity, 0.05f, 1f);
        worldMaxLines = cl(worldMaxLines, 1, 8);
        worldDriftAmount = cl(worldDriftAmount, 0f, 1f);
        spotifyPort = cl(spotifyPort, 1024, 65535);
        pollIntervalMs = cl(pollIntervalMs, 500, 5000);
    }

    // ---- helpers ----

    private static int cl(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static float cl(float v, float lo, float hi) {
        if (Float.isNaN(v)) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    private static String fixColor(String s, String def) { return tryParseColor(s) >= 0 ? s.trim() : def; }

    /** Parses "#RRGGBB", "RRGGBB", "#RGB", "r,g,b" or "rgb(r,g,b)". Returns 0xRRGGBB, or -1 if invalid. */
    public static int tryParseColor(String s) {
        if (s == null) return -1;
        s = s.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.startsWith("rgb(") && s.endsWith(")")) s = s.substring(4, s.length() - 1);
            if (s.contains(",")) {
                String[] p = s.trim().split("[,\\s]+");
                if (p.length != 3) return -1;
                int r = Integer.parseInt(p[0]), g = Integer.parseInt(p[1]), b = Integer.parseInt(p[2]);
                if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) return -1;
                return (r << 16) | (g << 8) | b;
            }
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 3) {
                s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
            }
            if (s.length() != 6) return -1;
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** RGB int (no alpha) for a color string, falling back to {@code def}. */
    public int rgb(String s, int def) {
        int v = tryParseColor(s);
        return v < 0 ? def : v;
    }
}
