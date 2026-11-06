package com.lyricsflow26.lyrics;

import java.util.List;

/** Parsed synchronized lyrics. Word timing exists only if the provider supplied it. */
public final class Lyrics {
    public record Word(long startMs, long endMs, String text) {}

    public record Line(long startMs, long endMs, String text, List<Word> words) {
        public boolean blank() { return text.isBlank(); }
    }

    public final List<Line> lines;
    public final boolean wordSync;

    public Lyrics(List<Line> lines, boolean wordSync) {
        this.lines = lines;
        this.wordSync = wordSync;
    }

    /** Index of the line active at {@code posMs}, or -1 before the first line / after the last one has ended. */
    public int indexAt(long posMs) {
        int lo = 0, hi = lines.size() - 1, res = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).startMs() <= posMs) { res = mid; lo = mid + 1; } else hi = mid - 1;
        }
        if (res >= 0 && posMs >= lines.get(res).endMs()) return -1;
        return res;
    }
}
