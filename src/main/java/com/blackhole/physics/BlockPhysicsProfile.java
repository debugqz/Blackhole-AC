package com.blackhole.physics;

/**
 * Physics behaviour of a block type. Data-driven so adding a new special
 * block later is a registry entry, not a new branch inside PredictionEngine.
 */
public final class BlockPhysicsProfile {

    public static final BlockPhysicsProfile DEFAULT_SOLID = builder().build();
    public static final BlockPhysicsProfile AIR = builder().solid(false).build();

    private final boolean solid;
    private final double slipperiness;
    private final LiquidType liquid;
    private final double velocityMultiplier;
    private final double extraHorizontalFriction;
    private final boolean climbable;
    private final boolean bouncy;
    private final double hitboxTopOffset;

    private BlockPhysicsProfile(Builder builder) {
        this.solid = builder.solid;
        this.slipperiness = builder.slipperiness;
        this.liquid = builder.liquid;
        this.velocityMultiplier = builder.velocityMultiplier;
        this.extraHorizontalFriction = builder.extraHorizontalFriction;
        this.climbable = builder.climbable;
        this.bouncy = builder.bouncy;
        this.hitboxTopOffset = builder.hitboxTopOffset;
    }

    public boolean isSolid() {
        return solid;
    }

    public double getSlipperiness() {
        return slipperiness;
    }

    public LiquidType getLiquid() {
        return liquid;
    }

    public boolean isLiquid() {
        return liquid != LiquidType.NONE;
    }

    public double getVelocityMultiplier() {
        return velocityMultiplier;
    }

    public double getExtraHorizontalFriction() {
        return extraHorizontalFriction;
    }

    public boolean isClimbable() {
        return climbable;
    }

    public boolean isBouncy() {
        return bouncy;
    }

    public double getHitboxTopOffset() {
        return hitboxTopOffset;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean solid = true;
        private double slipperiness = 0.6;
        private LiquidType liquid = LiquidType.NONE;
        private double velocityMultiplier = 1.0;
        private double extraHorizontalFriction = 1.0;
        private boolean climbable = false;
        private boolean bouncy = false;
        private double hitboxTopOffset = 0.0;

        public Builder solid(boolean solid) {
            this.solid = solid;
            return this;
        }

        public Builder slipperiness(double slipperiness) {
            this.slipperiness = slipperiness;
            return this;
        }

        public Builder liquid(LiquidType liquid) {
            this.liquid = liquid;
            return this;
        }

        public Builder velocityMultiplier(double velocityMultiplier) {
            this.velocityMultiplier = velocityMultiplier;
            return this;
        }

        public Builder extraHorizontalFriction(double extraHorizontalFriction) {
            this.extraHorizontalFriction = extraHorizontalFriction;
            return this;
        }

        public Builder climbable(boolean climbable) {
            this.climbable = climbable;
            return this;
        }

        public Builder bouncy(boolean bouncy) {
            this.bouncy = bouncy;
            return this;
        }

        public Builder hitboxTopOffset(double hitboxTopOffset) {
            this.hitboxTopOffset = hitboxTopOffset;
            return this;
        }

        public BlockPhysicsProfile build() {
            return new BlockPhysicsProfile(this);
        }
    }
}
