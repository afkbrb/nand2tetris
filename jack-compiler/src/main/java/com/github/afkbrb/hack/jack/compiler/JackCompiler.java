package com.github.afkbrb.hack.jack.compiler;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class JackCompiler {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java JackCompiler .jack/dir");
            return;
        }
        File sourceFile = new File(args[0]);
        JackCompiler compiler = new JackCompiler();
        try {
            compiler.compile(sourceFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void compile(File source) throws IOException, CompileException {
        if (source.isDirectory()) {
            for (File file : Objects.requireNonNull(source.listFiles())) {
                if (file.getName().endsWith(".jack")) {
                    compileFile(file);
                }
            }
        } else {
            compileFile(source);
        }
    }

    private void compileFile(File jackFile) throws IOException, CompileException {
        assert !jackFile.isDirectory();
        CompilationEngine compilationEngine = new CompilationEngine(jackFile);
        compilationEngine.compileClass();
        compilationEngine.close();
    }
}
