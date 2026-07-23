package com.blackhole.check.combat;

import com.blackhole.check.CheckResult;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.util.MathUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Deliberately not a hard CPS cap (section 6) - legitimate jitter/butterfly
 * clicking reaches 14-15 CPS. Instead measures how regular the intervals
 * between clicks are; CPS is only a secondary gate so a naturally slow but
 * steady clicker isn't flagged by chance.
 */
public final class AutoClickerCheck extends CombatCheck {

    private static final int MIN_CLICKS_IN_WINDOW = 6;
    private static final double LOW_STDDEV_MILLIS = 8.0;

    public AutoClickerCheck() {
        super("AutoClicker");
    }

    @Override
    public CheckResult evaluate(AttackContext context, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        List<Long> timestamps = context.getAttackerData().getRecentClickTimestamps();
        if (timestamps.size() < MIN_CLICKS_IN_WINDOW) {
            return CheckResult.clean();
        }

        List<Long> intervalsMillis = new ArrayList<>();
        for (int i = 0; i < timestamps.size() - 1; i++) {
            long deltaNanos = timestamps.get(i) - timestamps.get(i + 1);
            intervalsMillis.add(deltaNanos / 1_000_000L);
        }

        double stddev = Math.sqrt(MathUtil.variance(intervalsMillis));
        if (stddev >= LOW_STDDEV_MILLIS) {
            return CheckResult.clean();
        }
        return CheckResult.violation(1.0, "clicks=" + timestamps.size() + " stddev=" + String.format("%.2fms", stddev));
    }
}
