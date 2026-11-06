package com.lyricsflow26.spotify;

import java.awt.Desktop;
import java.net.URI;

/** Opens a URL in the user's default browser without touching any Minecraft API. */
final class Browser {
    static boolean open(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            }
        } catch (Throwable ignored) { /* headless AWT or missing module: fall through */ }
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("win")) new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            else if (os.contains("mac")) new ProcessBuilder("open", url).start();
            else new ProcessBuilder("xdg-open", url).start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Browser() {}
}
