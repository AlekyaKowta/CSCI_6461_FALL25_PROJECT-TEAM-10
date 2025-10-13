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

    // We'll update the UI class to take the controller in the next step.
    public MachineController(MachineState state, SimulatorUI ui) {
        this.state = state;
        this.ui = ui;
    }

    public MachineState getMachineState() {
        return state;
    }

    /**
     * Simulates the Initial Program Load (IPL) process.
     * Reads the load file (address/value pairs in octal) and stores it into memory.
     * @param loadFilePath Path to the assembler's Load File.
     */
    public void performIPL(String loadFilePath) throws IOException {
        int firstAddress = -1;
        int linesRead = 0;
        
        // This simulates the ROM load
        try (BufferedReader reader = new BufferedReader(new FileReader(loadFilePath))) {
            String addressLine, valueLine;

            while ((addressLine = reader.readLine()) != null) {
                // If the next line (value) is missing, the file is corrupt
                if ((valueLine = reader.readLine()) == null) {
                    throw new IOException("Corrupt load file: missing value for address " + addressLine);
                }
                
                // Address and Value are in OCTAL (base 8) [cite: 46]
                int address = Integer.parseInt(addressLine.trim(), 8);
                int value = Integer.parseInt(valueLine.trim(), 8);

                // Store the value into the simulated memory
                state.setMemory(address, value);

                // Track the first loaded address to set the PC
                if (firstAddress == -1) {
                    firstAddress = address;
                }
                linesRead += 2;
            }
        } 
        
        // Set the machine state to ready (PC set, stop at beginning) [cite: 212]
        if (firstAddress != -1) {
            state.setPC(firstAddress);
            ui.getPrinterArea().append("IPL successful. Loaded " + linesRead / 2 + " instructions starting at octal address " + String.format("%06o", firstAddress) + "\n");
        } else {
            ui.getPrinterArea().append("IPL warning: Load file was empty.\n");
        }
        
        // Now ready for user to hit run or single step
        ui.updateDisplays();
    }

    // Placeholder methods matching the DummyController interface
    public void handlePlaceholderAction(ActionEvent e) {
        ui.getPrinterArea().append("Button '" + e.getActionCommand() + "' pressed. Logic not yet implemented.\n");
    }

    public void handleRegisterLoad(String registerName) {
        ui.getPrinterArea().append("Load button pressed for register: " + registerName + ". Logic not yet implemented.\n");
    }
}