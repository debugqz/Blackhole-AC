package com.blackhole.physics;

import com.blackhole.util.Vector3d;

/**
 * Simulates vanilla 1.8 player physics tick-by-tick. Move checks never
 * reimplement rules of their own - they only interpret the MovementResult
 * this produces (predicted vs. reported position + deviation axis).
 */
public final class PredictionEngine {

    private static final double GRAVITY = 0.08;
    private static final double VERTICAL_DRAG = 0.98;
    private static final double AIR_FRICTION = 0.91;
    private static final double BASE_WALK_SPEED = 0.1;
    private static final double SPRINT_MULTIPLIER = 1.3;
    private static final double USING_ITEM_MULTIPLIER = 0.2;
    private static final double JUMP_VELOCITY = 0.42;
    private static final double JUMP_BOOST_PER_LEVEL = 0.1;
    private static final double SPRINT_JUMP_IMPULSE = 0.2;
    private static final double AIR_MOVE_FACTOR = 0.02;
    private static final double LIQUID_MOVE_FACTOR = 0.02;
    private static final double WATER_DRAG = 0.8;
    private static final double LAVA_DRAG = 0.5;
    private static final double WATER_GRAVITY = 0.02;
    private static final double WATER_SWIM_UP_IMPULSE = 0.04;
    private static final double LAVA_SWIM_UP_IMPULSE = 0.02;
    private static final double CLIMB_MAX_HORIZONTAL = 0.15;
    private static final double CLIMB_MAX_DESCEND_SPEED = 0.15;
    private static final double SLIME_BOUNCE_THRESHOLD = -0.1;

    /** Default tolerance for float/round-trip noise between predicted and reported position. */
    private double epsilon = 0.03;

    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    public double getEpsilon() {
        return epsilon;
    }

    public PhysicsState simulateTick(PhysicsState state, MovementInput input, BlockSnapshotProvider provider,
                                      BlockPhysicsRegistry registry) {
        Vector3d position = state.getPosition();
        Vector3d velocity = state.getVelocity();

        BlockPhysicsProfile groundProfile = groundProfile(position, provider, registry);
        BlockPhysicsProfile feetProfile = feetProfile(position, provider, registry);
        boolean inWeb = overlapsWeb(position, provider, registry);
        boolean climbing = feetProfile.isClimbable();

        if (feetProfile.isLiquid()) {
            velocity = simulateLiquidTick(state, input, feetProfile, velocity);
        } else {
            velocity = applyHorizontalInput(state, input, velocity, groundProfile);
            velocity = applyJump(state, input, velocity, groundProfile);
        }

        if (climbing) {
            velocity = clampClimbing(velocity, state.isSneaking());
        }

        if (inWeb) {
            velocity = velocity.multiply(0.05, 0.05, 0.05);
        }

        Vector3d preCollisionVelocity = velocity;
        CollisionResult collision = resolveCollision(position, velocity, provider, registry);
        position = collision.position;
        velocity = collision.velocity;
        boolean onGround = collision.onGround;

        if (onGround && collision.groundProfile != null && collision.groundProfile.isBouncy()
                && preCollisionVelocity.y < SLIME_BOUNCE_THRESHOLD && !state.isSneaking()) {
            velocity = velocity.withY(-preCollisionVelocity.y);
            onGround = false;
        }

        if (!feetProfile.isLiquid()) {
            velocity = applyFrictionAndGravity(velocity, onGround, groundProfile);
        }

        return new PhysicsState(position, velocity, onGround, state.isSneaking(), state.isSprinting(), state.isUsingItem(),
                state.getSpeedAmplifier(), state.getSlownessAmplifier(), state.getJumpBoostAmplifier());
    }

