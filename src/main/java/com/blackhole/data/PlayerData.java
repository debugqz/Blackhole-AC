package com.blackhole.data;

import com.blackhole.physics.PhysicsState;
import com.blackhole.util.Vector3d;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-player mutable state: physics snapshot, rotation, violation levels,
 * last known valid location for silent setback. Guarded by its own lock so
 * checks running off the main thread (packet/Netty) never serialize against
 * other players (see architecture section 11).
 */
public final class PlayerData {

    private final UUID uuid;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Double> violationLevels = new ConcurrentHashMap<>();
    private final Map<String, Integer> checkBuffers = new ConcurrentHashMap<>();

    private volatile PhysicsState physicsState;
    private volatile float yaw;
    private volatile float pitch;
    private volatile Vector3d lastValidPosition;
    private volatile boolean sneaking;
    private volatile boolean sprinting;
    private volatile boolean usingItem;
    private volatile int heldItemSlot;
    private volatile int heldItemKnockbackLevel;
    private volatile long lastArmSwingNanos;
    private volatile double fallDistance;
    private volatile int liquidStableTicks;
    private volatile int wallClimbTicks;
    private volatile long exemptUntilNanos;

    private static final int ROTATION_SAMPLE_LIMIT = 20;
    private static final long CLICK_WINDOW_NANOS = 1_000_000_000L;
    private static final long ATTACK_WINDOW_NANOS = 200_000_000L;
    private static final long PLACEMENT_WINDOW_NANOS = 3_000_000_000L;

    private final ConcurrentLinkedDeque<Long> recentYawDeltasMicroDegrees = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Long> recentClickTimestampsNanos = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<RecentAttack> recentAttacks = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<PlacementRecord> recentPlacements = new ConcurrentLinkedDeque<>();
    private volatile Vector3d pendingKnockback;
    private volatile long pendingKnockbackExpiryNanos;

    public PlayerData(UUID uuid, PhysicsState initialState) {
        this.uuid = uuid;
        this.physicsState = initialState;
        this.lastValidPosition = initialState.getPosition();
    }

    public UUID getUuid() {
        return uuid;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public PhysicsState getPhysicsState() {
        return physicsState;
    }

    public void setPhysicsState(PhysicsState physicsState) {
        this.physicsState = physicsState;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public Vector3d getLastValidPosition() {
        return lastValidPosition;
    }

    public void setLastValidPosition(Vector3d lastValidPosition) {
        this.lastValidPosition = lastValidPosition;
    }

    public double getViolationLevel(String checkName) {
        return violationLevels.getOrDefault(checkName, 0.0);
    }

    public void setViolationLevel(String checkName, double value) {
        violationLevels.put(checkName, value);
    }

    public double addViolationLevel(String checkName, double amount) {
        return violationLevels.merge(checkName, amount, Double::sum);
    }

    public Map<String, Double> getViolationLevels() {
        return violationLevels;
    }

    public Map<String, Integer> getCheckBuffers() {
        return checkBuffers;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public boolean isUsingItem() {
        return usingItem;
    }

    public void setUsingItem(boolean usingItem) {
        this.usingItem = usingItem;
    }

    public int getHeldItemSlot() {
        return heldItemSlot;
    }

    public void setHeldItemSlot(int heldItemSlot) {
        this.heldItemSlot = heldItemSlot;
    }

    public int getHeldItemKnockbackLevel() {
        return heldItemKnockbackLevel;
    }

    public void setHeldItemKnockbackLevel(int heldItemKnockbackLevel) {
        this.heldItemKnockbackLevel = heldItemKnockbackLevel;
    }

    public long getLastArmSwingNanos() {
        return lastArmSwingNanos;
    }

    public void setLastArmSwingNanos(long lastArmSwingNanos) {
        this.lastArmSwingNanos = lastArmSwingNanos;
    }

    public double getFallDistance() {
        return fallDistance;
    }

    public void setFallDistance(double fallDistance) {
        this.fallDistance = fallDistance;
    }

    public int getLiquidStableTicks() {
        return liquidStableTicks;
    }

    public void setLiquidStableTicks(int liquidStableTicks) {
        this.liquidStableTicks = liquidStableTicks;
    }

    public int getWallClimbTicks() {
        return wallClimbTicks;
    }

    public void setWallClimbTicks(int wallClimbTicks) {
        this.wallClimbTicks = wallClimbTicks;
    }

    public void exemptFor(long seconds) {
        this.exemptUntilNanos = System.nanoTime() + seconds * 1_000_000_000L;
    }

    public boolean isExempt() {
        return System.nanoTime() < exemptUntilNanos;
    }

    /** Scaled by 1e4 so MathUtil.gcd/variance operate on integer degree-fractions instead of raw floats. */
    public void recordYawDelta(float delta) {
        recentYawDeltasMicroDegrees.addFirst(Math.round(delta * 10_000.0));
        while (recentYawDeltasMicroDegrees.size() > ROTATION_SAMPLE_LIMIT) {
            recentYawDeltasMicroDegrees.pollLast();
        }
    }

    public java.util.List<Long> getRecentYawDeltas() {
        return new java.util.ArrayList<>(recentYawDeltasMicroDegrees);
    }

    public void recordClick(long nowNanos) {
        recentClickTimestampsNanos.addFirst(nowNanos);
        pruneOlderThan(recentClickTimestampsNanos, nowNanos, CLICK_WINDOW_NANOS);
    }

    public java.util.List<Long> getRecentClickTimestamps() {
        return new java.util.ArrayList<>(recentClickTimestampsNanos);
    }

    public void recordAttack(long nowNanos, UUID targetUuid) {
        recentAttacks.addFirst(new RecentAttack(nowNanos, targetUuid));
        while (true) {
            RecentAttack oldest = recentAttacks.peekLast();
            if (oldest == null || nowNanos - oldest.getTimestampNanos() <= ATTACK_WINDOW_NANOS) {
                break;
            }
            recentAttacks.pollLast();
        }
    }

    public java.util.List<RecentAttack> getRecentAttacks() {
        return new java.util.ArrayList<>(recentAttacks);
    }

    public void recordPlacement(long nowNanos, Vector3d blockPosition, float yaw, float pitch) {
        recentPlacements.addFirst(new PlacementRecord(nowNanos, blockPosition, yaw, pitch));
        while (true) {
            PlacementRecord oldest = recentPlacements.peekLast();
            if (oldest == null || nowNanos - oldest.getTimestampNanos() <= PLACEMENT_WINDOW_NANOS) {
                break;
            }
            recentPlacements.pollLast();
        }
    }

    public java.util.List<PlacementRecord> getRecentPlacements() {
        return new java.util.ArrayList<>(recentPlacements);
    }

    public void setPendingKnockback(Vector3d expectedVelocity, long expiryNanos) {
        this.pendingKnockback = expectedVelocity;
        this.pendingKnockbackExpiryNanos = expiryNanos;
    }

    public Vector3d consumePendingKnockback(long nowNanos) {
        Vector3d value = pendingKnockback;
        if (value == null) {
            return null;
        }
        pendingKnockback = null;
        return nowNanos <= pendingKnockbackExpiryNanos ? value : null;
    }

    private static void pruneOlderThan(ConcurrentLinkedDeque<Long> deque, long nowNanos, long windowNanos) {
        Iterator<Long> descendingIterator = deque.descendingIterator();
        while (descendingIterator.hasNext()) {
            long timestamp = descendingIterator.next();
            if (nowNanos - timestamp > windowNanos) {
                descendingIterator.remove();
            } else {
                break;
            }
        }
    }
}
