package com.lyricsflow26.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses LRC and enhanced LRC (per-word {@code <mm:ss.xx>} tags). */
public final class LrcParser {
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD = Pattern.compile("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>");

    private record RawWord(long start, String text) {}
    private record Raw(long start, String text, List<RawWord> words) {}

    private static long ms(Matcher m) {
        long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2)), frac = 0;
        String f = m.group(3);
        if (f != null) {
            frac = Long.parseLong(f);
            if (f.length() == 1) frac *= 100; else if (f.length() == 2) frac *= 10;
        }
        return (min * 60 + sec) * 1000 + frac;
    }

    /** @return parsed lyrics, or null if the text contains no timestamped lines. */
    public static Lyrics parse(String lrc) {
        if (lrc == null || lrc.isBlank()) return null;
        List<Raw> raws = new ArrayList<>();
        for (String ln : lrc.split("\\R")) {
            Matcher m = STAMP.matcher(ln);
            int pos = 0;
            List<Long> stamps = new ArrayList<>();
            while (m.find(pos) && m.start() == pos) { stamps.add(ms(m)); pos = m.end(); }
            if (stamps.isEmpty()) continue; // metadata such as [ar:...] or junk
            String body = ln.substring(pos);

            Matcher w = WORD.matcher(body);
            List<RawWord> words = null;
            if (w.find()) {
                words = new ArrayList<>();
                int last = 0;
                long curStart = stamps.get(0);
                w.reset();
                while (w.find()) {
                    addWord(words, curStart, body.substring(last, w.start()));
                    curStart = ms(w);
                    last = w.end();
                }
                addWord(words, curStart, body.substring(last));
                if (words.isEmpty()) words = null;
            }
            if (words != null) {
                RawWord first = words.get(0);
                words.set(0, new RawWord(first.start(), first.text().stripLeading()));
                RawWord lastW = words.get(words.size() - 1);
                words.set(words.size() - 1, new RawWord(lastW.start(), lastW.text().stripTrailing()));
                StringBuilder sb = new StringBuilder();
                for (RawWord rw : words) sb.append(rw.text());
                // word tags are absolute song times, so only the first stamp can own them
                raws.add(new Raw(stamps.get(0), sb.toString(), words));
            } else {
                String text = body.strip();
                for (long st : stamps) raws.add(new Raw(st, text, null));
            }
        }
        if (raws.isEmpty()) return null;
        raws.sort(Comparator.comparingLong(Raw::start));

        List<Lyrics.Line> out = new ArrayList<>();
        boolean wordSync = false;
        for (int i = 0; i < raws.size(); i++) {
            Raw r = raws.get(i);
            long end = i + 1 < raws.size() ? raws.get(i + 1).start() : r.start() + 6000;
            if (end <= r.start()) end = r.start() + 1;
            List<Lyrics.Word> words = null;
            if (r.words() != null) {
                words = new ArrayList<>();
                List<RawWord> rw = r.words();
                for (int j = 0; j < rw.size(); j++) {
                    long ws = rw.get(j).start();
                    long we = j + 1 < rw.size() ? rw.get(j + 1).start() : Math.min(end, ws + 1500);
                    if (we <= ws) we = ws + 1;
                    words.add(new Lyrics.Word(ws, we, rw.get(j).text()));
                }
                wordSync = true;
            }
            out.add(new Lyrics.Line(r.start(), end, r.text(), words == null ? null : List.copyOf(words)));
        }
        return new Lyrics(List.copyOf(out), wordSync);
    }

    private static void addWord(List<RawWord> words, long start, String seg) {
        if (seg.isEmpty()) return;
        if (seg.isBlank() && !words.isEmpty()) { // whitespace-only: glue to previous word
            RawWord p = words.remove(words.size() - 1);
            words.add(new RawWord(p.start(), p.text() + seg));
            return;
        }
        words.add(new RawWord(start, seg));
    }
}
