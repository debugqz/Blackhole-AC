package com.blackhole.check.combat;

import com.blackhole.check.CheckResult;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.util.MathUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Two independent signals over the attacker's recent yaw-delta window
 * (section 6): a suspiciously high, constant GCD across samples (aim-assist
 * that rounds sensitivity to fixed steps) and an instant "snap" toward the
 * target with none of a human's gradual inertia.
 */
public final class AimbotCheck extends CombatCheck {

    private static final int MIN_SAMPLES = 8;
    private static final long GCD_MIN_MICRO_DEGREES = 300;
    private static final double LOW_VARIANCE_RATIO = 0.15;
    private static final double SNAP_RATIO = 5.0;
    private static final long SNAP_MIN_MICRO_DEGREES = 150_000;

    public AimbotCheck() {
        super("Aimbot");
    }

    @Override
    public CheckResult evaluate(AttackContext context, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        List<Long> samples = context.getAttackerData().getRecentYawDeltas();
        List<Long> nonZero = new ArrayList<>();
        for (Long value : samples) {
            if (value != 0) {
                nonZero.add(Math.abs(value));
            }
        }
        if (nonZero.size() < MIN_SAMPLES) {
            return CheckResult.clean();
        }

        double vlAmount = 0.0;
        StringBuilder details = new StringBuilder();

        long gcd = MathUtil.gcd(nonZero);
        double mean = MathUtil.mean(nonZero);
        double variance = MathUtil.variance(nonZero);
        if (gcd >= GCD_MIN_MICRO_DEGREES && mean > 0 && Math.sqrt(variance) < mean * LOW_VARIANCE_RATIO) {
            vlAmount += 1.0;
            details.append("gcd=").append(gcd).append(" media=").append(String.format("%.0f", mean)).append(' ');
        }

        long lastDelta = nonZero.get(0);
        double previousAverage = MathUtil.mean(nonZero.subList(1, nonZero.size()));
        if (previousAverage > 0 && lastDelta > SNAP_MIN_MICRO_DEGREES && lastDelta > previousAverage * SNAP_RATIO) {
            vlAmount += 1.0;
            details.append("snap=").append(lastDelta).append(" previo=").append(String.format("%.0f", previousAverage));
        }

        if (vlAmount <= 0.0) {
            return CheckResult.clean();
        }
        return CheckResult.violation(vlAmount, details.toString().trim());
    }
}
