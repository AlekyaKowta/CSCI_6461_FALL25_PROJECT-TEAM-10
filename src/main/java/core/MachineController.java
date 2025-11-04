package src.main.java.core;

import src.main.java.ui.SimulatorUI;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
*Serves as the Central Processing Unit (CPU) logic for the C6461 machine simulator.
*  Its primary responsibilities are managing the fetch-decode-execute cycle, enforcing all
* Instruction Set Architecture (ISA) compliance rules, and handling all data flow between the
* MachineState (hardware model) and the SimulatorUI (front panel).
* The controller enforces architectural constraints such as the 12-bit address space,
* handles memory-related faults, and contains the modular logic for every implemented opcode.
 **/
public class MachineController {
    private MachineState state;
    private SimulatorUI ui;
    public boolean isRunning = false;

    // --- Masks and Constants ---
    private static final int MASK_12_BIT = 0b0000111111111111; // 4095
    private static final int MASK_16_BIT = 0xFFFF; // 65535
    private static final int SIGN_BIT_16 = 0x8000; // 16th bit

    // ISA Field Masks
    private static final int OPCODE_MASK = 0b111111; // 6 bits
    private static final int R_MASK = 0b11; // 2 bits (GPR index)
    private static final int IX_MASK = 0b11; // 2 bits (IXR index, repurposed as Ry for Reg-Reg)
    private static final int I_MASK = 0b1; // 1 bit (Indirect Addressing)
    private static final int ADDRESS_MASK = 0b11111; // 5 bits (Address/Immed/DevID)
    private static final int COUNT_MASK = 0b1111; // 4 bits for shift/rotate count

    // MFR Fault IDs
    private static final int FAULT_ILLEGAL_MEM_RESERVED = 1; // MFR 0001 (ID 0)
    private static final int FAULT_ILLEGAL_OPCODE = 4; // MFR 0100 (ID 2)
    private static final int FAULT_ILLEGAL_MEM_BEYOND = 8; // MFR 1000 (ID 3)
    private static final int RESERVED_MEM_END = 5;

    private StringBuilder printerLineBuf = new StringBuilder();

    private java.util.Queue<Integer> inputBuffer = new java.util.LinkedList<>();

    // Condition Code Bits (4 bits: CC(0) to CC(3))
    private static final int CC_OVERFLOW = 0b1000; // cc(0)
    private static final int CC_UNDERFLOW = 0b0100; // cc(1)
    private static final int CC_DIVZERO = 0b0010; // cc(2)
    private static final int CC_EQUALORNOT = 0b0001; // cc(3)

    // I/O Device IDs
    private static final int DEVID_KEYBOARD = 0;
    private static final int DEVID_PRINTER = 1;

    // --- OpCodes (Complete Non-Deferred List) ---
    private static final int OPCODE_HLT = 0;
    private static final int OPCODE_LDR = 1;
    private static final int OPCODE_STR = 2;
    private static final int OPCODE_LDA = 3;
    private static final int OPCODE_AMR = 4;
    private static final int OPCODE_SMR = 5;
    private static final int OPCODE_AIR = 6;
    private static final int OPCODE_SIR = 7;
    private static final int OPCODE_JZ = 010;
    private static final int OPCODE_JNE = 011;
    private static final int OPCODE_JCC = 012;
    private static final int OPCODE_JMA = 013;
    private static final int OPCODE_JSR = 014;
    private static final int OPCODE_RFS = 015;
    private static final int OPCODE_SOB = 016;
    private static final int OPCODE_JGE = 017;
    private static final int OPCODE_SRC = 031;
    private static final int OPCODE_RRC = 032;
    private static final int OPCODE_LDX = 041;
    private static final int OPCODE_STX = 042;
    private static final int OPCODE_IN = 061;
    private static final int OPCODE_OUT = 062;
    private static final int OPCODE_CHK = 063;
    private static final int OPCODE_MLT = 070;
    private static final int OPCODE_DVD = 071;
    private static final int OPCODE_TRR = 072;
    private static final int OPCODE_AND = 073;
    private static final int OPCODE_ORR = 074;
    private static final int OPCODE_NOT = 075;

    public MachineController(MachineState state, SimulatorUI ui) {
        this.state = state;
        this.ui = ui;
    }

    public MachineState getMachineState() {
        return state;
    }

    public boolean isRunning() {
        return this.isRunning;
    }

