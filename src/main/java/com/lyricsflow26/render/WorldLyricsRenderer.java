package com.lyricsflow26.render;

import com.lyricsflow26.config.LyricsConfig;
import com.lyricsflow26.core.Snapshot;
import com.lyricsflow26.lyrics.Lyrics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Floating "hologram" lyrics. Every lyric line is spawned as a particle with a real 3D position, floats
 * up / down / drifts, fades out, and is removed. Positions are projected through the real game camera and drawn
 * as camera-facing billboards, so they are readable from any angle in first and third person.
 *
 * Billboards are drawn in the HUD pass: they never collide with, block or intercept anything in the world.
 */
public final class WorldLyricsRenderer {
    private static final class F {
        Lyrics.Line line;
        float age, fadeStart, fadeDur, base, seed;
        boolean forced;
        double ax, ay, az;
    }

    private final LyricsConfig c;
    private final boolean preview;
    private final List<F> list = new ArrayList<>();
    private final Random rnd = new Random();
    private final float[] out = new float[3];
    private int lastIdx = -2;
    private String lastTrack = "";
    private long lastNow = -1;

    /** @param preview true inside the settings screen: fixed-position anchoring falls back to "world at spawn". */
    public WorldLyricsRenderer(LyricsConfig c, boolean preview) {
        this.c = c;
        this.preview = preview;
    }

    public void render(Gfx g, Snapshot s, Projector p, long now) {
        float dt = lastNow < 0 ? 0 : Math.min(100, now - lastNow);
        lastNow = now;
        float speed = Math.max(0.1f, c.animSpeed);
        Lyrics ly = s.lyrics();

        // --- spawn logic, driven by the real playback position ---
        if (!s.trackKey().equals(lastTrack) || ly == null) {
            forceAll();
            lastIdx = -2;
            lastTrack = s.trackKey();
        }
        if (ly != null) {
            int cur = ly.indexAt(s.positionMs());
            if (cur != lastIdx) {
                if (lastIdx >= 0 && Math.abs(cur - lastIdx) > 1) forceAll(); // user seeked
                lastIdx = cur;
                if (cur >= 0 && !ly.lines.get(cur).blank()) spawn(ly.lines.get(cur), s, p, speed);
            }
        }

        // --- update + draw (oldest first) ---
        TextPainter tp = new TextPainter(g, c);
        float lh = g.lineHeight();
        int dir = c.floatDirection == LyricsConfig.FloatDirection.DOWN ? -1 : 1;
        for (Iterator<F> it = list.iterator(); it.hasNext(); ) {
            F f = it.next();
            if (s.playing() || f.forced) f.age += dt; // holograms freeze while the music is paused
            if (f.age >= f.fadeStart + f.fadeDur) { it.remove(); continue; }

            float in = smooth(c.worldFadeInMs <= 0 ? 1f : f.age / Math.max(1f, c.worldFadeInMs / speed));
            float outA = f.age <= f.fadeStart ? 1f : 1f - smooth((f.age - f.fadeStart) / Math.max(1f, f.fadeDur));
            float alpha = in * outA * c.worldOpacity;
            if (alpha < 0.02f) continue;

            // world position
            double sec = f.age / 1000.0;
            double off = offset(f, sec, dir);
            double sway = c.floatDirection == LyricsConfig.FloatDirection.DRIFT
                    ? Math.sin(sec * 0.6 + f.seed * 1.7) * c.worldDriftAmount * 1.5 : 0;
            LyricsConfig.WorldAnchor mode = c.worldAnchor;
            if (preview && mode == LyricsConfig.WorldAnchor.FIXED_POSITION) mode = LyricsConfig.WorldAnchor.WORLD_AT_SPAWN;
            double wx, wy, wz;
            if (mode == LyricsConfig.WorldAnchor.FOLLOW_VIEW) {
                double up = c.worldVerticalOffset + off;
                wx = p.cx + p.fx * c.worldDistance + p.ux * up + p.rx * sway;
                wy = p.cy + p.fy * c.worldDistance + p.uy * up + p.ry * sway;
                wz = p.cz + p.fz * c.worldDistance + p.uz * up + p.rz * sway;
            } else {
                wx = f.ax + p.rx * sway;
                wy = f.ay + off;
                wz = f.az + p.rz * sway;
            }
            if (!p.project(wx, wy, wz, out)) continue;

            float sc = out[2] * c.worldScale / lh * (0.92f + 0.08f * in); // gentle pop-in
            sc = Math.max(0.2f, Math.min(sc, 8f));

            List<TextPainter.Seg> segs = (c.wordEffect != LyricsConfig.WordEffect.OFF && f.line.words() != null)
                    ? WordSegments.build(f.line, s.positionMs(), c) : WordSegments.whole(f.line.text(), c);
            float lw = tp.width(segs) * sc;
            float th = lh * sc;
            float x = out[0] - lw / 2f, y = out[1] - th / 2f;

            if (c.worldAvoidCrosshair) { // never sit on the crosshair
                float cx = p.vx + p.vw / 2f, cy = p.vy + p.vh / 2f;
                float ex = Math.max(0, Math.abs(out[0] - cx) - lw / 2f);
                float ey = Math.max(0, Math.abs(out[1] - cy) - th / 2f);
                float r = (float) Math.hypot(ex, ey) / (p.vh * 0.10f);
                alpha *= 0.25f + 0.75f * smooth(Math.min(1f, r));
            }

            if (c.bgEnabled) {
                int a = Math.round(Math.min(1f, c.bgOpacity * alpha) * 255f);
                if (a > 5) {
                    float pad = c.bgPadding * sc * 0.5f;
                    g.fill(x - pad, y - pad, x + lw + pad, y + th + pad, (a << 24) | c.rgb(c.bgColor, 0));
                }
            }
            g.push(x, y, sc);
            tp.draw(segs, 0, 0, alpha * c.textOpacity);
            g.pop();
        }
    }

