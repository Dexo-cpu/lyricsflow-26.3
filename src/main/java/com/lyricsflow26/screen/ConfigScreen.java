package com.lyricsflow26.screen;

import com.lyricsflow26.LyricsFlowMod;
import com.lyricsflow26.config.LyricsConfig;
import com.lyricsflow26.core.Snapshot;
import com.lyricsflow26.render.CameraCompat;
import com.lyricsflow26.render.Gfx;
import com.lyricsflow26.render.HudLyricsRenderer;
import com.lyricsflow26.render.PreviewFeed;
import com.lyricsflow26.render.Projector;
import com.lyricsflow26.render.WorldLyricsRenderer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/** Tabbed settings screen. The right-hand panel is a live preview that reacts to every change. */
public final class ConfigScreen extends Screen {
    private static final String[] TABS = {"General", "Text", "Glow", "Words/BG", "HUD", "World 1", "World 2", "Spotify"};
    private static int lastTab = 0;

    private record Label(String text, int x, int y) {}

    private final Screen parent;
    private final LyricsConfig c = LyricsFlowMod.config();
    private final List<Label> labels = new ArrayList<>();
    private final HudLyricsRenderer hudPreview = new HudLyricsRenderer(c, true);
    private final WorldLyricsRenderer worldPreview = new WorldLyricsRenderer(c, true);
    private final Projector proj = new Projector();
    private int leftW, rowH, rowY, rw, px, py, pw, ph;

