package com.lyricsflow26.render;

import com.lyricsflow26.config.LyricsConfig;
import com.lyricsflow26.core.Snapshot;
import com.lyricsflow26.core.Status;
import com.lyricsflow26.lyrics.Lyrics;

import java.util.List;

/** Classic 2D lyrics overlay: stacked lines, smooth scrolling, fades, word highlighting. Stateful (animation). */
public final class HudLyricsRenderer {
    private final LyricsConfig c;
    private final boolean preview;
    private double scroll, from;
    private long t0;
    private int lastIdx = -2;
    private String lastTrack = "";
    private boolean wasActive;
    private long activeSince, inactiveSince;

    /** @param preview true inside the settings screen, where HUD pixel offsets are scaled to fit the small panel. */
    public HudLyricsRenderer(LyricsConfig c, boolean preview) {
        this.c = c;
        this.preview = preview;
    }

    /** Renders inside the viewport (vx, vy, vw, vh) in GUI pixels. */
    public void render(Gfx g, Snapshot s, float vx, float vy, float vw, float vh, long now) {
        TextPainter tp = new TextPainter(g, c);
        float speed = Math.max(0.1f, c.animSpeed);
        Lyrics ly = s.lyrics();

        if (!s.trackKey().equals(lastTrack)) {
            lastTrack = s.trackKey();
            lastIdx = -2;
            wasActive = false;
            inactiveSince = now - 100_000;
        }
        long pos = s.positionMs();
        int cur = ly == null ? -1 : ly.indexAt(pos);
        boolean active = ly != null && cur >= 0 && !ly.lines.get(cur).blank();
        if (active && !wasActive) activeSince = now;
        if (!active && wasActive) inactiveSince = now;
        wasActive = active;

        // scroll animation
        if (cur != lastIdx) {
            if (cur >= 0) {
                if (lastIdx < 0 || Math.abs(cur - scroll) > 3) { from = cur; scroll = cur; }
                else from = scroll;
                t0 = now;
            }
            lastIdx = cur;
        }
        if (cur >= 0) {
            if (c.transition == LyricsConfig.Transition.SLIDE) {
                float p = clamp01((now - t0) / Math.max(1f, c.transitionMs / speed));
                scroll = from + (cur - from) * ease(p);
            } else {
                scroll = cur;
            }
        }

        float blockAlpha;
        if (active) {
            blockAlpha = c.transition == LyricsConfig.Transition.NONE ? 1f
                    : clamp01((now - activeSince) / Math.max(1f, c.fadeInMs / speed));
        } else {
            blockAlpha = c.transition == LyricsConfig.Transition.NONE ? 0f
                    : 1f - clamp01((now - inactiveSince) / Math.max(1f, c.fadeOutMs / speed));
        }

        float lh = g.lineHeight();
        int before = c.visibleLines / 3;
        int after = c.visibleLines - 1 - before;
        float baseScale = c.scale;
        float step = lh * baseScale + c.lineSpacing;
        float bh = (before + after + 1) * step - c.lineSpacing;
        float maxW = vw * 0.92f;

        // block width from the nominal window, so it stays stable while scrolling
        float bw = 1;
        if (ly != null && cur >= 0) {
            int center = (int) Math.round(scroll);
            for (int k = -before; k <= after; k++) {
                int i = center + k;
                if (i < 0 || i >= ly.lines.size()) continue;
                bw = Math.max(bw, Math.min(baseScale, maxW / Math.max(1, tp.width(ly.lines.get(i).text()))) * tp.width(ly.lines.get(i).text()));
            }
        }
        float titleScale = Math.max(0.6f, baseScale * 0.55f);
        String titleText = c.showTitleArtist && !s.title().isEmpty()
                ? (s.artist().isEmpty() ? s.title() : s.artist() + " \u2013 " + s.title()) : "";
        float titleW = titleText.isEmpty() ? 0 : tp.width(titleText) * titleScale;
        bw = Math.max(bw, Math.min(titleW, maxW));

        float[] o = origin(bw, bh, vx, vy, vw, vh);
        float bx = o[0], by = o[1];

        // title / artist
        if (!titleText.isEmpty()) {
            float ts = titleScale * Math.min(1f, maxW / Math.max(1, titleW));
            float tw = tp.width(titleText) * ts;
            float th = lh * ts;
            float ty = c.hudAnchor.row() == 0 ? by + bh + 4 : by - th - 4;
            float tx = alignX(bx, bw, tw);
            g.push(tx, ty, ts);
            tp.draw(List.of(new TextPainter.Seg(titleText, 0xCCCCCC, 1f)), 0, 0, c.textOpacity * (s.playing() ? 0.8f : 0.45f));
            g.pop();
        }

        if (ly != null && cur >= 0 && blockAlpha > 0.01f) {
            if (c.bgEnabled) {
                int bgA = Math.round(clamp01(c.bgOpacity * blockAlpha) * 255f);
                if (bgA > 5) {
                    float p = c.bgPadding;
                    g.fill(bx - p, by - p, bx + bw + p, by + bh + p, (bgA << 24) | c.rgb(c.bgColor, 0));
                }
            }
            int lo = (int) Math.floor(scroll) - before - 1;
            int hi = (int) Math.ceil(scroll) + after + 1;
            for (int i = Math.max(0, lo); i <= Math.min(ly.lines.size() - 1, hi); i++) {
                Lyrics.Line line = ly.lines.get(i);
                if (line.blank()) continue;
                float d = (float) (i - scroll);
                float ad = Math.abs(d);
                int span = d < 0 ? before : after;
                float la = lerp(1f, c.dimOpacity, Math.min(ad, 1f)) * clamp01(span + 1 - ad);
                float y = by + (d + before) * step;

                if (c.transition == LyricsConfig.Transition.FADE) {
                    float since = pos - ly.lines.get(cur).startMs();
                    if (i == cur) {
                        la *= clamp01(since / Math.max(1f, c.fadeInMs / speed));
                    } else if (i == cur - 1 && before == 0) { // cross-fade the outgoing line in place
                        y = by;
                        la = 1f - clamp01(since / Math.max(1f, c.fadeOutMs / speed));
                    }
                }
                if (la < 0.02f) continue;

                float w1 = tp.width(line.text());
                float sc = Math.min(baseScale, maxW / Math.max(1f, w1));
                float lw = w1 * sc;
                float lx = alignX(bx, bw, lw);
                List<TextPainter.Seg> segs = (i == cur && c.wordEffect != LyricsConfig.WordEffect.OFF && line.words() != null)
                        ? WordSegments.build(line, pos, c) : WordSegments.whole(line.text(), c);
                g.push(lx, y, sc);
                tp.draw(segs, 0, 0, la * blockAlpha * c.textOpacity);
                g.pop();
            }
        }

        // short status hint (e.g. "No synced lyrics found") — never fake lyrics
        if (c.showStatusMessages && (ly == null) && s.status() != Status.READY && s.status() != Status.NOT_PLAYING
                && s.status() != Status.LOADING && !s.statusText().isEmpty() && s.statusAgeMs() < 8000) {
            float ss = 1f;
            float sw = tp.width(s.statusText()) * ss;
            float[] so = origin(sw, lh, vx, vy, vw, vh);
            g.push(so[0], so[1], ss);
            tp.draw(List.of(new TextPainter.Seg(s.statusText(), 0xFFAAAA, 1f)), 0, 0, 0.9f);
            g.pop();
        }
    }

    private float[] origin(float bw, float bh, float vx, float vy, float vw, float vh) {
        float m = 10;
        float bx = switch (c.hudAnchor.col()) {
            case 0 -> vx + m;
            case 1 -> vx + (vw - bw) / 2f;
            default -> vx + vw - bw - m;
        };
        float by = switch (c.hudAnchor.row()) {
            case 0 -> vy + m;
            case 1 -> vy + (vh - bh) / 2f;
            default -> vy + vh - bh - m;
        };
        // offsets apply to the real HUD; in the small preview they are scaled down so the panel stays readable
        float k = preview ? Math.min(1f, vw / 427f) : 1f;
        return new float[]{bx + c.hudOffsetX * k, by + c.hudOffsetY * k};
    }

    private float alignX(float bx, float bw, float w) {
        return switch (c.align) {
            case LEFT -> bx;
            case CENTER -> bx + (bw - w) / 2f;
            case RIGHT -> bx + bw - w;
        };
    }

    static float clamp01(float v) { return v < 0 ? 0 : Math.min(v, 1f); }
    static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    static float ease(float t) { float u = 1 - t; return 1 - u * u * u; }
}