    // region IPL and Control
    /**
     * Phase 1: Clears all Memory and resets all Registers to 0.
     * (We assume MachineState.initialize() exists)
     */
    public void machineReset() {
        state.initialize();
        ui.getPrinterArea().setText("");
        ui.getPrinterArea().append("Machine Reset (All registers and memory cleared).\n");
        ui.updateDisplays();
        isRunning = false;
    }

    /**
     * Simulates the Initial Program Load (IPL) process.
     * Combines Phase 1 (Reset), Phase 3 (Loading), and Phase 4 (Set PC).
     * Reads the LoadFile.txt format: Address (octal) [TAB/SPACE] Value (octal) per line.
     * @param loadFilePath Path to the assembler's Load File.
     */
    public void performIPL(String loadFilePath) throws IOException {
        // Machine Reset
        machineReset();
        ui.clearPrinter();

        int firstAddress = -1;
        int linesRead = 0;

        // Program Loading and Set PC
        try (BufferedReader reader = new BufferedReader(new FileReader(loadFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty()) continue;
                String[] parts = trimmedLine.split("\\s+");
                if (parts.length < 2) {
                    throw new IOException("Corrupt load file line: missing value in line: " + line);
                }
                // Address and Value are in OCTAL (base 8)
                int address = Integer.parseInt(parts[0].trim(), 8);
                int value = Integer.parseInt(parts[1].trim(), 8);

                // Store the value into the simulated memory
                state.setMemory(address, value);

                // Track the first loaded address to set the PC
                if (firstAddress == -1) {
                    firstAddress = address;
                }
                linesRead++;
            }
        }

        // Delete Reserved Memory Code
        // Set Entry Point ---
        // 1. Check Reserved Location 5 (ESAR) for the TRUE execution start address.
        // M[5] will hold the address of the first instruction (e.g., 000016) if the assembler set it.
//        int executionStartPC = state.getMemory(5);
//
//        if (executionStartPC != 0) {
//            // Option A: Assembler set M[5]. This is the desired execution start.
//            executionStartPC &= MASK_12_BIT;
//            state.setPC(executionStartPC);
//            state.setMAR(executionStartPC);
//            ui.getPrinterArea().append("IPL successful. Loaded " + linesRead + " instructions.\n");
//        } else if (firstAddress != -1) {
//            // Option B: Fallback. M[5] is 0. Use the very first address loaded (e.g., 0006).
//            // This is primarily for visualization/debugging, acknowledging that execution may fail (hit HLT).
//            firstAddress &= MASK_12_BIT;
//            state.setPC(firstAddress);
//            state.setMAR(firstAddress);
//            ui.getPrinterArea().append("IPL warning: M[5] not set. PC set to first loaded location: " + String.format("%04o", firstAddress) + ". Execution may start at data.\n");
//        } else {
//            // No program loaded
//            ui.getPrinterArea().append("IPL warning: Load file was empty. PC remains 0.\n");
//        }

        // Set Entry Point (Option: Always use the first loaded address) ---

        if (firstAddress != -1) {
            // PC is set to the first address loaded and masked to 12 bits.
            firstAddress &= MASK_12_BIT;
            state.setPC(firstAddress);
            state.setMAR(firstAddress);
            ui.getPrinterArea().append("IPL successful. Loaded " + linesRead + " instructions.\n");
            ui.getPrinterArea().append("PC set to first loaded location: " + String.format("%04o", firstAddress) + ".\n");
        } else {
            // no program loaded
            ui.getPrinterArea().append("IPL warning: Load file was empty. PC remains 0.\n");
        }
        // Now ready for user to hit run or single step
        ui.updateDisplays();
    }

    public void handleHLT(){
        isRunning = false;
        // Re-enable Step and Run buttons on the EDT
        SwingUtilities.invokeLater(() -> ui.setStepRunButtonsEnabled(true));
        ui.getPrinterArea().append("HLT instruction executed. Simulator stopped.\n");
    }

    // endregion

    // region Execution Cycle (Step and Run)

