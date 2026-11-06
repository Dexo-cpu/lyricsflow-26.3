package com.lyricsflow26.core;

import com.lyricsflow26.LyricsFlowMod;
import com.lyricsflow26.config.LyricsConfig;
import com.lyricsflow26.lyrics.LrcLibProvider;
import com.lyricsflow26.lyrics.LrcParser;
import com.lyricsflow26.lyrics.Lyrics;
import com.lyricsflow26.lyrics.LyricsCache;
import com.lyricsflow26.lyrics.LyricsProvider;
import com.lyricsflow26.spotify.SpotifyAuth;
import com.lyricsflow26.spotify.SpotifyPoller;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns playback state and lyrics. All network work happens on background threads; the render thread only calls
 * {@link #snapshot()}, which is a few arithmetic operations.
 */
public final class LyricsService {
    private final LyricsConfig cfg;
    private final SpotifyAuth auth;
    private final SpotifyPoller poller;
    private final LyricsCache cache;
    private final List<LyricsProvider> providers = List.of(new LrcLibProvider());
    private final ScheduledExecutorService fetchExec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lyricsflow-lyrics");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger generation = new AtomicInteger();

    private volatile Sample sample;
    private volatile Lyrics lyrics;
    private volatile Status pollState = Status.NOT_PLAYING;
    private volatile String pollMsg = "";
    private volatile Status lyricState = Status.LOADING;
    private volatile String lyricMsg = "";
    private volatile Status shownStatus = null;
    private volatile long statusSince = System.currentTimeMillis();

    public LyricsService(LyricsConfig cfg) {
        this.cfg = cfg;
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("lyricsflow26");
        this.auth = new SpotifyAuth(cfg, dir);
        this.cache = new LyricsCache(dir.resolve("cache"));
        this.poller = new SpotifyPoller(cfg, auth, this);
    }

    public SpotifyAuth auth() { return auth; }
    public void start() { poller.start(); }
    public void stop() { poller.stop(); fetchExec.shutdownNow(); }

    // ---- called from the poller thread ----

    public synchronized void onPlayback(Sample n) {
        Sample prev = sample;
        boolean newTrack = prev == null || !prev.track().key().equals(n.track().key());
        if (!newTrack && prev.playing() && n.playing()) {
            // Blend small drift instead of snapping, so the lyric timing never visibly jitters.
            double predicted = prev.progressMs() + (n.receivedNanos() - prev.receivedNanos()) / 1e6;
            double err = n.progressMs() - predicted;
            if (Math.abs(err) < 500) {
                n = new Sample(n.track(), true, Math.round(predicted + err * 0.4), n.receivedNanos());
            }
        }
        sample = n;
        pollState = Status.READY;
        pollMsg = "";
        if (newTrack) startFetch(n.track());
        touchStatus();
    }

    public synchronized void onNothing(Status st, String msg) {
        pollState = st;
        pollMsg = msg;
        if (st == Status.NOT_PLAYING || st == Status.UNSUPPORTED) {
            sample = null;
            lyrics = null;
            generation.incrementAndGet(); // drop any in-flight lyric fetch
        }
        touchStatus();
    }

    // ---- lyric retrieval ----

    private void startFetch(Track track) {
        lyrics = null;
        lyricState = Status.LOADING;
        lyricMsg = "Loading lyrics...";
        int gen = generation.incrementAndGet();
        fetchExec.execute(() -> fetch(track, gen, 0));
    }

    private void fetch(Track track, int gen, int attempt) {
        try {
            LyricsCache.Entry e = cache.get(track.key());
            String lrc;
            if (e != null) {
                lrc = e.lrc();
            } else {
                lrc = null;
                for (LyricsProvider p : providers) {
                    Optional<String> o = p.fetchSyncedLrc(track);
                    if (o.isPresent()) { lrc = o.get(); break; }
                }
                cache.put(track.key(), lrc); // only reached when every provider answered (no exception)
            }
            if (gen != generation.get()) return;
            Lyrics parsed = lrc == null ? null : LrcParser.parse(lrc);
            synchronized (this) {
                if (gen != generation.get()) return;
                if (parsed == null) {
                    lyrics = null;
                    lyricState = Status.NO_SYNCED;
                    lyricMsg = "No synced lyrics found for this song";
                } else {
                    lyrics = parsed;
                    lyricState = Status.READY;
                    lyricMsg = "";
                }
                touchStatus();
            }
        } catch (Exception ex) {
            if (gen != generation.get()) return;
            synchronized (this) {
                lyricState = Status.ERROR;
                lyricMsg = "Lyrics provider unreachable, retrying...";
                touchStatus();
            }
            LyricsFlowMod.LOG.debug("Lyrics fetch failed: {}", ex.toString());
            if (attempt < 4) {
                try { fetchExec.schedule(() -> { if (gen == generation.get()) fetch(track, gen, attempt + 1); }, 15, TimeUnit.SECONDS); }
                catch (RuntimeException ignored) {}
            }
        }
    }

    private void touchStatus() {
        Status eff = pollState != Status.READY ? pollState : lyricState;
        if (eff != shownStatus) {
            shownStatus = eff;
            statusSince = System.currentTimeMillis();
        }
    }

    // ---- called every frame from the render thread ----

    public Snapshot snapshot() {
        Sample s = sample;
        Lyrics ly = lyrics;
        Status eff = pollState != Status.READY ? pollState : lyricState;
        String text = pollState != Status.READY ? pollMsg : lyricMsg;
        long age = System.currentTimeMillis() - statusSince;
        if (s == null) {
            return new Snapshot("", "", "", false, 0, null, eff, text, age);
        }
        long pos = s.progressMs();
        if (s.playing()) pos += (System.nanoTime() - s.receivedNanos()) / 1_000_000L;
        if (s.track().durationMs() > 0) pos = Math.min(pos, s.track().durationMs());
        pos += cfg.lyricsOffsetMs; // user calibration: positive = lyrics appear earlier
        return new Snapshot(s.track().key(), s.track().title(), s.track().artist(), s.playing(), pos, ly, eff, text, age);
    }
}
