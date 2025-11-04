package src.main.java.assembler;

import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.function.BiConsumer;

public class Assembler {

    // Tracks current address during assembly
    public int currentAddress = 0;

    // Output file names
    public String LISTING_FILE = "ListingFile.txt";
    public String LOAD_FILE = "LoadFile.txt";

    public static final int ADDRESS_MASK = 037;

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

    private int parseAndValidateR(String operand) {
        int r = Integer.parseInt(operand);
        if (r < 0 || r > 3) { // R0-R3
            throw new IllegalArgumentException("Invalid General Purpose Register (R) number: " + r + ". Must be between 0 and 3.");
        }
        return r;
    }

    private int parseAndValidateIX(String operand) {
        int ix = Integer.parseInt(operand);
        if (ix < 0 || ix > 3) { // X0-X3 (where 0 means no indexing)
            throw new IllegalArgumentException("Invalid Index Register (IX) number: " + ix + ". Must be between 0 and 3.");
        }
        return ix;
    }

    private int parseAndValidateAddress(String operand, boolean isImmediate) {
        // Resolve labels first
        int address = resolveOperandToAddress(operand);
        if (address < 0 || address > 31) { // 5 bits unsigned (Maximum absolute value is 31)
            String field = isImmediate ? "Immediate" : "Address Field";
            throw new IllegalArgumentException("Invalid " + field + " value: " + address + ". Must be between 0 and 31.");
        }
        return address;
    }

    private int parseAndValidateEvenR(String operand) {
        int r = Integer.parseInt(operand);
        if (r != 0 && r != 2) {
            throw new IllegalArgumentException("Invalid register for Multiply/Divide operation: " + r + ". Must be 0 or 2.");
        }
        return r;
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
            case "JSR":
            case "SOB":
            case "JGE":
            case "AMR":
            case "SMR":
                reg = parseAndValidateR(operands[0]);
                idx = parseAndValidateIX(operands[1]);
                address = parseAndValidateAddress(operands[2], false);
                indirect = (operands.length > 3) ? Integer.parseInt(operands[3]) : 0; // Optional operand
                return String.format("%06o\t%06o", currentAddress,
                        (opcode << 10) | (reg << 8) | (idx << 6) | (indirect << 5) | address);

            case "JMA":
                idx = parseAndValidateIX(operands[0]);
                address = parseAndValidateAddress(operands[1], false);  // must return 0..31
                indirect = (operands.length > 2) ? Integer.parseInt(operands[2]) : 0;
                if (indirect != 0 && indirect != 1) {
                    throw new IllegalArgumentException("Indirect bit (I) must be 0 or 1 for JMA.");
                }

                // Bits 9–8 (R field) are 00 for JMA
                return String.format("%06o\t%06o", currentAddress,
                        (opcode << 10) | (idx << 6) | (indirect << 5) | (address & 0x1F));

            // Instructions with format: opcode x,address[,i]
            case "LDX":
            case "STX":
                idx = parseAndValidateIX(operands[0]);
                address = parseAndValidateAddress(operands[1], false);
                indirect = (operands.length > 2) ? Integer.parseInt(operands[2]) : 0;
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | (idx << 6) | (indirect << 5) | address);

