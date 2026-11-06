package com.lyricsflow26.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lyricsflow26.config.LyricsConfig;
import com.lyricsflow26.lyrics.HttpUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spotify Authorization Code flow with PKCE (no client secret needed).
 * The user supplies their own Client ID; a one-shot loopback listener on 127.0.0.1 receives the redirect.
 */
public final class SpotifyAuth {
    private static final String SCOPE = "user-read-currently-playing user-read-playback-state";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final SecureRandom RNG = new SecureRandom();

    private final LyricsConfig cfg;
    private final Path tokenFile;
    private volatile String access;
    private volatile long expiresAtMs;
    private volatile String refresh;
    private volatile String status;
    private volatile String lastLoginUrl = "";
    private volatile ServerSocket server;

    public SpotifyAuth(LyricsConfig cfg, Path dir) {
        this.cfg = cfg;
        this.tokenFile = dir.resolve("spotify-token.json");
        loadToken();
        status = refresh != null ? "Connected (saved login)" : "Not connected";
    }

    public String redirectUri() { return "http://127.0.0.1:" + cfg.spotifyPort + "/callback"; }
    public String status() { return status; }
    public String lastLoginUrl() { return lastLoginUrl; }
    public boolean connected() { return refresh != null; }

    public void logout() {
        access = null;
        refresh = null;
        try { Files.deleteIfExists(tokenFile); } catch (IOException ignored) {}
        closeServer();
        status = "Not connected";
    }

    public void invalidateAccess() { access = null; }

    /** Starts the browser login on a background thread. */
    public void startLogin() {
        if (cfg.spotifyClientId == null || cfg.spotifyClientId.isBlank()) {
            status = "Enter your Spotify Client ID first";
            return;
        }
        closeServer();
        Thread t = new Thread(this::loginFlow, "lyricsflow-auth");
        t.setDaemon(true);
        t.start();
    }

    /** @return a usable access token (refreshing if needed), or null if the user is not logged in. */
    public synchronized String validAccessToken() {
        if (refresh == null) return null;
        if (access == null || System.currentTimeMillis() > expiresAtMs - 60_000) refreshNow();
        return access;
    }

    // ---------------------------------------------------------------

    private void loginFlow() {
        ServerSocket ss = null;
        try {
            String verifier = b64(rand(64));
            String challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            String state = b64(rand(16));
            ss = new ServerSocket(cfg.spotifyPort, 1, InetAddress.getByName("127.0.0.1"));
            ss.setSoTimeout(300_000);
            server = ss;

            String url = "https://accounts.spotify.com/authorize?response_type=code"
                    + "&client_id=" + HttpUtil.enc(cfg.spotifyClientId.trim())
                    + "&scope=" + HttpUtil.enc(SCOPE)
                    + "&redirect_uri=" + HttpUtil.enc(redirectUri())
                    + "&state=" + state
                    + "&code_challenge_method=S256&code_challenge=" + challenge;
            lastLoginUrl = url;
            status = Browser.open(url) ? "Waiting for you to approve in the browser..."
                                       : "Could not open a browser. Use 'Copy login link' and paste it in one.";

            String code = null;
            while (code == null) {
                try (Socket s = ss.accept()) {
                    s.setSoTimeout(5000);
                    BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    String req = br.readLine();
                    boolean cb = req != null && req.startsWith("GET /callback");
                    Map<String, String> q = cb ? query(req) : Map.of();
                    String page;
                    if (cb && q.containsKey("error")) {
                        reply(s, "Spotify login was cancelled. You can close this tab.");
                        throw new IOException("Spotify said: " + q.get("error"));
                    } else if (cb && state.equals(q.get("state")) && q.get("code") != null) {
                        code = q.get("code");
                        page = "LyricsFlow is connected to Spotify. You can close this tab and return to Minecraft.";
                    } else {
                        page = "Waiting for Spotify...";
                    }
                    reply(s, page);
                }
            }
            status = "Finishing login...";
            exchange(code, verifier);
        } catch (Exception e) {
            status = "Login failed: " + e.getMessage();
        } finally {
            if (ss != null) { try { ss.close(); } catch (IOException ignored) {} }
            server = null;
        }
    }

    private void exchange(String code, String verifier) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri());
        form.put("client_id", cfg.spotifyClientId.trim());
        form.put("code_verifier", verifier);
        HttpUtil.Response r = HttpUtil.postForm(TOKEN_URL, form);
        if (r.code() != 200) throw new IOException("token exchange failed (HTTP " + r.code() + ")");
        applyTokens(JsonParser.parseString(r.body()).getAsJsonObject());
        status = "Connected";
    }

    private void refreshNow() {
        try {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("grant_type", "refresh_token");
            form.put("refresh_token", refresh);
            form.put("client_id", cfg.spotifyClientId.trim());
            HttpUtil.Response r = HttpUtil.postForm(TOKEN_URL, form);
            if (r.code() == 200) {
                applyTokens(JsonParser.parseString(r.body()).getAsJsonObject());
                status = "Connected";
            } else if (r.code() == 400 || r.code() == 401) {
                refresh = null;
                access = null;
                try { Files.deleteIfExists(tokenFile); } catch (IOException ignored) {}
                status = "Login expired. Press Connect again.";
            }
        } catch (IOException e) {
            status = "Connected (network issue while refreshing)";
        }
    }

    private void applyTokens(JsonObject o) {
        access = o.get("access_token").getAsString();
        expiresAtMs = System.currentTimeMillis() + o.get("expires_in").getAsLong() * 1000L;
        if (o.has("refresh_token") && !o.get("refresh_token").isJsonNull()) {
            refresh = o.get("refresh_token").getAsString();
            saveToken();
        }
    }

    private void saveToken() {
        try {
            Files.createDirectories(tokenFile.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("refresh_token", refresh);
            Files.writeString(tokenFile, o.toString());
            try { Files.setPosixFilePermissions(tokenFile, PosixFilePermissions.fromString("rw-------")); }
            catch (Exception ignored) { /* non-POSIX filesystem */ }
        } catch (IOException ignored) {}
    }

    private void loadToken() {
        try {
            if (Files.exists(tokenFile)) {
                JsonObject o = JsonParser.parseString(Files.readString(tokenFile)).getAsJsonObject();
                if (o.has("refresh_token")) refresh = o.get("refresh_token").getAsString();
            }
        } catch (Exception ignored) {}
    }

    private void closeServer() {
        ServerSocket s = server;
        if (s != null) { try { s.close(); } catch (IOException ignored) {} }
    }

    private static byte[] rand(int n) { byte[] b = new byte[n]; RNG.nextBytes(b); return b; }
    private static String b64(byte[] b) { return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }

    private static Map<String, String> query(String requestLine) {
        Map<String, String> out = new HashMap<>();
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) return out;
        int qi = parts[1].indexOf('?');
        if (qi < 0) return out;
        for (String kv : parts[1].substring(qi + 1).split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0) {
                out.put(URLDecoder.decode(kv.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(kv.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static void reply(Socket s, String text) throws IOException {
        byte[] body = ("<html><body style=\"font-family:sans-serif;padding:2em\"><h3>" + text + "</h3></body></html>")
                .getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\n\r\n";
        OutputStream o = s.getOutputStream();
        o.write(head.getBytes(StandardCharsets.US_ASCII));
        o.write(body);
        o.flush();
    }
}
