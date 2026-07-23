package com.blackhole.check.combat;

import com.blackhole.check.CheckResult;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.util.Vector3d;

/**
 * Validated against the victim's rewound position (section 5.4/6), never
 * their live server position - otherwise anyone fighting a laggy player
 * would get flagged unfairly.
 */
public final class ReachCheck extends CombatCheck {

    private static final double MAX_REACH = 3.04;
    private static final double TARGET_CENTER_HEIGHT = 0.9;

    public ReachCheck() {
        super("Reach");
    }

    @Override
    public CheckResult evaluate(AttackContext context, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        if (context.getRewoundTarget() == null) {
            return CheckResult.clean();
        }

        Vector3d eye = context.getAttackerEyePosition();
        Vector3d targetCenter = context.getRewoundTarget().getPosition().add(0.0, TARGET_CENTER_HEIGHT, 0.0);
        double distance = eye.distance(targetCenter);

        if (distance <= MAX_REACH) {
            return CheckResult.clean();
        }
        return CheckResult.violation(distance - MAX_REACH, "distancia=" + String.format("%.3f", distance));
    }
}
