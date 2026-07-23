package com.blackhole.util;

/**
 * Reconstruction of vanilla 1.8 melee knockback (EntityLivingBase.knockBack +
 * the sprint/Knockback-enchant bonus EntityPlayer adds on top). No custom
 * kits on this server (section 6), so this is the only formula
 * AntiKnockbackCheck needs - no per-server exception list.
 */
public final class KnockbackFormula {

    private static final double BASE_HORIZONTAL = 0.4;
    private static final double BASE_VERTICAL = 0.4;
    private static final double PER_LEVEL_BONUS = 0.5;
    private static final double BONUS_VERTICAL = 0.1;

    private KnockbackFormula() {
    }

    public static Vector3d computeExpectedVelocity(Vector3d victimCurrentVelocity, Vector3d attackerToVictimHorizontal,
                                                     float attackerYaw, boolean attackerSprinting,
                                                     int knockbackEnchantLevel, double victimKnockbackResistance) {
        double dx = attackerToVictimHorizontal.x;
        double dz = attackerToVictimHorizontal.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        double nx, nz;
        if (length < 1.0E-4) {
            nx = 0.0;
            nz = 1.0;
        } else {
            nx = dx / length;
            nz = dz / length;
        }

        double vx = victimCurrentVelocity.x / 2.0 - nx * BASE_HORIZONTAL;
        double vz = victimCurrentVelocity.z / 2.0 - nz * BASE_HORIZONTAL;
        double vy = Math.min(victimCurrentVelocity.y / 2.0 + BASE_VERTICAL, BASE_VERTICAL);

        int totalLevel = knockbackEnchantLevel + (attackerSprinting ? 1 : 0);
        if (totalLevel > 0) {
            double yawRad = Math.toRadians(attackerYaw);
            vx += -Math.sin(yawRad) * totalLevel * PER_LEVEL_BONUS;
            vz += Math.cos(yawRad) * totalLevel * PER_LEVEL_BONUS;
            vy += BONUS_VERTICAL;
        }

        double resistance = MathUtil.clamp(victimKnockbackResistance, 0.0, 1.0);
        vx *= (1.0 - resistance);
        vz *= (1.0 - resistance);

        return new Vector3d(vx, vy, vz);
    }
}
