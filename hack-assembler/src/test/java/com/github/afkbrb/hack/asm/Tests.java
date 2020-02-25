package com.github.afkbrb.hack.asm;

import org.junit.Test;

public class Tests {

    @Test
    public void test01() {
        String notInt = "0xff";
        try {
            System.out.println(Integer.parseInt(notInt));
        } catch (Exception e) {
            System.out.println("not a int value");
        }
    }

    @Test
    public void test02() {
        System.out.println(intTo15Bits(1));
    }

    @Test
    public void test03() {
        String a = "\ta";
        System.out.println(a);
        System.out.println(a.trim());
    }

    String intTo15Bits(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            sb.append(n & 1);
            n >>>= 1;
        }
        return sb.reverse().toString();
    }
}
