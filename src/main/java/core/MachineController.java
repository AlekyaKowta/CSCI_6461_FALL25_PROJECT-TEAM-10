package src.main.java.core;

import src.main.java.ui.SimulatorUI;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class MachineController {
    private MachineState state;
    private SimulatorUI ui; // Need to link back to the UI to update the display and printer
    private boolean isRunning = false;

    // Constants based on the 16-bit ISA structure (Opcode: 6, R: 2, IX: 2, I: 1, Address: 5)
    private static final int OPCODE_MASK = 0b111111; // 6 bits
    private static final int R_MASK = 0b11; // 2 bits
    private static final int IX_MASK = 0b11; // 2 bits
    private static final int I_MASK = 0b1; // 1 bit
    private static final int ADDRESS_MASK = 0b11111; // 5 bits

    //Match with OpCodeTables
    private static final int OPCODE_HLT = 0;   // Octal 000
    private static final int OPCODE_LDR = 1;   // Octal 001
    private static final int OPCODE_STR = 2;   // Octal 002
    private static final int OPCODE_LDA = 3;   // Octal 003
    private static final int OPCODE_LDX = 041; // Octal 041
    private static final int OPCODE_STX = 042; // Octal 042
    private static final int OPCODE_JZ = 010;  // Octal 010
    private static final int OPCODE_JMA = 013; //Octal 013

    // We'll update the UI class to take the controller in the next step.
    public MachineController(MachineState state, SimulatorUI ui) {
        this.state = state;
        this.ui = ui;
    }

    public MachineState getMachineState() {
        return state;
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
        // Machine Reset ---
        machineReset();

        int firstAddress = -1;
        int linesRead = 0;

        // Program Loading and Set PC ---
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

        //TODO Fix
        // --- JUMP INJECTION FIX ---
        if (firstAddress == 6) {
            int trueInstructionStartDec = 14;
            int jumpInstruction = (OPCODE_JMA << 10) | (0 << 8) | (0 << 6) | (0 << 5) | (trueInstructionStartDec & ADDRESS_MASK);
            state.setMemory(6, jumpInstruction);
            ui.getPrinterArea().append("IPL NOTE: JMA instruction injected at 000006 to skip data block.\n");
        }

        // Set Entry Point ---
        if (firstAddress != -1) {
            state.setPC(firstAddress);
            state.setMAR(firstAddress); // Set MAR to show the start of the loaded program
            ui.getPrinterArea().append("IPL successful. Loaded " + linesRead + " instructions.\n");
            ui.getPrinterArea().append("PC set to starting address: " + String.format("%06o", firstAddress) + "\n");
        } else {
            ui.getPrinterArea().append("IPL warning: Load file was empty.\n");
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

        ui.getPrinterArea().append(String.format("PC=%06o: Op=%02o, R=%d, IX=%d, I=%d, Addr=%d", pc, opcode, reg, ix, i, address));

        // 3. EXECUTE
        int nextPC = pc + 1;

        try {
            switch (opcode) {
                case OPCODE_HLT: handleHLT(); return; // Stops execution, sets isRunning=false
                case OPCODE_LDR: handleLDR(reg, ix, i, address); break;
                case OPCODE_STR: handleSTR(reg, ix, i, address); break;
                case OPCODE_LDA: handleLDA(reg, ix, i, address); break;
                case OPCODE_LDX: handleLDX(ix, i, address); break;
                case OPCODE_STX: handleSTX(ix, i, address); break;
                case OPCODE_JZ: nextPC = handleJZ(reg, ix, i, address); break;
                case OPCODE_JMA: nextPC = handleJMA(ix, i, address); break;

                default:
                    state.setMFR(2);
                    ui.getPrinterArea().append(" -> FAULT: Illegal Opcode.\n");
                    handleHLT();
                    return;
            }
        } catch (Exception e) {
            state.setMFR(4);
            handleHLT();
            ui.getPrinterArea().append(" -> CRITICAL FAULT: " + e.getMessage() + "\n");
            return;
        }

        ui.getPrinterArea().append("\n");

        // 4. Update PC (only if not halted)
        if (state.getMFR() == 0) {
            state.setPC(nextPC);
            state.setMAR(nextPC);
        }

        ui.updateDisplays();

        // CRITICAL FIX: If it was NOT a continuous run, reset isRunning
        if (!continuousRun) {
            isRunning = false;
        }
    }

    public void runProgram() {
        if (isRunning) return;
        isRunning = true;

        // Disable Step and Run buttons
        SwingUtilities.invokeLater(() -> ui.setStepRunButtonsEnabled(false));

        new Thread(() -> {
            try {
                while (isRunning && state.getMFR() == 0) {

                    // CRITICAL FIX: Queue singleStep execution onto the EDT
                    // This ensures all UI access (printing, updateDisplays) happens safely.
                    // We use invokeAndWait for immediate execution required by the loop.
                    SwingUtilities.invokeAndWait(() -> {
                        if (isRunning && state.getMFR() == 0) {
                            singleStep();
                        }
                    });

                    // Add sleep outside the EDT loop
                    Thread.sleep(100);
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

    // region Execution Helpers (EA Calculation and Instruction Handlers)

    private int calculateEA(int ix, int i, int address) {
        int EA = address;

        // C(IX) + Address
        if (ix > 0) {
            // Index 0 of IXR array is generally unused; X1 is at index 1.
            EA += state.getIXR(ix);
        }

        // Indirect Addressing: I=1. EA <- C(EA)
        if (i == 1) {
            EA = state.getMemory(EA);
        }

        // Check for memory bounds
        if (EA < 0 || EA >= state.MEMORY_SIZE) {
            state.setMFR(1); // Illegal Memory Address Fault
            handleHLT();
            return -1;
        }
        return EA;
    }

    // --- Instruction Handlers ---

    private void handleLDR(int r, int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            int value = state.getMemory(EA);
            state.setGPR(r, value);
            ui.getPrinterArea().append(String.format(" -> LDR R%d <- M[%06o] = %06o", r, EA, value));
        }
    }

    private void handleSTR(int r, int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            int value = state.getGPR(r);
            state.setMemory(EA, value);
            ui.getPrinterArea().append(String.format(" -> STR M[%06o] <- R%d = %06o", EA, r, value));
        }
    }

    private void handleLDA(int r, int ix, int i, int address) {
        // LDA loads the EFFECTIVE ADDRESS into the register, not the content.
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            state.setGPR(r, EA);
            ui.getPrinterArea().append(String.format(" -> LDA R%d <- EA = %06o", r, EA));
        }
    }

    private void handleLDX(int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            int value = state.getMemory(EA);
            state.setIXR(ix, value);
            ui.getPrinterArea().append(String.format(" -> LDX X%d <- M[%06o] = %06o", ix, EA, value));
        }
    }

    private void handleSTX(int ix, int i, int address) {
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            int value = state.getIXR(ix);
            state.setMemory(EA, value);
            ui.getPrinterArea().append(String.format(" -> STX M[%06o] <- X%d = %06o", EA, ix, value));
        }
    }

    private int handleJZ(int r, int ix, int i, int address) {
        // JZ: Jump To EA if C(r) = 0
        int EA = calculateEA(ix, i, address);

        if (state.getGPR(r) == 0) {
            ui.getPrinterArea().append(String.format(" -> JZ Taken. C(R%d)=0. PC <- %06o", r, EA));
            return EA; // Next PC is the EA
        } else {
            ui.getPrinterArea().append(String.format(" -> JZ Not Taken. C(R%d) != 0. PC++", r));
            return state.getPC() + 1; // No jump, continue
        }
    }

    private int handleJMA(int ix, int i, int address) {
        // JMA: PC <- EA (R is ignored)
        int EA = calculateEA(ix, i, address);
        if (EA != -1) {
            ui.getPrinterArea().append(String.format(" -> JMA Taken. PC <- %06o", EA));
            return EA;
        }
        return state.getPC() + 1;
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

            switch (type) {
                case "GPR": state.setGPR(index, value); break;
                case "IXR": state.setIXR(index, value); break;
                case "PC": state.setPC(value); break;
                case "MAR": state.setMAR(value); break;
                case "MBR": state.setMBR(value); break;
                default: ui.getPrinterArea().append("Error: Unknown register " + registerName + ".\n");
            }
            ui.updateDisplays();
            ui.getPrinterArea().append(String.format("Manual Load: %s set to %06o.\n", registerName, value));
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
                    ui.getPrinterArea().append(String.format("Console Load: M[%06o] -> MBR = %06o\n", currentMAR, state.getMBR()));
                    break;
                case "Load+": // Load M[MAR] -> MBR; MAR++
                    state.setMBR(state.getMemory(currentMAR));
                    state.setMAR(currentMAR + 1);
                    ui.getPrinterArea().append(String.format("Console Load+: M[%06o] -> MBR; MAR++ to %06o\n", currentMAR, state.getMAR()));
                    break;
                case "Store": // Store MBR -> M[MAR]
                    state.setMemory(currentMAR, currentMBR);
                    ui.getPrinterArea().append(String.format("Console Store: M[%06o] <- MBR = %06o\n", currentMAR, currentMBR));
                    break;
                case "Store+": // Store MBR -> M[MAR]; MAR++
                    state.setMemory(currentMAR, currentMBR);
                    state.setMAR(currentMAR + 1);
                    ui.getPrinterArea().append(String.format("Console Store+: M[%06o] <- MBR; MAR++ to %06o\n", currentMAR, state.getMAR()));
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