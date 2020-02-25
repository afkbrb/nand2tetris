package com.github.afkbrb.hack.jack.compiler;

import org.junit.Test;

import java.io.*;

public class TokenizerTest {

    @Test
    public void tokenizerTest() throws IOException {
        File sourceFile = new File("Main.jack");
        File xmlFile = new File("MainT.xml");
        Writer writer = new BufferedWriter(new FileWriter(xmlFile));
        writer.write("<tokens>\n");

        Tokenizer tokenizer = new Tokenizer(sourceFile);
        tokenizer.advance();
        while (tokenizer.hasMoreTokens()) {
            writer.write("<" + tokenizer.tokenType() + ">");
            writer.write(" " + tokenizer.token() + " ");
            writer.write("</" + tokenizer.tokenType() + ">\n");

            tokenizer.advance();
        }

        writer.write("</tokens>");
        writer.close();
        tokenizer.close();
    }

    @Test
    public void test01() {
        int a = 1;
        int b = 2;
        int c = 3;
        a = b = c;
        System.out.println(a);
    }

    @Test
    public void test02() throws IOException {
        File file = new File("D:\\CS\\Nand2Tetris\\nand2tetris\\projects\\10\\jack-compiler\\Main.xml");
        System.out.println(file.getName());
        System.out.println(file.getAbsolutePath());
        System.out.println(file.getCanonicalPath());
    }
}
