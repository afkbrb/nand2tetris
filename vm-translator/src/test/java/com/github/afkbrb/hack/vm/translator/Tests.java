package com.github.afkbrb.hack.vm.translator;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class Tests {

    @Test
    public void test01() {
        String s = "a   b";
        System.out.println(Arrays.toString(s.split(" ")));
    }

}
