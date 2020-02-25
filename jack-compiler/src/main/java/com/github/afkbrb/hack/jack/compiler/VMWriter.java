package com.github.afkbrb.hack.jack.compiler;

import java.io.*;

public class VMWriter implements AutoCloseable {

    private Writer writer;

    public VMWriter(File vmFile) throws IOException {
        writer = new BufferedWriter(new FileWriter(vmFile));
    }

    public void writeString(String s) throws IOException {
        writePush(Segment.CONST, s.length());
        writeCall("String.new", 1);
        for (char ch : s.toCharArray()) {
            writePush(Segment.CONST, ch);
            writeCall("String.appendChar", 2);
        }
    }

    public void writePush(Segment segment, int index) throws IOException {
        emit("push " + segment + " " + index);
    }

    public void writePop(Segment segment, int index) throws IOException {
        emit("pop " + segment + " " + index);
    }

    public void writeArithmetic(ArithmeticOperator op) throws IOException {
        emit(op.getName());
    }

    public void writeLabel(String label) throws IOException {
        emit("label " + label);
    }

    public void writeGoto(String label) throws IOException {
        emit("goto " + label);
    }

    public void writeIf(String label) throws IOException {
        emit("if-goto " + label);
    }

    public void writeCall(String function, int args) throws IOException {
        emit("call " + function + " " + args);
    }

    public void writeFunction(String function, int locals) throws IOException {
        emit("function " + function + " " + locals);
    }

    public void writeReturn() throws IOException {
        emit("return\n"); // 换个行，清晰些
    }

    private void emit(String command) throws IOException {
        writer.write(command + "\n");
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    enum Segment {

        CONST("constant"),
        ARG("argument"),
        LOCAL("local"),
        STATIC("static"),
        THIS("this"),
        THAT("that"),
        POINTER("pointer"),
        TEMP("temp");

        private String name;

        Segment(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum ArithmeticOperator {

        ADD("add"),
        SUB("sub"),
        NEG("neg"),
        EQ("eq"),
        GT("gt"),
        LT("lt"),
        AND("and"),
        OR("or"),
        NOT("not");

        private String name;

        ArithmeticOperator(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
