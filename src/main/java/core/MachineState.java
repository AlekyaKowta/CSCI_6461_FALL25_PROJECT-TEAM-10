package src.main.java.core;

import src.main.java.core.cache.Cache;

public class MachineState {
    public final int MEMORY_SIZE = 2048;
    private int[] memory = new int[MEMORY_SIZE];
    
    // Registers (16-bit, stored as int, masked on update)
    private int PC, MAR, MBR, IR, CC, MFR;
    private int[] GPR = new int[4]; // General Purpose Registers R0-R3
    private int[] IXR = new int[4]; // Index Registers X0-X3 (X0 usually unused, or used as a GPR)

    private Cache cache;

    public MachineState() {
        initialize();
        cache = new Cache(this);
    }

    /**
     * Clears all Memory and resets all Registers to 0.
     */
    public void initialize(){
        // Clear all memory (required upon powering up)
        for (int i = 0; i < MEMORY_SIZE; i++) {
            memory[i] = 0;
        }
        // Reset all registers
        PC = MAR = MBR = IR = CC = MFR = 0;
        for (int i = 0; i < 4; i++) {
            GPR[i] = 0;
            // IXR[0] is not used, but initialize all for array safety
            IXR[i] = 0;
        }
    }


    // --- Memory Access ---

    public int getMemory(int address) {
        if (address < 0 || address >= MEMORY_SIZE) {
            // Trigger a Machine Fault (Illegal Memory Address)
            MFR = 1;
            return 0;
        }
        //return memory[address];

        return cache.readWord(address);
    }

    public void setMemory(int address, int value) {
        if (address < 0 || address >= MEMORY_SIZE) {
            MFR = 1;
            return;
        }
        // Mask to 16 bits (0xFFFF = 65535)
        //memory[address] = value & 0xFFFF;
        cache.writeWord(address, value);
    }

// --- Direct Memory Access (Used ONLY by Cache) ---

    // Renamed the original getter and made it public for Cache access
    public int getMemoryDirect(int address) {
        if (address < 0 || address >= MEMORY_SIZE) {
            // Only trigger fault if address is illegal (should be handled by public methods)
            return 0;
        }
        return memory[address];
    }

    public void setMemoryDirect(int address, int value) {
        if (address < 0 || address >= MEMORY_SIZE) {
            // Only trigger fault if address is illegal
            return;
        }
        // Mask to 16 bits (0xFFFF = 65535)
        memory[address] = value & 0xFFFF;
    }
    // --- Register Getters and Setters ---

    // Helper to ensure 16-bit value
    private int to16Bit(int value) {
        return value & 0xFFFF;
    }

    public int getPC() { return PC; }
    public void setPC(int value) { PC = to16Bit(value); }

    public int getMAR() { return MAR; }
    public void setMAR(int value) { MAR = to16Bit(value); }

    public int getMBR() { return MBR; }
    public void setMBR(int value) { MBR = to16Bit(value); }

    public int getIR() { return IR; }
    public void setIR(int value) { IR = to16Bit(value); }

    public int getCC() { return CC; }
    public void setCC(int value) { CC = value; } // CC is smaller, don't mask to 16-bit

    public int getMFR() { return MFR; }
    public void setMFR(int value) { MFR = value; }

    public int getGPR(int r) { return GPR[r]; }
    public void setGPR(int r, int value) { GPR[r] = to16Bit(value); }

    public int getIXR(int x) { return IXR[x]; }
    public void setIXR(int x, int value) { IXR[x] = to16Bit(value); }
}