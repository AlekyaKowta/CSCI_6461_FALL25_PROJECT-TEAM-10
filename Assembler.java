import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

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
        opcodeForMisallaneous.put("TRAP", 045);
        opcodeForLSAndOther.put("LDR", 001);
        opcodeForLSAndOther.put("STR", 002);
        opcodeForLSAndOther.put("LDA", 003);
        opcodeForLSAndOther.put("LDX", 004);
        opcodeForLSAndOther.put("STX", 005);
        opcodeForLSAndOther.put("SETCCE", 036);
        opcodeForLSAndOther.put("JZ", 006);
        opcodeForLSAndOther.put("JNE", 007);
        opcodeForLSAndOther.put("JCC", 010);
        opcodeForLSAndOther.put("JMA", 011);
        opcodeForLSAndOther.put("JSR", 012);
        opcodeForLSAndOther.put("RFS", 013);
        opcodeForLSAndOther.put("SOB", 014);
        opcodeForLSAndOther.put("JGE", 015);
        opcodeForLSAndOther.put("AMR", 016);
        opcodeForLSAndOther.put("SMR", 017);
        opcodeForLSAndOther.put("AIR", 020);
        opcodeForLSAndOther.put("SIR", 021);
        opcodeForArithmeticAndLogic.put("MLT", 022);
        opcodeForArithmeticAndLogic.put("DVD", 023);
        opcodeForArithmeticAndLogic.put("TRR", 024);
        opcodeForArithmeticAndLogic.put("AND", 025);
        opcodeForArithmeticAndLogic.put("ORR", 026);
        opcodeForArithmeticAndLogic.put("NOT", 027);
        opcodeForShiftRotate.put("SRC", 030);
        opcodeForShiftRotate.put("RRC", 031);
        opcodeForIO.put("IN", 032);
        opcodeForIO.put("OUT", 033);
        opcodeForIO.put("CHK", 034);
        //TODO: Include Floating Point Vectors
    }

    public int currentAddress = 0; // Tracks current address
    public String LISTING_FILE = "listingFile.txt"; 
    public String LOAD_FILE = "LoadFile.txt";

    // 🔹 Symbol Table
    public HashMap<String, Integer> symbolsMap = new HashMap<>();

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

    // Utility: write to file
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

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
