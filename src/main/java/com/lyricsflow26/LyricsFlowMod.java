package com.lyricsflow26;

import com.lyricsflow26.config.LyricsConfig;
import com.lyricsflow26.core.LyricsService;
import com.lyricsflow26.core.Snapshot;
import com.lyricsflow26.render.CameraCompat;
import com.lyricsflow26.render.Gfx;
import com.lyricsflow26.render.HudLyricsRenderer;
import com.lyricsflow26.render.Projector;
import com.lyricsflow26.render.WorldLyricsRenderer;
import com.lyricsflow26.screen.ConfigScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LyricsFlowMod implements ClientModInitializer {
    public static final String MOD_ID = "lyricsflow26";
    public static final Logger LOG = LoggerFactory.getLogger("LyricsFlow26");

    private static LyricsConfig config;
    private static LyricsService service;

    public static LyricsConfig config() { return config; }
    public static LyricsService service() { return service; }

    private KeyMapping toggleKey, modeKey, configKey;
    private HudLyricsRenderer hud;
    private WorldLyricsRenderer world;
    private final Projector projector = new Projector();

    @Override
    public void onInitializeClient() {
        config = LyricsConfig.load();
        service = new LyricsService(config);
        hud = new HudLyricsRenderer(config, false);
        world = new WorldLyricsRenderer(config, false);

        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lyricsflow26.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_L, cat));
        modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lyricsflow26.mode", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, cat));
        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.lyricsflow26.config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, cat));

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "lyrics"), this::renderHud);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> { service.stop(); config.save(); });

        service.start();
    }

    private void tick(Minecraft mc) {
        while (toggleKey.consumeClick()) {
            config.enabled = !config.enabled;
            config.save();
            notify(mc, "Lyrics " + (config.enabled ? "on" : "off"));
        }
        while (modeKey.consumeClick()) {
            config.mode = config.mode == LyricsConfig.DisplayMode.HUD
                    ? LyricsConfig.DisplayMode.WORLD : LyricsConfig.DisplayMode.HUD;
            config.save();
            notify(mc, config.mode == LyricsConfig.DisplayMode.HUD ? "Lyrics: HUD" : "Lyrics: floating in the world");
        }
        while (configKey.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ConfigScreen(null));
        }
    }

    private static void notify(Minecraft mc, String msg) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("LyricsFlow: " + msg), true);
    }

    private void renderHud(GuiGraphicsExtractor ex, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!config.enabled || mc.level == null || mc.options.hideGui) return;
        if (mc.screen instanceof ConfigScreen) return; // the settings screen draws its own preview
        Gfx g = new Gfx(ex);
        long now = Util.getMillis();
        Snapshot s = service.snapshot();
        if (config.mode == LyricsConfig.DisplayMode.HUD) {
            hud.render(g, s, 0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), now);
        } else if (CameraCompat.read(projector)) {
            world.render(g, s, projector, now);
        }
    }
}
