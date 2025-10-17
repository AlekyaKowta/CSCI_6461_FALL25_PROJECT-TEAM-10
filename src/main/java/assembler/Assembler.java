package src.main.java.assembler;

import javax.swing.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;

public class Assembler {

    // Tracks current address during assembly
    public int currentAddress = 0;

    // // NEW: Tracks the address of the first actual instruction (not LOC, not Data)
    // public int firstInstructionAddress = -1;

    // Output file names
    public String LISTING_FILE = "ListingFile.txt";
    public String LOAD_FILE = "LoadFile.txt";

    // Symbol Table mapping labels to addresses
    public HashMap<String, Integer> symbolsMap = new HashMap<>();

    // Expose machine code list for access after second pass
    public ArrayList<String> machineCodeOctal = new ArrayList<>();

    // region Helper Methods

    /// <summary>
    /// Writes list of output lines to a file.
    /// </summary>
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

    /// <summary>
    /// Checks if it is blank/ skippable
    /// </summary>
    private boolean isSkippableLine(String line) {
        String trimmed = line.trim();
        // LOC line
        if (trimmed.startsWith("LOC")) return true;
        // Blank or comment-only
        if (trimmed.isEmpty() || trimmed.startsWith(";")) return true;
        // Standalone label (e.g. "End:" or "Msg:")
        if (trimmed.matches("^[a-zA-Z_][a-zA-Z0-9_]*:$")) return true;
        return false;
    }

    /// <summary>
    /// Generates the assembly listing file which includes source lines alongside their machine code.
    /// </summary>
    /// <param name="inputFileLines">List of source input lines</param>
    /// <param name="destinationFile">Path of output listing file</param>
    /// <param name="output">Generated machine code lines aligned with source</param>
    public void generateListingFile(ArrayList<String> originalLines, String destinationFile, ArrayList<String> machineCodeOctal) {
        ArrayList<String> dataToWrite = new ArrayList<>();

        // // Rationale: We assume the entry at index 0 is the M[5] Entry Point (System Metadata)
        // // and that the first line of executable code starts at index 1.
        // int outputIndex = 1; // START AT INDEX 1 TO SKIP M[5] ENTRY

        // Process from start
        int outputIndex = 0;

        for (String sourceLine : originalLines) {
            if (isSkippableLine(sourceLine)) {
                // Skips LOC, blank lines, and standalone labels—these have no machine code.
                dataToWrite.add(sourceLine);
            } else {
                String resultLine = "";

                // Check if there is a machine code entry left to align with the source line.
                if (outputIndex < machineCodeOctal.size()) {
                    // Fetch the aligned machine code
                    resultLine = machineCodeOctal.get(outputIndex);
                    outputIndex++; // Advance the machine code pointer to the next generated line
                }

                // Format: [ADDRESS  VALUE] [Source Line]
                dataToWrite.add(String.format("%s %s", resultLine, sourceLine));
            }
        }
        writeDataToFile(destinationFile, dataToWrite);
    }

    /// <summary>
    /// Splits operand string by comma and trims whitespace from each operand.
    /// </summary>
    /// <param name="operandString">Raw operand string</param>
    /// <returns>Array of trimmed operand strings</returns>
    private String[] parseOperands(String operandString) {
        String[] ops = operandString.split(",");
        Arrays.setAll(ops, i -> ops[i].trim());
        return ops;
    }

    /// <summary>
    /// Before parsing the operand as an integer, check if it is a label and get its numeric address from the symbol table
    /// </summary>
    private int resolveOperandToAddress(String operand) {
        if (symbolsMap.containsKey(operand)) {
            return symbolsMap.get(operand);
        } else {
            try{
                return Integer.parseInt(operand);
            }
            catch(NumberFormatException e) {
                // The operand is not a defined label AND it's not a valid number.
                // This is a fatal "Undefined Symbol" error that must be reported.
                throw new IllegalArgumentException("Undefined symbol '" + operand + "'.");
            }

        }
    }

