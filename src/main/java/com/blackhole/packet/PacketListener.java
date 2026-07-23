package com.blackhole.packet;

import com.blackhole.check.CheckManager;
import com.blackhole.check.combat.AttackContext;
import com.blackhole.data.EntityHistoryManager;
import com.blackhole.data.EntitySnapshot;
import com.blackhole.data.PlayerData;
import com.blackhole.data.PlayerDataManager;
import com.blackhole.physics.BlockPhysicsRegistry;
import com.blackhole.physics.BlockSnapshotProvider;
import com.blackhole.physics.NetworkTickResult;
import com.blackhole.physics.PhysicsState;
import com.blackhole.physics.PredictionEngine;
import com.blackhole.util.MathUtil;
import com.blackhole.util.Vector3d;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Dispatches PacketContainers built from raw Netty-intercepted packets
 * (section 3) - independent of ProtocolLib's own listener/scheduler. Fully
 * wires the movement and combat packet families into PredictionEngine /
 * EntityHistoryManager / CheckManager; build/xray packets are parsed here
 * but only stubbed for now, their real handling lands in phases 4-5.
 */
public final class PacketListener {

    private static final long ARM_SWING_WINDOW_NANOS = 150_000_000L;

    private final Plugin plugin;
    private final PlayerDataManager playerDataManager;
    private final PredictionEngine predictionEngine;
    private final BlockSnapshotProvider blockSnapshotProvider;
    private final BlockPhysicsRegistry blockPhysicsRegistry;
    private final CheckManager checkManager;
    private final SyncManager syncManager;
    private final EntityHistoryManager entityHistoryManager;

