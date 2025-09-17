package edu.gwu.c6461.asm;

import java.util.*;

class Encoder {
    // Full opcode map (without floating point/vector)
    static final Map<String,Integer> OPC = new HashMap<>();
    static {
        // Misc
        OPC.put("HLT",   0b000000);
        OPC.put("TRAP",  0b110000);

        // Load/Store
        OPC.put("LDR",   0b000001);
        OPC.put("STR",   0b000010);
        OPC.put("LDA",   0b000011);
        OPC.put("LDX",   0b100001);
        OPC.put("STX",   0b100010);

        // Arithmetic/Logical
        OPC.put("ADD",   0b010000);
        OPC.put("SUB",   0b010001);
        OPC.put("MLT",   0b010010);
        OPC.put("DVD",   0b010011);
        OPC.put("TRR",   0b010100);
        OPC.put("AND",   0b010101);
        OPC.put("ORR",   0b010110);
        OPC.put("NOT",   0b010111);

        // Immediate
        OPC.put("AIR",   0b000110);
        OPC.put("SIR",   0b000111);

        // Transfer instructions
        OPC.put("JZ",    0b001000);
        OPC.put("JNE",   0b001001);
        OPC.put("JCC",   0b001010);
        OPC.put("JMA",   0b001011);
        OPC.put("JSR",   0b001100);
        OPC.put("RFS",   0b001101);
        OPC.put("SOB",   0b001110);
        OPC.put("JGE",   0b001111);
    }

    static int encode(SymbolTable symbolTable, TokenizedLine t) {
        String op = t.opcode.toUpperCase();
        if (!OPC.containsKey(op)) throw Assembler.error(t.lineNo, "Unknown opcode: " + op);
        int opc = OPC.get(op) & 0x3F;

        switch (op) {
            // Misc
            case "HLT": return opc << 10;
            case "TRAP": return emitTrap(opc, t);

            // Immediate
            case "AIR": case "SIR": return emitImmediate(opc, t);

            // RFS uses immediate
            case "RFS": return emitRfs(opc, t);

            // Load/Store
            case "LDX": case "STX": return emitIndexMemFormat(opc, t, symbolTable);
            case "LDR": case "STR": case "LDA":
                return emitMemFormat(opc, t, symbolTable);

            // Transfer
            case "JZ": case "JNE": case "JCC":
            case "JMA": case "JSR": case "SOB": case "JGE":
                return emitMemFormat(opc, t, symbolTable);

            // Arithmetic/Logical
            case "ADD": case "SUB": case "MLT": case "DVD":
            case "TRR": case "AND": case "ORR": case "NOT":
                return emitRegisterFormat(opc, t);

            default:
                throw Assembler.error(t.lineNo, "Opcode not supported: " + op);
        }
    }

    // --- Arithmetic/Logical format [opc][R1][R2][R3] ---
    private static int emitRegisterFormat(int opc, TokenizedLine t) {
        if (t.operands.size() != 3 && !"NOT".equals(t.opcode.toUpperCase()))
            throw Assembler.error(t.lineNo, t.opcode + " expects: r1,r2,r3 (NOT uses r1,r2)");
        int r1 = parseRange(t.operands.get(0), t.lineNo, 0, 3);
        int r2 = parseRange(t.operands.get(1), t.lineNo, 0, 3);
        int r3 = 0;
        if (!"NOT".equals(t.opcode.toUpperCase()))
            r3 = parseRange(t.operands.get(2), t.lineNo, 0, 3);
        return (opc << 10) | ((r1 & 0x3) << 8) | ((r2 & 0x3) << 6) | ((r3 & 0x3) << 4);
    }

    private static int emitTrap(int opc, TokenizedLine t) {
        if (t.operands.size() != 1) throw Assembler.error(t.lineNo, "Expected: TRAP code");
        int code = parseRange(t.operands.get(0), t.lineNo, 0, 31);
        return (opc << 10) | (code & 0x1F);
    }

    private static int emitImmediate(int opc, TokenizedLine t) {
        if (t.operands.size() < 2) throw Assembler.error(t.lineNo, "Expected: r,imm8");
        int r = parseRange(t.operands.get(0), t.lineNo, 0, 3);
        int imm = parseRange(t.operands.get(1), t.lineNo, 0, 255);
        return (opc << 10) | ((r & 0x3) << 8) | (imm & 0xFF);
    }

    private static int emitMemFormat(int opc, TokenizedLine t, SymbolTable symbolTable) {
        boolean indirect = false;
        List<String> ops = new ArrayList<>(t.operands);
        String last = ops.get(ops.size() - 1);
        if ("1".equals(last) || "I".equalsIgnoreCase(last)) { indirect = true; ops.remove(ops.size()-1); }

        int r = 0, ix = 0, addr;
        String op = t.opcode.toUpperCase();
        if ("JCC".equals(op)) {
            if (ops.size() != 3) throw Assembler.error(t.lineNo, "JCC expects: cc,x,address[,I]");
            r  = parseRange(ops.get(0), t.lineNo, 0, 3);
            ix = parseRange(ops.get(1), t.lineNo, 0, 3);
            addr = resolveAddr(symbolTable, ops.get(2), t.lineNo);
        } else {
            if (ops.size() != 3) throw Assembler.error(t.lineNo, op + " expects: r,x,address[,I]");
            r  = parseRange(ops.get(0), t.lineNo, 0, 3);
            ix = parseRange(ops.get(1), t.lineNo, 0, 3);
            addr = resolveAddr(symbolTable, ops.get(2), t.lineNo);
        }

        return (opc << 10) | ((r & 0x3) << 8) | ((ix & 0x3) << 6) | ((indirect ? 1 : 0) << 5) | (addr & 0x1F);
    }

    private static int emitIndexMemFormat(int opc, TokenizedLine t, SymbolTable symbolTable) {
        boolean indirect = false;
        List<String> ops = new ArrayList<>(t.operands);
        String last = ops.get(ops.size() - 1);
        if ("1".equals(last) || "I".equalsIgnoreCase(last)) { indirect = true; ops.remove(ops.size()-1); }

        if (ops.size() != 2) throw Assembler.error(t.lineNo, t.opcode + " expects: x,address[,I]");
        int ix = parseRange(ops.get(0), t.lineNo, 1, 3);
        int addr = resolveAddr(symbolTable, ops.get(1), t.lineNo);
        return (opc << 10) | ((0) << 8) | ((ix & 0x3) << 6) | ((indirect ? 1 : 0) << 5) | (addr & 0x1F);
    }

    private static int emitRfs(int opc, TokenizedLine t) {
        if (t.operands.size() != 1) throw Assembler.error(t.lineNo, "RFS expects: immed(0..31)");
        int immed = parseRange(t.operands.get(0), t.lineNo, 0, 31);
        return (opc << 10) | (immed & 0x1F);
    }

    private static int resolveAddr(SymbolTable symbolTable, String tok, int lineNo) {
        if (symbolTable.containsKey(tok)) return symbolTable.get(tok);
        try { return Integer.parseInt(tok); }
        catch (Exception e) { throw Assembler.error(lineNo, "Unknown address/label: " + tok); }
    }

    static int parseRange(String s, int lineNo, int min, int max) {
        try {
            int value = Integer.parseInt(s);
            if (value < min || value > max) throw new IllegalArgumentException();
            return value;
        } catch (Exception e) {
            throw Assembler.error(lineNo, "Expected integer in [" + min + "," + max + "]: " + s);
        }
    }
}