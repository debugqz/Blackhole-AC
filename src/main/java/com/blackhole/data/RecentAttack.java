package com.blackhole.data;

import java.util.UUID;

/** One attack timestamp+target, used by KillAuraCheck's multi-aura signal. */
public final class RecentAttack {

    private final long timestampNanos;
    private final UUID targetUuid;

    public RecentAttack(long timestampNanos, UUID targetUuid) {
        this.timestampNanos = timestampNanos;
        this.targetUuid = targetUuid;
    }

    public long getTimestampNanos() {
        return timestampNanos;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }
}
