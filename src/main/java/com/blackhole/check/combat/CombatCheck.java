package com.blackhole.check.combat;

import com.blackhole.check.Check;
import com.blackhole.check.CheckResult;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;

public abstract class CombatCheck extends Check {

    protected CombatCheck(String name) {
        super(name);
    }

    public abstract CheckResult evaluate(AttackContext context, BlockSnapshotProvider provider, BlockPhysicsRegistry registry);
}
