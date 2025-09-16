package edu.gwu.c6461.asm;

import java.nio.file.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !"assemble".equals(args[0])) {
            System.err.println("Usage: java edu.gwu.c6461.asm.Main assemble <source.asm> [--out-list out.list] [--out-load out.load]");
            System.exit(1);
        }
        String sourcePath = args[1];
        String outList = "out.list", outLoad = "out.load";
        for (int i = 2; i < args.length; i++) {
            if ("--out-list".equals(args[i]) && i+1 < args.length) outList = args[++i];
            else if ("--out-load".equals(args[i]) && i+1 < args.length) outLoad = args[++i];
        }

        List<String> lines = Files.readAllLines(Paths.get(sourcePath));
        Assembler asm = new Assembler();
        AssemblyResult result = asm.assemble(lines);

        FileIO.writeDataToFile(Paths.get(outList), result.listingLines);
        FileIO.writeDataToFile(Paths.get(outLoad), result.loadLines);

        System.out.println("Assembled OK");
        System.out.println("Listing: " + Paths.get(outList).toAbsolutePath());
        System.out.println("Load:    " + Paths.get(outLoad).toAbsolutePath());
    }
}
