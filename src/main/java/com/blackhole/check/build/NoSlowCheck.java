package com.blackhole.check.build;

import com.blackhole.check.CheckResult;
import com.blackhole.check.moves.MovementCheck;
import com.blackhole.data.PlayerData;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.physics.DeviationAxis;
import com.blackhole.physics.MovementResult;

/**
 * PredictionEngine's speed factor already drops to 20% while PlayerData.isUsingItem()
 * is true (see PredictionEngine.computeSpeedFactor), so a horizontal deviation here
 * specifically means "moved at full speed while claiming to use an item" - the same
 * MovementResult SpeedCheck sees, just labelled and thresholded as its own check
 * since the cause is already known instead of merely suspected.
 */
public final class NoSlowCheck extends MovementCheck {

    public NoSlowCheck() {
        super("NoSlow");
    }

    @Override
    public CheckResult evaluate(PlayerData data, MovementResult result, BlockSnapshotProvider provider,
                                 BlockPhysicsRegistry registry) {
        if (!data.isUsingItem()) {
            return CheckResult.clean();
        }
        DeviationAxis axis = result.getDeviationAxis();
        if (axis != DeviationAxis.HORIZONTAL && axis != DeviationAxis.BOTH) {
            return CheckResult.clean();
        }
        double excess = Math.sqrt(result.getDelta().x * result.getDelta().x + result.getDelta().z * result.getDelta().z);
        return CheckResult.violation(excess, "exceso con item en uso=" + String.format("%.4f", excess));
    }
}
