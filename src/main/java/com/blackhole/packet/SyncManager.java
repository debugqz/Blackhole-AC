package com.blackhole.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transaction-packet sync points (section 5.1): the server sends a Transaction
 * with a unique id, the client echoes it back untouched, and the round trip
 * anchors "this inbound packet corresponds to roughly this client-side
 * moment" instead of assuming it arrived the same tick it was processed.
 * Also tracks a ping EWMA that phase-3 rewind uses to size its lookback.
 */
public final class SyncManager {

    private static final double PING_EWMA_ALPHA = 0.25;

    private final Map<UUID, Map<Short, Long>> pendingTransactions = new ConcurrentHashMap<>();
    private final Map<UUID, Double> pingEstimateMillis = new ConcurrentHashMap<>();
    private final AtomicInteger idSequence = new AtomicInteger(1);

    public short sendTransaction(Player player) {
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        short id = (short) idSequence.incrementAndGet();

        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.TRANSACTION);
        packet.getIntegers().write(0, 0);
        packet.getShorts().write(0, id);
        packet.getBooleans().write(0, false);

        pendingTransactions.computeIfAbsent(player.getUniqueId(), u -> new ConcurrentHashMap<>())
                .put(id, System.nanoTime());

        try {
            protocolManager.sendServerPacket(player, packet, false);
        } catch (Exception ignored) {
            pendingTransactions.getOrDefault(player.getUniqueId(), java.util.Collections.emptyMap()).remove(id);
        }
        return id;
    }

    /** Called by PacketListener when a Transaction packet comes back from the client. */
    public void onTransactionResponse(Player player, short id) {
        Map<Short, Long> pending = pendingTransactions.get(player.getUniqueId());
        if (pending == null) {
            return;
        }
        Long sentAt = pending.remove(id);
        if (sentAt == null) {
            return;
        }
        double roundTripMillis = (System.nanoTime() - sentAt) / 1_000_000.0;
        pingEstimateMillis.merge(player.getUniqueId(), roundTripMillis,
                (oldValue, newValue) -> oldValue + PING_EWMA_ALPHA * (newValue - oldValue));
    }

    public double getPingMillis(UUID uuid) {
        return pingEstimateMillis.getOrDefault(uuid, 50.0);
    }

    public void remove(UUID uuid) {
        pendingTransactions.remove(uuid);
        pingEstimateMillis.remove(uuid);
    }
}
