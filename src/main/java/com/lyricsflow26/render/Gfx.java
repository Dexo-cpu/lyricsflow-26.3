package com.lyricsflow26.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The ONLY class in the mod that calls Minecraft's 2D draw API. If a 26.x snapshot renames a method
 * (text/fill/pose), fix it here and nowhere else.
 */
public final class Gfx {
    private final GuiGraphicsExtractor g;
    private final Font font = Minecraft.getInstance().font;

    public Gfx(GuiGraphicsExtractor g) { this.g = g; }

    public int width(Component c) { return font.width(c); }

    public int lineHeight() { return font.lineHeight; }

    public void fill(float x1, float y1, float x2, float y2, int argb) {
        g.fill(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), argb);
    }

    /** Draws text with its top-left at (x, y) in the current pose space, at sub-pixel precision, without vanilla shadow. */
    public void text(Component c, float x, float y, int argb) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        g.text(font, c, 0, 0, argb, false);
        pose.popMatrix();
    }

    /** Pushes a translate+uniform-scale transform. Must be paired with {@link #pop()}. */
    public void push(float x, float y, float scale) {
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
    }

    public void pop() { g.pose().popMatrix(); }
}
