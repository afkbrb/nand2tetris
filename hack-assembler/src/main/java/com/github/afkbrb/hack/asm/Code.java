package com.github.afkbrb.hack.asm;

import java.util.HashMap;
import java.util.Map;

final class Code {

    private static final Map<String, String> destMap = new HashMap<>();
    private static final Map<String, String> compMap = new HashMap<>();
    private static final Map<String, String> jumpMap = new HashMap<>();

    static {
        // destMap
        destMap.put("null", "000");
        destMap.put("A",    "100");
        destMap.put("M",    "001");
        destMap.put("D",    "010");
        destMap.put("AM",   "101");
        destMap.put("MA",   "101");
        destMap.put("AD",   "110");
        destMap.put("DA",   "110");
        destMap.put("MD",   "011");
        destMap.put("DM",   "011");
        destMap.put("AMD",  "111");
        destMap.put("ADM",  "111");
        destMap.put("MAD",  "111");
        destMap.put("MDA",  "111");
        destMap.put("DAM",  "111");
        destMap.put("DMA",  "111");

        // compMap
        compMap.put("0",   "0101010");
        compMap.put("1",   "0111111");
        compMap.put("-1",  "0111010");
        compMap.put("D",   "0001100");
        compMap.put("A",   "0110000");
        compMap.put("!D",  "0001101");
        compMap.put("!A",  "0110001");
        compMap.put("-D",  "0001111");
        compMap.put("-A",  "0110011");
        compMap.put("D+1", "0011111");
        compMap.put("1+D", "0011111");
        compMap.put("A+1", "0110111");
        compMap.put("1+A", "0110111");
        compMap.put("D-1", "0001110");
        compMap.put("A-1", "0110010");
        compMap.put("D+A", "0000010");
        compMap.put("A+D", "0000010");
        compMap.put("D-A", "0010011");
        compMap.put("A-D", "0000111");
        compMap.put("D&A", "0000000");
        compMap.put("A&D", "0000000");
        compMap.put("D|A", "0010101");
        compMap.put("A|D", "0010101");

        compMap.put("M",   "1110000");
        compMap.put("!M",  "1110001");
        compMap.put("-M",  "1110011");
        compMap.put("M+1", "1110111");
        compMap.put("1+M", "1110111");
        compMap.put("M-1", "1110010");
        compMap.put("D+M", "1000010");
        compMap.put("M+D", "1000010");
        compMap.put("D-M", "1010011");
        compMap.put("M-D", "1000111");
        compMap.put("D&M", "1000000");
        compMap.put("M&D", "1000000");
        compMap.put("D|M", "1010101");
        compMap.put("M|D", "1010101");

        // jumpMap
        jumpMap.put("null", "000");
        jumpMap.put("JGT",  "001");
        jumpMap.put("JEQ",  "010");
        jumpMap.put("JGE",  "011");
        jumpMap.put("JLT",  "100");
        jumpMap.put("JNE",  "101");
        jumpMap.put("JLE",  "110");
        jumpMap.put("JMP",  "111");
    }

    /**
     * Return the binary code of the dest mnemonic.
     */
    public static String dest(String mnemonic) throws AssemblyException {
        if (!destMap.containsKey(mnemonic)) {
            throw new AssemblyException(mnemonic + " is not a valid mnemonic");
        }
        return destMap.get(mnemonic);
    }

    /**
     * Return the binary code of the comp mnemonic.
     */
    public static String comp(String mnemonic) throws AssemblyException {
        if (!compMap.containsKey(mnemonic)) {
            throw new AssemblyException(mnemonic + " is not a valid mnemonic");
        }
        return compMap.get(mnemonic);
    }

    /**
     * Return the binary code of the jump mnemonic.
     */
    public static String jump(String mnemonic) throws AssemblyException {
        if (!jumpMap.containsKey(mnemonic)) {
            throw new AssemblyException(mnemonic + " is not a valid mnemonic");
        }
        return jumpMap.get(mnemonic);
    }

    private Code() {}
}
