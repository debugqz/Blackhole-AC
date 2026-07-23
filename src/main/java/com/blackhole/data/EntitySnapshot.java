package com.blackhole.data;

import com.blackhole.util.Vector3d;

/**
 * Single point-in-time record of an entity's position/rotation, used by the
 * phase-3 rewind history to validate combat checks against where the
 * attacker actually saw the victim, not the victim's live server position.
 */
public final class EntitySnapshot {

    private final long timestamp;
    private final Vector3d position;
    private final float yaw;
    private final float pitch;

    public EntitySnapshot(long timestamp, Vector3d position, float yaw, float pitch) {
        this.timestamp = timestamp;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Vector3d getPosition() {
        return position;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }
}
