package com.github.afkbrb.hack.vm.translator;

import java.io.*;

public class CodeWriter implements AutoCloseable {

    private Writer writer;

    private int jumpCounter = 0;

    private int callCounter = 0;

    private String filename;

    private String currFuncName = "_default"; // for the scope of label

    public CodeWriter(File file) throws IOException {
        writer = new BufferedWriter(new FileWriter(file));
    }

    // Set the name of the current parsing file, not the name of the output file.
    public void setFilename(String filename) {
        this.filename = filename;
    }

    // Bootstrap code
    public void writeInit() throws IOException {
        emit("@256");
        emit("D=A");
        emit("@SP");
        emit("M=D"); // SP = 256

        writeCall("Sys.init", 0); // call Sys.init 0
    }

    // add, sub, neg, eq, gt, lt, and, or, not
    public void writeArithmetic(String action) throws IOException {
        switch (action) {
            case "add":
                binaryArithmetic("+");
                break;
            case "sub":
                binaryArithmetic("-");
                break;
            case "and":
                binaryArithmetic("&");
                break;
            case "or":
                binaryArithmetic("|");
                break;
            case "neg":
                unaryArithmetic("-");
                break;
            case "not":
                unaryArithmetic("!");
                break;
            case "eq":
                comparision("JEQ");
                break;
            case "gt":
                comparision("JGT");
                break;
            case "lt":
                comparision("JLT");
                break;
            default:
                throw new IllegalStateException(String.format("bug, action: %s", action));
        }
    }

    // push, pop
    public void writeMemory(String action, String segment, int index) throws IOException {
        switch (action) {
            case "push":
                switch (segment) {
                    case "local":
                        pushSegment("LCL", index);
                        break;
                    case "argument":
                        pushSegment("ARG", index);
                        break;
                    case "this":
                        pushSegment("THIS", index);
                        break;
                    case "that":
                        pushSegment("THAT", index);
                        break;
                    case "pointer":
                        pushPointer(index);
                        break;
                    case "constant":
                        pushConstant(index);
                        break;
                    case "static":
                        pushStatic(index);
                        break;
                    case "temp":
                        pushTemp(index);
                        break;
                    default:
                        throw new IllegalStateException(String.format("bug, action: %s", action));
                }
                break;
            case "pop":
                switch (segment) {
                    case "local":
                        popSegment("LCL", index);
                        break;
                    case "argument":
                        popSegment("ARG", index);
                        break;
                    case "this":
                        popSegment("THIS", index);
                        break;
                    case "that":
                        popSegment("THAT", index);
                        break;
                    case "pointer":
                        popPointer(index);
                        break;
                    case "static":
                        popStatic(index);
                        break;
                    case "temp":
                        popTemp(index);
                        break;
                    default:
                        throw new IllegalStateException(String.format("bug, action: %s", action));
                }
                break;
            default:
                throw new IllegalStateException(String.format("bug, action: %s", action));
        }
    }

    public void writeLabel(String label) throws IOException {
        emit(String.format("(%s$%s)", currFuncName, label)); // (Foo.func$label)
    }

    public void writeGoto(String label) throws IOException {
        emit(String.format("@%s$%s", currFuncName, label));
        emit("0;JMP"); // goto Foo.func$label
    }

    public void writeIf(String label) throws IOException {
        pop();
        emit(String.format("@%s$%s", currFuncName, label));
        emit("D;JNE"); // if (pop() != 0) goto Foo.func$label
    }

    public void writeFunction(String functionName, int nVars) throws IOException {
        this.currFuncName = functionName;
        callCounter = 0;
        emit(String.format("\n(%s) // function %s %d", functionName, functionName, nVars)); // (Foo.func)
        for (int i = 0; i < nVars; i++) { // repeat n push 0
            emit("D=0");
            push();
        }
    }

    public void writeCall(String functionName, int nArgs) throws IOException {
        String retAddrLabel = String.format("%s$ret.%d", currFuncName, callCounter++);
        emit(String.format("@%s // call %s %d begin", retAddrLabel, functionName, nArgs)); // 指令流太长了，加点注释
        emit("D=A");
        push(); // push retAddrLabel
        emit("@LCL");
        emit("D=M");
        push(); // push LCL
        emit("@ARG");
        emit("D=M");
        push(); // push ARG
        emit("@THIS");
        emit("D=M");
        push(); // push THIS
        emit("@THAT");
        emit("D=M");
        push(); // push THAT

        emit("@SP");
        emit("D=M");
        push();
        pushConstant(5 + nArgs);
        writeArithmetic("sub");
        pop();
        emit("@ARG");
        emit("M=D"); // ARG = SP - 5 - nArgs

        emit("@SP");
        emit("D=M");
        emit("@LCL");
        emit("M=D"); // LCL = SP

        emit("@" + functionName);
        emit("0;JMP"); // goto Bar.callee

        emit(String.format("(%s) // call %s %d end", retAddrLabel, functionName, nArgs)); // (retAddrLabel)
    }

