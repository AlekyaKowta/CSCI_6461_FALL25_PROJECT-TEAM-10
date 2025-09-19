package src.main.java.assembler;

import java.util.HashMap;

public class OpCodeTables {
    public static final HashMap<String, Integer> arithmeticAndLogic = new HashMap<>();
    public static final HashMap<String, Integer> shiftRotate = new HashMap<>();
    public static final HashMap<String, Integer> io = new HashMap<>();
    public static final HashMap<String, Integer> loadStoreOther = new HashMap<>();
    public static final HashMap<String, Integer> miscellaneous = new HashMap<>();

    static {
        miscellaneous.put("HLT", 000);
        miscellaneous.put("TRAP", 030);
        loadStoreOther.put("LDR", 001);
        loadStoreOther.put("STR", 002);
        loadStoreOther.put("LDA", 003);
        loadStoreOther.put("LDX", 041);
        loadStoreOther.put("STX", 042);
        loadStoreOther.put("JZ", 010);
        loadStoreOther.put("JNE", 011);
        loadStoreOther.put("JCC", 012);
        loadStoreOther.put("JMA", 013);
        loadStoreOther.put("JSR", 014);
        loadStoreOther.put("RFS", 015);
        loadStoreOther.put("SOB", 016);
        loadStoreOther.put("JGE", 017);
        loadStoreOther.put("AMR", 004);
        loadStoreOther.put("SMR", 005);
        loadStoreOther.put("AIR", 006);
        loadStoreOther.put("SIR", 007);
        arithmeticAndLogic.put("MLT", 070);
        arithmeticAndLogic.put("DVD", 071);
        arithmeticAndLogic.put("TRR", 072);
        arithmeticAndLogic.put("AND", 073);
        arithmeticAndLogic.put("ORR", 074);
        arithmeticAndLogic.put("NOT", 075);
        shiftRotate.put("SRC", 031);
        shiftRotate.put("RRC", 032);
        io.put("IN", 061);
        io.put("OUT", 062);
        io.put("CHK", 063);
    }
}
