package com.blackhole.check.moves;

import com.blackhole.check.Check;
import com.blackhole.check.CheckResult;
import com.blackhole.data.PlayerData;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.physics.MovementResult;

/**
 * Move checks only interpret the MovementResult PredictionEngine already
 * produced (deviation axis/magnitude) - they never recompute physics rules of
 * their own. BlockSnapshotProvider/registry are exposed only for cheap,
 * read-only context queries (e.g. "is there a solid block beside the
 * player"), not to reimplement collision or friction.
 */
public abstract class MovementCheck extends Check {

    protected MovementCheck(String name) {
        super(name);
    }

    public abstract CheckResult evaluate(PlayerData data, MovementResult result, BlockSnapshotProvider provider,
                                          BlockPhysicsRegistry registry);
}
