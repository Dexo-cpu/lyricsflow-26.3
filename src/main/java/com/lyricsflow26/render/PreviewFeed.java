package com.lyricsflow26.render;

import com.lyricsflow26.core.Snapshot;
import com.lyricsflow26.core.Status;
import com.lyricsflow26.lyrics.Lyrics;

import java.util.ArrayList;
import java.util.List;

/** Sample text used ONLY inside the settings preview panel (clearly labelled there). Never shown in-game. */
public final class PreviewFeed {
    private static final String[] TEXT = {
            "Your lyrics will glow right here",
            "Floating through the world",
            "Word by word in perfect sync",
            "Customize every little detail"
    };
    private static final long LINE_MS = 3400;
    private static final Lyrics LYRICS = build();

    private static Lyrics build() {
        List<Lyrics.Line> lines = new ArrayList<>();
        for (int i = 0; i < TEXT.length; i++) {
            long start = i * LINE_MS;
            String[] words = TEXT[i].split(" ");
            long per = (LINE_MS - 500) / words.length;
            List<Lyrics.Word> ws = new ArrayList<>();
            for (int j = 0; j < words.length; j++) {
                ws.add(new Lyrics.Word(start + j * per, start + (j + 1) * per, j < words.length - 1 ? words[j] + " " : words[j]));
            }
            lines.add(new Lyrics.Line(start, start + LINE_MS, TEXT[i], ws));
        }
        return new Lyrics(lines, true);
    }

    public static Snapshot snapshot(long nowMs) {
        long total = TEXT.length * LINE_MS + 1200;
        long pos = nowMs % total;
        return new Snapshot("preview-" + (nowMs / total), "Sample Song", "Sample Artist", true, pos, LYRICS, Status.READY, "", 99999);
    }

    private PreviewFeed() {}
}
