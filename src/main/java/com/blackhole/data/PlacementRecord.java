package com.blackhole.data;

import com.blackhole.util.Vector3d;

/** One block placement: position, attacker rotation, and timestamp - used by Scaffold/Tower. */
public final class PlacementRecord {

    private final long timestampNanos;
    private final Vector3d blockPosition;
    private final float yaw;
    private final float pitch;

    public PlacementRecord(long timestampNanos, Vector3d blockPosition, float yaw, float pitch) {
        this.timestampNanos = timestampNanos;
        this.blockPosition = blockPosition;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public long getTimestampNanos() {
        return timestampNanos;
    }

    public Vector3d getBlockPosition() {
        return blockPosition;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