    /// <summary>
    /// Parses load/store and related instructions into machine code words.
    /// Instructions supported vary and have different formats; handled via switch-case.
    /// </summary>
    /// <param name="instructionComponents">OpCode and operand string array</param>
    /// <returns>Formatted string: octal address and machine code</returns>
    String lsInstructionParse(String[] instructionComponents) {
        // Get the opcode based on instruction mnemonic
        // NOTE: This assumes OpCodeTables is in scope and initialized
        int opcode = OpCodeTables.loadStoreOther.get(instructionComponents[0]);

        // Split and trim the operand list
        String[] operands = parseOperands(instructionComponents[1]);

        // reg: General purpose register
        // idx: Index register
        // address: Memory address or label resolved to integer
        // indirect: Indirect addressing bit (optional)
        int reg, idx, address, indirect;

        // Instructions with format: opcode r,x,address[,i]
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
                reg = Integer.parseInt(operands[0]);
                idx = Integer.parseInt(operands[1]);
                address = resolveOperandToAddress(operands[2]);
                indirect = (operands.length > 3) ? Integer.parseInt(operands[3]) : 0; // Optional operand
                return String.format("%06o\t%06o", currentAddress,
                        (opcode << 10) | (reg << 8) | (idx << 6) | (indirect << 5) | address);

            // Instructions with format: opcode x,address[,i]
            case "LDX":
            case "STX":
                idx = Integer.parseInt(operands[0]);
                address = resolveOperandToAddress(operands[1]);
                indirect = (operands.length > 2) ? Integer.parseInt(operands[2]) : 0;
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | (idx << 6) | (indirect << 5) | address);