    /**
     * Validates one network movement tick against a PhysicsState carried over from
     * the previous tick. Movement packets never expose raw WASD input, so instead
     * of forward-simulating from a known input this checks whether the reported
     * position falls inside the "reachable envelope": previous velocity (already
     * friction/gravity-decayed) plus at most one tick's worth of legitimate
     * acceleration (bounded by the speed factor, in any horizontal direction) and,
     * vertically, either free-fall/ground physics or one inferred jump impulse.
     * The velocity carried into the next tick is derived from the actual reported
     * delta, not the guessed input, so single flagged ticks never compound errors.
     */
    public NetworkTickResult validateMovement(PhysicsState previous, Vector3d reportedPosition, boolean reportedOnGround,
                                               float yaw, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        Vector3d prevPos = previous.getPosition();
        Vector3d v0 = previous.getVelocity();

        BlockPhysicsProfile groundProfile = groundProfile(prevPos, provider, registry);
        BlockPhysicsProfile feetProfile = feetProfile(prevPos, provider, registry);
        boolean climbing = feetProfile.isClimbable();
        boolean liquid = feetProfile.isLiquid();

        Vector3d predictedPosition = validateHorizontal(previous, prevPos, reportedPosition, v0, groundProfile, climbing, liquid);
        predictedPosition = validateVertical(previous, prevPos, reportedPosition, predictedPosition, v0, climbing, liquid,
                provider, registry);

        MovementResult result = compare(predictedPosition, reportedPosition);

        Vector3d observedDelta = reportedPosition.subtract(prevPos);
        Vector3d nextVelocity;
        if (climbing) {
            nextVelocity = clampClimbing(observedDelta, previous.isSneaking());
        } else if (liquid) {
            double drag = feetProfile.getLiquid() == LiquidType.LAVA ? LAVA_DRAG : WATER_DRAG;
            nextVelocity = observedDelta.multiply(drag, drag, drag).withY(observedDelta.y * drag - WATER_GRAVITY);
        } else {
            BlockPhysicsProfile nextGroundProfile = reportedOnGround ? groundProfile(reportedPosition, provider, registry) : groundProfile;
            nextVelocity = applyFrictionAndGravity(observedDelta, reportedOnGround, nextGroundProfile);
        }

        PhysicsState nextState = new PhysicsState(reportedPosition, nextVelocity, reportedOnGround,
                previous.isSneaking(), previous.isSprinting(), previous.isUsingItem(), previous.getSpeedAmplifier(),
                previous.getSlownessAmplifier(), previous.getJumpBoostAmplifier());

        return new NetworkTickResult(result, nextState);
    }

    private Vector3d validateHorizontal(PhysicsState previous, Vector3d prevPos, Vector3d reportedPosition, Vector3d v0,
                                         BlockPhysicsProfile groundProfile, boolean climbing, boolean liquid) {
        double radius;
        if (climbing) {
            radius = CLIMB_MAX_HORIZONTAL + epsilon;
        } else if (liquid) {
            radius = LIQUID_MOVE_FACTOR + Math.max(Math.abs(v0.x), Math.abs(v0.z)) + 0.15 + epsilon;
        } else {
            radius = computeSpeedFactor(previous, groundProfile) + epsilon;
        }

        double envelopeX = prevPos.x + v0.x;
        double envelopeZ = prevPos.z + v0.z;
        double diffX = reportedPosition.x - envelopeX;
        double diffZ = reportedPosition.z - envelopeZ;
        double distance = Math.sqrt(diffX * diffX + diffZ * diffZ);

        if (distance <= radius || distance < 1.0E-9) {
            return new Vector3d(reportedPosition.x, prevPos.y, reportedPosition.z);
        }

        double scale = radius / distance;
        double clampedX = envelopeX + diffX * scale;
        double clampedZ = envelopeZ + diffZ * scale;
        return new Vector3d(clampedX, prevPos.y, clampedZ);
    }

