package com.blackhole.physics;

/**
 * Per-tick input the prediction engine needs beyond the current PhysicsState:
 * analog forward/strafe (-1..1, as derived from held movement keys), facing
 * yaw, and the jump key state. Sourced from PacketPlayInFlying/EntityAction
 * once phase 2 wires packet capture in.
 */
public final class MovementInput {

    private final double forward;
    private final double strafe;
    private final float yaw;
    private final boolean jumping;

    public MovementInput(double forward, double strafe, float yaw, boolean jumping) {
        this.forward = forward;
        this.strafe = strafe;
        this.yaw = yaw;
        this.jumping = jumping;
    }

    public double getForward() {
        return forward;
    }

    public double getStrafe() {
        return strafe;
    }

    public float getYaw() {
        return yaw;
    }

    public boolean isJumping() {
        return jumping;
    }
}