            // Instructions with format: opcode r[,i]
            case "SETCCE":
                reg = Integer.parseInt(operands[0]);
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | (reg << 8));

            // Instructions with format: opcode address
            case "RFS":
                address = resolveOperandToAddress(operands[0]);
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | address);

            // Instructions with format: opcode r,address
            case "AIR":
            case "SIR":
                reg = Integer.parseInt(operands[0]);
                address = resolveOperandToAddress(operands[1]);
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | (reg << 8) | address);

            default:
                return "ERROR: Unknown or invalid instruction!";
        }
    }

    //endregion

    //region First and Second passes

    /// <summary>
    /// Reads source program, strips comments, preserves original Lines, collects labels and addresses, prepares cleaned input lines for assembly.
    /// </summary>
    /// <param name="inputFile">File path to source code</param>
    /// <returns>List of cleaned source lines</returns>
    public ArrayList<String> firstPassWithComments(String inputFile, ArrayList<String> originalLines) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        ArrayList<String> cleanLines = new ArrayList<>();
        String row;

        while ((row = reader.readLine()) != null) {
            originalLines.add(row);  // preserve comment lines
            String clean = row;
            int commentIndex = row.indexOf(';');
            if (commentIndex != -1) {
                clean = row.substring(0, commentIndex).trim();
            } else {
                clean = row.trim();
            }
            if (!clean.isEmpty()) {
                cleanLines.add(clean);
            }
        }
        reader.close();

        currentAddress = 0; // Reset for Pass 1 Symbol Table build
        // Delete reserved code
        // boolean isFirstExecutableInstruction = true; // Flag to track first instruction

        // Symbol table build on clean lines
        for (String line : cleanLines) {
            if (line.startsWith("LOC")) {
                String locationString = "LOC";
                currentAddress = Integer.parseInt(line.substring(locationString.length()).trim());
                continue;
            }

            int symbolIndex = line.indexOf(':');
            if (symbolIndex != -1) {
                String label = line.substring(0, symbolIndex).trim();
                symbolsMap.put(label, currentAddress);
            }

            if (!line.isEmpty()) {
                // Delete reserved space code
                // // If this is the first non-LOC, non-label, non-blank line, record its address.
                // if (isFirstExecutableInstruction && !line.startsWith("Data")) {
                //     this.firstInstructionAddress = currentAddress;
                //     isFirstExecutableInstruction = false;
                // }
                currentAddress++;
            }
        }
        return cleanLines;
    }

    /// <summary>
    /// Injects the address of the first executable instruction into M[5].
    /// DELETE RESERVE CODE
    /// </summary>
    // private void injectEntrypoint() {
    //     if (firstInstructionAddress != -1) {
    //         // Reserved Memory Address 5 is used as the Execution Start Address Register (ESAR) location.
    //         int reservedAddress5 = 5;
    //
    //         // 1. Format the true starting address (e.g., 000016) into 6-digit octal.
    //         String entryPointValueOctal = String.format("%06o", firstInstructionAddress);
    //
    //         // 2. Create the system metadata line: "000005 [TAB] 000016"
    //         String entryPointLine = String.format("%06o\t%s", reservedAddress5, entryPointValueOctal);
    //
    //         // 3. CRITICAL: Inject this line at index 0 of the machineCodeOctal list.
    //         // This ensures M[5] is the very first entry in the Load File.
    //         machineCodeOctal.add(0, entryPointLine);
    //     }
    // }

    /// <summary>
    /// Second pass assembles machine codes line by line using appropriate handlers based on opcode.
    /// </summary>
    /// <param name="inputFileLines">List of cleaned source lines from first pass</param>
    public void secondPass(ArrayList<String> inputFileLines) throws IOException {
        // Handler map for opcodes
        Map<String, BiConsumer<String[], ArrayList<String>>> handlerMap = new HashMap<>();
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
        machineCodeOctal.clear();
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
            BiConsumer<String[], ArrayList<String>> handler = handlerMap.get(opcode);
            if (handler != null) {
                handler.accept(instructionComponents, machineCodeOctal);
            } else {
                machineCodeOctal.add("ERROR: Unknown instruction!");
            }
            currentAddress++;
        }

        // Delete Reserved Space code
        // CRITICAL ADD: Inject the M[5] entry point BEFORE writing the file.
        // injectEntrypoint();

        System.out.println("\nBinary Code Lines:");
        System.out.println(machineCodeOctal);
        writeDataToFile(LOAD_FILE, machineCodeOctal);
    }

    /// <summary>
    /// Additional Helper Methods for secondPass
    /// </summary>
    private void handleData(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        int dataValue;
        String operand = instructionComponents[1];
        if (symbolsMap.containsKey(operand)) {
            dataValue = symbolsMap.get(operand);
        } else {
            dataValue = Integer.parseInt(operand);
        }
        machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, dataValue));
    }

    /// <summary>
    /// Additional Helper Methods for secondPass: Arithmetic and Logic
    /// </summary>
    private void handleArithmeticLogic(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        // NOTE: This assumes OpCodeTables is in scope and initialized
        int opcode = OpCodeTables.arithmeticAndLogic.get(instructionComponents[0]);
        String[] operands = parseOperands(instructionComponents[1]);
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

    /// <summary>
    /// Additional Helper Methods for secondPass: Shift Rotate
    /// </summary>
    private void handleShiftRotate(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        // NOTE: This assumes OpCodeTables is in scope and initialized
        int opcode = OpCodeTables.shiftRotate.get(instructionComponents[0]);
        String[] operands = parseOperands(instructionComponents[1]);
        int a = Integer.parseInt(operands[0]);
        int b = Integer.parseInt(operands[1]);
        int c = Integer.parseInt(operands[2]);
        int d = Integer.parseInt(operands[3]);
        machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (a << 8) | (d << 7) | (c << 6) | b));
    }

    /// <summary>
    /// Additional Helper Methods for secondPass: IO
    /// </summary>
    private void handleIO(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        // NOTE: This assumes OpCodeTables is in scope and initialized
        int opcode = OpCodeTables.io.get(instructionComponents[0]);
        String[] operands = parseOperands(instructionComponents[1]);
        Arrays.setAll(operands, i -> operands[i].trim());
        int r = Integer.parseInt(operands[0]);
        int devId = Integer.parseInt(operands[1]);
        machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (r << 8) | devId));
    }

    /// <summary>
    /// Additional Helper Methods for secondPass: Miscellaneous
    /// </summary>
    private void handleMisc(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        // NOTE: This assumes OpCodeTables is in scope and initialized
        int opcode = OpCodeTables.miscellaneous.get(instructionComponents[0]);
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

    /// <summary>
    /// Additional Helper Methods for secondPass: Load Store and Other
    /// </summary>
    private void handleLSOther(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        machineCodeOctal.add(lsInstructionParse(instructionComponents));
    }

    //endregion

    /// <summary>
    /// Main method to run assembler: reads source file, performs assembly passes, and writes outputs.
    /// </summary>
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            JFileChooser fileChooser = new JFileChooser(".");
            fileChooser.setDialogTitle("Select Assembly Source File");

            int userSelection = fileChooser.showOpenDialog(null);

            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToOpen = fileChooser.getSelectedFile();
                String inputSourceFile = fileToOpen.getAbsolutePath();

                Assembler assembler = new Assembler();

                ArrayList<String> originalLines = new ArrayList<>();

                // First Pass
                ArrayList<String> cleanedLines = assembler.firstPassWithComments(inputSourceFile, originalLines);

                // Debug: print symbol table
                System.out.println("Symbol Table:");
                for (String key : assembler.symbolsMap.keySet()) {
                    System.out.println(key + " -> " + assembler.symbolsMap.get(key));
                }

                // Debug: print cleaned input lines
                System.out.println("\nOriginal Input Lines:");
                for (String line : originalLines) {
                    System.out.println(line);
                }

                // Second pass
                assembler.secondPass(cleanedLines);
                //Generate Listing File
                assembler.generateListingFile(originalLines, assembler.LISTING_FILE, assembler.machineCodeOctal);
                System.out.println("\nAssembly completed. Check listingFile.txt and LoadFile.txt for output.");
            }
            else{
                System.out.println("No file selected. Exiting...");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("ASSEMBLER FATAL ERROR: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("FILE I/O ERROR: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("UNEXPECTED ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}