            // Instructions with format: opcode r[,i]
            case "SETCCE":
                reg = parseAndValidateR(operands[0]);
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | (reg << 8));

            // Instructions with format: opcode address
            case "RFS":
                address = parseAndValidateAddress(operands[0], true);
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | address);

            // Instructions with format: opcode r,address
            case "AIR":
            case "SIR":
                reg = parseAndValidateR(operands[0]);
                address = parseAndValidateAddress(operands[1], true); // Immed is validated
                return String.format("%06o\t%06o", currentAddress, (opcode << 10) | (reg << 8) | address);

            default:
                return String.format("ERROR: Unknown or invalid instruction! Mnemonic: %s", instructionComponents[0]);
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
            originalLines.add(row);
            int semi = row.indexOf(';');
            String clean = (semi >= 0 ? row.substring(0, semi) : row).trim();
            if (!clean.isEmpty()) cleanLines.add(clean);
        }
        reader.close();

        // Opcodes/pseudo-ops that allocate exactly one 16-bit word
        final Set<String> oneWordOps = new HashSet<>(Arrays.asList(
                // Misc
                "HLT","TRAP",
                // Load/Store
                "LDR","STR","LDA","LDX","STX",
                // Transfer
                "JZ","JNE","JCC","JMA","JSR","RFS","SOB","JGE",
                // Arithmetic/Logical (memory/immediate)
                "AMR","SMR","AIR","SIR",
                // Register-to-register & mult/div/logical
                "MLT","DVD","TRR","AND","ORR","NOT",
                // Shift/Rotate
                "SRC","RRC",
                // I/O
                "IN","OUT","CHK",
                // Floating/vector (if you implement them now)
                "FADD","FSUB","VADD","VSUB","CNVRT","LDFR","STFR"
        ));

        currentAddress = 0;

        for (String line : cleanLines) {
            String s = line.trim();

            // Handle "LOC n" anywhere on the line (with or without label)
            // Split label (if present)
            int colon = s.indexOf(':');
            String label = null;
            String body = s;
            if (colon >= 0) {
                label = s.substring(0, colon).trim();
                body  = s.substring(colon + 1).trim();
                // Duplicate label guard
                if (symbolsMap.containsKey(label)) {
                    throw new IllegalArgumentException("Duplicate label: " + label);
                }
                symbolsMap.put(label, currentAddress);
            }

            if (body.isEmpty()) continue;

            // Tokenize body to identify directive/opcode
            String[] toks = body.split("\\s+|\\t+|,\\s*");
            if (toks.length == 0) continue;

            String head = toks[0].toUpperCase(Locale.ROOT);

            // LOC does NOT allocate memory
            if ("LOC".equals(head)) {
                if (toks.length < 2) {
                    throw new IllegalArgumentException("LOC requires a decimal address.");
                }
                int loc = Integer.parseInt(toks[1]); // ISA: decimal in source
                currentAddress = loc;
                continue;
            }

            // DATA allocates exactly one word
            if ("DATA".equals(head)) {
                currentAddress += 1;
                continue;
            }

            // Known opcodes allocate one word
            if (oneWordOps.contains(head)) {
                currentAddress += 1;
                continue;
            }

            // Anything else is an error in pass 1 (prevents phantom increments)
            throw new IllegalArgumentException("Unknown opcode/directive in pass 1: " + head);
        }

        return cleanLines;
    }

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
                currentAddress++;
            } else {
                machineCodeOctal.add(String.format("ERROR: Unknown instruction! Mnemonic: %s", opcode));
            }
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
        if (instructionComponents[0].equals("MLT") || instructionComponents[0].equals("DVD")) {
            reg1 = parseAndValidateEvenR(operands[0]);
            reg2 = parseAndValidateEvenR(operands[1]);
            machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (reg1 << 8) | (reg2 << 6)));
        }
        else if (instructionComponents[0].equals("AND") || instructionComponents[0].equals("ORR")
                || instructionComponents[0].equals("TRR")) {
            reg1 = parseAndValidateR(operands[0]);
            reg2 = parseAndValidateR(operands[1]);
            machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (reg1 << 8) | (reg2 << 6)));
        } else if (instructionComponents[0].equals("NOT")) {
            reg1 = parseAndValidateR(operands[0]);
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
        int r = parseAndValidateR(operands[0]); // R field (a)
        int count = Integer.parseInt(operands[1]); // Count field (b)
        int lr = Integer.parseInt(operands[2]); // L/R field (c)
        int al = Integer.parseInt(operands[3]); // A/L field (d)

        if (count < 0 || count > 15) {
            throw new IllegalArgumentException("Invalid Count value for Shift/Rotate: " + count + ". Must be between 0 and 15 (4 bits).");
        }
        machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (r << 8) | (al << 7) | (lr << 6) | count));
    }

    /// <summary>
    /// Additional Helper Methods for secondPass: IO
    /// </summary>
    private void handleIO(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        int opcode = OpCodeTables.io.get(instructionComponents[0]);
        String[] operands = parseOperands(instructionComponents[1]);
        int r = parseAndValidateR(operands[0]);
        int devId = Integer.parseInt(operands[1]);

        if (devId < 0 || devId > 31) {
            throw new IllegalArgumentException("Invalid Device ID: " + devId + ". Must be between 0 and 31 (5 bits).");
        }

        machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, (opcode << 10) | (r << 8) | devId));
    }

    /// <summary>
    /// Additional Helper Methods for secondPass: Miscellaneous
    /// </summary>
    private void handleMisc(String[] instructionComponents, ArrayList<String> machineCodeOctal) {
        int opcode = OpCodeTables.miscellaneous.get(instructionComponents[0]);
        switch (instructionComponents[0]) {
            case "HLT":
                machineCodeOctal.add(String.format("%06o\t%06o", currentAddress, 0));
                break;
            case "TRAP":
                if (instructionComponents.length > 1) {
                    int operand = Integer.parseInt(instructionComponents[1]);
                    if (operand < 0 || operand > 15) {
                        throw new IllegalArgumentException("Invalid TRAP code: " + operand + ". Must be between 0 and 15 (4 bits).");
                    }
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
        // Ensure L&F is set on the EDT before any Swing UI
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        });

        // Determine input source (CLI arg preferred to avoid chooser in IDE runs)
        String inputSourceFile;
        if (args != null && args.length > 0) {
            inputSourceFile = new java.io.File(args[0]).getAbsolutePath();
        } else {
            final String[] chosen = new String[1];

            // Show JFileChooser on the EDT with a visible owner to avoid macOS window stacking issues
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                javax.swing.JFileChooser fileChooser = new javax.swing.JFileChooser(".");
                fileChooser.setDialogTitle("Select Assembly Source File");

                javax.swing.JFrame owner = new javax.swing.JFrame();
                owner.setUndecorated(true);
                owner.setAlwaysOnTop(true);
                // Mark as utility window (helps macOS focus behavior)
                try {
                    owner.setType(java.awt.Window.Type.UTILITY);
                } catch (Throwable ignored) { /* not available on older JREs */ }

                owner.setLocationRelativeTo(null);
                owner.setVisible(true);

                try {
                    int result = fileChooser.showOpenDialog(owner);
                    if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                        chosen[0] = fileChooser.getSelectedFile().getAbsolutePath();
                    }
                } finally {
                    owner.dispose();
                }
            });

            if (chosen[0] == null) {
                System.out.println("No file selected. Exiting...");
                return;
            }
            inputSourceFile = chosen[0];
        }

        // === Your original assembly flow (preserves comments via originalLines) ===
        Assembler assembler = new Assembler();

        java.util.ArrayList<String> originalLines = new java.util.ArrayList<>();
        // First pass that preserves comments into originalLines
        java.util.ArrayList<String> cleanedLines = assembler.firstPassWithComments(inputSourceFile, originalLines);

        // Debug: print symbol table
        System.out.println("Symbol Table:");
        for (java.util.Map.Entry<String, Integer> e : assembler.symbolsMap.entrySet()) {
            System.out.println(e.getKey() + " -> " + e.getValue());
        }

        // Debug: print original input lines (with comments preserved)
        System.out.println("\nOriginal Input Lines:");
        for (String line : originalLines) {
            System.out.println(line);
        }

        // Second pass + outputs
        assembler.secondPass(cleanedLines);
        assembler.generateListingFile(originalLines, assembler.LISTING_FILE, assembler.machineCodeOctal);

        System.out.println("\nAssembly completed. Check listingFile.txt and LoadFile.txt for output.");

    } catch (IllegalArgumentException e) {
        System.err.println("ASSEMBLER FATAL ERROR: " + e.getMessage());
    } catch (java.io.IOException e) {
        System.err.println("FILE I/O ERROR: " + e.getMessage());
    } catch (Exception e) {
        System.err.println("UNEXPECTED ERROR: " + e.getMessage());
        e.printStackTrace();
    }
}

}