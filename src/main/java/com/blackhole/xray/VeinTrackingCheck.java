package com.blackhole.xray;

import com.blackhole.util.Vector3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Straight-tunnel-to-vein detection (section 8, signal 3): legitimate mining
 * wanders (following ore, avoiding caves, branching); a tunnel with an
 * abnormally constant direction vector over several consecutive blocks reads
 * as "aimed", not explored.
 */
public final class VeinTrackingCheck {

    private static final int WINDOW = 8;
    private static final double MIN_DIRECTION_CONSISTENCY = 0.8;

    private final Map<UUID, Deque<Vector3d>> history = new ConcurrentHashMap<>();

    public boolean isSuspiciousTunnel(UUID uuid, Vector3d newPosition) {
        Deque<Vector3d> deque = history.computeIfAbsent(uuid, u -> new ArrayDeque<>());
        deque.addFirst(newPosition);
        while (deque.size() > WINDOW) {
            deque.removeLast();
        }
        if (deque.size() < WINDOW) {
            return false;
        }

        List<Vector3d> positions = new ArrayList<>(deque);
        List<Vector3d> directions = new ArrayList<>();
        for (int i = 0; i < positions.size() - 1; i++) {
            Vector3d diff = positions.get(i).subtract(positions.get(i + 1));
            double length = diff.length();
            if (length < 1.0E-4) {
                continue;
            }
            directions.add(diff.multiply(1.0 / length));
        }
        if (directions.size() < 2) {
            return false;
        }

        for (int i = 0; i < directions.size() - 1; i++) {
            Vector3d a = directions.get(i);
            Vector3d b = directions.get(i + 1);
            double dot = a.x * b.x + a.y * b.y + a.z * b.z;
            if (dot < MIN_DIRECTION_CONSISTENCY) {
                return false;
            }
        }
        return true;
    }

    public void remove(UUID uuid) {
        history.remove(uuid);
    }
}
