package com.blackhole.check.moves;

import com.blackhole.check.Check;
import com.blackhole.data.PlayerData;
import com.blackhole.data.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Fall distance is tracked independently by CheckManager (off our own
 * collision-derived ground truth, see PredictionEngine.isSolidBelow) so a
 * client lying about onGround in packets can't make the server skip fall
 * damage it should have taken.
 */
public final class NoFallCheck extends Check implements Listener {

    private final PlayerDataManager playerDataManager;

    public NoFallCheck(PlayerDataManager playerDataManager) {
        super("NoFall");
        this.playerDataManager = playerDataManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isEnabled() || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        PlayerData data = playerDataManager.get(player.getUniqueId());
        if (data == null) {
            return;
        }

        double expectedDamage = Math.max(0, Math.floor(data.getFallDistance() - 3.0));
        if (expectedDamage > 0 && event.getDamage() < expectedDamage - 1.0) {
            data.addViolationLevel(getName(), expectedDamage - event.getDamage());
        }
        data.setFallDistance(0.0);
    }
}
