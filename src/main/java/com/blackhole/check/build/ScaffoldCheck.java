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
 * Auto-bridging pattern (section 7): a straight horizontal line of
 * placements, all made while looking steeply down/back, with placement
 * timing far more uniform than a human's. Any one of these alone happens
 * legitimately; all three together is the bridging signature.
 */
public final class ScaffoldCheck extends BuildCheck {

    private static final int MIN_PLACEMENTS = 4;
    private static final double MIN_DIRECTION_CONSISTENCY = 0.9;
    private static final float DOWNWARD_PITCH_THRESHOLD = 55.0f;
    private static final double LOW_STDDEV_MILLIS = 15.0;

    public ScaffoldCheck() {
        super("Scaffold");
    }

    @Override
    public CheckResult evaluate(PlayerData data, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        List<PlacementRecord> placements = data.getRecentPlacements();
        if (placements.size() < MIN_PLACEMENTS) {
            return CheckResult.clean();
        }

        if (!isConsistentLine(placements)) {
            return CheckResult.clean();
        }

        double averagePitch = 0.0;
        for (PlacementRecord record : placements) {
            averagePitch += record.getPitch();
        }
        averagePitch /= placements.size();
        if (averagePitch < DOWNWARD_PITCH_THRESHOLD) {
            return CheckResult.clean();
        }

        List<Long> intervalsMillis = new ArrayList<>();
        for (int i = 0; i < placements.size() - 1; i++) {
            intervalsMillis.add((placements.get(i).getTimestampNanos() - placements.get(i + 1).getTimestampNanos()) / 1_000_000L);
        }
        double stddev = Math.sqrt(MathUtil.variance(intervalsMillis));
        if (stddev >= LOW_STDDEV_MILLIS) {
            return CheckResult.clean();
        }

        return CheckResult.violation(1.0, String.format("linea consistente, pitch=%.1f, stddev=%.2fms", averagePitch, stddev));
    }

    private boolean isConsistentLine(List<PlacementRecord> placements) {
        List<Vector3d> directions = new ArrayList<>();
        for (int i = 0; i < placements.size() - 1; i++) {
            Vector3d a = placements.get(i).getBlockPosition();
            Vector3d b = placements.get(i + 1).getBlockPosition();
            double dx = a.x - b.x;
            double dz = a.z - b.z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0E-4) {
                return false;
            }
            directions.add(new Vector3d(dx / length, 0.0, dz / length));
        }

        for (int i = 0; i < directions.size() - 1; i++) {
            Vector3d a = directions.get(i);
            Vector3d b = directions.get(i + 1);
            double dot = a.x * b.x + a.z * b.z;
            if (dot < MIN_DIRECTION_CONSISTENCY) {
                return false;
            }
        }
        return true;
    }
}
