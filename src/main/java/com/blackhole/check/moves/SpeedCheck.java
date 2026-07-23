package com.blackhole.check.moves;

import com.blackhole.check.CheckResult;
import com.blackhole.data.PlayerData;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.physics.DeviationAxis;
import com.blackhole.physics.MovementResult;

public final class SpeedCheck extends MovementCheck {

    public SpeedCheck() {
        super("Speed");
    }

    @Override
    public CheckResult evaluate(PlayerData data, MovementResult result, BlockSnapshotProvider provider,
                                 BlockPhysicsRegistry registry) {
        DeviationAxis axis = result.getDeviationAxis();
        if (axis != DeviationAxis.HORIZONTAL && axis != DeviationAxis.BOTH) {
            return CheckResult.clean();
        }
        double excess = Math.sqrt(result.getDelta().x * result.getDelta().x + result.getDelta().z * result.getDelta().z);
        return CheckResult.violation(excess, "exceso horizontal=" + String.format("%.4f", excess));
    }
}
