package src.main.java.paragraph;

import src.main.java.assembler.Assembler;
import src.main.java.ui.SimulatorUI;
import src.main.java.core.MachineController;
import src.main.java.core.MachineState;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParagraphSearchTest {
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
        String asmPath = new File("files/word_search.asm").getCanonicalPath();
        String loadFile = assemble(asmPath);

        String paragraph = "Rain falls gently against the window. A gentle rain often brings peace, yet sometimes it hides a storm. The children watch the rain as it gathers into puddles that reflect the sky.\n";
        String expected = paragraph + "\n" + "Enter word: " + "Word: " + "window" + " Sentence: " + "1" + " Word: " + "6" + "\n";
        boolean ok = runCase(loadFile, "gamma\n", expected);
        if (!ok) System.exit(1);
        System.out.println("Paragraph search test PASSED.");
    }
}
