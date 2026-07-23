package com.blackhole.check;

import com.blackhole.check.combat.AimbotCheck;
import com.blackhole.check.combat.AntiKnockbackCheck;
import com.blackhole.check.combat.AttackContext;
import com.blackhole.check.combat.AutoClickerCheck;
import com.blackhole.check.combat.CombatCheck;
import com.blackhole.check.combat.KillAuraCheck;
import com.blackhole.check.combat.ReachCheck;
import com.blackhole.check.build.BuildCheck;
import com.blackhole.check.build.NoSlowCheck;
import com.blackhole.check.build.ScaffoldCheck;
import com.blackhole.check.build.TowerCheck;
import com.blackhole.check.moves.FlyCheck;
import com.blackhole.check.moves.JesusCheck;
import com.blackhole.check.moves.MovementCheck;
import com.blackhole.check.moves.SpeedCheck;
import com.blackhole.check.moves.SpiderCheck;
import com.blackhole.data.PlayerData;
import com.blackhole.physics.BlockPhysicsProfile;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.physics.DeviationAxis;
import com.blackhole.physics.MovementResult;
import com.blackhole.physics.PredictionEngine;
import com.blackhole.punishment.PunishmentManager;
import com.blackhole.util.Vector3d;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registers move checks and, per network tick, updates the small pieces of
 * cross-tick context they need (fall distance, liquid-stability streak,
 * wall-climb streak) before letting each check interpret the tick's
 * MovementResult. Grace ticks (section 5.2) are applied generically here so
 * individual checks don't each reimplement tolerance buffering.
 */
public final class CheckManager {

    private static final int GRACE_TICKS = 2;
    private static final double LIQUID_STABLE_EPSILON = 0.02;
    private static final double WALL_CLIMB_MIN_RISE = 0.05;

    private final PredictionEngine predictionEngine;
    private final PunishmentManager punishmentManager;
    private final List<MovementCheck> movementChecks = new CopyOnWriteArrayList<>();
    private final List<CombatCheck> combatChecks = new CopyOnWriteArrayList<>();
    private final List<BuildCheck> buildChecks = new CopyOnWriteArrayList<>();
    private final AntiKnockbackCheck antiKnockbackCheck = new AntiKnockbackCheck();

    public CheckManager(PredictionEngine predictionEngine, PunishmentManager punishmentManager) {
        this.predictionEngine = predictionEngine;
        this.punishmentManager = punishmentManager;
        movementChecks.add(new SpeedCheck());
        movementChecks.add(new FlyCheck());
        movementChecks.add(new JesusCheck());
        movementChecks.add(new SpiderCheck());
        movementChecks.add(new NoSlowCheck());
        combatChecks.add(new ReachCheck());
        combatChecks.add(new KillAuraCheck());
        combatChecks.add(new AimbotCheck());
        combatChecks.add(new AutoClickerCheck());
        buildChecks.add(new ScaffoldCheck());
        buildChecks.add(new TowerCheck());
    }

    public List<MovementCheck> getMovementChecks() {
        return movementChecks;
    }

    public List<CombatCheck> getCombatChecks() {
        return combatChecks;
    }

    public List<BuildCheck> getBuildChecks() {
        return buildChecks;
    }

    public AntiKnockbackCheck getAntiKnockbackCheck() {
        return antiKnockbackCheck;
    }

    /** Section 13: "activar/desactivar cada check individualmente" - re-read on enable and on /ac reload. */
    public void applyConfig(FileConfiguration config) {
        List<Check> all = new ArrayList<>();
        all.addAll(movementChecks);
        all.addAll(combatChecks);
        all.addAll(buildChecks);
        all.add(antiKnockbackCheck);
        for (Check check : all) {
            check.setEnabled(config.getBoolean("checks." + check.getName() + ".enabled", true));
        }
    }

    public void processMovement(Player player, PlayerData data, Vector3d previousPosition, MovementResult result,
                                 BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        if (isBypassed(player, data)) {
            data.setLastValidPosition(result.getReportedPosition());
            return;
        }

        Vector3d reportedPosition = result.getReportedPosition();
        BlockPhysicsProfile feetProfile = feetProfileAt(previousPosition, provider, registry);

        if (result.isWithinTolerance()) {
            data.setLastValidPosition(reportedPosition);
        }

        updateFallDistance(data, previousPosition, reportedPosition, provider, registry);
        updateLiquidStableTicks(data, feetProfile, reportedPosition.y - previousPosition.y);
        updateWallClimbTicks(data, previousPosition, result, feetProfile, reportedPosition, provider, registry);

        for (MovementCheck check : movementChecks) {
            if (!check.isEnabled()) {
                continue;
            }
            CheckResult checkResult = check.evaluate(data, result, provider, registry);
            handleResult(player, data, check, checkResult);
        }

        if (antiKnockbackCheck.isEnabled()) {
            CheckResult kbResult = antiKnockbackCheck.evaluateOnMovement(data, previousPosition, result, System.nanoTime());
            handleResult(player, data, antiKnockbackCheck, kbResult);
        }
    }

