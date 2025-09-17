package edu.gwu.c6461.asm;

import java.util.*;

class TokenizedLine {
    final int lineNo;
    final String raw;
    final String label;
    final String directive; // "LOC" or "DATA" or null
    final String opcode;    // mnemonic or null
    final List<String> operands;
    final String comment;   // includes leading ';' if present

    private TokenizedLine(int lineNo, String raw, String label, String directive, String opcode, List<String> operands, String comment) {
        this.lineNo = lineNo;
        this.raw = raw;
        this.label = label;
        this.directive = directive;
        this.opcode = opcode;
        this.operands = operands;
        this.comment = comment;
    }

    static TokenizedLine parse(int lineNo, String raw) {
        String line = raw;
        String comment = "";
        int sc = line.indexOf(';');
        if (sc >= 0) { comment = line.substring(sc); line = line.substring(0, sc); }
        if (line.trim().isEmpty()) return null;

        String label = null;
        String rest = line;
        int colon = line.indexOf(':');
        if (colon >= 0) {
            label = line.substring(0, colon).trim();
            rest = line.substring(colon+1);
        }

        String[] parts = rest.trim().split("\\s+", 2);
        if (parts.length == 0 || parts[0].isEmpty()) {
            return new TokenizedLine(lineNo, raw, label, null, null, Collections.emptyList(), comment);
        }
        String head = parts[0].toUpperCase();
        String tail = (parts.length > 1) ? parts[1] : "";

        String directive = null, opcode = null;
        if ("LOC".equals(head) || "DATA".equals(head)) directive = head;
        else opcode = head;

        List<String> ops;
        if (tail.trim().isEmpty()) ops = new ArrayList<>();
        else ops = new ArrayList<>(Arrays.asList(tail.split("\\s*,\\s*")));

        return new TokenizedLine(lineNo, raw, label, directive, opcode, ops, comment);
    }

    boolean isDirective(String name) { return name.equalsIgnoreCase(directive); }

    String trailingComment() { return (comment == null || comment.isEmpty()) ? "" : " " + comment; }

    String reconstruct() {
        String head = opcode != null ? opcode : directive;
        String ops = String.join(",", operands);
        String lbl = (label != null) ? label + ": " : "";
        return lbl + head + (ops.isEmpty() ? "" : " " + ops);
    }
}
