package com.blackhole.physics;

/**
 * Output of PredictionEngine.validateMovement: the check-facing MovementResult
 * plus the PhysicsState that must be persisted onto PlayerData for the next
 * network tick (velocity carries over - packets never expose raw input).
 */
public final class NetworkTickResult {

    private final MovementResult movementResult;
    private final PhysicsState nextState;

    public NetworkTickResult(MovementResult movementResult, PhysicsState nextState) {
        this.movementResult = movementResult;
        this.nextState = nextState;
    }

    public MovementResult getMovementResult() {
        return movementResult;
    }

    public PhysicsState getNextState() {
        return nextState;
    }
}
