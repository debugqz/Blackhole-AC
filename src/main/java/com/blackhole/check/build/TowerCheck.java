package com.blackhole.check.build;

import com.blackhole.check.CheckResult;
import com.blackhole.data.PlacementRecord;
import com.blackhole.data.PlayerData;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.util.MathUtil;
import com.blackhole.util.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical bridging (section 7): placements directly beneath the player's own
 * feet, one block higher each time, with jump-to-jump timing far tighter
 * than the natural delay of a legitimate repeated jump-and-place. 1.8 has no
 * elytra, so this is the only "tower" pattern that matters here.
 */
public final class TowerCheck extends BuildCheck {

    private static final int MIN_PLACEMENTS = 3;
    private static final double MAX_HORIZONTAL_DRIFT = 0.3;
    private static final double MAX_INTERVAL_MILLIS = 300.0;
    private static final double LOW_STDDEV_MILLIS = 20.0;

    public TowerCheck() {
        super("Tower");
    }

    @Override
    public CheckResult evaluate(PlayerData data, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        List<PlacementRecord> placements = data.getRecentPlacements();
        if (placements.size() < MIN_PLACEMENTS) {
            return CheckResult.clean();
        }

        List<Long> intervalsMillis = new ArrayList<>();
        for (int i = 0; i < placements.size() - 1; i++) {
            Vector3d a = placements.get(i).getBlockPosition();
            Vector3d b = placements.get(i + 1).getBlockPosition();

            double horizontalDrift = Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z));
            if (horizontalDrift > MAX_HORIZONTAL_DRIFT || Math.abs(a.y - b.y) < 0.5) {
                return CheckResult.clean();
            }

            long intervalMillis = (placements.get(i).getTimestampNanos() - placements.get(i + 1).getTimestampNanos()) / 1_000_000L;
            if (intervalMillis > MAX_INTERVAL_MILLIS) {
                return CheckResult.clean();
            }
            intervalsMillis.add(intervalMillis);
        }

        double stddev = Math.sqrt(MathUtil.variance(intervalsMillis));
        if (stddev >= LOW_STDDEV_MILLIS) {
            return CheckResult.clean();
        }

        return CheckResult.violation(1.0, String.format("torre vertical, stddev=%.2fms", stddev));
    }
}
