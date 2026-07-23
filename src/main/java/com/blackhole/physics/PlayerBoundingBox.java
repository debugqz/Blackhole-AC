package com.blackhole.physics;

import com.blackhole.util.Vector3d;

/**
 * Axis-aligned bounding box for a 1.8 player (0.6 x 1.8 x 0.6) plus the
 * single-axis collision sweep against solid full-cube blocks. Non-full
 * shapes (stairs, slabs, fences) are approximated as full cubes for now -
 * flagged as a phase-1 limitation, extend AxisScan when those matter.
 */
public final class PlayerBoundingBox {

    public static final double WIDTH = 0.6;
    public static final double HALF_WIDTH = WIDTH / 2.0;
    public static final double HEIGHT = 1.8;
    public static final double STEP_HEIGHT = 0.5;
    private static final double EPS = 1.0E-7;

    private final double minX, minY, minZ, maxX, maxY, maxZ;

    private PlayerBoundingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public static PlayerBoundingBox atFeet(Vector3d feet) {
        return new PlayerBoundingBox(
                feet.x - HALF_WIDTH, feet.y, feet.z - HALF_WIDTH,
                feet.x + HALF_WIDTH, feet.y + HEIGHT, feet.z + HALF_WIDTH);
    }

    public PlayerBoundingBox offset(double dx, double dy, double dz) {
        return new PlayerBoundingBox(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
    }

    public double getMinX() {
        return minX;
    }

    public double getMinY() {
        return minY;
    }

    public double getMinZ() {
        return minZ;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    public double getMaxZ() {
        return maxZ;
    }

    public boolean intersects(double bx0, double by0, double bz0, double bx1, double by1, double bz1) {
        return minX < bx1 - EPS && maxX > bx0 + EPS
                && minY < by1 - EPS && maxY > by0 + EPS
                && minZ < bz1 - EPS && maxZ > bz0 + EPS;
    }

    /**
     * Clamps a single-axis movement delta against solid blocks overlapping the
     * box's perpendicular extent, returning the largest delta (same sign,
     * possibly smaller magnitude) that does not penetrate a solid block.
     */
    public double clampAxis(Axis axis, double delta, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        if (delta == 0.0) {
            return 0.0;
        }

        int perpAMin, perpAMax, perpBMin, perpBMax;
        switch (axis) {
            case X:
                perpAMin = (int) Math.floor(minY + EPS);
                perpAMax = (int) Math.floor(maxY - EPS);
                perpBMin = (int) Math.floor(minZ + EPS);
                perpBMax = (int) Math.floor(maxZ - EPS);
                break;
            case Y:
                perpAMin = (int) Math.floor(minX + EPS);
                perpAMax = (int) Math.floor(maxX - EPS);
                perpBMin = (int) Math.floor(minZ + EPS);
                perpBMax = (int) Math.floor(maxZ - EPS);
                break;
            default:
                perpAMin = (int) Math.floor(minX + EPS);
                perpAMax = (int) Math.floor(maxX - EPS);
                perpBMin = (int) Math.floor(minY + EPS);
                perpBMax = (int) Math.floor(maxY - EPS);
                break;
        }

        double faceStart = axis == Axis.X ? (delta > 0 ? maxX : minX)
                : axis == Axis.Y ? (delta > 0 ? maxY : minY)
                : (delta > 0 ? maxZ : minZ);
        double faceEnd = faceStart + delta;

        int scanStart = (int) Math.floor(delta > 0 ? faceStart - EPS : faceEnd + EPS);
        int scanEnd = (int) Math.floor(delta > 0 ? faceEnd - EPS : faceStart + EPS);

        double clamped = delta;
        for (int a = perpAMin; a <= perpAMax; a++) {
            for (int b = perpBMin; b <= perpBMax; b++) {
                int lo = Math.min(scanStart, scanEnd);
                int hi = Math.max(scanStart, scanEnd);
                for (int m = lo; m <= hi; m++) {
                    int bx, by, bz;
                    if (axis == Axis.X) {
                        bx = m;
                        by = a;
                        bz = b;
                    } else if (axis == Axis.Y) {
                        bx = a;
                        by = m;
                        bz = b;
                    } else {
                        bx = a;
                        by = b;
                        bz = m;
                    }

                    BlockPhysicsProfile profile = provider.getProfile(registry, bx, by, bz);
                    if (!profile.isSolid()) {
                        continue;
                    }

                    double blockMin = axis == Axis.X ? bx : axis == Axis.Y ? by : bz;
                    double blockMax = blockMin + 1.0 + (axis == Axis.Y ? profile.getHitboxTopOffset() : 0.0);

                    if (!blockOverlapsPerp(axis, bx, by, bz)) {
                        continue;
                    }

                    double allowed;
                    if (delta > 0) {
                        allowed = blockMin - faceStart;
                        if (allowed < 0) {
                            continue;
                        }
                    } else {
                        allowed = blockMax - faceStart;
                        if (allowed > 0) {
                            continue;
                        }
                    }
                    if (Math.abs(allowed) < Math.abs(clamped)) {
                        clamped = allowed;
                    }
                }
            }
        }
        return clamped;
    }

    private boolean blockOverlapsPerp(Axis axis, int bx, int by, int bz) {
        switch (axis) {
            case X:
                return by + 1.0 > minY + EPS && by < maxY - EPS && bz + 1.0 > minZ + EPS && bz < maxZ - EPS;
            case Y:
                return bx + 1.0 > minX + EPS && bx < maxX - EPS && bz + 1.0 > minZ + EPS && bz < maxZ - EPS;
            default:
                return bx + 1.0 > minX + EPS && bx < maxX - EPS && by + 1.0 > minY + EPS && by < maxY - EPS;
        }
    }

    public enum Axis {
        X, Y, Z
    }
}