    public void processAttack(AttackContext context, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        if (isBypassed(context.getAttacker(), context.getAttackerData())) {
            return;
        }
        for (CombatCheck check : combatChecks) {
            if (!check.isEnabled()) {
                continue;
            }
            CheckResult checkResult = check.evaluate(context, provider, registry);
            handleResult(context.getAttacker(), context.getAttackerData(), check, checkResult);
        }
    }

    public void processPlacement(Player player, PlayerData data, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        if (isBypassed(player, data)) {
            return;
        }
        for (BuildCheck check : buildChecks) {
            if (!check.isEnabled()) {
                continue;
            }
            CheckResult checkResult = check.evaluate(data, provider, registry);
            handleResult(player, data, check, checkResult);
        }
    }

    private boolean isBypassed(Player player, PlayerData data) {
        return data.isExempt() || player.hasPermission("anticheat.bypass");
    }

    private void handleResult(Player player, PlayerData data, Check check, CheckResult result) {
        Map<String, Integer> buffers = data.getCheckBuffers();
        if (!result.isViolated()) {
            buffers.put(check.getName(), 0);
            return;
        }

        int buffer = buffers.merge(check.getName(), 1, Integer::sum);
        if (buffer <= GRACE_TICKS) {
            return;
        }

        punishmentManager.onFlag(player, data, check.getName(), result.getVlAmount(), result.getDetails());
    }

    private void updateFallDistance(PlayerData data, Vector3d previousPosition, Vector3d reportedPosition,
                                     BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        if (predictionEngine.isSolidBelow(reportedPosition, provider, registry)) {
            data.setFallDistance(0.0);
            return;
        }
        double drop = previousPosition.y - reportedPosition.y;
        if (drop > 0) {
            data.setFallDistance(data.getFallDistance() + drop);
        }
    }

    private void updateLiquidStableTicks(PlayerData data, BlockPhysicsProfile feetProfile, double deltaY) {
        if (!feetProfile.isLiquid()) {
            data.setLiquidStableTicks(0);
            return;
        }
        if (Math.abs(deltaY) < LIQUID_STABLE_EPSILON) {
            data.setLiquidStableTicks(data.getLiquidStableTicks() + 1);
        } else {
            data.setLiquidStableTicks(0);
        }
    }

    private void updateWallClimbTicks(PlayerData data, Vector3d previousPosition, MovementResult result,
                                       BlockPhysicsProfile feetProfile, Vector3d reportedPosition,
                                       BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        boolean verticalViolation = result.getDeviationAxis() == DeviationAxis.VERTICAL
                || result.getDeviationAxis() == DeviationAxis.BOTH;
        boolean movingUp = result.getDelta().y > 0 || (reportedPosition.y - previousPosition.y) > WALL_CLIMB_MIN_RISE;

        if (verticalViolation && movingUp && !feetProfile.isClimbable() && !feetProfile.isLiquid()
                && isAdjacentToSolid(reportedPosition, provider, registry)) {
            data.setWallClimbTicks(data.getWallClimbTicks() + 1);
        } else {
            data.setWallClimbTicks(0);
        }
    }

    private boolean isAdjacentToSolid(Vector3d position, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y + 0.9);
        int bz = (int) Math.floor(position.z);
        return provider.getProfile(registry, bx + 1, by, bz).isSolid()
                || provider.getProfile(registry, bx - 1, by, bz).isSolid()
                || provider.getProfile(registry, bx, by, bz + 1).isSolid()
                || provider.getProfile(registry, bx, by, bz - 1).isSolid();
    }

    private BlockPhysicsProfile feetProfileAt(Vector3d position, BlockSnapshotProvider provider, BlockPhysicsRegistry registry) {
        int bx = (int) Math.floor(position.x);
        int by = (int) Math.floor(position.y + 0.01);
        int bz = (int) Math.floor(position.z);
        return provider.getProfile(registry, bx, by, bz);
    }
}
