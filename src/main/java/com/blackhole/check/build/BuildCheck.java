package com.blackhole.check.build;

import com.blackhole.check.Check;
import com.blackhole.check.CheckResult;
import com.blackhole.data.PlayerData;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;

/** Evaluated right after a block placement is recorded, not per movement tick. */
public abstract class BuildCheck extends Check {

    protected BuildCheck(String name) {
        super(name);
    }

    public abstract CheckResult evaluate(PlayerData data, BlockSnapshotProvider provider, BlockPhysicsRegistry registry);
}
