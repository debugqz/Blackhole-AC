package com.blackhole.check.moves;

import com.blackhole.check.CheckResult;
import com.blackhole.data.PlayerData;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.physics.MovementResult;

/**
 * Interprets PlayerData.wallClimbTicks, which CheckManager only increments
 * when a tick shows an unexplained vertical Fly-style deviation AND the
 * player's bounding box is pressed against a solid, non-climbable block -
 * open-air Fly never touches this counter.
 */
public final class SpiderCheck extends MovementCheck {

    private static final int WALL_CLIMB_TICKS_THRESHOLD = 4;

    public SpiderCheck() {
        super("Spider");
    }

    @Override
    public CheckResult evaluate(PlayerData data, MovementResult result, BlockSnapshotProvider provider,
                                 BlockPhysicsRegistry registry) {
        if (data.getWallClimbTicks() < WALL_CLIMB_TICKS_THRESHOLD) {
            return CheckResult.clean();
        }
        return CheckResult.violation(1.0, "ticks trepando pared=" + data.getWallClimbTicks());
    }
}
