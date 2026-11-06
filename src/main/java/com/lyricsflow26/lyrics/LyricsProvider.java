package com.lyricsflow26.lyrics;

import com.lyricsflow26.core.Track;

import java.io.IOException;
import java.util.Optional;

/** A source of time-synchronized lyrics in (enhanced) LRC format. Implementations run off the render thread. */
public interface LyricsProvider {
    /** @return synced LRC text, or empty if this provider has none. Throws on network/server errors. */
    Optional<String> fetchSyncedLrc(Track track) throws IOException;
}
