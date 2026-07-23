package com.blackhole.check.combat;

import com.blackhole.data.EntitySnapshot;
import com.blackhole.data.PlayerData;
import com.blackhole.util.Vector3d;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Everything a combat check needs for one attack, built entirely from
 * already-thread-safe cached state (PlayerData, EntityHistoryManager) - no
 * main-thread hop needed to evaluate Reach/KillAura/Aimbot/AutoClicker in
 * real time on the packet/Netty thread.
 */
public final class AttackContext {

    private final Player attacker;
    private final PlayerData attackerData;
    private final UUID targetUuid;
    private final EntitySnapshot rewoundTarget;
    private final long hitNanos;
    private final boolean hadRecentArmSwing;

    public AttackContext(Player attacker, PlayerData attackerData, UUID targetUuid, EntitySnapshot rewoundTarget,
                          long hitNanos, boolean hadRecentArmSwing) {
        this.attacker = attacker;
        this.attackerData = attackerData;
        this.targetUuid = targetUuid;
        this.rewoundTarget = rewoundTarget;
        this.hitNanos = hitNanos;
        this.hadRecentArmSwing = hadRecentArmSwing;
    }

    public Player getAttacker() {
        return attacker;
    }

    public PlayerData getAttackerData() {
        return attackerData;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public EntitySnapshot getRewoundTarget() {
        return rewoundTarget;
    }

    public long getHitNanos() {
        return hitNanos;
    }

    public boolean isHadRecentArmSwing() {
        return hadRecentArmSwing;
    }

    public Vector3d getAttackerEyePosition() {
        double eyeHeight = attackerData.isSneaking() ? 1.54 : 1.62;
        return attackerData.getPhysicsState().getPosition().add(0.0, eyeHeight, 0.0);
    }

    public float getAttackerYaw() {
        return attackerData.getYaw();
    }

    public float getAttackerPitch() {
        return attackerData.getPitch();
    }
}
