package com.lyricsflow26.core;

/** One Spotify playback reading. {@code receivedNanos} is when (System.nanoTime) progressMs was valid. */
public record Sample(Track track, boolean playing, long progressMs, long receivedNanos) {}