    /**
     * Executes one instruction: Fetch, Decode, Execute, Update PC.
     */
    public void singleStep() {
        if (state.getMFR() != 0) { handleHLT(); return; }

        // Check if we are running continuously or just executing a single step
        boolean continuousRun = isRunning;
        if (!isRunning) isRunning = true;

        int pc = state.getPC();

        // 1. FETCH
        state.setMAR(pc);
        int instruction = state.getMemory(pc);
        state.setMBR(instruction);
        state.setIR(instruction);

        // 2. DECODE
        int opcode = (instruction >> 10) & OPCODE_MASK;
        int reg = (instruction >> 8) & R_MASK;
        int ix = (instruction >> 6) & IX_MASK;
        int i = (instruction >> 5) & I_MASK;
        int address = instruction & ADDRESS_MASK;

        // Specialized fields
        int rx = reg;
        int ry = ix; // Ry for Reg-Reg is the IX field (bits 6-7)
        int cc = reg; // CC index for JCC is the R field (bits 8-9)
        int immed = address; // Immediate for AIR/SIR/RFS
        int devid = address; // DevID for I/O

        // Fields specific to Shift/Rotate (SRC/RRC)
        // Note: Shift/Rotate format is OpCode (6), R (2), A/L (1), L/R (1), Unused (4), Count (4)
        // Decoding needs to use correct bits based on Table 9 format
        // The R field is bits 10-11, A/L is bit 8, L/R is bit 9, Count is bits 0-3.
        int sr_r = (instruction >> 10) & R_MASK;
        int al = (instruction >> 7) & 0b1;
        int lr = (instruction >> 6) & 0b1;
        int count = instruction & COUNT_MASK;


        ui.getPrinterArea().append(String.format("PC=%04o: Op=%02o, R=%d, IX=%d, I=%d, Addr=%d", pc, opcode, reg, ix, i, address));

        // 3. EXECUTE
        int nextPC = pc + 1;
        boolean instructionCompleted = true;

        try {
            switch (opcode) {
                // HLT
                case OPCODE_HLT: handleHLT(); return;

                // Load/Store
                case OPCODE_LDR: handleLDR(reg, ix, i, address); break;
                case OPCODE_STR: handleSTR(reg, ix, i, address); break;
                case OPCODE_LDA: handleLDA(reg, ix, i, address); break;
                case OPCODE_LDX: handleLDX(ix, i, address); break;
                case OPCODE_STX: handleSTX(ix, i, address); break;

                // Arithmetic (Mem/Imm)
                case OPCODE_AMR: handleAMR(reg, ix, i, address); break;
                case OPCODE_SMR: handleSMR(reg, ix, i, address); break;
                case OPCODE_AIR: handleAIR(reg, immed); break;
                case OPCODE_SIR: handleSIR(reg, immed); break;

                // Transfer
                case OPCODE_JZ: nextPC = handleJZ(reg, ix, i, address); break;
                case OPCODE_JNE: nextPC = handleJNE(reg, ix, i, address); break;
                case OPCODE_JCC: nextPC = handleJCC(cc, ix, i, address); break;
                case OPCODE_JMA: nextPC = handleJMA(ix, i, address); break;
                case OPCODE_JSR: nextPC = handleJSR(ix, i, address); break;
                case OPCODE_RFS: nextPC = handleRFS(immed); break;
                case OPCODE_SOB: nextPC = handleSOB(reg, ix, i, address); break;
                case OPCODE_JGE: nextPC = handleJGE(reg, ix, i, address); break;

                // Arithmetic/Logical (Reg-Reg)
                case OPCODE_MLT: handleMLT(rx, ry); break;
                case OPCODE_DVD: handleDVD(rx, ry); break;
                case OPCODE_TRR: handleTRR(rx, ry); break;
                case OPCODE_AND: handleAND(rx, ry); break;
                case OPCODE_ORR: handleORR(rx, ry); break;
                case OPCODE_NOT: handleNOT(rx); break;

                // Shift/Rotate
                case OPCODE_SRC: handleSRC(sr_r, count, lr, al); break;
                case OPCODE_RRC: handleRRC(sr_r, count, lr, al); break;

                // I/O
                case OPCODE_IN: instructionCompleted = handleIN(reg, devid); break;
                case OPCODE_OUT: handleOUT(reg, devid); break;
                case OPCODE_CHK: handleCHK(reg, devid); break;

                default:
                    state.setMFR(FAULT_ILLEGAL_OPCODE);
                    ui.getPrinterArea().append(" -> FAULT: Illegal Opcode (" + String.format("%02o", opcode) + ").\n");
                    handleHLT();
                    return;
            }
        } catch (Exception e) {
            state.setMFR(FAULT_ILLEGAL_MEM_RESERVED);
            handleHLT();
            ui.getPrinterArea().append(" -> CRITICAL FAULT: " + e.getMessage() + "\n");
            return;
        }

        ui.getPrinterArea().append("\n");

        // 4. Update PC (only if not halted)
        if (state.getMFR() == 0) {
            if (instructionCompleted) {
                // If instruction completed (not an IN waiting for input), advance PC
                state.setPC(nextPC);
                state.setMAR(nextPC);

                // If it was NOT a continuous run, reset isRunning
                if (!continuousRun) {
                    isRunning = false;
                }
            } else {
                // Instruction was IN and is waiting. Don't advance PC.
                // Stop the continuous run and re-enable controls.
                isRunning = false;
                SwingUtilities.invokeLater(() -> ui.setStepRunButtonsEnabled(true));
            }
        } else {
            // If a fault occurred, ensure isRunning is set to false and controls re-enabled
            handleHLT();
            return;
        }

        // If it was NOT a continuous run, reset isRunning
//        if (!continuousRun) {
//            isRunning = false;
//        }
        ui.updateDisplays();
    }