    private double offset(F f, double sec, int dir) {
        double sp = c.floatSpeed;
        if (c.floatDirection == LyricsConfig.FloatDirection.DRIFT) {
            return f.base + sp * 0.4 * sec + Math.sin(sec * 0.9 + f.seed) * c.worldDriftAmount;
        }
        return f.base + dir * sp * sec;
    }

    private void spawn(Lyrics.Line line, Snapshot s, Projector p, float speed) {
        float remaining = line.endMs() - s.positionMs();
        float hold = Math.max(700f, Math.min(15000f, remaining));
        F f = new F();
        f.line = line;
        f.seed = rnd.nextFloat() * 6.2831f;
        f.fadeStart = hold;
        f.fadeDur = c.worldFadeOutMs / speed;

        int dir = c.floatDirection == LyricsConfig.FloatDirection.DOWN ? -1 : 1;
        float gap = c.worldScale * 1.25f;
        F prev = list.isEmpty() ? null : list.get(list.size() - 1);
        if (prev != null && !prev.forced) {
            double po = offset(prev, prev.age / 1000.0, dir);
            if (dir * po < gap) f.base = (float) (po - dir * gap); // start behind the previous line so they never overlap
        }

        LyricsConfig.WorldAnchor mode = c.worldAnchor;
        if (preview && mode == LyricsConfig.WorldAnchor.FIXED_POSITION) mode = LyricsConfig.WorldAnchor.WORLD_AT_SPAWN;
        if (mode == LyricsConfig.WorldAnchor.FIXED_POSITION) {
            f.ax = c.worldFixedX; f.ay = c.worldFixedY; f.az = c.worldFixedZ;
        } else {
            f.ax = p.cx + p.fx * c.worldDistance;
            f.ay = p.cy + p.fy * c.worldDistance + c.worldVerticalOffset;
            f.az = p.cz + p.fz * c.worldDistance;
        }
        list.add(f);

        int live = 0;
        for (F x : list) if (!x.forced) live++;
        for (F x : list) {
            if (live <= c.worldMaxLines) break;
            if (!x.forced) { force(x); live--; }
        }
    }

    private void forceAll() { for (F f : list) force(f); }

    private void force(F f) {
        if (f.age < f.fadeStart) f.fadeStart = f.age;
        f.fadeDur = Math.min(f.fadeDur, 350f);
        f.forced = true;
    }

    private static float smooth(float t) {
        t = t < 0 ? 0 : Math.min(t, 1f);
        return t * t * (3 - 2 * t);
    }
}
