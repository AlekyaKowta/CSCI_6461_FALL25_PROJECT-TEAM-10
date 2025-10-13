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

    // We'll update the UI class to take the controller in the next step.
    public MachineController(MachineState state, SimulatorUI ui) {
        this.state = state;
        this.ui = ui;
    }

    public MachineState getMachineState() {
        return state;
    }

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