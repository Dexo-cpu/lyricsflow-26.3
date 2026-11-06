package com.lyricsflow26.core;

import com.lyricsflow26.lyrics.Lyrics;

/** Immutable per-frame view of what the renderers need. {@code lyrics} is null unless real synced lyrics exist. */
public record Snapshot(String trackKey, String title, String artist, boolean playing, long positionMs,
                       Lyrics lyrics, Status status, String statusText, long statusAgeMs) {}
