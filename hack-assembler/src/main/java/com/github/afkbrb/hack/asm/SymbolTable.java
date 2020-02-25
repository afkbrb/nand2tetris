package com.github.afkbrb.hack.asm;

import java.util.HashMap;
import java.util.Map;

public class SymbolTable {

    private final Map<String, Integer> map = new HashMap<>();

    /**
     * Add the pair(symbol, address) to the table.
     */
    public void addEntry(String symbol, int address) {
        map.put(symbol, address);
    }

    /**
     * Does the symbol table contain the given symbol?
     */
    public boolean contains(String symbol) {
        return map.containsKey(symbol);
    }

    /**
     * Return the address associated with the symbol.
     */
    public int getAddress(String symbol) {
        return map.get(symbol);
    }
}
