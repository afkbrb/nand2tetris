package com.github.afkbrb.hack.jack.compiler;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class CompilationEngineTest {

    @Test
    public void compilationEngineTest() throws IOException, CompileException {
        CompilationEngine compilationEngine = new CompilationEngine(new File("Main.jack"));
        compilationEngine.compileClass();
        compilationEngine.close();
    }
}