    public PacketListener(Plugin plugin, PlayerDataManager playerDataManager, PredictionEngine predictionEngine,
                           BlockSnapshotProvider blockSnapshotProvider, BlockPhysicsRegistry blockPhysicsRegistry,
                           CheckManager checkManager, SyncManager syncManager, EntityHistoryManager entityHistoryManager) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.predictionEngine = predictionEngine;
        this.blockSnapshotProvider = blockSnapshotProvider;
        this.blockPhysicsRegistry = blockPhysicsRegistry;
        this.checkManager = checkManager;
        this.syncManager = syncManager;
        this.entityHistoryManager = entityHistoryManager;
    }

    public void onPacketReceived(Player player, PacketContainer packet, long receivedAtNanos) {
        try {
            PacketType type = packet.getType();
            if (type == PacketType.Play.Client.POSITION || type == PacketType.Play.Client.LOOK
                    || type == PacketType.Play.Client.POSITION_LOOK) {
                handleMovement(player, packet, type);
            } else if (type == PacketType.Play.Client.ARM_ANIMATION) {
                handleArmAnimation(player);
            } else if (type == PacketType.Play.Client.ENTITY_ACTION) {
                handleEntityAction(player, packet);
            } else if (type == PacketType.Play.Client.HELD_ITEM_SLOT) {
                handleHeldItemSlot(player, packet);
            } else if (type == PacketType.Play.Client.TRANSACTION) {
                handleTransaction(player, packet);
            } else if (type == PacketType.Play.Client.USE_ENTITY) {
                handleUseEntity(player, packet, receivedAtNanos);
            } else if (type == PacketType.Play.Client.BLOCK_PLACE || type == PacketType.Play.Client.BLOCK_DIG) {
                handleBlockAction(player, packet, type);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error despachando paquete de " + player.getName(), e);
        }
    }

    public void onPacketSent(Player player, PacketContainer packet, long sentAtNanos) {
        // Reserved for outbound sync bookkeeping (section 5) as later phases need it.
    }

    private void handleMovement(Player player, PacketContainer packet, PacketType type) {
        PlayerData data = playerDataManager.get(player.getUniqueId());
        if (data == null) {
            return;
        }

        boolean hasLook = type == PacketType.Play.Client.LOOK || type == PacketType.Play.Client.POSITION_LOOK;
        if (hasLook) {
            float newYaw = packet.getFloat().read(0);
            float newPitch = packet.getFloat().read(1);
            data.recordYawDelta(MathUtil.wrapDegrees(newYaw - data.getYaw()));
            data.setYaw(newYaw);
            data.setPitch(newPitch);
        }

        boolean hasPosition = type == PacketType.Play.Client.POSITION || type == PacketType.Play.Client.POSITION_LOOK;
        if (!hasPosition) {
            return;
        }

        double x = packet.getDoubles().read(0);
        double y = packet.getDoubles().read(1);
        double z = packet.getDoubles().read(2);
        boolean onGround = packet.getBooleans().read(0);
        float yaw = data.getYaw();

        data.getLock().lock();
        try {
            Vector3d reportedPosition = new Vector3d(x, y, z);
            Vector3d previousPosition = data.getPhysicsState().getPosition();

            // Sneaking/sprinting/usingItem only change via separate packets (EntityAction,
            // BlockPlace/BlockDig), never through PhysicsState itself - refresh them from
            // PlayerData's live flags here or computeSpeedFactor would keep using whatever
            // was true back when this PhysicsState was first created.
            PhysicsState currentState = data.getPhysicsState()
                    .withSneaking(data.isSneaking())
                    .withSprinting(data.isSprinting())
                    .withUsingItem(data.isUsingItem());

            NetworkTickResult tickResult = predictionEngine.validateMovement(currentState, reportedPosition,
                    onGround, yaw, blockSnapshotProvider, blockPhysicsRegistry);

            data.setPhysicsState(tickResult.getNextState());
            checkManager.processMovement(player, data, previousPosition, tickResult.getMovementResult(),
                    blockSnapshotProvider, blockPhysicsRegistry);
        } finally {
            data.getLock().unlock();
        }
    }

    private void handleArmAnimation(Player player) {
        PlayerData data = playerDataManager.get(player.getUniqueId());
        if (data != null) {
            long now = System.nanoTime();
            data.setLastArmSwingNanos(now);
            data.recordClick(now);
        }
    }

    private void handleEntityAction(Player player, PacketContainer packet) {
        PlayerData data = playerDataManager.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        EnumWrappers.PlayerAction action = packet.getPlayerActions().read(0);
        if (action == EnumWrappers.PlayerAction.START_SNEAKING) {
            data.setSneaking(true);
        } else if (action == EnumWrappers.PlayerAction.STOP_SNEAKING) {
            data.setSneaking(false);
        } else if (action == EnumWrappers.PlayerAction.START_SPRINTING) {
            data.setSprinting(true);
        } else if (action == EnumWrappers.PlayerAction.STOP_SPRINTING) {
            data.setSprinting(false);
        }
    }

    private void handleHeldItemSlot(Player player, PacketContainer packet) {
        PlayerData data = playerDataManager.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        int slot = packet.getIntegers().read(0);
        data.setHeldItemSlot(slot);
        // Inventory reads need the main thread; combat evaluation itself stays off it (see AttackContext).
        plugin.getServer().getScheduler().runTask(plugin, () -> refreshHeldItemKnockbackLevel(player, data, slot));
    }

    private void refreshHeldItemKnockbackLevel(Player player, PlayerData data, int slot) {
        ItemStack item = player.getInventory().getItem(slot);
        int level = item != null ? item.getEnchantmentLevel(Enchantment.KNOCKBACK) : 0;
        data.setHeldItemKnockbackLevel(level);
    }

    private void handleTransaction(Player player, PacketContainer packet) {
        short id = packet.getShorts().read(0);
        syncManager.onTransactionResponse(player, id);
    }

    private void handleUseEntity(Player player, PacketContainer packet, long receivedAtNanos) {
        EnumWrappers.EntityUseAction action = packet.getEntityUseActions().read(0);
        if (action != EnumWrappers.EntityUseAction.ATTACK) {
            return;
        }

        PlayerData attackerData = playerDataManager.get(player.getUniqueId());
        if (attackerData == null) {
            return;
        }

        int targetEntityId = packet.getIntegers().read(0);
        UUID targetUuid = entityHistoryManager.resolveUuid(targetEntityId);
        if (targetUuid == null) {
            return;
        }

        double pingMillis = syncManager.getPingMillis(player.getUniqueId());
        long rewindNanos = (long) (pingMillis * 1_000_000.0 / 2.0);
        EntitySnapshot rewound = entityHistoryManager.getSnapshotAt(targetUuid, receivedAtNanos - rewindNanos);

        boolean hadRecentSwing = (receivedAtNanos - attackerData.getLastArmSwingNanos()) < ARM_SWING_WINDOW_NANOS;
        attackerData.recordAttack(receivedAtNanos, targetUuid);

        AttackContext context = new AttackContext(player, attackerData, targetUuid, rewound, receivedAtNanos, hadRecentSwing);
        checkManager.processAttack(context, blockSnapshotProvider, blockPhysicsRegistry);

        PlayerData victimData = playerDataManager.get(targetUuid);
        if (victimData != null) {
            checkManager.getAntiKnockbackCheck().registerExpectedKnockback(victimData,
                    attackerData.getPhysicsState().getPosition(), attackerData.getYaw(), attackerData.isSprinting(),
                    attackerData.getHeldItemKnockbackLevel(), receivedAtNanos);
        }
    }

    private void handleBlockAction(Player player, PacketContainer packet, PacketType type) {
        PlayerData data = playerDataManager.get(player.getUniqueId());
        if (data == null) {
            return;
        }

        if (type == PacketType.Play.Client.BLOCK_DIG) {
            EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().read(0);
            if (digType == EnumWrappers.PlayerDigType.RELEASE_USE_ITEM) {
                data.setUsingItem(false);
            }
            // START_DESTROY_BLOCK/STOP_DESTROY_BLOCK feed XrayStatsManager/OreRevealCheck starting phase 5.
            return;
        }

        try {
            BlockPosition position = packet.getBlockPositionModifier().read(0);
            boolean isRealPlacement = position != null && !(position.getX() == -1 && position.getY() == -1 && position.getZ() == -1);

            if (!isRealPlacement) {
                data.setUsingItem(true);
                return;
            }

            data.setUsingItem(false);
            long now = System.nanoTime();
            data.recordPlacement(now, new Vector3d(position.getX(), position.getY(), position.getZ()), data.getYaw(), data.getPitch());
            checkManager.processPlacement(player, data, blockSnapshotProvider, blockPhysicsRegistry);
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "No se pudo interpretar BlockPlace de " + player.getName(), e);
        }
    }
}
