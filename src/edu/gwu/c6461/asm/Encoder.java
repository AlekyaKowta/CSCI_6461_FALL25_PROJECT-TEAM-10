package edu.gwu.c6461.asm;

import java.util.*;

class Encoder {
    // Minimal opcode map to start; teammates will fill the rest
    static final Map<String,Integer> OPC = new HashMap<>();
    static {
        OPC.put("HLT",   0b000000);
        OPC.put("LDR",   0b000001);
        OPC.put("STR",   0b000010);
        OPC.put("LDA",   0b000011);
        OPC.put("AIR",   0b000110);
        OPC.put("SIR",   0b000111);
        OPC.put("JZ",    0b001000);
        OPC.put("JNE",   0b001001);
        OPC.put("JCC",   0b001010);
        OPC.put("JMA",   0b001011);
        OPC.put("JSR",   0b001100);
        OPC.put("RFS",   0b001101);
        OPC.put("SOB",   0b001110);
        OPC.put("JGE",   0b001111);
        OPC.put("LDX",   0b100001);
        OPC.put("STX",   0b100010);
        OPC.put("TRAP",  0b110000); // placeholder for misc
    }

    static int encode(SymbolTable symtab, TokenizedLine t) {
        String op = t.opcode.toUpperCase();
        if (!OPC.containsKey(op)) throw Assembler.error(t.lineNo, "Unknown opcode: " + op);
        int opc = OPC.get(op) & 0x3F;

        switch (op) {
            case "HLT": return opc << 10;
            case "TRAP": return emitTrap(opc, t);
            case "AIR":
            case "SIR":  return emitImmediate(opc, t);
            case "RFS":  return emitRfs(opc, t);
            case "LDX":
            case "STX":  return emitIndexMemFormat(opc, t, symtab);
            case "LDR": case "STR": case "LDA":
            case "JZ": case "JNE": case "JCC":
            case "JMA": case "JSR": case "SOB": case "JGE":
                return emitMemFormat(opc, t, symtab);
            default:
                throw Assembler.error(t.lineNo, "Opcode not supported: " + op);
        }
    }

    // TRAP: [opc][r=0][ix=0][i=0][trapcode(5)]
    private static int emitTrap(int opc, TokenizedLine t) {
        if (t.operands.size() != 1) throw Assembler.error(t.lineNo, "Expected: TRAP code");
        int code = parseRange(t.operands.get(0), t.lineNo, 0, 31);
        return (opc << 10) | (code & 0x1F);
    }

    // Immediate: [opc][R2][imm8]
    private static int emitImmediate(int opc, TokenizedLine t) {
        if (t.operands.size() < 2) throw Assembler.error(t.lineNo, "Expected: r,imm8");
        int r = parseRange(t.operands.get(0), t.lineNo, 0, 3);
        int imm = parseRange(t.operands.get(1), t.lineNo, 0, 255);
        return (opc << 10) | ((r & 0x3) << 8) | (imm & 0xFF);
    }

    // Memory-format (R,x,addr[,I]) except JCC (cc,x,addr[,I])
    private static int emitMemFormat(int opc, TokenizedLine t, SymbolTable symtab) {
        boolean indirect = false;
        List<String> ops = new ArrayList<>(t.operands);
        String last = ops.get(ops.size()-1);
        if ("1".equals(last) || "I".equalsIgnoreCase(last)) { indirect = true; ops.remove(ops.size()-1); }

        int r = 0, ix = 0, addr;
        String op = t.opcode.toUpperCase();
        if ("JCC".equals(op)) {
            if (ops.size() != 3) throw Assembler.error(t.lineNo, "JCC expects: cc,x,address[,I]");
            r  = parseRange(ops.get(0), t.lineNo, 0, 3); // use R field to carry CC code
            ix = parseRange(ops.get(1), t.lineNo, 0, 3);
            addr = resolveAddr(symtab, ops.get(2), t.lineNo);
        } else if ("LDX".equals(op) || "STX".equals(op)) {
            throw Assembler.error(t.lineNo, "Internal: LDX/STX handled elsewhere");
        } else {
            if (ops.size() != 3) throw Assembler.error(t.lineNo, op + " expects: r,x,address[,I]");
            r  = parseRange(ops.get(0), t.lineNo, 0, 3);
            ix = parseRange(ops.get(1), t.lineNo, 0, 3);
            addr = resolveAddr(symtab, ops.get(2), t.lineNo);
        }

        return (opc << 10) | ((r & 0x3) << 8) | ((ix & 0x3) << 6) | ((indirect?1:0) << 5) | (addr & 0x1F);
    }

    // Index-memory format (x,address[,I]) for LDX/STX
    private static int emitIndexMemFormat(int opc, TokenizedLine t, SymbolTable symtab) {
        boolean indirect = false;
        List<String> ops = new ArrayList<>(t.operands);
        String last = ops.get(ops.size()-1);
        if ("1".equals(last) || "I".equalsIgnoreCase(last)) { indirect = true; ops.remove(ops.size()-1); }

        if (ops.size() != 2) throw Assembler.error(t.lineNo, t.opcode + " expects: x,address[,I]");
        int ix = parseRange(ops.get(0), t.lineNo, 1, 3);
        int addr = resolveAddr(symtab, ops.get(1), t.lineNo);
        return (opc << 10) | ((0) << 8) | ((ix & 0x3) << 6) | ((indirect?1:0) << 5) | (addr & 0x1F);
    }

    // RFS uses immediate in address field
    private static int emitRfs(int opc, TokenizedLine t) {
        if (t.operands.size() != 1) throw Assembler.error(t.lineNo, "RFS expects: immed(0..31)");
        int immed = parseRange(t.operands.get(0), t.lineNo, 0, 31);
        return (opc << 10) | (immed & 0x1F);
    }

    private static int resolveAddr(SymbolTable symtab, String tok, int lineNo) {
        if (symtab.containsKey(tok)) return symtab.get(tok);
        try { return Integer.parseInt(tok); }
        catch (Exception e) { throw Assembler.error(lineNo, "Unknown address/label: " + tok); }
    }

    static int parseRange(String s, int lineNo, int min, int max) {
        try {
            int v = Integer.parseInt(s);
            if (v < min || v > max) throw new IllegalArgumentException();
            return v;
        } catch (Exception e) {
            throw Assembler.error(lineNo, "Expected integer in [" + min + "," + max + "]: " + s);
        }
    }
}
