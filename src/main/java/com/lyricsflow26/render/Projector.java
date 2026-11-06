package com.lyricsflow26.render;

/**
 * Pinhole camera that maps world positions to GUI-scaled screen coordinates. Used for the floating world lyrics
 * (real game camera) and for the settings preview (a virtual camera inside the preview panel).
 */
public final class Projector {
    public double cx, cy, cz;                 // camera position
    public double fx, fy, fz, rx, ry, rz, ux, uy, uz; // forward / right / up basis
    public float vx, vy, vw, vh;              // viewport in GUI pixels
    public double tanHalf = 1;                // tan(vertical fov / 2)

    /** Minecraft convention: yaw 0 faces +Z, pitch positive looks down. FOV is the vertical FOV in degrees. */
    public void set(double x, double y, double z, float yawDeg, float pitchDeg, double fovDeg,
                    float vx, float vy, float vw, float vh) {
        cx = x; cy = y; cz = z;
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        double sy = Math.sin(yaw), cyw = Math.cos(yaw), sp = Math.sin(pitch), cp = Math.cos(pitch);
        fx = -sy * cp; fy = -sp; fz = cyw * cp;
        rx = -cyw; ry = 0; rz = -sy;
        ux = ry * fz - rz * fy;               // up = right x forward
        uy = rz * fx - rx * fz;
        uz = rx * fy - ry * fx;
        this.vx = vx; this.vy = vy; this.vw = vw; this.vh = vh;
        tanHalf = Math.tan(Math.toRadians(fovDeg) * 0.5);
    }

    /** @param out receives {screenX, screenY, pixelsPerBlock at that depth}. @return false if behind the camera. */
    public boolean project(double wx, double wy, double wz, float[] out) {
        double dx = wx - cx, dy = wy - cy, dz = wz - cz;
        double z = dx * fx + dy * fy + dz * fz;
        if (z < 0.1) return false;
        double x = dx * rx + dy * ry + dz * rz;
        double y = dx * ux + dy * uy + dz * uz;
        double aspect = vw / (double) vh;
        double ndcX = x / (z * tanHalf * aspect);
        double ndcY = y / (z * tanHalf);
        if (Math.abs(ndcX) > 3 || Math.abs(ndcY) > 3) return false;
        out[0] = (float) (vx + vw * (0.5 + 0.5 * ndcX));
        out[1] = (float) (vy + vh * (0.5 - 0.5 * ndcY));
        out[2] = (float) ((vh * 0.5) / (z * tanHalf));
        return true;
    }
}
