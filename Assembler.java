import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;

public class Assembler {
    public static final HashMap<String, Integer> opcodeForArithmeticAndLogic = new HashMap<>();
    public static final HashMap<String, Integer> opcodeForShiftRotate = new HashMap<>();
    public static final HashMap<String, Integer> opcodeForIO = new HashMap<>();
    public static final HashMap<String, Integer> opcodeForLSAndOther = new HashMap<>();
    public static final HashMap<String, Integer> opcodeForMisallaneous = new HashMap<>();
    //TODO: Include Floating Point Vectors

    static {
        // Initialize opcode hashmaps
        opcodeForMisallaneous.put("HLT", 000);
        opcodeForMisallaneous.put("TRAP", 030);
        opcodeForLSAndOther.put("LDR", 001);
        opcodeForLSAndOther.put("STR", 002);
        opcodeForLSAndOther.put("LDA", 003);
        opcodeForLSAndOther.put("LDX", 041);
        opcodeForLSAndOther.put("STX", 042);
        opcodeForLSAndOther.put("SETCCE", 036); //TODO
        opcodeForLSAndOther.put("JZ", 010);
        opcodeForLSAndOther.put("JNE", 011);
        opcodeForLSAndOther.put("JCC", 012);
        opcodeForLSAndOther.put("JMA", 013);
        opcodeForLSAndOther.put("JSR", 014);
        opcodeForLSAndOther.put("RFS", 015);
        opcodeForLSAndOther.put("SOB", 016);
        opcodeForLSAndOther.put("JGE", 017);
        opcodeForLSAndOther.put("AMR", 004);
        opcodeForLSAndOther.put("SMR", 005);
        opcodeForLSAndOther.put("AIR", 006);
        opcodeForLSAndOther.put("SIR", 007);
        opcodeForArithmeticAndLogic.put("MLT", 070);
        opcodeForArithmeticAndLogic.put("DVD", 071);
        opcodeForArithmeticAndLogic.put("TRR", 072);
        opcodeForArithmeticAndLogic.put("AND", 073);
        opcodeForArithmeticAndLogic.put("ORR", 074);
        opcodeForArithmeticAndLogic.put("NOT", 075);
        opcodeForShiftRotate.put("SRC", 031);
        opcodeForShiftRotate.put("RRC", 032);
        opcodeForIO.put("IN", 061);
        opcodeForIO.put("OUT", 062);
        opcodeForIO.put("CHK", 063);
        //TODO: Include Floating Point Vectors
    }

    public int currentAddress = 0; // Tracks current address
    public String LISTING_FILE = "listingFile.txt"; 
    public String LOAD_FILE = "LoadFile.txt";

    // 🔹 Symbol Table
    public HashMap<String, Integer> symbolsMap = new HashMap<>();

    // region Helper Methods

    /// <summary>
    /// Helper Methods
    /// </summary>

    // Utility: Export the data to output files as required
    public static void exportDataToFile(String filePath, ArrayList<String> data) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (String line : data) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    // Utility: Write data to file
    public static void writeDataToFile(String filePath, ArrayList<String> data) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (String line : data) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    // Utility: Create a listing file in the specific format
    public void generateListingFile(ArrayList<String> inputFileLines, String destinationFile, ArrayList<String> output) {

        ArrayList<String> dataToWrite = new ArrayList<>();
        int maxLines = Math.max(inputFileLines.size(), output.size());

        for (int i = 0; i < maxLines; i++) {
            String sourceLine = (i < inputFileLines.size()) ? inputFileLines.get(i) : "";
            String resultLine = (i <=output.size()) ? output.get(i) : "";

            dataToWrite.add(String.format("%-50s %s", resultLine, sourceLine)); // Adjust the format according to your needs
        }
        writeDataToFile(destinationFile, dataToWrite);
    }

    // Utility: Parse the instructions
    String lsInstructionParse (String[] instructionComponents) {
        // Get the opcode from the map
        int opcode = opcodeForLSAndOther.get(instructionComponents[0]);

        // Split and trim the operand list
        String[] operands = instructionComponents[1].split(",");
        Arrays.setAll(operands, i -> operands[i].trim());

        int r, a1, address, i;

        switch (instructionComponents[0]) {
            case "LDR":
            case "STR":
            case "LDA":
            case "JCC":
            case "SOB":
            case "JGE":
            case "AMR":
            case "SMR":
                r = Integer.parseInt(operands[0]);
                a1 = Integer.parseInt(operands[1]);
                address = Integer.parseInt(operands[2]);
                i = (operands.length > 3) ? Integer.parseInt(operands[3]) : 0; // Optional operand
                return String.format("%06o\t%06o", currentAddress,
                        (opcode << 10) | (r << 8) | (a1 << 6) | (i << 5) | address);

            case "LDX":
            case "STX":
            case "JZ":
            case "JNE":
            case "JMA":
            case "JSR":
                a1 = Integer.parseInt(operands[0]);
                address = Integer.parseInt(operands[1]);
                i = (operands.length > 2) ? Integer.parseInt(operands[2]) : 0;
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | (a1 << 6) | (i << 5) | address);

            case "SETCCE":
                r = Integer.parseInt(operands[0]);
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | (r << 8));