    private Vector3d validateVertical(PhysicsState previous, Vector3d prevPos, Vector3d reportedPosition,
                                       Vector3d predictedSoFar, Vector3d v0, boolean climbing, boolean liquid,
                                       BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        if (climbing || liquid) {
            return predictedSoFar.withY(reportedPosition.y);
        }

        double freeFallY = prevPos.y + v0.y;
        double freeFallDiff = Math.abs(reportedPosition.y - freeFallY);

        if (previous.isOnGround()) {
            double jumpVelocity = JUMP_VELOCITY + previous.getJumpBoostAmplifier() * JUMP_BOOST_PER_LEVEL;
            double jumpY = prevPos.y + jumpVelocity;
            double jumpDiff = Math.abs(reportedPosition.y - jumpY);
            if (jumpDiff <= freeFallDiff) {
                return predictedSoFar.withY(jumpDiff <= epsilon ? reportedPosition.y : jumpY);
            }
        }

        return predictedSoFar.withY(freeFallDiff <= epsilon ? reportedPosition.y : freeFallY);
    }

    public MovementResult compare(Vector3d predictedPosition, Vector3d reportedPosition) {
        Vector3d delta = reportedPosition.subtract(predictedPosition);

        boolean horizontalOff = Math.sqrt(delta.x * delta.x + delta.z * delta.z) > epsilon;
        boolean verticalOff = Math.abs(delta.y) > epsilon;

        DeviationAxis axis;
        if (horizontalOff && verticalOff) {
            axis = DeviationAxis.BOTH;
        } else if (horizontalOff) {
            axis = DeviationAxis.HORIZONTAL;
        } else if (verticalOff) {
            axis = DeviationAxis.VERTICAL;
        } else {
            axis = DeviationAxis.NONE;
        }

        return new MovementResult(predictedPosition, reportedPosition, delta, axis, axis == DeviationAxis.NONE);
    }

    public MovementResult compare(PhysicsState predicted, Vector3d reportedPosition) {
        return compare(predicted.getPosition(), reportedPosition);
    }

    private Vector3d applyHorizontalInput(PhysicsState state, MovementInput input, Vector3d velocity,
                                           BlockPhysicsProfile groundProfile) {
        double speedFactor = computeSpeedFactor(state, groundProfile);
        return moveFlying(input.getForward(), input.getStrafe(), input.getYaw(), speedFactor, velocity);
    }

    private double computeSpeedFactor(PhysicsState state, BlockPhysicsProfile groundProfile) {
        if (state.isOnGround()) {
            double slip = groundProfile.getSlipperiness() * AIR_FRICTION;
            double moveFactor = 0.16277136 / (slip * slip * slip);
            double walkSpeed = BASE_WALK_SPEED;
            if (state.isSprinting()) {
                walkSpeed *= SPRINT_MULTIPLIER;
            }
            walkSpeed *= speedPotionMultiplier(state);
            if (state.isUsingItem()) {
                walkSpeed *= USING_ITEM_MULTIPLIER;
            }
            return walkSpeed * moveFactor;
        }
        return AIR_MOVE_FACTOR;
    }

    private double speedPotionMultiplier(PhysicsState state) {
        return Math.max(0.0, 1.0 + 0.2 * state.getSpeedAmplifier() - 0.2 * state.getSlownessAmplifier());
    }

    private Vector3d moveFlying(double forward, double strafe, float yaw, double speedFactor, Vector3d velocity) {
        double lengthSq = forward * forward + strafe * strafe;
        if (lengthSq < 1.0E-4) {
            return velocity;
        }
        double length = Math.sqrt(lengthSq);
        if (length < 1.0) {
            length = 1.0;
        }
        double scale = speedFactor / length;
        double f = forward * scale;
        double s = strafe * scale;
        double sinYaw = Math.sin(Math.toRadians(yaw));
        double cosYaw = Math.cos(Math.toRadians(yaw));
        return velocity.add(s * cosYaw - f * sinYaw, 0.0, f * cosYaw + s * sinYaw);
    }

