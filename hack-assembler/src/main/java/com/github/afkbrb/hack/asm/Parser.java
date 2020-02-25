package com.github.afkbrb.hack.asm;

import java.io.*;

import static com.github.afkbrb.hack.asm.Parser.CommandType.*;

/**
 * 从 asm 中解析指令
 */
public class Parser implements AutoCloseable {

    private final BufferedReader reader;

    private CommandType commandType;

    private String symbol;

    private String dest;
    private String comp;
    private String jump;

    private int lineno = 0;

    private final boolean labelOnly;

    private boolean hasMoreCommands;

    public Parser(File source) throws IOException {
        this(source, false);
    }

    /**
     * Open the input file/stream and get ready to parse it.
     */
    public Parser(File source, boolean labelOnly) throws IOException {
        assert source != null;
        assert !source.isDirectory();
        assert source.canRead();
        this.labelOnly = labelOnly;
        reader = new BufferedReader(new FileReader(source));
    }

    public void advance() throws IOException, AssemblyException {
        hasMoreCommands = false;
        String line;
        while ((line = reader.readLine()) != null) { // null means EOF reached
            lineno++;
            if (line.contains("//")) { // 可能是一个注释一行，也有可能是注释跟在指令后面
                line = line.substring(0, line.indexOf("//"));
            }
            line = line.trim();
            if (line.length() == 0) {
                continue;
            }
            hasMoreCommands = true;
            break;
        }
        if (!hasMoreCommands) return;

        assert line != null;
        switch (line.charAt(0)) {
            case '@':  // A_COMMAND
                commandType = A_COMMAND;
                if (labelOnly) { // only care about label?
                    break;
                }
                symbol = line.substring(1).trim();
                // System.out.printf("line: %d, address: %s\n", lineno, symbol); // TODO: delete this line
                break;
            case '(':  // L_COMMAND
                commandType = L_COMMAND;
                if (!labelOnly) {
                    break;
                }
                line = line.substring(1);
                int index = line.indexOf(')');
                if (index == -1) {
                    throw new AssemblyException("expect ')' at line: " + lineno);
                }
                line = line.substring(0, index).trim();
                if (line.length() == 0) {
                    throw new AssemblyException("expect a symbol between '(' and ') at line: " + lineno);
                }
                symbol = line;
                // System.out.printf("line: %d, label: %s\n", lineno, symbol); // TODO: delete this line
                break;
            default:  // C_COMMAND?
                commandType = C_COMMAND;
                if (labelOnly) { // only care about label?
                    break;
                }
                if (line.indexOf('=') != -1) {
                    String[] split = line.split("=");
                    dest = split[0].trim();
                    line = split[1];
                } else {
                    dest = "null";
                }

                if (line.indexOf(';') != -1) {
                    String[] split = line.split(";");
                    jump = split[1].trim();
                    line = split[0];
                } else {
                    jump = "null";
                }

                comp = compact(line);

                // System.out.printf("line: %d, dest = %s, comp = %s, jump = %s\n", lineno, dest, comp, jump);
                hasMoreCommands = true;
                break;
        }
    }

    /**
     * Are there more lines in the file?
     */
    public boolean hasMoreCommands() throws IOException, AssemblyException {
        return hasMoreCommands;
    }

    /**
     * Return the type of the current command.
     */
    public CommandType commandType() {
        return commandType;
    }

    /**
     * Return the symbol or the decimal xxx of the current command @xxx or (xxx).
     * Should be called only when commandType() is A_COMMAND or L_COMMAND.
     */
    public String symbol() {
        return symbol;
    }

    /**
     * Return the dest mnemonic in the current C_COMMAND(8 possibilities).
     * Should be called only when commandType() is C_COMMAND
     */
    public String dest() {
        return dest;
    }

    /**
     * Return the comp mnemonic in the current C_COMMAND(28 possibilities).
     * Should be called only when commandType() is C_COMMAND.
     */
    public String comp() {
        return comp;
    }

    /**
     * Return the jump mnemonic in the current C_COMMAND(8 possibilities).
     * Should be called only when commandType() is C_COMMAND.
     */
    public String jump() {
        return jump;
    }

    /**
     * Delete the whitespace in the string.
     */
    private static String compact(String str) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (ch == ' ' || ch == '\t') {
                continue;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public enum CommandType {
        A_COMMAND, // @xxx where xxx is either a symbol or a decimal number
        C_COMMAND, // dest=comp;jump
        L_COMMAND // (xxx) where xxx is a symbol
    }

}
