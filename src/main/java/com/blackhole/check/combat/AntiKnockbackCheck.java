package com.blackhole.check.combat;

import com.blackhole.check.Check;
import com.blackhole.check.CheckResult;
import com.blackhole.data.PlayerData;
import com.blackhole.physics.MovementResult;
import com.blackhole.util.KnockbackFormula;
import com.blackhole.util.Vector3d;

/**
 * Server is vanilla-pure for knockback (section 6), so no per-kit exception
 * list is needed - just the real formula. Runs in two halves: the attack
 * side records what the victim's velocity SHOULD become; the victim's next
 * movement tick (in CheckManager.processMovement) checks whether it did.
 */
public final class AntiKnockbackCheck extends Check {

    private static final long EXPIRY_NANOS = 250_000_000L;
    private static final double MIN_EXPECTED_HORIZONTAL = 0.05;
    private static final double CANCELLED_RATIO = 0.5;

    public AntiKnockbackCheck() {
        super("AntiKnockback");
    }

    public void registerExpectedKnockback(PlayerData victimData, Vector3d attackerPosition, float attackerYaw,
                                           boolean attackerSprinting, int knockbackEnchantLevel, long nowNanos) {
        Vector3d victimPosition = victimData.getPhysicsState().getPosition();
        Vector3d victimVelocity = victimData.getPhysicsState().getVelocity();
        Vector3d toVictim = new Vector3d(victimPosition.x - attackerPosition.x, 0.0, victimPosition.z - attackerPosition.z);

        Vector3d expected = KnockbackFormula.computeExpectedVelocity(victimVelocity, toVictim, attackerYaw,
                attackerSprinting, knockbackEnchantLevel, 0.0);

        victimData.setPendingKnockback(expected, nowNanos + EXPIRY_NANOS);
    }

    public CheckResult evaluateOnMovement(PlayerData victimData, Vector3d previousPosition, MovementResult result,
                                           long nowNanos) {
        Vector3d expected = victimData.consumePendingKnockback(nowNanos);
        if (expected == null) {
            return CheckResult.clean();
        }

        double expectedHorizontal = Math.sqrt(expected.x * expected.x + expected.z * expected.z);
        if (expectedHorizontal < MIN_EXPECTED_HORIZONTAL) {
            return CheckResult.clean();
        }

        Vector3d observedDelta = result.getReportedPosition().subtract(previousPosition);
        double observedHorizontal = Math.sqrt(observedDelta.x * observedDelta.x + observedDelta.z * observedDelta.z);

        if (observedHorizontal < expectedHorizontal * CANCELLED_RATIO) {
            return CheckResult.violation(expectedHorizontal - observedHorizontal,
                    String.format("kb esperado=%.3f observado=%.3f", expectedHorizontal, observedHorizontal));
        }
        return CheckResult.clean();
    }
}
