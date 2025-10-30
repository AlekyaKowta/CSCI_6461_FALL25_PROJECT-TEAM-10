package src.main.java.core.cache;

import src.main.java.core.MachineState;

public class Cache {
    private static final int NUM_LINES = 16;
    private final CacheLine[] lines = new CacheLine[NUM_LINES];

    // Global counter used to assign a unique 'age' for FIFO replacement
    private int globalAgeCounter = 0;

    private MachineState machineState; // Reference to MachineState for memory access

    public Cache(MachineState machineState) {
        this.machineState = machineState;
        for (int i = 0; i < NUM_LINES; i++) {
            lines[i] = new CacheLine();
        }
    }

    /**
     * Splits an 11-bit memory address into its Tag and Word Offset components.
     * @param address The 11-bit memory address.
     * @return An array: [0] = Tag (8 bits), [1] = Offset (3 bits).
     */
    private int[] decomposeAddress(int address) {
        // Tag (8 bits): Bits 10 down to 3
        int tag = address >> CacheLine.WORDS_PER_LINE; // address / 8

        // Word Offset (3 bits): Bits 2 down to 0
        int offset = address & 0b111; // address % 8

        return new int[]{tag, offset};
    }

    // --- Public Access Methods ---

    /**
     * Reads a 16-bit word from the cache, fetching from memory on a miss.
     * @param address The 11-bit memory address to read.
     * @return The 16-bit word data.
     */
    public int readWord(int address) {
        int[] components = decomposeAddress(address);
        int tag = components[0];
        int offset = components[1];

        CacheLine hitLine = null;

        // 1. Search for a Cache Hit
        for (CacheLine line : lines) {
            if (line.isValid() && line.getTag() == tag) {
                hitLine = line;
                System.out.println("Cache HIT: Reading from address " + address); // For demonstration
                break;
            }
        }

        // 2. Handle Hit/Miss
        if (hitLine != null) {
            // HIT: Update age and return data
            hitLine.setAge(globalAgeCounter++);
            return hitLine.getData(offset);
        } else {
            // MISS: Load block from memory
            System.out.println("Cache MISS: Loading block for address " + address); // For demonstration
            CacheLine victimLine = findVictimLine();
            loadBlockFromMemory(victimLine, address, tag);

            // Return data from the newly loaded line
            return victimLine.getData(offset);
        }
    }

    /**
     * Writes a 16-bit word using a Write-Through policy.
     * Updates the cache on a hit, and always updates main memory.
     * @param address The 11-bit memory address to write.
     * @param value The 16-bit data value to write.
     */
    public void writeWord(int address, int value) {
        int[] components = decomposeAddress(address);
        int tag = components[0];
        int offset = components[1];

        // 1. Write-Through to Main Memory (Always happens)
        machineState.setMemoryDirect(address, value);

        // 2. Update Cache (on a Hit)
        for (CacheLine line : lines) {
            if (line.isValid() && line.getTag() == tag) {
                // Cache HIT: Update age and data
                line.setData(offset, value);
                line.setAge(globalAgeCounter++);
                System.out.println("Cache HIT (Write-Through): Updated address " + address); // For demonstration
                return;
            }
        }

        // Cache MISS: We follow a No-Write-Allocate policy (only memory updated, cache not loaded)
        // This is a common simplification for Write-Through.
        System.out.println("Cache MISS (Write-Through): Only memory updated for address " + address); // For demonstration
    }


    // --- Internal Logic ---

    /**
     * Finds the victim line for replacement using the FIFO algorithm.
     * Prioritizes an invalid line, otherwise selects the oldest valid line.
     * @return The CacheLine to be replaced.
     */
    private CacheLine findVictimLine() {
        CacheLine oldestLine = null;
        int minAge = Integer.MAX_VALUE; // Initialize to max to ensure any valid age is smaller

        for (CacheLine line : lines) {
            // 1. Check for an empty (invalid) line first (highest priority victim)
            if (!line.isValid()) {
                System.out.println("Found invalid line for victim.");
                return line;
            }

            // 2. Otherwise, track the line with the smallest (oldest) age
            if (line.getAge() < minAge) {
                minAge = line.getAge();
                oldestLine = line;
            }
        }

        // At this point, the cache is full (no invalid line was found)
        System.out.println("Found oldest line (age: " + minAge + ") for victim.");
        // oldestLine will be the line with the minimum age, since the loop checked all 16 lines
        return oldestLine;
    }

    /**
     * Fetches an 8-word block from memory and loads it into the victim line.
     * @param victimLine The cache line to load data into.
     * @param address The memory address that caused the miss.
     * @param tag The tag component of the address.
     */
    private void loadBlockFromMemory(CacheLine victimLine, int address, int tag) {
        // Find the start address of the 8-word block
        // Block Start Address = (Tag * 8)
        int blockStartAddress = tag * CacheLine.WORDS_PER_LINE;

        int[] blockData = new int[CacheLine.WORDS_PER_LINE];
        for (int i = 0; i < CacheLine.WORDS_PER_LINE; i++) {
            blockData[i] = machineState.getMemoryDirect(blockStartAddress + i);
        }

        // Load data into the victim line
        victimLine.setAllData(blockData);
        victimLine.setTag(tag);
        victimLine.setValid(true);
        // Assign the newest age
        victimLine.setAge(globalAgeCounter++);
    }

    // Getter for the UI to display cache contents
    public CacheLine[] getCacheLines() {
        return lines;
    }
}