    private Vector3d applyJump(PhysicsState state, MovementInput input, Vector3d velocity,
                                BlockPhysicsProfile groundProfile) {
        if (!input.isJumping() || !state.isOnGround()) {
            return velocity;
        }
        double jumpVelocity = JUMP_VELOCITY + state.getJumpBoostAmplifier() * JUMP_BOOST_PER_LEVEL;
        velocity = velocity.withY(jumpVelocity);
        if (state.isSprinting()) {
            double yawRad = Math.toRadians(input.getYaw());
            velocity = velocity.add(-Math.sin(yawRad) * SPRINT_JUMP_IMPULSE, 0.0, Math.cos(yawRad) * SPRINT_JUMP_IMPULSE);
        }
        return velocity;
    }

    private Vector3d simulateLiquidTick(PhysicsState state, MovementInput input, BlockPhysicsProfile feetProfile,
                                         Vector3d velocity) {
        velocity = moveFlying(input.getForward(), input.getStrafe(), input.getYaw(), LIQUID_MOVE_FACTOR, velocity);
        double drag = feetProfile.getLiquid() == LiquidType.LAVA ? LAVA_DRAG : WATER_DRAG;
        velocity = velocity.multiply(drag, drag, drag);
        velocity = velocity.withY(velocity.y - WATER_GRAVITY);
        if (input.isJumping()) {
            double impulse = feetProfile.getLiquid() == LiquidType.LAVA ? LAVA_SWIM_UP_IMPULSE : WATER_SWIM_UP_IMPULSE;
            velocity = velocity.withY(velocity.y + impulse);
        }
        return velocity;
    }

    private Vector3d clampClimbing(Vector3d velocity, boolean sneaking) {
        double x = com.blackhole.util.MathUtil.clamp(velocity.x, -CLIMB_MAX_HORIZONTAL, CLIMB_MAX_HORIZONTAL);
        double z = com.blackhole.util.MathUtil.clamp(velocity.z, -CLIMB_MAX_HORIZONTAL, CLIMB_MAX_HORIZONTAL);
        double y = Math.max(velocity.y, -CLIMB_MAX_DESCEND_SPEED);
        if (sneaking && y < 0) {
            y = 0.0;
        }
        return new Vector3d(x, y, z);
    }

    private Vector3d applyFrictionAndGravity(Vector3d velocity, boolean onGround, BlockPhysicsProfile groundProfile) {
        double friction = onGround ? groundProfile.getSlipperiness() * AIR_FRICTION : AIR_FRICTION;
        double horizontalExtra = onGround ? groundProfile.getExtraHorizontalFriction() : 1.0;
        double x = velocity.x * friction * horizontalExtra;
        double z = velocity.z * friction * horizontalExtra;
        double y = (velocity.y - GRAVITY) * VERTICAL_DRAG;
        return new Vector3d(x, y, z);
    }

    private CollisionResult resolveCollision(Vector3d position, Vector3d velocity, BlockSnapshotProvider provider,
                                              BlockPhysicsRegistry registry) {
        PlayerBoundingBox box = PlayerBoundingBox.atFeet(position);

        double dy = box.clampAxis(PlayerBoundingBox.Axis.Y, velocity.y, provider, registry);
        box = box.offset(0, dy, 0);

        BoxStepResult stepX = moveWithStep(box, velocity.x, PlayerBoundingBox.Axis.X, provider, registry);
        box = stepX.box;
        double dx = stepX.delta;

        BoxStepResult stepZ = moveWithStep(box, velocity.z, PlayerBoundingBox.Axis.Z, provider, registry);
        box = stepZ.box;
        double dz = stepZ.delta;

        Vector3d newPosition = new Vector3d(position.x + dx, box.getMinY(), position.z + dz);

        double resolvedVelX = dx != velocity.x ? 0.0 : velocity.x;
        double resolvedVelY = dy != velocity.y ? 0.0 : velocity.y;
        double resolvedVelZ = dz != velocity.z ? 0.0 : velocity.z;

        boolean onGround = dy < velocity.y - 1.0E-7 && velocity.y <= 0;

        BlockPhysicsProfile groundProfile = null;
        if (onGround) {
            groundProfile = groundProfile(newPosition, provider, registry);
        }

        return new CollisionResult(newPosition, new Vector3d(resolvedVelX, resolvedVelY, resolvedVelZ), onGround, groundProfile);
    }

