package com.blackhole.check.moves;

import com.blackhole.check.CheckResult;
import com.blackhole.data.PlayerData;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.physics.MovementResult;

/**
 * A single tick's MovementResult can't distinguish "swimming normally" from
 * "walking on water" since liquid verticals are auto-accepted by the engine.
 * CheckManager instead tracks consecutive liquid ticks with no sinking
 * (PlayerData.liquidStableTicks) - this just interprets that counter.
 */
public final class JesusCheck extends MovementCheck {

    private static final int STABLE_TICKS_THRESHOLD = 10;

    public JesusCheck() {
        super("Jesus");
    }

    @Override
    public CheckResult evaluate(PlayerData data, MovementResult result, BlockSnapshotProvider provider,
                                 BlockPhysicsRegistry registry) {
        if (data.getLiquidStableTicks() < STABLE_TICKS_THRESHOLD) {
            return CheckResult.clean();
        }
        return CheckResult.violation(1.0, "ticks sin hundirse en liquido=" + data.getLiquidStableTicks());
    }
}
