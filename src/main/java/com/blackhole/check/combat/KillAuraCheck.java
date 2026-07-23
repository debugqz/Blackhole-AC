package com.blackhole.check.combat;

import com.blackhole.check.CheckResult;
import com.blackhole.data.RecentAttack;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.util.Vector3d;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Combines several independent signals (section 6) rather than relying on
 * any single one: look angle away from the target, missing arm-swing before
 * the hit, hitting through a wall, and hitting multiple distinct targets in
 * a very short window.
 */
public final class KillAuraCheck extends CombatCheck {

    private static final double MAX_LOOK_ANGLE_DEGREES = 70.0;
    private static final double RAYCAST_STEP = 0.2;
    private static final double TARGET_CENTER_HEIGHT = 0.9;

    public KillAuraCheck() {
        super("KillAura");
    }

    @Override
    public CheckResult evaluate(AttackContext context, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        if (context.getRewoundTarget() == null) {
            return CheckResult.clean();
        }

        Vector3d eye = context.getAttackerEyePosition();
        Vector3d targetCenter = context.getRewoundTarget().getPosition().add(0.0, TARGET_CENTER_HEIGHT, 0.0);

        double vlAmount = 0.0;
        StringBuilder details = new StringBuilder();

        double angle = angleBetweenLookAndTarget(eye, targetCenter, context.getAttackerYaw(), context.getAttackerPitch());
        if (angle > MAX_LOOK_ANGLE_DEGREES) {
            vlAmount += 1.0;
            details.append("angulo=").append(String.format("%.1f", angle)).append(' ');
        }

        if (!context.isHadRecentArmSwing()) {
            vlAmount += 1.0;
            details.append("sin-swing ");
        }

        if (isBlockedByWall(eye, targetCenter, provider, registry)) {
            vlAmount += 2.0;
            details.append("a-traves-de-pared ");
        }

        int distinctTargets = countDistinctRecentTargets(context);
        if (distinctTargets > 1) {
            vlAmount += distinctTargets;
            details.append("multi-aura=").append(distinctTargets).append(' ');
        }

        if (vlAmount <= 0.0) {
            return CheckResult.clean();
        }
        return CheckResult.violation(vlAmount, details.toString().trim());
    }

    private double angleBetweenLookAndTarget(Vector3d eye, Vector3d targetCenter, float yaw, float pitch) {
        Vector3d toTarget = targetCenter.subtract(eye);
        double length = toTarget.length();
        if (length < 1.0E-4) {
            return 0.0;
        }

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double lookY = -Math.sin(pitchRad);
        double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

        double dot = (toTarget.x / length) * lookX + (toTarget.y / length) * lookY + (toTarget.z / length) * lookZ;
        dot = Math.max(-1.0, Math.min(1.0, dot));
        return Math.toDegrees(Math.acos(dot));
    }

    private boolean isBlockedByWall(Vector3d eye, Vector3d targetCenter, BlockSnapshotProvider provider,
                                     BlockPhysicsRegistry registry) {
        Vector3d direction = targetCenter.subtract(eye);
        double distance = direction.length();
        if (distance < 1.0E-4) {
            return false;
        }
        int steps = (int) Math.ceil(distance / RAYCAST_STEP);
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            double x = eye.x + direction.x * t;
            double y = eye.y + direction.y * t;
            double z = eye.z + direction.z * t;
            if (provider.getProfile(registry, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)).isSolid()) {
                return true;
            }
        }
        return false;
    }

    private int countDistinctRecentTargets(AttackContext context) {
        Set<UUID> distinct = new HashSet<>();
        for (RecentAttack attack : context.getAttackerData().getRecentAttacks()) {
            distinct.add(attack.getTargetUuid());
        }
        return distinct.size();
    }
}
