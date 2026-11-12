package com.limelight.vr;

import java.util.ArrayDeque;

/**
 * The smallest of the values added over the last {@code windowNs}. Values are added in time order; each add and
 * {@link #min()} take constant time on average. Used for "the fastest frame lately", the baseline that a frame's
 * delay at each stage is measured against.
 */
final class WindowMin {
    private final long windowNs;
    /** {time, value}, oldest first, with increasing values. */
    private final ArrayDeque<long[]> entries = new ArrayDeque<>();

    WindowMin(long windowNs) {
        this.windowNs = windowNs;
    }

    void add(long timeNs, long value) {
        while (!entries.isEmpty() && entries.peekLast()[1] >= value) {
            entries.pollLast();
        }
        entries.addLast(new long[] {timeNs, value});
        while (entries.peekFirst()[0] < timeNs - windowNs) {
            entries.pollFirst();
        }
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The smallest value in the window, or 0 if nothing was added yet. */
    long min() {
        return entries.isEmpty() ? 0 : entries.peekFirst()[1];
    }

    void clear() {
        entries.clear();
    }
}
