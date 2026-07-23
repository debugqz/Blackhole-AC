package com.blackhole.check.moves;

import com.blackhole.check.CheckResult;
import com.blackhole.data.PlayerData;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.physics.DeviationAxis;
import com.blackhole.physics.MovementResult;

/**
 * PredictionEngine already treats liquid/climbable ticks as auto-accepted
 * (see PredictionEngine.validateVertical), so a VERTICAL/BOTH deviation here
 * can only mean unexplained lift while airborne on solid ground physics.
 */
public final class FlyCheck extends MovementCheck {

    public FlyCheck() {
        super("Fly");
    }

    @Override
    public CheckResult evaluate(PlayerData data, MovementResult result, BlockSnapshotProvider provider,
                                 BlockPhysicsRegistry registry) {
        DeviationAxis axis = result.getDeviationAxis();
        if (axis != DeviationAxis.VERTICAL && axis != DeviationAxis.BOTH) {
            return CheckResult.clean();
        }
        double excess = Math.abs(result.getDelta().y);
        return CheckResult.violation(excess, "desvio vertical=" + String.format("%.4f", excess));
    }
}
