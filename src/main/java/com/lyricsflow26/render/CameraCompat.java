package com.lyricsflow26.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Reads the live game camera (first- or third-person) into a {@link Projector}. */
public final class CameraCompat {
    /** @return false when there is no world/camera to project into. */
    public static boolean read(Projector p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 pos = cam.position();            // 26.x name; was getPosition() in 1.21.x
        float yaw = cam.yRot();               // 26.x name; was getYRot()
        float pitch = cam.xRot();             // 26.x name; was getXRot()
        p.set(pos.x, pos.y, pos.z, yaw, pitch, mc.options.fov().get(),
                0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        return true;
    }

    private CameraCompat() {}
}