            case "RFS":
                address = Integer.parseInt(operands[0]);
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | address);

            case "AIR":
            case "SIR":
                r = Integer.parseInt(operands[0]);
                address = Integer.parseInt(operands[1]);
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | (r << 8) | address);

            default:
                return "ERROR: Unknown or invalid instruction!";
        }
    }

    //endregion

    //region First and Second passes
    /// <summary>
    /// firstPass Method:
    /// <params> inputFile </params>
    /// </summary>
    public ArrayList<String> firstPass(String inputFile) throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        ArrayList<String> inputRows = new ArrayList<>();
        String row;

        // Read and clean comments
        while ((row = reader.readLine()) != null) {
            int commentIndex = row.indexOf(';');
            if (commentIndex != -1) {
                row = row.substring(0, commentIndex).trim();
            } else {
                row = row.trim();
            }
            if (!row.isEmpty()) {
                inputRows.add(row);
            }
        }
        reader.close();

        // 🔹 Parse symbols & update currentAddress
        for (String line : inputRows) {
            // LOC directive
            if (line.startsWith("LOC")) {
                String locationString = "LOC";
                currentAddress = Integer.parseInt(line.substring(locationString.length()).trim());
                continue;
            }

            // Label handling (e.g., End:)
            int symbolIndex = line.indexOf(':');
            if (symbolIndex != -1) {
                String label = line.substring(0, symbolIndex).trim();
                symbolsMap.put(label, currentAddress);
                line = line.substring(symbolIndex + 1).trim(); // Remaining part after label
            }

            // Instructions or data → increment address
            if (!line.isEmpty()) {
                currentAddress++;
            }
        }

        return inputRows;
    }

    /// <summary>
    /// Second Pass Method:
    /// <params> inputFileLines</params>
    /// </summary>
    public void secondPass(ArrayList<String> inputFileLines) throws IOException {

        BufferedWriter lstWriter = new BufferedWriter(new FileWriter(LISTING_FILE));
        BufferedWriter objWriter = new BufferedWriter(new FileWriter(LOAD_FILE));
        currentAddress = 0;

        ArrayList<String> machineCodeOctal = new ArrayList<>();
        int symbolIndex;
        for (String inputInstruction : inputFileLines) {
            if (inputInstruction.startsWith("LOC")) {
                String locationDirective = "LOC";
                currentAddress = Integer.parseInt(inputInstruction.substring(locationDirective.length()).trim());
                machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, 0)); // Initialize memory location with zero value
                continue;
            }

            symbolIndex = inputInstruction.indexOf(':'); // Ignoring the label
            if (symbolIndex != -1) {
                inputInstruction = inputInstruction.substring(symbolIndex + 1).trim();
            }

            if (inputInstruction.isEmpty()) { // passing the empty instruction
                continue;
            }
            String[] instructionComponents = inputInstruction.split("\\s+", 2);
            if (instructionComponents[0].equals("Data")) {   // parsing using the opcodes form the hashmaps if the component is related to data
                int dataValue;
                try {
                    dataValue = Integer.parseInt(instructionComponents[1]);
                } catch (NumberFormatException e) {
                    dataValue = symbolsMap.get(instructionComponents[1]);
                }
                machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, dataValue));
            }

            else if ((instructionComponents[0].equals("AND")) || (instructionComponents[0].equals("ORR"))
                    || (instructionComponents[0].equals("NOT")) || (instructionComponents[0].equals("MLT"))
                    || (instructionComponents[0].equals("DVD")) || (instructionComponents[0].equals("TRR"))) { //parsing using the opcodes form the hashmaps if the component is related to arithmetic and logical operations
                int opcode = opcodeForArithmeticAndLogic.get(instructionComponents[0]);

                // Split and trim the operand list (assuming operands are comma-separated)
                String[] operands = instructionComponents[1].split(",");
                Arrays.setAll(operands, i -> operands[i].trim());

                int reg1, reg2;

                if (instructionComponents[0].equals("AND") || instructionComponents[0].equals("ORR")
                        || instructionComponents[0].equals("MLT")
                        || instructionComponents[0].equals("DVD") || instructionComponents[0].equals("TRR")) {
                    reg1 = Integer.parseInt(operands[0]);
                    reg2 = Integer.parseInt(operands[1]);
                    machineCodeOctal
                            .add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (reg1 << 8) | (reg2 << 6)));
                }
                if (instructionComponents[0].equals("NOT")) {
                    reg1 = Integer.parseInt(operands[0]);
                    machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (reg1 << 8)));
                }
            }

            else if ((instructionComponents[0].equals("SRC")) || (instructionComponents[0].equals("RRC"))) { // parsing using the opcodes form the hashmaps if the component is related to shift rotate operation

                int opcode = opcodeForShiftRotate.get(instructionComponents[0]);// Get the opcode from the
                // corresponding hashmap

                String[] operands = instructionComponents[1].split(",");// Spliting and trim the operand list
                Arrays.setAll(operands, i -> operands[i].trim());

                int a, b, c, d;
                if (instructionComponents[0].equals("SRC") || instructionComponents[0].equals("RRC")) {
                    a = Integer.parseInt(operands[0]);
                    b = Integer.parseInt(operands[1]);
                    c = Integer.parseInt(operands[2]);
                    d = Integer.parseInt(operands[3]);
                    machineCodeOctal.add(String.format("%06o\t%06o", currentAddress,
                            (opcode << 10) | (a << 8) | (d << 7) | (c << 6) | b));
                }
            }

            else if ((instructionComponents[0].equals("IN")) || (instructionComponents[0].equals("OUT"))
                    || (instructionComponents[0].equals("CHK"))) { //parsing using the opcodes form the hashmaps if the component is related to Input and output opertions
                // Get the opcode from the map
                int opcode = opcodeForIO.get(instructionComponents[0]);

                // Split and trim the operand list
                String[] operands = instructionComponents[1].split(",");
                Arrays.setAll(operands, i -> operands[i].trim());

                int r, devId;
                r = Integer.parseInt(operands[0]);
                devId = Integer.parseInt(operands[1]);
                machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (r << 8) | devId));

            } else if ((instructionComponents[0].equals("HLT")) || (instructionComponents[0].equals("TRAP"))) { //parsing using the opcodes form the hashmaps if the component is related to Misllaneous operations
                // Get the opcode from the map
                int opcode = opcodeForMisallaneous.get(instructionComponents[0]);

                // Switch statement to handle specific instructions like HLT and TRAP
                switch (instructionComponents[0]) {
                    case "HLT": // Halt instruction has no operand, return machine code with 0
                        machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, 0));
                        break;
                    case "TRAP": // Trap instruction has an operand, combining both opcode and operand
                        // Ensuring if there's a second component for the operand
                        if (instructionComponents.length > 1) {
                            int operand = Integer.parseInt(instructionComponents[1]);
                            machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | operand));
                        } else {
                            machineCodeOctal.add("ERROR: Missing operand for TRAP instruction!");
                        }
                        break;
                    default: // for handling unknown instructions

                        if (opcode == 0) {// Check if the opcode exists, if not return an error
                            machineCodeOctal.add("ERROR: Unknown instruction!");
                        }
                        machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, opcode));
                }
            }

            //TODO: Floating Point SecondPass

            else if ((instructionComponents[0].equals("LDR")) || (instructionComponents[0].equals("STR")) ||
                    (instructionComponents[0].equals("LDA")) || (instructionComponents[0].equals("LDX")) ||
                    (instructionComponents[0].equals("STX")) || (instructionComponents[0].equals("SETCCE")) ||
                    (instructionComponents[0].equals("JZ")) || (instructionComponents[0].equals("JNE")) ||
                    (instructionComponents[0].equals("JCC")) || (instructionComponents[0].equals("JMA")) ||
                    (instructionComponents[0].equals("JSR")) || (instructionComponents[0].equals("RFS")) ||
                    (instructionComponents[0].equals("SOB")) || (instructionComponents[0].equals("JGE")) ||
                    (instructionComponents[0].equals("AMR")) || (instructionComponents[0].equals("SMR")) ||
                    (instructionComponents[0].equals("SIR")) || (instructionComponents[0].equals("AIR"))) { // parsing using the opcodes form the hashmaps if the component is related to load , store , jump etc operations.
                machineCodeOctal.add(lsInstructionParse(instructionComponents));
            } else {
                machineCodeOctal.add("ERROR: Unknown instruction!");
            }
            currentAddress++;
        }
        System.out.println("\nBinary Code Lines:");
        System.out.println(machineCodeOctal);
        // Write to files
        writeDataToFile(LOAD_FILE, machineCodeOctal); // loadfile generation - output file
        generateListingFile(inputFileLines, LISTING_FILE, machineCodeOctal); //  listing file generation - output file
        lstWriter.close();
        objWriter.close();
    }

    //endregion

    /// <summary>
    /// Main Entry Point
    /// </summary>
    public static void main(String[] args) {
        try {
            Assembler assembler = new Assembler();

            // 🔹 First pass
            ArrayList<String> inputLines = assembler.firstPass("sourceProgram.txt");

            // Debug: print symbol table
            System.out.println("Symbol Table:");
            for (String key : assembler.symbolsMap.keySet()) {
                System.out.println(key + " -> " + assembler.symbolsMap.get(key));
            }

            // Debug: print cleaned input lines
            System.out.println("\nCleaned Input Lines:");
            for (String line : inputLines) {
                System.out.println(line);
            }

            // 🔹 Second pass
            assembler.secondPass(inputLines);
            System.out.println("\nAssembly completed. Check listingFile.txt and LoadFile.txt for output.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
