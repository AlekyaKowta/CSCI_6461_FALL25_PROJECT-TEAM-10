package edu.gwu.c6461.asm;

import java.util.*;

class Assembler {

    static final boolean DEBUG_PASS1 = true;

    static class Pass1Result {
        final SymbolTable symtab;
        final List<TokenizedLine> toks;
        Pass1Result(SymbolTable s, List<TokenizedLine> t) { this.symtab = s; this.toks = t; }
    }

    AssemblyResult assemble(List<String> lines) {
        // === Pass 1: labels + LOC/DATA sizing ===
        Pass1Result p1 = firstPass(lines);
        SymbolTable symtab = p1.symtab;
        List<TokenizedLine> toks = p1.toks;

        // === Pass 2: emit listing/load ===
        List<String> listing = new ArrayList<>();
        List<String> load = new ArrayList<>();
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

    // Pass 1 implementation
    Pass1Result firstPass(List<String> lines) {
        SymbolTable symtab = new SymbolTable();
        List<TokenizedLine> toks = new ArrayList<>();
        int loc = 0;

        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            TokenizedLine t = TokenizedLine.parse(i+1, raw);
            if (t == null) continue; // blank/comment-only
            toks.add(t);

            if (t.label != null) {
                if (symtab.contains(t.label)) {
                    throw error(i+1, "Duplicate label: " + t.label);
                }
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
}
