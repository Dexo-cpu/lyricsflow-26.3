package com.lyricsflow26.render;

import com.lyricsflow26.config.LyricsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;

/** Draws colored text segments with shadow, glow, outline and extra thickness. */
public final class TextPainter {
    public record Seg(String text, int rgb, float alpha) {}

    private static final float[] COS = new float[8], SIN = new float[8];
    static {
        for (int i = 0; i < 8; i++) {
            COS[i] = (float) Math.cos(i * Math.PI / 4);
            SIN[i] = (float) Math.sin(i * Math.PI / 4);
        }
    }

    private final Gfx g;
    private final LyricsConfig c;
    private final Style style;

    public TextPainter(Gfx g, LyricsConfig c) {
        this.g = g;
        this.c = c;
        boolean bold = c.fontStyle == LyricsConfig.FontStyle.BOLD || c.fontStyle == LyricsConfig.FontStyle.BOLD_ITALIC;
        boolean italic = c.fontStyle == LyricsConfig.FontStyle.ITALIC || c.fontStyle == LyricsConfig.FontStyle.BOLD_ITALIC;
        this.style = Style.EMPTY.withBold(bold).withItalic(italic);
    }

    public Component comp(String s) { return Component.literal(s).withStyle(style); }

    public float width(String s) { return g.width(comp(s)); }

    public float width(List<Seg> segs) {
        float w = 0;
        for (Seg s : segs) w += g.width(comp(s.text()));
        return w;
    }

    /** Draws segments left to right starting at (x, y) in the current pose space. */
    public void draw(List<Seg> segs, float x, float y, float alphaMul) {
        float cx = x;
        for (Seg s : segs) {
            Component comp = comp(s.text());
            float w = g.width(comp);
            float a = s.alpha() * alphaMul;
            if (a >= 0.02f) drawSeg(comp, cx, y, s.rgb(), a);
            cx += w;
        }
    }

    private void drawSeg(Component comp, float x, float y, int rgb, float a) {
        if (c.shadowEnabled && c.shadowOpacity > 0.01f) {
            layer(comp, x + c.shadowOffset, y + c.shadowOffset, c.rgb(c.shadowColor, 0), a * c.shadowOpacity);
        }
        if (c.glowEnabled && c.glowIntensity > 0.01f && c.glowRadius > 0.05f) {
            int gc = c.rgb(c.glowColor, 0x7FD7FF);
            float ga = Math.min(1f, a * c.glowIntensity * 0.16f);
            for (int ring = 1; ring <= 2; ring++) {
                float r = c.glowRadius * ring * 0.5f;
                for (int k = 0; k < 8; k++) layer(comp, x + COS[k] * r, y + SIN[k] * r, gc, ga);
            }
        }
        if (c.outlineEnabled) {
            int oc = c.rgb(c.outlineColor, 0);
            for (int k = 0; k < 8; k++) layer(comp, x + COS[k] * c.outlineWidth, y + SIN[k] * c.outlineWidth, oc, a);
        }
        layer(comp, x, y, rgb, a);
        if (c.thickness > 0.01f) { // fake heavier strokes by re-drawing a fraction of a pixel to the right
            layer(comp, x + c.thickness * 0.5f, y, rgb, a);
            layer(comp, x + c.thickness, y, rgb, a);
        }
    }

    private void layer(Component comp, float x, float y, int rgb, float a) {
        int ai = Math.round(Math.min(1f, a) * 255f);
        if (ai < 6) return; // vanilla treats very low alpha as opaque, so skip instead
        g.text(comp, x, y, (ai << 24) | (rgb & 0xFFFFFF));
    }
}
