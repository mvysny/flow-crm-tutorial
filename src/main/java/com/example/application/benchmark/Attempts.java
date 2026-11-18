package com.example.application.benchmark;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts what the user tried, for the benchmark's report: a scenario that took three saves
 * shows it, a lucky first guess too.
 *
 * <pre>{@code
 * save.addClickListener(e -> { Attempts.count("save"); ... });
 * }</pre>
 *
 * <p>Thread-safe.
 */
public final class Attempts {

    private static final Map<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    private Attempts() {
    }

    public static void count(String what) {
        COUNTS.computeIfAbsent(what, k -> new AtomicInteger()).incrementAndGet();
    }

    static void reset() {
        COUNTS.clear();
    }

    static Map<String, Integer> snapshot() {
        Map<String, Integer> result = new TreeMap<>();
        COUNTS.forEach((what, count) -> result.put(what, count.get()));
        return result;
    }
}
