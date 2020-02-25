package com.github.afkbrb.hack.vm.translator;

import java.io.File;
import java.io.IOException;

public class VMTranslator implements AutoCloseable {

    private CodeWriter writer;

    private final File source;

    private final File target;


    public VMTranslator(File source) throws IOException {
        this.source = source;
        String canonicalPath = source.getCanonicalPath();
        if (source.isDirectory()) {
            target = new File(canonicalPath + File.separator + source.getName() + ".asm");
        } else {
            String parent = canonicalPath.substring(0, canonicalPath.lastIndexOf(File.separator));
            target = new File(parent + File.separator + source.getName().split("\\.")[0] + ".asm");
        }
    }

    // Translate a file or directory.
    public void translate() throws IOException {
        if (source.isDirectory()) {
            writer = new CodeWriter(target);
            writer.setFilename(source.getName());
            writer.writeInit();
            File[] files = source.listFiles();
            assert files != null;
            for (File file : files) {
                if (file.getName().endsWith(".vm")) {
                    translateFile(file);
                }
            }
        } else {
            // TODO: Do we need to init for a single file?
            writer = new CodeWriter(target);
            translateFile(source);
        }
    }

    // Translate a single file.
    private void translateFile(File file) throws IOException {
        assert !file.isDirectory();
        String filename = file.getName().split("\\.")[0];
        writer.setFilename(filename);
        try (Parser parser = new Parser(file)) {
            parser.advance();
            while (parser.hasMoreCommand()) {
                switch (parser.type()) {
                    case ARITHMETIC:
                        writer.writeArithmetic(parser.action());
                        break;
                    case MEMORY:
                        writer.writeMemory(parser.action(), parser.segment(), parser.index());
                        break;
                    case BRANCH:
                        switch (parser.action()) {
                            case "label":
                                writer.writeLabel(parser.label());
                                break;
                            case "goto":
                                writer.writeGoto(parser.label());
                                break;
                            case "if-goto":
                                writer.writeIf(parser.label());
                                break;
                            default:
                                throw new IllegalStateException(String.format("bug, action: %s", parser.action()));
                        }
                        break;
                    case FUNCTION:
                        switch (parser.action()) {
                            case "function":
                                writer.writeFunction(parser.functionName(), parser.nVars());
                                break;
                            case "call":
                                writer.writeCall(parser.functionName(), parser.nArgs());
                                break;
                            case "return":
                                writer.writeReturn();
                                break;
                            default:
                                throw new IllegalStateException(String.format("bug, action: %s", parser.action()));
                        }
                        break;
                    default:
                        throw new IllegalStateException(String.format("bug, action: %s", parser.action()));
                }
                parser.advance();
            }
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar vm-translator.jar vm-file/vm-directory");
            System.exit(0);
        }
        try (VMTranslator translator = new VMTranslator(new File(args[0]))) {
            translator.translate();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("error when translating");
            System.exit(0);
        }
    }

}
