package src.main.java.core.cache;

import src.main.java.core.MachineState;

/**
 * Implements a simple, unified, fully associative cache for the simulator.
 * * - Size: 16 lines
 * - Block Size: 1 word
 * - Replacement Policy: FIFO (First-In, First-Out)
 * - Write Policy: Write-Through (writes go to cache and main memory)
 * - Allocation Policy: Write-Allocate (a write miss loads the block into cache)
 */
public class Cache {
    private final int CACHE_SIZE = 16;
    private final CacheLine[] lines;
    private int fifoPointer;
    private MachineState machineState; // Reference to main memory

    public enum AccessKind { READ_HIT, READ_MISS, WRITE_HIT, WRITE_MISS, NONE }

    private int lastAccessIndex = -1;
    private AccessKind lastAccessKind = AccessKind.NONE;


    /**
     * Inner class representing a single cache line.
     */
    private class CacheLine {
        boolean valid;
        int tag;  // For a fully associative, 1-word block cache, the tag is the full address.
        int data; // The 16-bit data word

        public CacheLine() {
            this.valid = false;
            this.tag = 0;
            this.data = 0;
        }
    }

    public Cache(MachineState state) {
        this.machineState = state;
        this.lines = new CacheLine[CACHE_SIZE];
        this.fifoPointer = 0;
        for (int i = 0; i < CACHE_SIZE; i++) {
            lines[i] = new CacheLine();
        }
    }

    /**
     * Clears the cache by invalidating all lines.
     * This is called by MachineState.initialize() during a machine reset.
     */
//    public void initialize() {
//        for (int i = 0; i < CACHE_SIZE; i++) {
//            lines[i].valid = false;
//        }
//        this.fifoPointer = 0;
//    }

    public void initialize() {
        for (int i = 0; i < CACHE_SIZE; i++) lines[i].valid = false;
        this.fifoPointer = 0;
        this.lastAccessIndex = -1;
        this.lastAccessKind = AccessKind.NONE;
    }


    /**
     * Finds the index of a cache line matching the address.
     * @param address The 12-bit memory address (which serves as the tag).
     * @return The index (0-15) if found (cache hit), or -1 if not found (cache miss).
     */
    private int findLine(int address) {
        for (int i = 0; i < CACHE_SIZE; i++) {
            if (lines[i].valid && lines[i].tag == address) {
                return i; // Cache Hit
            }
        }
        return -1; // Cache Miss
    }

    /**
     * Loads a word into the cache using the FIFO replacement policy.
     * @param address The address (tag) to load.
     * @param data The 16-bit data from memory.
     */
    private int loadLine(int address, int data) {
        int replacedIndex = fifoPointer;               // index being written
        CacheLine lineToReplace = lines[replacedIndex];
        lineToReplace.valid = true;
        lineToReplace.tag = address;
        lineToReplace.data = data;
        fifoPointer = (fifoPointer + 1) % CACHE_SIZE;  // advance for next time
        return replacedIndex;
    }


    /**
     * Reads a word from memory, using the cache.
     * This is called by MachineState.getMemory().
     */
    public int readWord(int address) {
        int lineIndex = findLine(address);
        if (lineIndex != -1) {
            lastAccessIndex = lineIndex;
            lastAccessKind  = AccessKind.READ_HIT;
            return lines[lineIndex].data;
        } else {
            int data = machineState.getMemoryDirect(address);
            int idx  = loadLine(address, data);
            lastAccessIndex = idx;
            lastAccessKind  = AccessKind.READ_MISS;
            return data;
        }
    }

    /**
     * Writes a word to memory, using the cache.
     * This is called by MachineState.setMemory().
     */
    public void writeWord(int address, int value) {
        machineState.setMemoryDirect(address, value); // write-through
        int lineIndex = findLine(address);
        if (lineIndex != -1) {
            lines[lineIndex].data = value;
            lastAccessIndex = lineIndex;
            lastAccessKind  = AccessKind.WRITE_HIT;
        } else {
            int idx = loadLine(address, value); // write-allocate
            lastAccessIndex = idx;
            lastAccessKind  = AccessKind.WRITE_MISS;
        }
    }


    /**
     * Generates a string representation of the cache state for display in the UI.
     * This directly demonstrates the cache's behavior.
     */
    public String getCacheStateString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("FIFO Ptr -> %02d\n", fifoPointer));
        sb.append("LN | V | Tag(Oct) | Data(Oct)\n");
        sb.append("---|---|----------|----------\n");

        for (int i = 0; i < CACHE_SIZE; i++) {
            CacheLine line = lines[i];
            sb.append(String.format("%02d | %d | %s | %s\n",
                    i,
                    line.valid ? 1 : 0,
                    line.valid ? String.format("%04o", line.tag) : "----",
                    line.valid ? String.format("%06o", line.data) : "------"
            ));
        }
        return sb.toString();
    }

    public static final class SnapshotLine {
        public final boolean valid;
        public final int tag;
        public final int data;
        public SnapshotLine(boolean valid, int tag, int data) {
            this.valid = valid; this.tag = tag; this.data = data;
        }
    }

    public static final class Snapshot {
        public final int fifoPointer;
        public final SnapshotLine[] lines;
        public final int lastAccessIndex;
        public final Cache.AccessKind lastAccessKind;
        public Snapshot(int fifoPointer, SnapshotLine[] lines, int lastAccessIndex, Cache.AccessKind kind) {
            this.fifoPointer = fifoPointer;
            this.lines = lines;
            this.lastAccessIndex = lastAccessIndex;
            this.lastAccessKind = kind;
        }
    }

    public Snapshot snapshot() {
        SnapshotLine[] snap = new SnapshotLine[CACHE_SIZE];
        for (int i = 0; i < CACHE_SIZE; i++) {
            CacheLine cl = lines[i];
            snap[i] = new SnapshotLine(cl.valid, cl.tag, cl.data);
        }
        return new Snapshot(fifoPointer, snap, lastAccessIndex, lastAccessKind);
    }
}


