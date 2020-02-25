package com.github.afkbrb.hack.vm.translator;

import java.io.*;
import java.util.StringTokenizer;

import static com.github.afkbrb.hack.vm.translator.Parser.CommandType.*;

public class Parser implements AutoCloseable {

    private CommandType type;
    private String action;
    private String segment;
    private int index;
    private String label;
    private String functionName;
    private int nVars;
    private int nArgs;

    private BufferedReader reader;

    private boolean hasMoreCommands;

    // One file, one Parser.
    public Parser(File file) throws FileNotFoundException {
        reader = new BufferedReader(new FileReader(file));
    }

    public boolean hasMoreCommand() throws IOException {
        return hasMoreCommands;
    }

    // We've read a line of command, we now parser it.
    public void advance() throws IOException {
        hasMoreCommands = false;
        String line;
        while ((line = reader.readLine()) != null) {
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
        StringTokenizer tokenizer = new StringTokenizer(line);
        assert tokenizer.hasMoreTokens();
        action = tokenizer.nextToken();
        switch (action) {
            case "add":
            case "sub":
            case "neg":
            case "eq":
            case "gt":
            case "lt":
            case "and":
            case "or":
            case "not":
                type = ARITHMETIC;
                break;
            case "push":
            case "pop":
                type = MEMORY;
                assert tokenizer.hasMoreTokens();
                segment = tokenizer.nextToken();
                assert tokenizer.hasMoreTokens();
                index = Integer.parseInt(tokenizer.nextToken());
                break;
            case "label":
            case "goto":
            case "if-goto":
                type = BRANCH;
                assert tokenizer.hasMoreTokens();
                label = tokenizer.nextToken();
                break;
            case "function":
            case "call":
            case "return":
                type = FUNCTION;
                if (action.equals("return")) {
                    break;
                }
                assert tokenizer.hasMoreTokens();
                functionName = tokenizer.nextToken();
                assert tokenizer.hasMoreTokens();
                if (action.equals("function")) {
                    nVars = Integer.parseInt(tokenizer.nextToken());
                } else {
                    nArgs = Integer.parseInt(tokenizer.nextToken());
                }
                break;
            default:
                throw new IllegalStateException(String.format("bug, action: %s", action));
        }
    }

    public CommandType type() {
        return type;
    }

    public String action() {
        return action;
    }

    public String segment() {
        return segment;
    }

    public int index() {
        return index;
    }

    public String label() {
        return label;
    }

    public String functionName() {
        return functionName;
    }

    public int nVars() {
        return nVars;
    }

    public int nArgs() {
        return nArgs;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    enum CommandType {
        ARITHMETIC, // or logical
        MEMORY,
        BRANCH,
        FUNCTION
    }
}