    /**
    Run Program
     **/
    public void runProgram() {
        if (isRunning) return;
        isRunning = true;

        // Disable Step and Run buttons
        SwingUtilities.invokeLater(() -> ui.setStepRunButtonsEnabled(false));

        new Thread(() -> {
            try {
                while (isRunning && state.getMFR() == 0) {
                    // Queue singleStep execution onto the EDT
                    // This ensures all UI access (printing, updateDisplays) happens safely.
                    // We use invokeAndWait for immediate execution required by the loop.
                    SwingUtilities.invokeAndWait(() -> {
                        if (isRunning && state.getMFR() == 0) {
                            singleStep();
                        }
                    });
                    // Add sleep outside the EDT loop
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Handle unexpected exception during execution
                ui.getPrinterArea().append("CRASH: Program execution failed unexpectedly: " + e.getMessage() + "\n");
            }
            finally {
                // Ensure HLT cleanup runs when the loop terminates (either by HLT or fault)
                // handleHLT() contains the logic to set isRunning=false and re-enable buttons on the EDT.
                if (isRunning) {
                    handleHLT();
                }
            }
        }).start();
    }

    // endregion

    // region Execution Helpers (EA Calculation, CC, Instruction Handlers)

    /**
    * Calculate EA with Security Gate
     **/
    private int calculateEA(int ix, int i, int address) {
        int EA;

        if (ix == 0) {
            EA = address;
        } else {
            // Else If c(IX) = 1..3, Then EA = c(IX) + c(Address) [cite: 191, 193]
            EA = state.getIXR(ix) + address;
        }

        // Indirect Addressing: I=1. EA <- C(EA)
        if (i == 1) {
            EA = state.getMemory(EA) & MASK_12_BIT;
        }
        
        if (EA <= 5) {
            state.setMFR(FAULT_ILLEGAL_MEM_RESERVED);
            handleHLT();
            return -1;
        }

        if (EA < 0 || EA >= state.MEMORY_SIZE) {
            state.setMFR(FAULT_ILLEGAL_MEM_BEYOND);
            handleHLT();
            return -1;
        }
        return EA & MASK_12_BIT;
    }

    private int signExtend(int value) {
        if ((value & SIGN_BIT_16) != 0) {
            return value | 0xFFFF0000;
        }
        return value;
    }

    private void setCC_Arithmetic(long result) {
        state.setCC(0);
        final int MAX_SIGNED_16 = 32767;
        final int MIN_SIGNED_16 = -32768;

        if (result > MAX_SIGNED_16) {
            state.setCC(state.getCC() | CC_OVERFLOW);
        } else if (result < MIN_SIGNED_16) {
            state.setCC(state.getCC() | CC_UNDERFLOW);
        }
    }

    // --- Load/Store Instructions ---

    private void handleLDR(int r, int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            int value = state.getMemory(EA);
            state.setGPR(r, value);
            ui.getPrinterArea().append(String.format(" -> LDR R%d <- M[%04o] = %06o", r, EA, value));
        }
    }

