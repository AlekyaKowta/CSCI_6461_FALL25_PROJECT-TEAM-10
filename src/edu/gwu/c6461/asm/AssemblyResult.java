package edu.gwu.c6461.asm;

import java.util.*;

class AssemblyResult {
    final List<String> listingLines;
    final List<String> loadLines;
    AssemblyResult(List<String> listingLines, List<String> loadLines) {
        this.listingLines = listingLines;
        this.loadLines = loadLines;
    }
}
