package com.blackhole.physics;

import com.blackhole.util.Vector3d;

/**
 * Immutable snapshot of everything PredictionEngine needs to simulate the
 * next tick. Kept separate from PlayerData (which also holds VLs, history,
 * locks) so the engine itself stays pure and independently testable.
 */
public final class PhysicsState {

    private final Vector3d position;
    private final Vector3d velocity;
    private final boolean onGround;
    private final boolean sneaking;
    private final boolean sprinting;
    private final boolean usingItem;
    private final int speedAmplifier;
    private final int slownessAmplifier;
    private final int jumpBoostAmplifier;

    public PhysicsState(Vector3d position, Vector3d velocity, boolean onGround, boolean sneaking, boolean sprinting,
                         boolean usingItem, int speedAmplifier, int slownessAmplifier, int jumpBoostAmplifier) {
        this.position = position;
        this.velocity = velocity;
        this.onGround = onGround;
        this.sneaking = sneaking;
        this.sprinting = sprinting;
        this.usingItem = usingItem;
        this.speedAmplifier = speedAmplifier;
        this.slownessAmplifier = slownessAmplifier;
        this.jumpBoostAmplifier = jumpBoostAmplifier;
    }

    public Vector3d getPosition() {
        return position;
    }

    public Vector3d getVelocity() {
        return velocity;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public boolean isUsingItem() {
        return usingItem;
    }

    public int getSpeedAmplifier() {
        return speedAmplifier;
    }

    public int getSlownessAmplifier() {
        return slownessAmplifier;
    }

    public int getJumpBoostAmplifier() {
        return jumpBoostAmplifier;
    }

    public PhysicsState withPosition(Vector3d newPosition) {
        return new PhysicsState(newPosition, velocity, onGround, sneaking, sprinting, usingItem,
                speedAmplifier, slownessAmplifier, jumpBoostAmplifier);
    }

    public PhysicsState withVelocity(Vector3d newVelocity) {
        return new PhysicsState(position, newVelocity, onGround, sneaking, sprinting, usingItem,
                speedAmplifier, slownessAmplifier, jumpBoostAmplifier);
    }

    public PhysicsState withOnGround(boolean newOnGround) {
        return new PhysicsState(position, velocity, newOnGround, sneaking, sprinting, usingItem,
                speedAmplifier, slownessAmplifier, jumpBoostAmplifier);
    }

    public PhysicsState withSneaking(boolean newSneaking) {
        return new PhysicsState(position, velocity, onGround, newSneaking, sprinting, usingItem,
                speedAmplifier, slownessAmplifier, jumpBoostAmplifier);
    }

    public PhysicsState withSprinting(boolean newSprinting) {
        return new PhysicsState(position, velocity, onGround, sneaking, newSprinting, usingItem,
                speedAmplifier, slownessAmplifier, jumpBoostAmplifier);
    }

    public PhysicsState withUsingItem(boolean newUsingItem) {
        return new PhysicsState(position, velocity, onGround, sneaking, sprinting, newUsingItem,
                speedAmplifier, slownessAmplifier, jumpBoostAmplifier);
    }
}