    private void handleSTR(int r, int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            int value = state.getGPR(r);
            state.setMemory(EA, value);
            ui.getPrinterArea().append(String.format(" -> STR M[%04o] <- R%d = %06o", EA, r, value));
        }
    }

    private void handleLDA(int r, int ix, int i, int address) {
        // LDA loads the EFFECTIVE ADDRESS into the register, not the content.
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            state.setGPR(r, EA);
            ui.getPrinterArea().append(String.format(" -> LDA R%d <- EA = %04o", r, EA));
        }
    }

    private void handleLDX(int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            int value = state.getMemory(EA);
            state.setIXR(ix, value);
            ui.getPrinterArea().append(String.format(" -> LDX X%d <- M[%04o] = %06o", ix, EA, value));
        }
    }

    private void handleSTX(int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            int value = state.getIXR(ix);
            state.setMemory(EA, value);
            ui.getPrinterArea().append(String.format(" -> STX M[%04o] <- X%d = %06o", EA, ix, value));
        }
    }

    // --- Arithmetic (Memory/Immediate) Instructions ---

    private void handleAMR(int r, int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            int rValue = signExtend(state.getGPR(r));
            int mValue = signExtend(state.getMemory(EA));
            long result = (long)rValue + mValue;

            setCC_Arithmetic(result);
            state.setGPR(r, (int)result & MASK_16_BIT);
            ui.getPrinterArea().append(String.format(" -> AMR R%d <- R%d + M[%04o] = %06o", r, r, EA, state.getGPR(r)));
        }
    }

    private void handleSMR(int r, int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            int rValue = signExtend(state.getGPR(r));
            int mValue = signExtend(state.getMemory(EA));
            long result = (long)rValue - mValue;

            setCC_Arithmetic(result);
            state.setGPR(r, (int)result & MASK_16_BIT);
            ui.getPrinterArea().append(String.format(" -> SMR R%d <- R%d - M[%04o] = %06o", r, r, EA, state.getGPR(r)));
        }
    }

    private void handleAIR(int r, int immediate) {
        int rValue = signExtend(state.getGPR(r));
        long result;

        if (immediate == 0) {
            ui.getPrinterArea().append(String.format(" -> AIR R%d: Immed=0. No change (Note 1).", r));
            return;
        }
        if ((state.getGPR(r) & MASK_16_BIT) == 0) {
            result = immediate;
            ui.getPrinterArea().append(String.format(" -> AIR R%d <- Immed=%d (Note 2)", r, immediate));
        } else {
            result = (long)rValue + immediate;
            ui.getPrinterArea().append(String.format(" -> AIR R%d <- R%d + %d = %06o", r, r, immediate, (int)result & MASK_16_BIT));
        }
        setCC_Arithmetic(result);
        state.setGPR(r, (int)result & MASK_16_BIT);
    }

    private void handleSIR(int r, int immediate) {
        int rValue = signExtend(state.getGPR(r));
        long result;

        if (immediate == 0) {
            ui.getPrinterArea().append(String.format(" -> SIR R%d: Immed=0. No change (Note 1).", r));
            return;
        }

        if ((state.getGPR(r) & MASK_16_BIT) == 0) {
            result = -immediate;
            ui.getPrinterArea().append(String.format(" -> SIR R%d <- -(Immed)=%d (Note 2)", r, result));
        } else {
            result = (long)rValue - immediate;
            ui.getPrinterArea().append(String.format(" -> SIR R%d <- R%d - %d = %06o", r, r, immediate, (int)result & MASK_16_BIT));
        }
        setCC_Arithmetic(result);
        state.setGPR(r, (int)result & MASK_16_BIT);
    }

    // --- Arithmetic/Logical (Reg-Reg) Instructions ---

    private void handleMLT(int rx, int ry) {
//        if (rx % 2 != 0 || ry % 2 != 0) {
//            ui.getPrinterArea().append(" -> MLT Fault: rx or ry not 0 or 2.");
//            state.setMFR(FAULT_ILLEGAL_OPCODE);
//            handleHLT();
//            return;
//        }

        long op1 = signExtend(state.getGPR(rx));
        long op2 = signExtend(state.getGPR(ry));
        long result = op1 * op2;

        int highOrder = (int)(result >> 16) & MASK_16_BIT;
        int lowOrder = (int)result & MASK_16_BIT;

        state.setCC(state.getCC() & ~CC_OVERFLOW);
        if ((highOrder != 0) && (highOrder != MASK_16_BIT)) {
            state.setCC(state.getCC() | CC_OVERFLOW);
        }

        state.setGPR(rx, highOrder);
        state.setGPR(rx + 1, lowOrder);

        ui.getPrinterArea().append(String.format(" -> MLT R%d,R%d <- %06o * %06o = %06o|%06o",
                rx, rx + 1, state.getGPR(rx), state.getGPR(ry), highOrder, lowOrder));
    }

    private void handleDVD(int rx, int ry) {
//        if (rx % 2 != 0 || ry % 2 != 0) {
//            ui.getPrinterArea().append(" -> DVD Fault: rx or ry not 0 or 2.");
//            state.setMFR(FAULT_ILLEGAL_OPCODE);
//            handleHLT();
//            return;
//        }

        int divisor = state.getGPR(ry);
        state.setCC(state.getCC() & ~(CC_DIVZERO | CC_OVERFLOW));

        if ((divisor & MASK_16_BIT) == 0) {
            state.setCC(state.getCC() | CC_DIVZERO);
            ui.getPrinterArea().append(" -> DVD Fault: Division by Zero.");
            return;
        }

        int op1 = signExtend(state.getGPR(rx));
        int op2 = signExtend(divisor);

        int quotient = op1 / op2;
        int remainder = op1 % op2;

        if (quotient > 32767 || quotient < -32768) {
            state.setCC(state.getCC() | CC_OVERFLOW);
        }

        state.setGPR(rx, quotient & MASK_16_BIT);
        state.setGPR(rx + 1, remainder & MASK_16_BIT);
        ui.getPrinterArea().append(String.format(" -> DVD Q(R%d), R(R%d) <- Q:%06o, R:%06o",
                rx, rx + 1, state.getGPR(rx), state.getGPR(rx + 1)));
    }

    private void handleTRR(int rx, int ry) {
        state.setCC(state.getCC() & ~CC_EQUALORNOT);
        if ((state.getGPR(rx) & MASK_16_BIT) == (state.getGPR(ry) & MASK_16_BIT)) {
            state.setCC(state.getCC() | CC_EQUALORNOT);
            ui.getPrinterArea().append(" -> TRR Equal. CC(EQUALORNOT)=1");
        } else {
            ui.getPrinterArea().append(" -> TRR Not Equal. CC(EQUALORNOT)=0");
        }
    }

    private void handleAND(int rx, int ry) {
        int result = (state.getGPR(rx) & MASK_16_BIT) & (state.getGPR(ry) & MASK_16_BIT);
        state.setGPR(rx, result);
        ui.getPrinterArea().append(String.format(" -> AND R%d <- R%d AND R%d = %06o", rx, rx, ry, state.getGPR(rx)));
    }

    private void handleORR(int rx, int ry) {
        int result = (state.getGPR(rx) & MASK_16_BIT) | (state.getGPR(ry) & MASK_16_BIT);
        state.setGPR(rx, result);
        ui.getPrinterArea().append(String.format(" -> ORR R%d <- R%d OR R%d = %06o", rx, rx, ry, state.getGPR(rx)));
    }

    private void handleNOT(int rx) {
        int result = ~state.getGPR(rx);
        state.setGPR(rx, result & MASK_16_BIT);
        ui.getPrinterArea().append(String.format(" -> NOT R%d <- NOT R%d = %06o", rx, rx, state.getGPR(rx)));
    }

    // --- Transfer Instructions ---

    private int handleJZ(int r, int ix, int i, int address) {
        // JZ: Jump To EA if C(r) = 0
        int EA = calculateEA(ix, i, address);
        if (EA == -1) return state.getPC() + 1;

        if ((state.getGPR(r) & MASK_16_BIT) == 0) {
            ui.getPrinterArea().append(String.format(" -> JZ Taken. C(R%d)=0. PC <- %04o", r, EA));
            return EA;
        } else {
            ui.getPrinterArea().append(String.format(" -> JZ Not Taken. C(R%d) != 0. PC++", r));
            return state.getPC() + 1;
        }
    }

    private int handleJNE(int r, int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA == -1) return state.getPC() + 1;

        if ((state.getGPR(r) & MASK_16_BIT) != 0) {
            ui.getPrinterArea().append(String.format(" -> JNE Taken. C(R%d)!=0. PC <- %04o", r, EA));
            return EA;
        } else {
            ui.getPrinterArea().append(String.format(" -> JNE Not Taken. C(R%d)=0. PC++", r));
            return state.getPC() + 1;
        }
    }

    private int handleJCC(int cc, int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA == -1) return state.getPC() + 1;

        int ccBitMask = 1 << (3 - cc);

        if ((state.getCC() & ccBitMask) != 0) {
            ui.getPrinterArea().append(String.format(" -> JCC Taken. CC bit %d is 1. PC <- %04o", cc, EA));
            return EA;
        } else {
            ui.getPrinterArea().append(String.format(" -> JCC Not Taken. CC bit %d is 0. PC++", cc));
            return state.getPC() + 1;
        }
    }

    private int handleJMA(int ix, int i, int address) {
        // JMA: PC <- EA (R is ignored)
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            ui.getPrinterArea().append(String.format(" -> JMA Taken. PC <- %04o", EA));
            return EA;
        }
        return state.getPC() + 1;
    }

    private int handleJSR(int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA == -1) return state.getPC() + 1;

        state.setGPR(3, state.getPC() + 1);
        ui.getPrinterArea().append(String.format(" -> JSR Taken. R3 <- %04o. PC <- %04o", state.getGPR(3), EA));
        return EA;
    }

    private int handleRFS(int immediate) {
        int returnAddress = state.getGPR(3);

        state.setGPR(0, immediate);
        ui.getPrinterArea().append(String.format(" -> RFS: R0 <- %d. PC <- %04o", immediate, returnAddress));
        return returnAddress;
    }

    private int handleSOB(int r, int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA == -1) return state.getPC() + 1;

        int currentValue = state.getGPR(r);
        int newValue = (currentValue - 1) & MASK_16_BIT;

        state.setGPR(r, newValue);

        if (signExtend(newValue) > 0) {
            ui.getPrinterArea().append(String.format(" -> SOB Taken. R%d-- to %06o. PC <- %04o", r, newValue, EA));
            return EA;
        } else {
            ui.getPrinterArea().append(String.format(" -> SOB Not Taken. R%d-- to %06o. PC++", r, newValue));
            return state.getPC() + 1;
        }
    }

    private int handleJGE(int r, int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA == -1) return state.getPC() + 1;

        if (signExtend(state.getGPR(r)) >= 0) {
            ui.getPrinterArea().append(String.format(" -> JGE Taken. C(R%d)>=0. PC <- %04o", r, EA));
            return EA;
        } else {
            ui.getPrinterArea().append(String.format(" -> JGE Not Taken. C(R%d)<0. PC++", r));
            return state.getPC() + 1;
        }
    }

    // --- Shift/Rotate Operations ---

    private void handleSRC(int r, int count, int lr, int al) {
        int value = state.getGPR(r);
        if (count == 0) return;

        if (lr == 1) { // Left Shift (L/R=1)
            value = value << count;
        } else { // Right Shift (L/R=0)
            if (al == 1) { // Logical Shift Right (A/L=1)
                value = value >>> count;
            } else { // Arithmetic Shift Right (A/L=0)
                value = signExtend(value) >> count;
            }
        }
        state.setGPR(r, value & MASK_16_BIT);
        ui.getPrinterArea().append(String.format(" -> SRC R%d %s %s by %d to %06o", r,
                (lr == 1 ? "Left" : "Right"), (al == 1 ? "Logical" : "Arithmetic"), count, state.getGPR(r)));
    }

    private void handleRRC(int r, int count, int lr, int al) {
        int value = state.getGPR(r) & MASK_16_BIT;
        count = count % 16;
        if (count == 0) return;

        if (lr == 1) { // Left Rotate
            value = (value << count) | (value >>> (16 - count));
        } else { // Right Rotate
            value = (value >>> count) | (value << (16 - count));
        }

        state.setGPR(r, value & MASK_16_BIT);
        ui.getPrinterArea().append(String.format(" -> RRC R%d %s by %d to %06o", r,
                (lr == 1 ? "Left" : "Right"), count, state.getGPR(r)));
    }

    // --- I/O Operations ---

    /**
     * Handles IN instruction.
     * @return true if execution can continue, false if waiting for input.
     */
    private boolean handleIN(int r, int devid) {
        if (devid == DEVID_KEYBOARD) {
            if (!inputBuffer.isEmpty()) {
                int charValue = inputBuffer.poll(); // Get and remove the head character (ASCII)
                state.setGPR(r, charValue & 0xFFFF); // Store the full 16-bit value

                ui.getPrinterArea().append(String.format(" -> IN R%d <- Console Char '%c'", r, (char)charValue));
                return true; // <<<--- RETURN TRUE (Success)
            } else {
                // Input buffer is empty. Pause and wait.
                ui.getPrinterArea().append(String.format(" -> IN R%d: Waiting for Console Input...", r));

//                isRunning = false;
//                SwingUtilities.invokeLater(() -> ui.setStepRunButtonsEnabled(true));
                return false; // <<<--- RETURN FALSE (Waiting)
            }
        } else {
            ui.getPrinterArea().append(String.format(" -> IN R%d: DevID %d not implemented.", r, devid));
            return true; // (Success for other devices)
        }
    }

    public void depositInput(String input) {
        for (char c : input.toCharArray()) {
            inputBuffer.offer((int) c);
        }
        // Optional: Add a newline character to terminate the line visually
        inputBuffer.offer((int) '\n');
    }

    private void handleOUT(int r, int devid) {
        int value = state.getGPR(r) & 0xFFFF;
        if (devid == DEVID_PRINTER) {
            char ch = (char)(value & 0xFF);
            ui.appendPrinterChar(value); // keeps the existing console log behavior

            if (ch == '\n') {
                // deliver the finished line to the big banner (strip trailing CR if you ever add it)
                String line = printerLineBuf.toString();
                ui.setLastOutput(line);
                printerLineBuf.setLength(0);
            } else {
                printerLineBuf.append(ch);
            }
            return;
        }
        System.out.println(String.format("OUT R%d: DevID %d not implemented.", r, devid));
    }

    private void handleCHK(int r, int devid) {
        int status = 0;

        if (devid == DEVID_KEYBOARD) {
            status = ui.getConsoleInputField().getText().length() > 0 ? 1 : 0;
            ui.getPrinterArea().append(String.format(" -> CHK R%d: Keyboard Status = %d", r, status));
        } else if (devid == DEVID_PRINTER) {
            status = 1;
            ui.getPrinterArea().append(String.format(" -> CHK R%d: Printer Status = %d", r, status));
        } else {
            ui.getPrinterArea().append(String.format(" -> CHK R%d: DevID %d not implemented. Status is 0.", r, devid));
        }

        state.setGPR(r, status);
    }

    // endregion

    // region Manual Console Input Handlers

    /**
     * Handles the small square load button next to GPR/IXR/PC/MAR/MBR.
     * Loads the Octal Input field value into the specified register.
     */
    public void handleRegisterLoad(String registerName) {
        try {
            String octalString = ui.getOctalInputField().getText().trim();
            int value = Integer.parseInt(octalString, 8);

            String[] parts = registerName.split(" ");
            String type = parts[0];
            int index = (parts.length > 1) ? Integer.parseInt(parts[1]) : -1;

            // Apply 12-bit mask for PC and MAR if they are the target
            int maskedValue = value;
            if (type.equals("PC") || type.equals("MAR")) {
                maskedValue &= MASK_12_BIT;
            }

            switch (type) {
                case "GPR": state.setGPR(index, value); break;
                case "IXR": state.setIXR(index, value); break;
                case "PC": state.setPC(maskedValue); break;
                case "MAR": state.setMAR(maskedValue); break;
                case "MBR": state.setMBR(value); break;
                default: ui.getPrinterArea().append("Error: Unknown register " + registerName + ".\n");
            }
            ui.updateDisplays();
            String format = (type.equals("PC") || type.equals("MAR")) ? "%04o" : "%06o";
            int displayValue = (type.equals("PC") || type.equals("MAR")) ? maskedValue : value;
            ui.getPrinterArea().append(String.format("Manual Load: %s set to " + format + ".\n", registerName, displayValue));
        } catch (NumberFormatException e) {
            ui.getPrinterArea().append("Error: Invalid octal value in input field.\n");
        }
    }

    /**
     * Handler for the main control panel buttons (Load, Store, Load+, Store+).
     * This fulfills the memory inspection/deposit capabilities.
     */
    public void handlePlaceholderAction(ActionEvent e) {
        String command = e.getActionCommand();
        try {
            int currentMAR = state.getMAR();
            int currentMBR = state.getMBR();

            switch (command) {
                case "Load": // Load M[MAR] -> MBR
                    state.setMBR(state.getMemory(currentMAR));
                    ui.getPrinterArea().append(String.format("Console Load: M[%04o] -> MBR = %06o\n", currentMAR, state.getMBR()));
                    break;
                case "Load+": // Load M[MAR] -> MBR; MAR++
                    state.setMBR(state.getMemory(currentMAR));
                    state.setMAR((currentMAR + 1) & MASK_12_BIT);
                    ui.getPrinterArea().append(String.format("Console Load+: M[%04o] -> MBR; MAR++ to %04o\n", currentMAR, state.getMAR()));
                    break;
                case "Store": // Store MBR -> M[MAR]
                    state.setMemory(currentMAR, currentMBR);
                    ui.getPrinterArea().append(String.format("Console Store: M[%04o] <- MBR = %06o\n", currentMAR, currentMBR));
                    break;
                case "Store+":  // Store MBR -> M[MAR]; MAR++
                    state.setMemory(currentMAR, currentMBR);
                    state.setMAR((currentMAR + 1) & MASK_12_BIT);
                    ui.getPrinterArea().append(String.format("Console Store+: M[%04o] <- MBR; MAR++ to %04o\n", currentMAR, state.getMAR()));
                    break;
                case "Run":
                    runProgram();
                    break;
                case "Step":
                    singleStep();
                    break;
                case "Halt":
                    handleHLT();
                    break;
                default:
                    ui.getPrinterArea().append("Button '" + command + "' pressed. Logic not implemented.\n");
            }
        } catch (Exception ex) {
            ui.getPrinterArea().append("CONSOLE ERROR: " + ex.getMessage() + "\n");
        }
        ui.updateDisplays();
    }

    // endregion
}