package com.github.afkbrb.hack.asm;

import java.io.*;

import static com.github.afkbrb.hack.asm.Parser.CommandType.*;

public class HackAssembler {

    private final File asmFile;

    private final File hackFile;

    private final SymbolTable symbolTable = new SymbolTable();

    public HackAssembler(File asmFile) throws IOException {
        this.asmFile = asmFile;
        if (asmFile.isDirectory()) {
            throw new IOException("asmFile file " + asmFile.getName() + " is a directory");
        }
        if (!asmFile.canRead()) {
            throw new IOException("cannot read from " + asmFile.getName());
        }

        String canonicalPath = asmFile.getCanonicalPath();
        String parent = canonicalPath.substring(0, canonicalPath.lastIndexOf(File.separator));
        String hackFilename = parent + File.separator + asmFile.getName().split("\\.")[0] + ".hack";
        hackFile = new File(hackFilename);

        initSymbolTable();
    }

    /**
     * Add the pre-defined symbols to the symbol table.
     */
    private void initSymbolTable() {
        // 虚拟寄存器
        symbolTable.addEntry("R0", 0);
        symbolTable.addEntry("R1", 1);
        symbolTable.addEntry("R2", 2);
        symbolTable.addEntry("R3", 3);
        symbolTable.addEntry("R4", 4);
        symbolTable.addEntry("R5", 5);
        symbolTable.addEntry("R6", 6);
        symbolTable.addEntry("R7", 7);
        symbolTable.addEntry("R8", 8);
        symbolTable.addEntry("R9", 9);
        symbolTable.addEntry("R10", 10);
        symbolTable.addEntry("R11", 11);
        symbolTable.addEntry("R12", 12);
        symbolTable.addEntry("R13", 13);
        symbolTable.addEntry("R14", 14);
        symbolTable.addEntry("R15", 15);

        // I/O map
        symbolTable.addEntry("SCREEN", 16384);
        symbolTable.addEntry("KBD", 24576);

        // VM
        symbolTable.addEntry("SP", 0);
        symbolTable.addEntry("LCL", 1);
        symbolTable.addEntry("ARG", 2);
        symbolTable.addEntry("THIS", 3);
        symbolTable.addEntry("THAT", 4);
    }

    /**
     * First pass:
     * Scan the entire program, for each "instruction" of the form (xxx),
     * add the pair(xxx, address) to the symbol table, where address is
     * the number of the instruction following (xxx).
     */
    public void setupSymbolTable() throws IOException, AssemblyException {
        int nextAddress = 0;
        try (Parser parser = new Parser(asmFile, true)) { // only deal with label, so we set labelOnly to true for better performance
            parser.advance();
            while (parser.hasMoreCommands()) {
                if (parser.commandType() == L_COMMAND) { // label
                    if (symbolTable.contains(parser.symbol())) {
                        throw new AssemblyException("label " + parser.symbol() + " must be unique");
                    }
                    symbolTable.addEntry(parser.symbol(), nextAddress);
                } else {
                    nextAddress++;
                }
                parser.advance();
            }
        }
    }


    /**
     * Second pass:
     * Set n to 16
     * Scan the entire program again; for each instruction:
     * - If the instruction is @symbol, look up symbol in the symbol table;
     *      • If (symbol, value) is found, use value to complete the instruction’s translation;
     *      • If not found:
     *          - Add (symbol, n) to the symbol table,
     *          - Use n to complete the instruction’s translation,
     *          - n++
     * - If the instruction is a C-instruction, complete the instruction’s translation
     * - Write the translated instruction to the output file.
     */
    public void genCode() throws IOException, AssemblyException {
        int nextVarAddress = 16;
        try (Parser parser = new Parser(asmFile, false); Writer writer = new BufferedWriter(new FileWriter(hackFile))) {
            parser.advance();
            while (parser.hasMoreCommands()) {
                if (parser.commandType() == A_COMMAND) {
                    String symbol = parser.symbol();
                    int address = -1;
                    try {
                        address = Integer.parseInt(symbol); // symbol may be a number
                    } catch (Exception e) {
                        ; // do nothing
                    }
                    if (address == -1) {
                        if (symbolTable.contains(symbol)) { // defined var or label
                            address = symbolTable.getAddress(symbol);
                        } else {
                            symbolTable.addEntry(symbol, nextVarAddress); // allocate memory address for var
                            address = nextVarAddress;
                            nextVarAddress++;
                        }
                    }

                    // we need to convert address to 15 bits
                    String addressStr = intTo15Bits(address);
                    String instruction = "0" + addressStr + "\n";
                    writer.write(instruction);
                } else if (parser.commandType() == C_COMMAND) {
                    String dest = Code.dest(parser.dest());
                    String comp = Code.comp(parser.comp());
                    String jump = Code.jump(parser.jump());
                    String instruction = "111" + comp + dest + jump + "\n";
                    writer.write(instruction);
                } // don't need to deal with L_COMMAND in this pass
                parser.advance();
            }
        }
    }

    private static String intTo15Bits(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            sb.append(n & 1);
            n >>>= 1;
        }
        return sb.reverse().toString();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar hack-assembler.jar foo.asm");
            System.exit(0);
        }
        try {
            HackAssembler assembler = new HackAssembler(new File(args[0]));
            assembler.setupSymbolTable();
            assembler.genCode();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("convert hack asm to hack machine code failed");
            System.exit(0);
        }
    }
}
