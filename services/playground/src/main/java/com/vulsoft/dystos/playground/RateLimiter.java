package com.vulsoft.dystos.playground;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Limiteur à fenêtre glissante : au plus {@code limit} requêtes par clé et par fenêtre. */
final class RateLimiter {

    private static final int MAX_KEYS = 20_000;

    private final int limit;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    RateLimiter(int limit, long windowMillis) {
        this.limit = limit;
        this.windowMillis = windowMillis;
    }

    synchronized boolean tryAcquire(String key, long nowMillis) {
        if (hits.size() > MAX_KEYS) {
            hits.values().removeIf(d -> d.isEmpty() || nowMillis - d.peekLast() >= windowMillis);
        }
        Deque<Long> d = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!d.isEmpty() && nowMillis - d.peekFirst() >= windowMillis) {
            d.pollFirst();
        }
        if (d.size() >= limit) {
            return false;
        }
        d.addLast(nowMillis);
        return true;
    }
}
