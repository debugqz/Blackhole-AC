package com.blackhole.physics;

import com.blackhole.util.Vector3d;

/**
 * Comparison between PredictionEngine's predicted position and the position
 * the client actually reported. Move checks (Speed/Fly/Jesus/Spider/NoFall)
 * only interpret this - they never recompute physics themselves.
 */
public final class MovementResult {

    private final Vector3d predictedPosition;
    private final Vector3d reportedPosition;
    private final Vector3d delta;
    private final DeviationAxis deviationAxis;
    private final boolean withinTolerance;

    public MovementResult(Vector3d predictedPosition, Vector3d reportedPosition, Vector3d delta,
                           DeviationAxis deviationAxis, boolean withinTolerance) {
        this.predictedPosition = predictedPosition;
        this.reportedPosition = reportedPosition;
        this.delta = delta;
        this.deviationAxis = deviationAxis;
        this.withinTolerance = withinTolerance;
    }

    public Vector3d getPredictedPosition() {
        return predictedPosition;
    }

    public Vector3d getReportedPosition() {
        return reportedPosition;
    }

    public Vector3d getDelta() {
        return delta;
    }

    public DeviationAxis getDeviationAxis() {
        return deviationAxis;
    }

    public boolean isWithinTolerance() {
        return withinTolerance;
    }
}
