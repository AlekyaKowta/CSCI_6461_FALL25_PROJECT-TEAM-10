package src.main.java.core.cache;

// A single line in the cache
public class CacheLine {
    // 8 words per line, 16-bit words (stored as int/short in Java)
    public static final int WORDS_PER_LINE = 8;

    private boolean valid = false; // Valid Bit: Indicates if the line has valid data
    private int tag = 0;           // Tag: 8 high-order bits of the memory address (0-255)
    private int[] data = new int[WORDS_PER_LINE]; // Data: 8 words of 16-bit data
    private int age = 0;           // Age Counter: Used for FIFO replacement (higher number is newer)

    /**
     * Initializes a cache line, setting it to an invalid state.
     */
    public CacheLine() {
        reset();
    }

    /**
     * Resets the cache line's metadata.
     */
    public void reset() {
        this.valid = false;
        this.tag = 0;
        this.age = 0;
        for (int i = 0; i < WORDS_PER_LINE; i++) {
            this.data[i] = 0;
        }
    }

    // --- Getters and Setters ---

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public int getTag() {
        return tag;
    }

    public void setTag(int tag) {
        this.tag = tag;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public int getData(int offset) {
        if (offset < 0 || offset >= WORDS_PER_LINE) {
            throw new IllegalArgumentException("Invalid word offset for cache line.");
        }
        return data[offset];
    }

    public void setData(int offset, int value) {
        if (offset < 0 || offset >= WORDS_PER_LINE) {
            throw new IllegalArgumentException("Invalid word offset for cache line.");
        }
        // Mask to 16 bits (C6461 word size)
        this.data[offset] = value & 0xFFFF;
    }

    public int[] getAllData() {
        return data;
    }

    public void setAllData(int[] block) {
        if (block.length != WORDS_PER_LINE) {
            throw new IllegalArgumentException("Data block size mismatch for cache line.");
        }
        for (int i = 0; i < WORDS_PER_LINE; i++) {
            this.data[i] = block[i] & 0xFFFF; // Ensure 16-bit
        }
    }
}
