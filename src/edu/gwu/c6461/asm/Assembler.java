package edu.gwu.c6461.asm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Assembler: two-pass structure for Part 0, with centralized opcode maps and basic file I/O helpers.
 * - Pass 1 (programmatic): labels + LOC/DATA sizing (used by assemble(List<String>)).
 * - Pass 2 (programmatic): emit listing/load; calls Encoder.encode(...) for instructions.
 * - firstPass(String): convenience reader that strips ';' comments and returns cleaned lines.
 * - writeDataToFile(...): simple shared writer.
 */
class Assembler {

    // ===== Central opcode maps (extend/verify as the team finalizes the table) =====
    static final Map<String,Integer> OPC_ARITH_LOGIC = new HashMap<>();
    static final Map<String,Integer> OPC_SHIFT_ROT   = new HashMap<>();
    static final Map<String,Integer> OPC_IO          = new HashMap<>();
    static final Map<String,Integer> OPC_LS_OTHER    = new HashMap<>();
    static final Map<String,Integer> OPC_MISC        = new HashMap<>();
    static {
        // NOTE: Values are kept here for easy coordination; verify against your ISA tables.
        // Misc / simple
        OPC_MISC.put("HLT", 000);
        OPC_MISC.put("TRAP",045);

        // Load/Store + transfers + memory/immediate ops
        OPC_LS_OTHER.put("LDR", 001);
        OPC_LS_OTHER.put("STR", 002);
        OPC_LS_OTHER.put("LDA", 003);
        OPC_LS_OTHER.put("LDX", 004);
        OPC_LS_OTHER.put("STX", 005);
        OPC_LS_OTHER.put("SETCCE",036); // keep as placeholder if your team wants it
        OPC_LS_OTHER.put("JZ",  006);
        OPC_LS_OTHER.put("JNE", 007);
        OPC_LS_OTHER.put("JCC", 010);
        OPC_LS_OTHER.put("JMA", 011);
        OPC_LS_OTHER.put("JSR", 012);
        OPC_LS_OTHER.put("RFS", 013);
        OPC_LS_OTHER.put("SOB", 014);
        OPC_LS_OTHER.put("JGE", 015);
        OPC_LS_OTHER.put("AMR", 016);
        OPC_LS_OTHER.put("SMR", 017);
        OPC_LS_OTHER.put("AIR", 020);
        OPC_LS_OTHER.put("SIR", 021);

        // Register-to-register
        OPC_ARITH_LOGIC.put("MLT", 022);
        OPC_ARITH_LOGIC.put("DVD", 023);
        OPC_ARITH_LOGIC.put("TRR", 024);
        OPC_ARITH_LOGIC.put("AND", 025);
        OPC_ARITH_LOGIC.put("ORR", 026);
        OPC_ARITH_LOGIC.put("NOT", 027);

        // Shifts/rotates
        OPC_SHIFT_ROT.put("SRC", 030);
        OPC_SHIFT_ROT.put("RRC", 031);

        // I/O
        OPC_IO.put("IN",  032);
        OPC_IO.put("OUT", 033);
        OPC_IO.put("CHK", 034);
    }

    // ===== Person-1: Pass-1 debug toggle =====
    static final boolean DEBUG_PASS1 = true;

    // ===== Pass 1 result carrier =====
    static class Pass1Result {
        final SymbolTable symtab;
        final List<TokenizedLine> toks;
        Pass1Result(SymbolTable s, List<TokenizedLine> t) { this.symtab = s; this.toks = t; }
    }

    // ===== Public two-pass assemble (used by Main.java) =====
    AssemblyResult assemble(List<String> lines) {
        Pass1Result p1 = firstPass(lines);            // build labels, size addresses
        SymbolTable symtab = p1.symtab;
        List<TokenizedLine> toks = p1.toks;

        List<String> listing = new ArrayList<>();
        List<String> load    = new ArrayList<>();
        int loc = 0;

        for (TokenizedLine t : toks) {
            if (t.isDirective("LOC")) {
                loc = parseDecimal(t.operands.get(0), t.lineNo);
                listing.add(String.format("    LOC %d%s", loc, t.trailingComment()));
                continue;
            }
            int addrBefore = loc;

            if (t.isDirective("DATA")) {
                int word = resolveData(symtab, t.operands.get(0), t.lineNo);
                listing.add(FileIO.formListingFileData(addrBefore, word, "Data " + t.operands.get(0), t.trailingComment()));
                load.add(FileIO.formLoadFileData(addrBefore, word));
                loc += 1;
                continue;
            }

            if (t.opcode != null) {
                int word = Encoder.encode(symtab, t);
                listing.add(FileIO.formListingFileData(addrBefore, word, t.reconstruct(), t.trailingComment()));
                load.add(FileIO.formLoadFileData(addrBefore, word));
                loc += 1;
            }
        }
        return new AssemblyResult(listing, load);
    }

    // ===== Person-1: Programmatic Pass 1 used by assemble(List<String>) =====
    Pass1Result firstPass(List<String> lines) {
        SymbolTable symtab = new SymbolTable();
        List<TokenizedLine> toks = new ArrayList<>();
        int loc = 0;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            TokenizedLine t = TokenizedLine.parse(i+1, raw);
            if (t == null) continue; // blank or comment-only
            toks.add(t);

            if (t.label != null) {
                if (symtab.contains(t.label)) throw error(i+1, "Duplicate label: " + t.label);
                symtab.put(t.label, loc);
                if (DEBUG_PASS1) System.out.printf("PASS1 line %d: label %-12s -> %d%n", t.lineNo, t.label, loc);
            }

            if (t.isDirective("LOC")) {
                loc = parseDecimal(t.operands.get(0), t.lineNo);
                if (DEBUG_PASS1) System.out.printf("PASS1 line %d: LOC -> %d%n", t.lineNo, loc);
            } else if (t.isDirective("DATA")) {
                if (DEBUG_PASS1) System.out.printf("PASS1 line %d: DATA at %d%n", t.lineNo, loc);
                loc += 1;
            } else if (t.opcode != null) {
                if (DEBUG_PASS1) System.out.printf("PASS1 line %d: OPC %-6s at %d%n", t.lineNo, t.opcode, loc);
                loc += 1;
            }
        }
        return new Pass1Result(symtab, toks);
    }

    // ===== Convenience reader: firstPass(String) that returns cleaned lines =====
    public ArrayList<String> firstPass(String inputFile) throws IOException {
        ArrayList<String> inputLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int commentIndex = line.indexOf(';');
                if (commentIndex != -1) {
                    line = line.substring(0, commentIndex).trim();
                    if (!line.isEmpty()) inputLines.add(line);
                } else {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) inputLines.add(trimmed);
                }
            }
        }
        // (Symbol parsing and address updates are handled by assemble(List<String>) via TokenizedLine + Pass1.)
        return inputLines;
    }

    // ===== Data helpers =====
    static int resolveData(SymbolTable symtab, String token, int lineNo) {
        if (symtab.contains(token)) return symtab.get(token);
        return parseDecimal(token, lineNo);
    }
    static int parseDecimal(String s, int lineNo) {
        try { return Integer.parseInt(s); }
        catch (Exception e) { throw error(lineNo, "Expected decimal literal: " + s); }
    }
    static RuntimeException error(int line, String msg) {
        return new RuntimeException("Line " + line + ": " + msg);
    }

    // ===== Simple reusable writer (UTF-8 platform default) =====
    public static void writeDataToFile(String filePath, ArrayList<String> data) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (String row : data) {
                writer.write(row);
                writer.newLine();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
