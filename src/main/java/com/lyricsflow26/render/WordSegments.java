package com.lyricsflow26.render;

import com.lyricsflow26.config.LyricsConfig;
import com.lyricsflow26.lyrics.Lyrics;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds colored segments for word-by-word highlighting from REAL word timestamps only.
 * Lines without word timing never reach this class and are drawn whole.
 */
final class WordSegments {
    static List<TextPainter.Seg> whole(String text, LyricsConfig c) {
        return List.of(new TextPainter.Seg(text, c.rgb(c.textColor, 0xFFFFFF), 1f));
    }

    static List<TextPainter.Seg> build(Lyrics.Line line, long pos, LyricsConfig c) {
        boolean reveal = c.wordEffect == LyricsConfig.WordEffect.REVEAL;
        int base = c.rgb(c.textColor, 0xFFFFFF);
        int sungRgb = reveal ? base : c.rgb(c.highlightColor, 0xFFE066);
        float unsungAlpha = reveal ? 0f : c.unsungOpacity;

        List<TextPainter.Seg> out = new ArrayList<>();
        for (Lyrics.Word w : line.words()) {
            String t = w.text();
            if (pos >= w.endMs()) {
                add(out, t, sungRgb, 1f);
            } else if (pos < w.startMs()) {
                add(out, t, base, unsungAlpha);
            } else {
                float frac = (float) (pos - w.startMs()) / Math.max(1, w.endMs() - w.startMs());
                int k = Math.max(0, Math.min(t.length(), (int) (frac * t.length())));
                add(out, t.substring(0, k), sungRgb, 1f);
                add(out, t.substring(k), base, unsungAlpha);
            }
        }
        return out;
    }

    private static void add(List<TextPainter.Seg> out, String text, int rgb, float alpha) {
        if (text.isEmpty()) return;
        if (!out.isEmpty()) {
            TextPainter.Seg p = out.get(out.size() - 1);
            if (p.rgb() == rgb && p.alpha() == alpha) { // merge neighbours to keep draw calls low
                out.set(out.size() - 1, new TextPainter.Seg(p.text() + text, rgb, alpha));
                return;
            }
        }
        out.add(new TextPainter.Seg(text, rgb, alpha));
    }

    private WordSegments() {}
}
