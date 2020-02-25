package com.github.afkbrb.hack.jack.compiler;

import java.util.HashMap;
import java.util.Map;

/**
 * 符号表
 */
public class SymbolTable {

    private Map<String, VariableNode> classMap = new HashMap<>();
    private Map<String, VariableNode> subroutineMap = new HashMap<>();

    private int staticCount = 0;
    private int fieldCount = 0;
    private int argCount = 0;
    private int varCount = 0;

    /**
     * Starts a new subroutine scope.
     * i.e., reset the subroutine's symbol table.
     */
    public void startSubroutine() {
        subroutineMap.clear();
        argCount = 0;
        varCount = 0;
    }

    public void define(String name, String type, Kind kind) {
        switch (kind) {
            case STATIC:
                classMap.put(name, new VariableNode(name, type, kind, staticCount++));
                break;
            case FIELD:
                classMap.put(name, new VariableNode(name, type, kind, fieldCount++));
                break;
            case ARG:
                subroutineMap.put(name, new VariableNode(name, type, kind, argCount++));
                break;
            case VAR:
                subroutineMap.put(name, new VariableNode(name, type, kind, varCount++));
                break;
            default:
                throw new IllegalArgumentException("bug");
        }
    }

    //  已经定义的当前类型的变量个数
    public int varCount(Kind kind) {
        switch (kind) {
            case VAR:
                return varCount;
            case ARG:
                return argCount;
            case FIELD:
                return fieldCount;
            case STATIC:
                return staticCount;
            default:
                throw new IllegalArgumentException("bug");
        }
    }

    public Kind kindOf(String name) {
        if (subroutineMap.containsKey(name)) {
            return subroutineMap.get(name).kind;
        } else if (classMap.containsKey(name)) {
            return classMap.get(name).kind;
        }
        return Kind.NONE;
    }

    public String typeOf(String name) {
        if (subroutineMap.containsKey(name)) {
            return subroutineMap.get(name).type;
        } else if (classMap.containsKey(name)) {
            return classMap.get(name).type;
        }
        return null;
    }

    public int indexOf(String name) {
        if (subroutineMap.containsKey(name)) {
            return subroutineMap.get(name).index;
        } else if (classMap.containsKey(name)) {
            return classMap.get(name).index;
        }
        return -1;
    }

    enum Kind {
        STATIC,
        FIELD,
        ARG,
        VAR,
        NONE; // for compiler
    }

    private static class VariableNode {
        String name;
        String type;
        Kind kind;
        int index;

        VariableNode(String name, String type, Kind kind, int index) {
            this.name = name;
            this.type = type;
            this.kind = kind;
            this.index = index;
        }
    }
}
