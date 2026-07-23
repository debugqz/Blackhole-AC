package com.blackhole.data;

import com.blackhole.util.Vector3d;
import org.bukkit.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * ~1s position/rotation history per entity (section 5.4). Combat checks never
 * validate against an entity's live server position - they rewind to the
 * moment the attacker's client actually rendered it, which lag makes
 * different from "now". Written only from the main thread (entity state
 * isn't safe to read off it); read from any Netty thread during an attack.
 */
public final class EntityHistoryManager {

    private static final long HISTORY_WINDOW_NANOS = 1_500_000_000L;

    private final Map<UUID, ConcurrentLinkedDeque<EntitySnapshot>> history = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> entityIdToUuid = new ConcurrentHashMap<>();

    /** Main-thread only. */
    public void record(Entity entity, long nowNanos) {
        entityIdToUuid.put(entity.getEntityId(), entity.getUniqueId());

        ConcurrentLinkedDeque<EntitySnapshot> deque = history.computeIfAbsent(entity.getUniqueId(),
                u -> new ConcurrentLinkedDeque<>());

        Vector3d position = new Vector3d(entity.getLocation().getX(), entity.getLocation().getY(), entity.getLocation().getZ());
        deque.addFirst(new EntitySnapshot(nowNanos, position, entity.getLocation().getYaw(), entity.getLocation().getPitch()));

        while (true) {
            EntitySnapshot oldest = deque.peekLast();
            if (oldest == null || nowNanos - oldest.getTimestamp() <= HISTORY_WINDOW_NANOS) {
                break;
            }
            deque.pollLast();
        }
    }

    /** Returns the snapshot closest to (but not after) targetNanos, or the entity's current snapshot if history is empty. */
    public EntitySnapshot getSnapshotAt(UUID uuid, long targetNanos) {
        ConcurrentLinkedDeque<EntitySnapshot> deque = history.get(uuid);
        if (deque == null || deque.isEmpty()) {
            return null;
        }

        EntitySnapshot best = deque.peekFirst();
        for (EntitySnapshot snapshot : deque) {
            if (snapshot.getTimestamp() <= targetNanos) {
                return snapshot;
            }
            best = snapshot;
        }
        return best;
    }

    /** Newest-first copy of whatever history is held for uuid - used to build punishment evidence (section 9). */
    public java.util.List<EntitySnapshot> getRecentSnapshots(UUID uuid) {
        ConcurrentLinkedDeque<EntitySnapshot> deque = history.get(uuid);
        return deque == null ? java.util.Collections.emptyList() : new java.util.ArrayList<>(deque);
    }

    public void remove(UUID uuid) {
        history.remove(uuid);
    }

    /** Thread-safe: entity ID -> UUID is only ever written from the main-thread sampling pass. */
    public UUID resolveUuid(int entityId) {
        return entityIdToUuid.get(entityId);
    }
}
