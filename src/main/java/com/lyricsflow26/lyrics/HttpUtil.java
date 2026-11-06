package com.lyricsflow26.lyrics;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Minimal blocking HTTP helper (java.base only). Never call from the render thread. */
public final class HttpUtil {
    public record Response(int code, String body, String retryAfter) {}

    public static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    public static Response get(String url, Map<String, String> headers) throws IOException {
        return send("GET", url, headers, null);
    }

    public static Response postForm(String url, Map<String, String> form) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return send("POST", url, Map.of("Content-Type", "application/x-www-form-urlencoded"),
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Response send(String method, String url, Map<String, String> headers, byte[] body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            c.setConnectTimeout(6000);
            c.setReadTimeout(9000);
            c.setRequestMethod(method);
            for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            if (body != null) {
                c.setDoOutput(true);
                try (OutputStream o = c.getOutputStream()) { o.write(body); }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new Response(code, text, c.getHeaderField("Retry-After"));
        } finally {
            c.disconnect();
        }
    }

    private HttpUtil() {}
}
