package com.ofek.clicktimer;

import java.util.NoSuchElementException;

/** Sorted timeline using absolute epoch millis (System.currentTimeMillis). */
public final class TimeQueue {
    private static final class Node {
        long dueAtMs;   // absolute epoch ms when this fires
        int codeCase;
        Node next;
        Node(long dueAtMs, int codeCase) { this.dueAtMs = dueAtMs; this.codeCase = codeCase; }
    }

    public static final class First {
        public long remainingMs; // max(0, dueAt - now)
        public final int codeCase;
        public long DelayRemaingMs;
        public boolean isFreezed = false;
        private First(long remainingMs, int codeCase) {
            this.remainingMs = remainingMs; this.codeCase = codeCase; DelayRemaingMs = 0;
        }
        public void Freez(){
            DelayRemaingMs = remainingMs - System.currentTimeMillis();
            isFreezed = true;
        }
        public void UnFreez(){
            remainingMs = remainingMs + System.currentTimeMillis();
            isFreezed = false;
        }
    }

    private Node head;

    /** Add with a relative delay (ms). Stored as absolute epoch time. */
    public void addTime(long delayMs, int codeCase) {
        long now = System.currentTimeMillis();
        long due = now + delayMs;
        Node n = new Node(due, codeCase);

        if (head == null || due < head.dueAtMs) { n.next = head; head = n; return; }
        Node cur = head;
        while (cur.next != null && due >= cur.next.dueAtMs) cur = cur.next;
        n.next = cur.next;
        cur.next = n;
    }

    /** Pop earliest: returns remaining time from NOW and its code. */
    public First getFirst() {
        if (head == null) throw new NoSuchElementException("empty");
        long remaining = Math.max(0L, head.dueAtMs);
        int code = head.codeCase;
        head = head.next;
        return new First(remaining, code);
    }

    /** Shift nothing in wall-clock mode. Kept for API parity. */
    public void timePassed(long ignored) {
        // no-op: absolute times already account for real time passing
    }

    public boolean isEmpty() { return head == null; }
}
