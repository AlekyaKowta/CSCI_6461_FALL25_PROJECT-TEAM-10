
 import java.io.BufferedReader;
 import java.io.BufferedWriter;
 import java.io.FileReader;
 import java.io.FileWriter;
 import java.io.IOException;
 import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class Assembler{
    public static final HashMap<String, Integer> opcodeForArithmeticAndLogic = new HashMap<>();
    public static final HashMap<String, Integer> opcodeForShiftRotate = new HashMap<>();
    public static final HashMap<String, Integer> opcodeForIO = new HashMap<>();
    public static final HashMap<String, Integer> opcodeForLSAndOther = new HashMap<>();
    public static final HashMap<String, Integer> opcodeForMisallaneous = new HashMap<>();
    //TODO: Include Floating Point Vectors

    static {
        // Initializing the opcode hashmap with instruction mnemonics and their
        // corresponding binary opcode representations.
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
    public int currentAddress = 0; // This tracks the current address location.
    public String LISTING_FILE = "listingFile.txt"; // output file name for the listing file5.
    public String LOAD_FILE = "LoadFile.txt";

    public ArrayList<String> firstPass(String inputFile)throws Exception   { 
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        ArrayList<String> inputRows = new ArrayList<>(); // ArrayList to store the instructions for the secondPass
        // call.
        String row;

        while ((row = reader.readLine()) != null) { // Read each line of the input file and remove comment if it is
            // present in the inputfile.
            int commentIndex = row.indexOf(';');
            if (commentIndex != -1) {
                row = row.substring(0, commentIndex).trim();
                if (!row.isEmpty()) {
                    inputRows.add(row);
                }
            } else {
                inputRows.add(row.trim());
            }
        }
        reader.close(); // closing the file after reading it.

        //TODO: Parse Symbols and Update Current Address
       
    }

  // This method gets the data and writes its contents into the file path given
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
            Assembler assembler = new Assembler();// Create an instance of the assembler.

            //TODO: Implement first and second passes

        } catch (Exception e) { //to to catch IOException later

            //e.printStackTrace(); // for debugging the error occured during the execution .
        }
    }

}  

