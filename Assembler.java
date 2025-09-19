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

        int outputIndex = 0;
        for (int i = 0; i < inputFileLines.size(); i++) {
            String sourceLine = inputFileLines.get(i);
            if (sourceLine.startsWith("LOC")) {
                dataToWrite.add(sourceLine); // No machine code for LOC
            } else {
                String resultLine = (outputIndex < output.size()) ? output.get(outputIndex++) : "";
                dataToWrite.add(String.format("%s %s", resultLine, sourceLine));
            }
        }
        writeDataToFile(destinationFile, dataToWrite);
    }

    // Utility: Parse the instructions
    String lsInstructionParse(String[] instructionComponents) {
        // Get the opcode from the map
        int opcode = opcodeForLSAndOther.get(instructionComponents[0]);

        // Split and trim the operand list
        String[] operands = instructionComponents[1].split(",");
        Arrays.setAll(operands, i -> operands[i].trim());

        int r, x, address, i; // Changed 'a1' to 'x' to be more descriptive

        switch (instructionComponents[0]) {
            case "LDR":
            case "STR":
            case "LDA":
            case "JCC":
            case "JZ":
            case "JNE":
            case "JMA":
            case "JSR":
            case "SOB":
            case "JGE":
            case "AMR":
            case "SMR":
                r = Integer.parseInt(operands[0]);
                x = Integer.parseInt(operands[1]);
                address = Integer.parseInt(operands[2]);
                i = (operands.length > 3) ? Integer.parseInt(operands[3]) : 0; // Optional operand
                return String.format("%06o\t%06o", currentAddress,
                        (opcode << 10) | (r << 8) | (x << 6) | (i << 5) | address);

            case "LDX":
            case "STX":
                x = Integer.parseInt(operands[0]);
                address = Integer.parseInt(operands[1]);
                i = (operands.length > 2) ? Integer.parseInt(operands[2]) : 0;
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | (x << 6) | (i << 5) | address);

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
        // Handler map for opcodes
        java.util.Map<String, java.util.function.BiConsumer<String[], ArrayList<String>>> handlerMap = new java.util.HashMap<>();
        handlerMap.put("Data", this::handleData);
        // Arithmetic/Logic
        handlerMap.put("AND", this::handleArithmeticLogic);
        handlerMap.put("ORR", this::handleArithmeticLogic);
        handlerMap.put("NOT", this::handleArithmeticLogic);
        handlerMap.put("MLT", this::handleArithmeticLogic);
        handlerMap.put("DVD", this::handleArithmeticLogic);
        handlerMap.put("TRR", this::handleArithmeticLogic);
        // Shift/Rotate
        handlerMap.put("SRC", this::handleShiftRotate);
        handlerMap.put("RRC", this::handleShiftRotate);
        // IO
        handlerMap.put("IN", this::handleIO);
        handlerMap.put("OUT", this::handleIO);
        handlerMap.put("CHK", this::handleIO);
        // Misc
        handlerMap.put("HLT", this::handleMisc);
        handlerMap.put("TRAP", this::handleMisc);
        // Load/Store/Jump/Other
        String[] lsOps = {"LDR","STR","LDA","LDX","STX","SETCCE","JZ","JNE","JCC","JMA","JSR","RFS","SOB","JGE","AMR","SMR","SIR","AIR"};
        for (String op : lsOps) handlerMap.put(op, this::handleLSOther);

        currentAddress = 0;
        ArrayList<String> machineCodeOctal = new ArrayList<>();
        int symbolIndex;
        for (String inputInstruction : inputFileLines) {
            if (inputInstruction.startsWith("LOC")) {
                String locationDirective = "LOC";
                currentAddress = Integer.parseInt(inputInstruction.substring(locationDirective.length()).trim());
                continue;
            }
            symbolIndex = inputInstruction.indexOf(':');
            if (symbolIndex != -1) {
                inputInstruction = inputInstruction.substring(symbolIndex + 1).trim();
            }
            if (inputInstruction.isEmpty()) {
                continue;
            }
            String[] instructionComponents = inputInstruction.split("\\s+", 2);
            String opcode = instructionComponents[0];
            java.util.function.BiConsumer<String[], ArrayList<String>> handler = handlerMap.get(opcode);
            if (handler != null) {
                handler.accept(instructionComponents, machineCodeOctal);
            } else {
                machineCodeOctal.add("ERROR: Unknown instruction!");
            }
            currentAddress++;
        }
        System.out.println("\nBinary Code Lines:");
        System.out.println(machineCodeOctal);
        writeDataToFile(LOAD_FILE, machineCodeOctal);
        generateListingFile(inputFileLines, LISTING_FILE, machineCodeOctal);
    }

    // Handler for Data
    private void handleData(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        int dataValue;
        try {
            dataValue = Integer.parseInt(instructionComponents[1]);
        } catch (NumberFormatException e) {
            dataValue = symbolsMap.get(instructionComponents[1]);
        }
        machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, dataValue));
    }

    // Handler for Arithmetic/Logic
    private void handleArithmeticLogic(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        int opcode = opcodeForArithmeticAndLogic.get(instructionComponents[0]);
        String[] operands = instructionComponents[1].split(",");
        Arrays.setAll(operands, i -> operands[i].trim());
        int reg1, reg2;
        if (instructionComponents[0].equals("AND") || instructionComponents[0].equals("ORR")
                || instructionComponents[0].equals("MLT") || instructionComponents[0].equals("DVD") || instructionComponents[0].equals("TRR")) {
            reg1 = Integer.parseInt(operands[0]);
            reg2 = Integer.parseInt(operands[1]);
            machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (reg1 << 8) | (reg2 << 6)));
        } else if (instructionComponents[0].equals("NOT")) {
            reg1 = Integer.parseInt(operands[0]);
            machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (reg1 << 8)));
        }
    }

    // Handler for Shift/Rotate
    private void handleShiftRotate(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        int opcode = opcodeForShiftRotate.get(instructionComponents[0]);
        String[] operands = instructionComponents[1].split(",");
        Arrays.setAll(operands, i -> operands[i].trim());
        int a = Integer.parseInt(operands[0]);
        int b = Integer.parseInt(operands[1]);
        int c = Integer.parseInt(operands[2]);
        int d = Integer.parseInt(operands[3]);
        machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (a << 8) | (d << 7) | (c << 6) | b));
    }

    // Handler for IO
    private void handleIO(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        int opcode = opcodeForIO.get(instructionComponents[0]);
        String[] operands = instructionComponents[1].split(",");
        Arrays.setAll(operands, i -> operands[i].trim());
        int r = Integer.parseInt(operands[0]);
        int devId = Integer.parseInt(operands[1]);
        machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (r << 8) | devId));
    }

    // Handler for Miscellaneous (HLT, TRAP)
    private void handleMisc(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        int opcode = opcodeForMisallaneous.get(instructionComponents[0]);
        switch (instructionComponents[0]) {
            case "HLT":
                machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, 0));
                break;
            case "TRAP":
                if (instructionComponents.length > 1) {
                    int operand = Integer.parseInt(instructionComponents[1]);
                    machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | operand));
                } else {
                    machineCodeOctal.add("ERROR: Missing operand for TRAP instruction!");
                }
                break;
            default:
                if (opcode == 0) {
                    machineCodeOctal.add("ERROR: Unknown instruction!");
                }
                machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, opcode));
        }
    }

    // Handler for Load/Store/Other
    private void handleLSOther(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        machineCodeOctal.add(lsInstructionParse(instructionComponents));
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