    public void writeReturn() throws IOException {
        emit(String.format("@LCL // return from %s begin", currFuncName));
        emit("D=M");
        emit("@R13");
        emit("M=D"); // endFrame = LCL

        emit("@ARG");
        emit("D=M");
        emit("@R14");
        emit("M=D"); // R14 = original ARG
        pop(); // D = returnValue
        emit("@R15");
        // we cannot set *ARG = pop() now, because if the callee has no arguments
        // the saved return address will be overwrite be the return value
        emit("M=D"); // R15 = returnValue

        emit("@ARG");
        emit("D=M+1");
        emit("@SP");
        emit("M=D"); // SP = ARG + 1

        emit("@R13");
        emit("AM=M-1");
        emit("D=M");
        emit("@THAT");
        emit("M=D"); // THAT = *(--endFrame) = *(originalEndFrame - 1)

        emit("@R13");
        emit("AM=M-1");
        emit("D=M");
        emit("@THIS");
        emit("M=D"); // THIS = *(--endFrame) = *(originalEndFrame - 2)

        emit("@R13");
        emit("AM=M-1");
        emit("D=M");
        emit("@ARG");
        emit("M=D"); // ARG = *(--endFrame) = *(originalEndFrame - 3)

        emit("@R13");
        emit("AM=M-1");
        emit("D=M");
        emit("@LCL");
        emit("M=D"); // LCL = *(--endFrame) = *(originalEndFrame - 4)

        emit("@R13");
        emit("AM=M-1"); // A = --endFrame = originalEndFrame - 5
        emit("D=M"); // D = *(originalEndFrame - 5) = returnAddress
        emit("@R13");
        emit("M=D"); // we now use R13 to store returnAddress

        emit("@R15"); // R15 = returnValue
        emit("D=M"); // D = returnValue
        emit("@R14"); // R14 = original ARG
        emit("A=M");
        emit("M=D"); // *originalARG = D = returnValue

        emit("@R13"); // R13 = returnAddress
        emit("A=M"); // @returnAddress <=> A = returnAddress = M
        emit(String.format("0;JMP // return from %s end", currFuncName));
    }

    // for local, argument, this, that
    private void pushSegment(String segment, int i) throws IOException {
        emit("@" + segment);
        emit("D=M"); // D = segmentPtr
        emit("@" + i);
        emit("A=D+A"); // A = segmentPtr + i
        emit("D=M"); // D = *(segmentPtr + i)
        push();
    }

    private void popSegment(String segment, int i) throws IOException {
        emit("@" + segment);
        emit("D=M");
        emit("@" + i);
        emit("D=D+A");
        setReg(); // addr = segmentPtr + i
        pop();
        emit("@R13");
        emit("A=M");
        emit("M=D"); // *addr = *SP
    }

    private void pushPointer(int i) throws IOException {
        if (i == 0) {
            emit("@THIS");
        } else {
            emit("@THAT");
        }
        emit("D=M");
        push();
    }

    private void popPointer(int i) throws IOException {
        pop();
        if (i == 0) {
            emit("@THIS");
        } else {
            emit("@THAT");
        }
        emit("M=D");
    }

    private void pushStatic(int i) throws IOException {
        emit("@" + filename + "." + i); // @Foo.i
        emit("D=M"); // D = Foo.i
        push();
    }

    private void popStatic(int i) throws IOException {
        pop();
        emit("@" + filename + "." + i); // @Foo.i
        emit("M=D"); // Foo.i = D
    }

    private void pushConstant(int constant) throws IOException {
        emit("@" + constant);
        emit("D=A");
        push();
    }

    private void pushTemp(int i) throws IOException {
        emit("@" + (5 + i));
        emit("D=M"); // D = *(5 + i)
        push();
    }

    private void popTemp(int i) throws IOException {
        pop();
        emit("@" + (5 + i));
        emit("M=D"); // *(5 + i) = D
    }

    private void binaryArithmetic(String op) throws IOException {
        pop();
        setReg(); // R = D
        pop();
        emit("@R13");
        emit("D=D" + op + "M"); // D = D op R
        push();
    }

    private void unaryArithmetic(String op) throws IOException {
        pop();
        emit("D=" + op + "D"); // D = ?D
        push();
    }

    private void comparision(String cmp) throws IOException {
        int counter = jumpCounter++;
        pop();
        setReg(); // R = D
        pop();
        emit("@R13");
        emit("D=D-M"); // D = D - R
        emit("@JMP_" + counter);
        emit("D;" + cmp); // if (D ? M) jump to set -1
        emit("D=0"); // set 0
        emit("@JMP_END_" + counter);
        emit("0;JMP");
        emit("(JMP_" + counter + ")");
        emit("D=-1"); // set -1
        emit("(JMP_END_" + counter + ")");
        push();
    }

    // R = D
    // R13-R15 are general purpose registers, we only use R13 in this translator
    private void setReg() throws IOException {
        emit("@R13");
        emit("M=D");
    }

    // D = R
    private void getReg() throws IOException {
        emit("@R13");
        emit("D=M");
    }

    private void push() throws IOException {
        emit("@SP");
        emit("A=M");
        emit("M=D"); // *SP == D
        emit("@SP");
        emit("M=M+1"); // SP++
    }

    private void pop() throws IOException {
        emit("@SP");
        emit("AM=M-1"); // SP--
        emit("D=M"); // D = *SP
    }


    private void emit(String command) throws IOException {
        writer.write(command + "\n");
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
