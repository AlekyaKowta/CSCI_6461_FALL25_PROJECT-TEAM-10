// File: src/main/java/core/MachineController.java (continued)

package src.main.java.core;

import src.main.java.ui.SimulatorUI;

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
        ui.getPrinterArea().append("HLT instruction executed. Simulator stopped.\n");
    }

    // endregion

    // region Execution Cycle (Step and Run)

    /**
     * Executes one instruction: Fetch, Decode, Execute, Update PC.
     */
    public void singleStep() {
        if (state.getMFR() != 0) {
            handleHLT();
            return;
        }

        int pc = state.getPC();

        // 1. FETCH: IR <- M[PC]
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
                case OPCODE_HLT: handleHLT(); return; // Stop execution
                //case OPCODE_LDR: handleLDR(reg, ix, i, address); break;
                //case OPCODE_STR: handleSTR(reg, ix, i, address); break;
                //case OPCODE_LDA: handleLDA(reg, ix, i, address); break;
                //case OPCODE_LDX: handleLDX(ix, i, address); break;
                //case OPCODE_STX: handleSTX(ix, i, address); break;
                //case OPCODE_JZ: nextPC = handleJZ(reg, ix, i, address); break;
                // Add other P1 instructions (JNE, JMA, etc.) here as you implement them

                default:
                    // Machine Fault: Illegal Opcode
                    state.setMFR(2);
                    ui.getPrinterArea().append(" -> FAULT: Illegal Opcode.\n");
                    handleHLT();
                    return;
            }
        } catch (Exception e) {
            state.setMFR(4); // Internal error fault
            handleHLT();
            ui.getPrinterArea().append(" -> CRITICAL FAULT: " + e.getMessage() + "\n");
            return;
        }

//        ui.getPrinterArea().append("\n");
//
//        // 4. Update PC (only if not halted)
//        if (state.getMFR() == 0 && !isRunning) {
//            state.setPC(nextPC);
//            state.setMAR(nextPC); // Set MAR to show where the next instruction is
//        }
//
//        ui.updateDisplays();
    }

    public void runProgram() {
        if (isRunning) return;
        isRunning = true;

        // Use a background thread for running to prevent freezing the UI
        new Thread(() -> {
            try {
                while (isRunning && state.getMFR() == 0) {
                    singleStep();
                    Thread.sleep(100); // Small delay to visualize steps
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handleHLT();
            }
        }).start();
    }

    // endregion

    // Placeholder methods matching the DummyController interface
    public void handlePlaceholderAction(ActionEvent e) {
        // Delegate to specific logic for Load, Store, Run, Step, Halt
        String command = e.getActionCommand();
        if (command.equals("Run")) {
            // runProgram();
        } else if (command.equals("Step")) {
            // singleStep();
        } else if (command.equals("Halt")) {
            // handleHLT();
        }
        ui.getPrinterArea().append("Button '" + command + "' pressed. Logic not yet implemented.\n");
    }

    public void handleRegisterLoad(String registerName) {
        ui.getPrinterArea().append("Load button pressed for register: " + registerName + ". Logic not yet implemented.\n");
    }
}