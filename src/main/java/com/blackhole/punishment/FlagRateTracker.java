package com.blackhole.punishment;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory 5-minute sliding window of ALL flags combined, across every check
 * (section 9). A pure failsafe for servers without active staff - independent
 * of whether any single check reached its own punish-VL.
 */
public final class FlagRateTracker {

    private static final long WINDOW_NANOS = 5L * 60 * 1_000_000_000L;

    private final Map<UUID, ConcurrentLinkedDeque<Long>> flagsByPlayer = new ConcurrentHashMap<>();

    /** Records one flag (any check) and returns the count still inside the 5-minute window. */
    public int recordFlag(UUID uuid) {
        long now = System.nanoTime();
        ConcurrentLinkedDeque<Long> deque = flagsByPlayer.computeIfAbsent(uuid, u -> new ConcurrentLinkedDeque<>());
        deque.addFirst(now);

        while (true) {
            Long oldest = deque.peekLast();
            if (oldest == null || now - oldest <= WINDOW_NANOS) {
                break;
            }
            deque.pollLast();
        }
        return deque.size();
    }

    public void remove(UUID uuid) {
        flagsByPlayer.remove(uuid);
    }
}
