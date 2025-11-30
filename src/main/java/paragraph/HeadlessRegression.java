package src.main.java.paragraph;

import src.main.java.assembler.Assembler;
import src.main.java.ui.SimulatorUI;
import src.main.java.core.MachineController;
import src.main.java.core.MachineState;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Headless regression runner for closest_n.asm.
 *
 * It assembles closest_n.asm, loads the generated LoadFile.txt into the simulator core,
 * feeds console input, single-steps to HLT, and validates the characters printed via OUT 3,1.
 */
public class HeadlessRegression {

    private static String assemble(String asmPath) throws Exception {
        Assembler assembler = new Assembler();
        ArrayList<String> originalLines = new ArrayList<>();
        ArrayList<String> cleaned = assembler.firstPassWithComments(asmPath, originalLines);
        assembler.secondPass(cleaned);
        assembler.generateListingFile(originalLines, assembler.LISTING_FILE, assembler.machineCodeOctal);
        return new File(assembler.LOAD_FILE).getAbsolutePath();
    }

    private static String extractPrinted(SimulatorUI ui) {
        String log = ui.getPrinterArea().getText();
        StringBuilder sb = new StringBuilder();
        Pattern p = Pattern.compile("-> OUT Printer: '([^']*)'");
        Matcher m = p.matcher(log);
        while (m.find()) {
            sb.append(m.group(1));
        }
        return sb.toString();
    }

    private static boolean runCase(String loadFilePath, String input, String expected) throws Exception {
        SimulatorUI ui = new SimulatorUI(true);
        MachineController ctrl = ui.getController();
        MachineState state = ctrl.getMachineState();

        ctrl.performIPL(loadFilePath);
        ctrl.depositInput(input);

        int steps = 0;
        int maxSteps = 200000;
        while (steps < maxSteps && state.getMFR() == 0 && ui.getPrinterArea().getText().indexOf("HLT instruction executed") < 0) {
            ctrl.singleStep();
            steps++;
        }

        String printed = extractPrinted(ui);
        boolean pass = expected.equals(printed);

        System.out.println("INPUT:    " + input.trim());
        System.out.println("PRINTED:  " + printed);
        System.out.println("EXPECTED: " + expected);
        System.out.println("PC=" + String.format("%04o", state.getPC()) + ", MFR=" + String.format("%04o", state.getMFR()) + ", steps=" + steps);
        System.out.println(pass ? "RESULT: PASS" : "RESULT: FAIL");
        if (!pass) {
            System.out.println("--- RAW PRINTER LOG START ---");
            System.out.println(ui.getPrinterArea().getText());
            System.out.println("--- RAW PRINTER LOG END ---");
        }
        System.out.println("--------------------------------------------------");
        return pass;
    }

    public static void main(String[] args) throws Exception {
        String paratest = new java.io.File("files/paragraph.txt").getCanonicalPath();
        String loadFile = assemble(paratest);

    // Fixed 20-input mode: provide 20 candidates followed by a single target
    String base1 = "-50 -25 -10 -5 -1 0 1 2 3 4 5 10 25 50 100 200 300 400 500 1000 ";
    boolean c1 = runCase(loadFile, base1 + "450\n", "500");           // tie 400 vs 500 -> prefer later 500

    String base2 = "1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 ";
    boolean c2 = runCase(loadFile, base2 + "9\n", "9");                // exact 9

    String base3 = "-1 -2 -3 -4 -5 -6 -7 -8 -9 -10 -11 -12 -13 -14 -15 -16 -17 -18 -19 -20 ";
    boolean c3 = runCase(loadFile, base3 + "-4\n", "-4");               // exact -4

    String base4 = "0 10 20 30 40 50 60 70 80 90 100 110 120 130 140 150 160 170 180 190 ";
    boolean c4 = runCase(loadFile, base4 + "95\n", "100");              // tie 90 vs 100 -> prefer later 100

    // Higher magnitude positives and negatives (within 16-bit signed safe range)
    String hiPos = "1000 2000 5000 10000 15000 20000 22000 25000 28000 30000 30500 31000 31500 31800 31900 32000 32100 32200 32500 32700 ";
    boolean c5 = runCase(loadFile, hiPos + "31950\n", "32000"); // tie 31900 vs 32000 -> prefer later 32000

    String hiNeg = "-1000 -2000 -5000 -10000 -15000 -20000 -22000 -25000 -28000 -30000 -30500 -31000 -31500 -31800 -31900 -32000 -32100 -32200 -32500 -32700 ";
    boolean c6 = runCase(loadFile, hiNeg + "-31950\n", "-32000"); // tie -31900 vs -32000 -> prefer later -32000

    String mixed = "-32000 -25000 -20000 -15000 -10000 -5000 -100 0 100 500 1000 5000 10000 15000 20000 25000 28000 30000 32000 32700 ";
    boolean c7 = runCase(loadFile, mixed + "50\n", "100"); // tie 0 vs 100 -> prefer later 100

    String edgePos = "100 500 1000 2000 5000 8000 12000 16000 20000 24000 28000 30000 30500 31000 31500 32000 32300 32500 32700 32760 ";
    boolean c8 = runCase(loadFile, edgePos + "32750\n", "32760"); // nearest to 32750 is 32760

    if (!(c1 && c2 && c3 && c4 && c5 && c6 && c7 && c8)) System.exit(1);
        System.out.println("All regression cases PASSED.");
    }
}