    public ConfigScreen(Screen parent) {
        super(Component.literal("LyricsFlow settings"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        labels.clear();
        leftW = Math.min(250, width * 46 / 100);
        px = leftW + 20;
        py = 22;
        pw = width - px - 10;
        ph = height - py - 32;
        rw = leftW - 10;
        rowH = Math.max(17, Math.min(21, (height - 60 - 30) / 8));
        rowY = 58;

        int tw = (leftW - 10) / 4;
        for (int t = 0; t < TABS.length; t++) {
            final int idx = t;
            Button b = Button.builder(Component.literal(TABS[t]), btn -> { lastTab = idx; rebuildWidgets(); })
                    .bounds(10 + (t % 4) * tw, 20 + (t / 4) * 17, tw - 2, 16).build();
            b.active = t != lastTab;
            addRenderableWidget(b);
        }

        switch (lastTab) {
            case 0 -> tabGeneral();
            case 1 -> tabText();
            case 2 -> tabGlow();
            case 3 -> tabWords();
            case 4 -> tabHud();
            case 5 -> tabWorld1();
            case 6 -> tabWorld2();
            default -> tabSpotify();
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(10, height - 26, rw, 20).build());
    }

    private void tabGeneral() {
        toggle("Lyrics overlay", () -> c.enabled, v -> c.enabled = v);
        cycle("Display", LyricsConfig.DisplayMode.values(), () -> c.mode, v -> c.mode = v);
        slider("Visible lines", 1, 7, 1, c.visibleLines, "%.0f", v -> c.visibleLines = (int) v);
        cycle("Transition", LyricsConfig.Transition.values(), () -> c.transition, v -> c.transition = v);
        slider("Transition ms", 50, 1500, 10, c.transitionMs, "%.0f", v -> c.transitionMs = (int) v);
        slider("Animation speed", 0.25, 3, 0.05, c.animSpeed, "%.2fx", v -> c.animSpeed = (float) v);
        slider("Lyrics offset ms", -2000, 2000, 25, c.lyricsOffsetMs, "%.0f", v -> c.lyricsOffsetMs = (int) v);
        toggle("Song title / artist", () -> c.showTitleArtist, v -> c.showTitleArtist = v);
    }

    private void tabText() {
        colorRow("Text color", () -> c.textColor, v -> c.textColor = v);
        cycle("Font style", LyricsConfig.FontStyle.values(), () -> c.fontStyle, v -> c.fontStyle = v);
        slider("Thickness", 0, 2, 0.1, c.thickness, "%.1f", v -> c.thickness = (float) v);
        slider("Font scale", 0.6, 4, 0.05, c.scale, "%.2fx", v -> c.scale = (float) v);
        slider("Text opacity", 0.1, 1, 0.05, c.textOpacity, "%.2f", v -> c.textOpacity = (float) v);
        cycle("Alignment", LyricsConfig.Align.values(), () -> c.align, v -> c.align = v);
        slider("Fade in ms", 0, 2000, 10, c.fadeInMs, "%.0f", v -> c.fadeInMs = (int) v);
        slider("Fade out ms", 0, 2000, 10, c.fadeOutMs, "%.0f", v -> c.fadeOutMs = (int) v);
    }

    private void tabGlow() {
        toggle("Glow", () -> c.glowEnabled, v -> c.glowEnabled = v);
        colorRow("Glow color", () -> c.glowColor, v -> c.glowColor = v);
        slider("Glow intensity", 0, 1.5, 0.05, c.glowIntensity, "%.2f", v -> c.glowIntensity = (float) v);
        slider("Glow radius", 0, 6, 0.1, c.glowRadius, "%.1f", v -> c.glowRadius = (float) v);
        toggle("Shadow", () -> c.shadowEnabled, v -> c.shadowEnabled = v);
        colorRow("Shadow color", () -> c.shadowColor, v -> c.shadowColor = v);
        toggle("Outline", () -> c.outlineEnabled, v -> c.outlineEnabled = v);
        colorRow("Outline color", () -> c.outlineColor, v -> c.outlineColor = v);
    }

    private void tabWords() {
        cycle("Word effect", LyricsConfig.WordEffect.values(), () -> c.wordEffect, v -> c.wordEffect = v);
        colorRow("Highlight color", () -> c.highlightColor, v -> c.highlightColor = v);
        slider("Unsung opacity", 0, 1, 0.05, c.unsungOpacity, "%.2f", v -> c.unsungOpacity = (float) v);
        slider("Other lines dim", 0, 1, 0.05, c.dimOpacity, "%.2f", v -> c.dimOpacity = (float) v);
        toggle("Background", () -> c.bgEnabled, v -> c.bgEnabled = v);
        colorRow("Background color", () -> c.bgColor, v -> c.bgColor = v);
        slider("Background opacity", 0, 1, 0.05, c.bgOpacity, "%.2f", v -> c.bgOpacity = (float) v);
        slider("Background padding", 0, 20, 1, c.bgPadding, "%.0f", v -> c.bgPadding = (int) v);
    }

    private void tabHud() {
        cycle("Position", LyricsConfig.HudAnchor.values(), () -> c.hudAnchor, v -> c.hudAnchor = v);
        slider("Offset X", -400, 400, 1, c.hudOffsetX, "%.0f", v -> c.hudOffsetX = (int) v);
        slider("Offset Y", -400, 400, 1, c.hudOffsetY, "%.0f", v -> c.hudOffsetY = (int) v);
        slider("Line spacing", 0, 20, 1, c.lineSpacing, "%.0f", v -> c.lineSpacing = (int) v);
        info("These apply to HUD mode. Floating world");
        info("lyrics are configured on the World tabs.");
    }

    private void tabWorld1() {
        cycle("Anchor", LyricsConfig.WorldAnchor.values(), () -> c.worldAnchor, v -> c.worldAnchor = v);
        cycle("Float", LyricsConfig.FloatDirection.values(), () -> c.floatDirection, v -> c.floatDirection = v);
        slider("Float speed", 0, 2, 0.05, c.floatSpeed, "%.2f b/s", v -> c.floatSpeed = (float) v);
        slider("Distance", 1, 10, 0.1, c.worldDistance, "%.1f blocks", v -> c.worldDistance = (float) v);
        slider("Text size", 0.15, 1.5, 0.01, c.worldScale, "%.2f blocks", v -> c.worldScale = (float) v);
        slider("Vertical offset", -2, 2, 0.05, c.worldVerticalOffset, "%.2f", v -> c.worldVerticalOffset = (float) v);
        slider("Drift amount", 0, 0.6, 0.01, c.worldDriftAmount, "%.2f", v -> c.worldDriftAmount = (float) v);
        slider("Max lines at once", 1, 8, 1, c.worldMaxLines, "%.0f", v -> c.worldMaxLines = (int) v);
    }

    private void tabWorld2() {
        slider("World fade in ms", 0, 3000, 50, c.worldFadeInMs, "%.0f", v -> c.worldFadeInMs = (int) v);
        slider("World fade out ms", 200, 5000, 50, c.worldFadeOutMs, "%.0f", v -> c.worldFadeOutMs = (int) v);
        slider("World opacity", 0.1, 1, 0.05, c.worldOpacity, "%.2f", v -> c.worldOpacity = (float) v);
        toggle("Keep clear of crosshair", () -> c.worldAvoidCrosshair, v -> c.worldAvoidCrosshair = v);
        addRow(Button.builder(Component.literal("Fixed anchor: use my position"), b -> {
            Projector p = new Projector();
            if (CameraCompat.read(p)) {
                c.worldFixedX = Math.round(p.cx * 10) / 10.0;
                c.worldFixedY = Math.round(p.cy * 10) / 10.0 + 1;
                c.worldFixedZ = Math.round(p.cz * 10) / 10.0;
            }
        }));
        info("Used when Anchor = FIXED_POSITION.");
        info(String.format("Now: %.1f, %.1f, %.1f", c.worldFixedX, c.worldFixedY, c.worldFixedZ));
        info("Glow, color and size also apply here.");
    }

    private void tabSpotify() {
        var auth = LyricsFlowMod.service().auth();
        labels.add(new Label("Client ID", 12, rowY + (rowH - 10) / 2));
        EditBox id = new EditBox(Minecraft.getInstance().font, 10 + rw * 30 / 100, rowY, rw - rw * 30 / 100, rowH - 2, Component.literal("Client ID"));
        id.setMaxLength(64);
        id.setValue(c.spotifyClientId);
        id.setHint(Component.literal("from developer.spotify.com"));
        id.setResponder(v -> c.spotifyClientId = v.trim());
        addRenderableWidget(id);
        rowY += rowH;
        addRow(Button.builder(Component.literal("Connect Spotify"), b -> auth.startLogin()));
        addRow(Button.builder(Component.literal("Copy login link"), b -> {
            if (!auth.lastLoginUrl().isEmpty()) Minecraft.getInstance().keyboardHandler.setClipboard(auth.lastLoginUrl());
        }));
        addRow(Button.builder(Component.literal("Disconnect"), b -> auth.logout()));
        slider("Poll interval ms", 500, 3000, 100, c.pollIntervalMs, "%.0f", v -> c.pollIntervalMs = (int) v);
        toggle("Status hints", () -> c.showStatusMessages, v -> c.showStatusMessages = v);
        info("Redirect URI to register:");
        info(auth.redirectUri());
    }

    // ------------------------------------------------------------------ widget helpers

    private void addRow(Button.Builder b) {
        addRenderableWidget(b.bounds(10, rowY, rw, rowH - 2).build());
        rowY += rowH;
    }

    private void info(String text) {
        labels.add(new Label(text, 12, rowY));
        rowY += 11;
    }

    private void toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
        addRow(Button.builder(Component.literal(label + ": " + (get.getAsBoolean() ? "ON" : "OFF")), btn -> {
            boolean v = !get.getAsBoolean();
            set.accept(v);
            btn.setMessage(Component.literal(label + ": " + (v ? "ON" : "OFF")));
        }));
    }

    private <E extends Enum<E>> void cycle(String label, E[] values, Supplier<E> get, Consumer<E> set) {
        addRow(Button.builder(Component.literal(label + ": " + get.get().name()), btn -> {
            E next = values[(get.get().ordinal() + 1) % values.length];
            set.accept(next);
            btn.setMessage(Component.literal(label + ": " + next.name()));
        }));
    }

    private void slider(String label, double min, double max, double step, double cur, String fmt, DoubleConsumer set) {
        addRenderableWidget(new Sl(10, rowY, rw, rowH - 2, label, min, max, step, cur, fmt, set));
        rowY += rowH;
    }

    private void colorRow(String label, Supplier<String> get, Consumer<String> set) {
        labels.add(new Label(label, 12, rowY + (rowH - 10) / 2));
        int bx = 10 + rw * 48 / 100;
        EditBox eb = new EditBox(Minecraft.getInstance().font, bx, rowY, rw - rw * 48 / 100, rowH - 2, Component.literal(label));
        eb.setMaxLength(24);
        eb.setValue(get.get());
        eb.setHint(Component.literal("#RRGGBB or r,g,b"));
        eb.setResponder(v -> {
            if (LyricsConfig.tryParseColor(v) >= 0) { set.accept(v.trim()); eb.setTextColor(0xFFFFFFFF); }
            else eb.setTextColor(0xFFFF5555);
        });
        addRenderableWidget(eb);
        rowY += rowH;
    }

    private static final class Sl extends AbstractSliderButton {
        private final String label, fmt;
        private final double min, max, step;
        private final DoubleConsumer set;

        Sl(int x, int y, int w, int h, String label, double min, double max, double step, double cur, String fmt, DoubleConsumer set) {
            super(x, y, w, h, Component.empty(), Math.max(0, Math.min(1, (cur - min) / (max - min))));
            this.label = label; this.fmt = fmt; this.min = min; this.max = max; this.step = step; this.set = set;
            updateMessage();
        }

        private double val() {
            double v = min + value * (max - min);
            return step > 0 ? Math.round(v / step) * step : v;
        }

        @Override protected void updateMessage() {
            if (label == null) return; // called from super constructor before fields exist
            setMessage(Component.literal(label + ": " + String.format(fmt, val())));
        }

        @Override protected void applyValue() { set.accept(val()); }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void extractRenderState(GuiGraphicsExtractor ex, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(ex, mouseX, mouseY, partialTick);
        Gfx g = new Gfx(ex);
        g.text(Component.literal("LyricsFlow 26.3"), 10, 8, 0xFFFFFFFF);
        for (Label l : labels) g.text(Component.literal(l.text()), l.x(), l.y(), 0xFFDDDDDD);
        if (lastTab == 7) {
            g.text(Component.literal(LyricsFlowMod.service().auth().status()), 12, height - 40, 0xFF9FE79F);
        }
        if (pw < 100) return;

        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF555B66);
        g.fill(px, py, px + pw, py + ph, 0xFF0E1218);
        g.text(Component.literal("Live preview - sample text, not real lyrics"), px + 4, py + 3, 0xFF8A93A3);

        long now = Util.getMillis();
        Snapshot snap = PreviewFeed.snapshot(now);
        float vy = py + 14, vh = ph - 14;
        if (c.mode == LyricsConfig.DisplayMode.HUD) {
            hudPreview.render(g, snap, px, vy, pw, vh, now);
        } else {
            proj.set(0, 0, 0, 0, 0, 70, px, vy, pw, vh);
            worldPreview.render(g, snap, proj, now);
            float cx = px + pw / 2f, cy = vy + vh / 2f; // faux crosshair
            g.fill(cx - 4, cy - 0.5f, cx + 4, cy + 0.5f, 0x66FFFFFF);
            g.fill(cx - 0.5f, cy - 4, cx + 0.5f, cy + 4, 0x66FFFFFF);
        }
    }

    @Override
    public void onClose() {
        c.save();
        Minecraft.getInstance().setScreen(parent);
    }
}