    private BoxStepResult moveWithStep(PlayerBoundingBox box, double delta, PlayerBoundingBox.Axis axis,
                                        BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        double direct = box.clampAxis(axis, delta, provider, registry);
        if (Math.abs(direct - delta) < 1.0E-9) {
            PlayerBoundingBox moved = axis == PlayerBoundingBox.Axis.X ? box.offset(direct, 0, 0) : box.offset(0, 0, direct);
            return new BoxStepResult(moved, direct);
        }

        double stepUp = box.clampAxis(PlayerBoundingBox.Axis.Y, PlayerBoundingBox.STEP_HEIGHT, provider, registry);
        if (stepUp <= 1.0E-9) {
            PlayerBoundingBox moved = axis == PlayerBoundingBox.Axis.X ? box.offset(direct, 0, 0) : box.offset(0, 0, direct);
            return new BoxStepResult(moved, direct);
        }

        PlayerBoundingBox raised = box.offset(0, stepUp, 0);
        double steppedDelta = raised.clampAxis(axis, delta, provider, registry);
        if (Math.abs(steppedDelta) > Math.abs(direct)) {
            PlayerBoundingBox moved = axis == PlayerBoundingBox.Axis.X ? raised.offset(steppedDelta, 0, 0) : raised.offset(0, 0, steppedDelta);
            double settleDown = moved.clampAxis(PlayerBoundingBox.Axis.Y, -stepUp, provider, registry);
            moved = moved.offset(0, settleDown, 0);
            return new BoxStepResult(moved, steppedDelta);
        }

        PlayerBoundingBox moved = axis == PlayerBoundingBox.Axis.X ? box.offset(direct, 0, 0) : box.offset(0, 0, direct);
        return new BoxStepResult(moved, direct);
    }

    /**
     * Ground truth independent of the client's reported onGround flag - used by
     * NoFallCheck so a client that lies about touching ground can't dodge fall
     * damage bookkeeping.
     */
    public boolean isSolidBelow(Vector3d position, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        return groundProfile(position, provider, registry).isSolid();
    }

    private BlockPhysicsProfile groundProfile(Vector3d position, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y - 0.01);
        int bz = (int) Math.floor(position.z);
        return provider.getProfile(registry, bx, by, bz);
    }

    private BlockPhysicsProfile feetProfile(Vector3d position, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y + 0.01);
        int bz = (int) Math.floor(position.z);
        return provider.getProfile(registry, bx, by, bz);
    }

    private boolean overlapsWeb(Vector3d position, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        PlayerBoundingBox box = PlayerBoundingBox.atFeet(position);
        int minBX = (int) Math.floor(box.getMinX());
        int maxBX = (int) Math.floor(box.getMaxX());
        int minBY = (int) Math.floor(box.getMinY());
        int maxBY = (int) Math.floor(box.getMaxY());
        int minBZ = (int) Math.floor(box.getMinZ());
        int maxBZ = (int) Math.floor(box.getMaxZ());
        for (int x = minBX; x <= maxBX; x++) {
            for (int y = minBY; y <= maxBY; y++) {
                for (int z = minBZ; z <= maxBZ; z++) {
                    if (provider.getProfile(registry, x, y, z).getVelocityMultiplier() < 1.0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static final class CollisionResult {
        final Vector3d position;
        final Vector3d velocity;
        final boolean onGround;
        final BlockPhysicsProfile groundProfile;

        CollisionResult(Vector3d position, Vector3d velocity, boolean onGround, BlockPhysicsProfile groundProfile) {
            this.position = position;
            this.velocity = velocity;
            this.onGround = onGround;
            this.groundProfile = groundProfile;
        }
    }

    private static final class BoxStepResult {
        final PlayerBoundingBox box;
        final double delta;

        BoxStepResult(PlayerBoundingBox box, double delta) {
            this.box = box;
            this.delta = delta;
        }
    }
}
