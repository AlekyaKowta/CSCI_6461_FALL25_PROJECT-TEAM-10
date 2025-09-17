package edu.gwu.c6461.asm;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

class FileIO {
    static void writeDataToFile(Path path, List<String> lines) throws IOException {
        Files.write(path, lines);
    }
    static String formListingFileData(int addr, int word, String reconstructed, String comment) {
        String row = String.format("%06o %06o %s", addr & 0xFFFF, word & 0xFFFF, reconstructed);
        if (comment != null && !comment.isEmpty()) row += " " + comment;
        return row;
    }
    static String formLoadFileData(int addr, int word) {
        return String.format("%06o %06o", addr & 0xFFFF, word & 0xFFFF);
    }
}
