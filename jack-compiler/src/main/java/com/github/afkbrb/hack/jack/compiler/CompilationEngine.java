package com.github.afkbrb.hack.jack.compiler;

import java.io.*;

import com.github.afkbrb.hack.jack.compiler.Tokenizer.Keyword;
import com.github.afkbrb.hack.jack.compiler.SymbolTable.Kind;
import com.github.afkbrb.hack.jack.compiler.VMWriter.Segment;
import com.github.afkbrb.hack.jack.compiler.VMWriter.ArithmeticOperator;

import static com.github.afkbrb.hack.jack.compiler.Tokenizer.Keyword.*;
import static com.github.afkbrb.hack.jack.compiler.Tokenizer.TokenType.*;

/**
 * 从 Tokenizer 中获取 token，采用递归下降法通过 VMWriter 进行代码生成
 */
public class CompilationEngine implements AutoCloseable {

    private Tokenizer tokenizer;

    private SymbolTable symbolTable = new SymbolTable();

    private Writer xmlWriter;

    private VMWriter vmWriter;

    private int indent = 0;

    private String identifier;

    // 类相关
    private String className;

    // 符号表相关
    private SymbolTable.Kind varKind; // 当前变量段类型（field、static、local、argument）
    private String varType; // 当前变量类型（int、char、boolean、ClassName）
    private String varName; // 当前变量名称

    // 函数相关
    private SubroutineType subroutineType;
    private String subroutineName;

    private int labelCounter = 0;

    public CompilationEngine(File jackFile) throws IOException {
        tokenizer = new Tokenizer(jackFile);
        String canonicalPath = jackFile.getCanonicalPath();
        String parent = canonicalPath.substring(0, canonicalPath.lastIndexOf(File.separator));
        String outFilename = parent + File.separator + jackFile.getName().split("\\.")[0];
        xmlWriter = new BufferedWriter(new FileWriter(new File(outFilename + ".xml")));
        vmWriter = new VMWriter(new File(outFilename + ".vm"));

        symbolTable.startSubroutine();
    }

    // class: 'class' ClassName '{' classVarDec* subroutineDec* }
    public void compileClass() throws IOException, CompileException {
        writeLabelOpen("class");

        tokenizer.advance();
        matchKeyword(CLASS);
        matchClassName();
        matchSymbol('{');

        while (tokenizer.tokenType() == KEYWORD && (tokenizer.keyword() == STATIC ||
                tokenizer.keyword() == FIELD)) {
            compileClassVarDec();
        }
        while (tokenizer.tokenType() == KEYWORD && (tokenizer.keyword() == CONSTRUCTOR ||
                tokenizer.keyword() == FUNCTION || tokenizer.keyword() == METHOD)) {
            compileSubroutineDec();
        }
        matchSymbol('}');

        writeLabelClose("class");
    }

    // ('static' | 'field') type varName (',' varName)* ';'
    private void compileClassVarDec() throws IOException, CompileException {
        writeLabelOpen("classVarDec");

        matchClassVarDec();
        matchVarType();
        matchVarName();
        defineVariable();

        while (tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == ',') {
            matchSymbol(',');
            matchVarName();
            defineVariable();
        }
        matchSymbol(';');

        writeLabelClose("classVarDec");
    }

    // ('constructor' | 'function' | 'method') ('void' | type) subroutineName '(' parameterList ')' subroutineBody
    private void compileSubroutineDec() throws IOException, CompileException {
        writeLabelOpen("subroutineDec");

        symbolTable.startSubroutine(); // 遇到新的 subroutine 就清空局部变量表
        matchSubroutineDec();
        if (subroutineType == SubroutineType.METHOD) { // 如果是 method 的话，需要在符号表中插入 this
            varName = "this";
            varType = className;
            varKind = Kind.ARG;
            defineVariable(); // 插入 this
        }
        matchSubroutineType();
        matchSubroutineName();
        matchSymbol('(');
        compileParameterList(); // 将参数插入符号表
        matchSymbol(')');
        compileSubroutineBody();

        writeLabelClose("subroutineDec");
    }

    private void compileParameterList() throws IOException, CompileException {
        writeLabelOpen("parameterList");

        varKind = Kind.ARG;
        if (!(tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == ')')) {
            matchVarType();
            matchVarName();
            defineVariable();
            while (tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == ',') {
                matchSymbol(',');
                matchVarType();
                matchVarName();
                defineVariable();
            }
        }

        writeLabelClose("parameterList");
    }

    private void compileSubroutineBody() throws IOException, CompileException {
        writeLabelOpen("subroutineBody");

        matchSymbol('{');
        while (tokenizer.tokenType() == KEYWORD && tokenizer.keyword() == VAR) { // 所有局部变量必须先声明
            compileVarDec(); // 将局部变量插入符号表
        }
        // 此处知道函数局部变量个数了
        // function ClassName.functionName locals
        vmWriter.writeFunction(className + "." + subroutineName, symbolTable.varCount(Kind.VAR));
        if (subroutineType == SubroutineType.CONSTRUCTOR) {
            // 如果是构造函数的话需要分配内存
            int fieldVarCount = symbolTable.varCount(Kind.FIELD);
            vmWriter.writePush(Segment.CONST, fieldVarCount); // push constant fieldVarCount
            vmWriter.writeCall("Memory.alloc", 1); // call Memory.alloc 1: one argument
            vmWriter.writePop(Segment.POINTER, 0); // pop pointer 0: this = Memory.alloc(fieldVarCount)，最后 return this 会将该指针返回
        } else if (subroutineType == SubroutineType.METHOD) {
            // 获取 this 引用
            vmWriter.writePush(Segment.ARG, 0); // push argument 0
            vmWriter.writePop(Segment.POINTER, 0); // pop pointer 0: this = arg0
        } // function 的话不需要特殊处理
        compileStatements(); // return 也包括在 statements 里面了
        matchSymbol('}');

        writeLabelClose("subroutineBody");
    }

    private void compileVarDec() throws IOException, CompileException {
        writeLabelOpen("varDec");

        varKind = Kind.VAR;
        matchKeyword(VAR);
        matchVarType();
        matchVarName();
        defineVariable();
        while (tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == ',') {
            matchSymbol(',');
            matchVarName();
            defineVariable();
        }
        matchSymbol(';');

        writeLabelClose("varDec");
    }

    private void compileStatements() throws IOException, CompileException {
        writeLabelOpen("statements");

        while (tokenizer.tokenType() == KEYWORD && (tokenizer.keyword() == LET ||
                tokenizer.keyword() == IF || tokenizer.keyword() == WHILE ||
                tokenizer.keyword() == DO || tokenizer.keyword() == RETURN)) {
            switch (tokenizer.keyword()) {
                case LET:
                    compileLet();
                    break;
                case IF:
                    compileIf();
                    break;
                case WHILE:
                    compileWhile();
                    break;
                case DO:
                    compileDo();
                    break;
                case RETURN:
                    compileReturn();
                    break;
                default:
                    error("bug");
            }
        }

        writeLabelClose("statements");
    }

    private void compileLet() throws IOException, CompileException {
        writeLabelOpen("letStatement");

        matchKeyword(LET);
        matchVarName();
        Segment segment = getVarSegment();
        int index = getVarIndex();
        boolean isArray;

        if (tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == '[') { // a[exp1] = exp2
            isArray = true;
            matchSymbol('[');
            vmWriter.writePush(segment, index); // push a
            compileExpression(); // push exp1
            vmWriter.writeArithmetic(ArithmeticOperator.ADD); // add
            matchSymbol(']');
        } else {
            isArray = false;
        }
        matchSymbol('=');
        compileExpression(); // push exp2
        matchSymbol(';');
        if (!isArray) {
            vmWriter.writePop(segment, index); // pop segment i
        } else {
            vmWriter.writePop(Segment.TEMP, 0); // pop temp 0: 要将栈顶元素赋给它下面的指针，需要用一个 temp 变量来保存栈顶元素
            vmWriter.writePop(Segment.POINTER, 1); // pop pointer 1: that 指向要接受赋值的数组位置
            vmWriter.writePush(Segment.TEMP, 0); // push temp 0
            vmWriter.writePop(Segment.THAT, 0); // pop that 0
        }

        writeLabelClose("letStatement");
    }

    private void compileIf() throws IOException, CompileException {
        writeLabelOpen("ifStatement");

        String elseLabel = "IF_ELSE_" + labelCounter;
        String endLabel = "IF_END_" + labelCounter;
        labelCounter++;
        matchKeyword(IF);
        matchSymbol('(');
        compileExpression(); // push exp
        vmWriter.writeArithmetic(ArithmeticOperator.NOT); // not
        vmWriter.writeIf(elseLabel); // if-goto IF_ELSE
        matchSymbol(')');
        matchSymbol('{');
        compileStatements();
        vmWriter.writeGoto(endLabel); // goto IF_END
        matchSymbol('}');
        vmWriter.writeLabel(elseLabel); // label IF_ELSE
        if (tokenizer.tokenType() == KEYWORD && tokenizer.keyword() == ELSE) {
            matchKeyword(ELSE);
            matchSymbol('{');
            compileStatements();
            matchSymbol('}');
        }
        vmWriter.writeLabel(endLabel); // label IF_END

        writeLabelClose("ifStatement");
    }

    private void compileWhile() throws IOException, CompileException {
        writeLabelOpen("whileStatement");

        String continueLabel = "WHILE_CONTINUE_" + labelCounter;
        String endLabel = "WHILE_END_" + labelCounter;
        labelCounter++;
        matchKeyword(WHILE);
        matchSymbol('(');
        vmWriter.writeLabel(continueLabel); // label WHILE_CONTINUE
        compileExpression(); // push exp
        vmWriter.writeArithmetic(ArithmeticOperator.NOT); // not
        vmWriter.writeIf(endLabel); // if-goto WHILE_END
        matchSymbol(')');
        matchSymbol('{');
        compileStatements(); // statements
        vmWriter.writeGoto(continueLabel); // goto WHILE_CONTINUE
        vmWriter.writeLabel(endLabel); // label WHILE_END
        matchSymbol('}');

        writeLabelClose("whileStatement");
    }

    private void compileDo() throws IOException, CompileException {
        writeLabelOpen("doStatement");

        matchKeyword(DO);
        matchIdentifier(); // 不确定是 varName 还是 subroutineName
        if (tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == '(') {
            subroutineName = identifier; // 现在确定上一个 identifier 是 subroutineName 了
            String calledSubroutineName = subroutineName; // 需要先保存 subroutineName，否则如果参数也调用了函数，则会被覆盖
            // TODO：此处假定静态方法（function）都是通过 ClassName.funcName 的形式调用
            // TODO：也就是说，如果一个调用前没有'.'，则假设是 this.subroutineName(...)
            int argCount = 1; // this
            vmWriter.writePush(Segment.POINTER, 0); // push pointer 0: push this
            matchSymbol('(');
            argCount += compileExpressionList(); // push arg1, push arg2...
            matchSymbol(')');
            vmWriter.writeCall(className + "." + calledSubroutineName, argCount);
        } else { // obj.xxx(...)
            varName = identifier; // varName 也有可能是 ClassName，如 Math.xxx()
            String targetClass;
            int argCount;
            if (isVarName()) {
                argCount = 1;
                targetClass = getVarType();
                vmWriter.writePush(getVarSegment(), getVarIndex()); // push obj
            } else {
                argCount = 0;
                targetClass = varName; // varName 不是一个实例，而是一个类名
            }
            matchSymbol('.');
            matchSubroutineName();
            String calledSubroutineName = subroutineName;
            matchSymbol('(');
            argCount += compileExpressionList();
            matchSymbol(')');
            vmWriter.writeCall(targetClass + "." + calledSubroutineName, argCount);
        }
        matchSymbol(';');
        vmWriter.writePop(Segment.TEMP, 0); // pop temp 0: must pop

        writeLabelClose("doStatement");
    }

    private void compileReturn() throws IOException, CompileException {
        writeLabelOpen("returnStatement");

        matchKeyword(RETURN);
        if (!(tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == ';')) {
            compileExpression(); // push exp
        } else {
            vmWriter.writePush(Segment.CONST, 0); // push 0: void 方法返回 0，所有函数都要有返回值
        }
        matchSymbol(';');
        vmWriter.writeReturn();

        writeLabelClose("returnStatement");
    }

    private void compileExpression() throws IOException, CompileException {
        writeLabelOpen("expression");

        compileTerm(); // push term
        while (tokenizer.tokenType() == SYMBOL && (tokenizer.symbol() == '+' || tokenizer.symbol() == '-' ||
                tokenizer.symbol() == '*' || tokenizer.symbol() == '/' || tokenizer.symbol() == '&' ||
                tokenizer.symbol() == '|' || tokenizer.symbol() == '<' || tokenizer.symbol() == '>' || tokenizer.symbol() == '=')) {
            char symbol = tokenizer.symbol();
            matchSymbol();
            compileTerm(); // pushTerm
            switch (symbol) { // op
                case '+':
                    vmWriter.writeArithmetic(ArithmeticOperator.ADD);
                    break;
                case '-':
                    vmWriter.writeArithmetic(ArithmeticOperator.SUB);
                    break;
                case '*':
                    vmWriter.writeCall("Math.multiply", 2);
                    break;
                case '/':
                    vmWriter.writeCall("Math.divide", 2);
                    break;
                case '&':
                    vmWriter.writeArithmetic(ArithmeticOperator.AND);
                    break;
                case '|':
                    vmWriter.writeArithmetic(ArithmeticOperator.OR);
                    break;
                case '<':
                    vmWriter.writeArithmetic(ArithmeticOperator.LT);
                    break;
                case '>':
                    vmWriter.writeArithmetic(ArithmeticOperator.GT);
                    break;
                case '=':
                    vmWriter.writeArithmetic(ArithmeticOperator.EQ);
                    break;
                default:
                    error("bug");
            }
        }

        writeLabelClose("expression");
    }

    private void compileTerm() throws IOException, CompileException {
        writeLabelOpen("term");

        if (tokenizer.tokenType() == INT_CONST) {
            if (tokenizer.intVal() < 0) {
                vmWriter.writePush(Segment.CONST, -tokenizer.intVal()); // 不能直接 push 负数
                vmWriter.writeArithmetic(ArithmeticOperator.NEG);
            } else {
                vmWriter.writePush(Segment.CONST, tokenizer.intVal());
            }
            matchIntConst();
        } else if (tokenizer.tokenType() == STRING_CONST) {
            vmWriter.writeString(tokenizer.stringVal());
            matchStringConst();
        } else if (tokenizer.tokenType() == KEYWORD && (tokenizer.keyword() == TRUE ||
                tokenizer.keyword() == FALSE || tokenizer.keyword() == NULL || tokenizer.keyword() == THIS)) {
            switch (tokenizer.keyword()) {
                case TRUE:
                    vmWriter.writePush(Segment.CONST, 1);
                    vmWriter.writeArithmetic(ArithmeticOperator.NEG);
                    break;
                case FALSE:
                case NULL:
                    vmWriter.writePush(Segment.CONST, 0);
                    break;
                case THIS:
                    vmWriter.writePush(Segment.POINTER, 0);
                    break;
                default:
                    error("bug");
            }
            matchKeyword();
        } else if (tokenizer.tokenType() == IDENTIFIER) {
            matchIdentifier(); // 不确定是 varName 还是 subroutineName
            if (tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == '[') { // a[exp]
                varName = identifier;
                vmWriter.writePush(getVarSegment(), getVarIndex()); // push a
                matchSymbol('[');
                compileExpression(); // push exp
                matchSymbol(']');
                vmWriter.writeArithmetic(ArithmeticOperator.ADD); // add
                vmWriter.writePop(Segment.POINTER, 1); // pop pointer 1
                vmWriter.writePush(Segment.THAT, 0); // push that 0
            } else if (tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == '(') {
                subroutineName = identifier; // TODO：重复代码
                String calledSubroutineName = subroutineName;
                int argCount = 1; // this
                vmWriter.writePush(Segment.POINTER, 0); // push pointer 0: push this
                matchSymbol('(');
                argCount += compileExpressionList(); // push arg1, push arg2...
                matchSymbol(')');
                vmWriter.writeCall(className + "." + calledSubroutineName, argCount);
            } else if (tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == '.') {
                // TODO：重复代码
                varName = identifier; // varName 也有可能是 ClassName，如 Math.xxx()
                String targetClass;
                int argCount;
                if (isVarName()) {
                    argCount = 1;
                    targetClass = getVarType();
                    vmWriter.writePush(getVarSegment(), getVarIndex()); // push obj
                } else {
                    argCount = 0;
                    targetClass = varName; // varName 不是一个实例，而是一个类名
                }
                matchSymbol('.');
                matchSubroutineName();
                String calledSubroutineName = subroutineName;
                matchSymbol('(');
                argCount += compileExpressionList();
                matchSymbol(')');
                vmWriter.writeCall(targetClass + "." + calledSubroutineName, argCount);
            } else {
                varName = identifier;
                vmWriter.writePush(getVarSegment(), getVarIndex()); // push variable
            }
        } else if (tokenizer.tokenType() == SYMBOL && (tokenizer.symbol() == '-' || tokenizer.symbol() == '~')) {
            char symbol = tokenizer.symbol();
            matchSymbol();
            compileTerm(); // push term
            vmWriter.writeArithmetic(symbol == '-' ? ArithmeticOperator.NEG : ArithmeticOperator.NOT);
        } else {
            matchSymbol('(');
            compileExpression(); // push exp
            matchSymbol(')');
        }

        writeLabelClose("term");
    }

    private int compileExpressionList() throws IOException, CompileException {
        writeLabelOpen("expressionList");

        int argCount = 0;
        if (!(tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == ')')) {
            compileExpression(); // push exp
            argCount++;
            while (tokenizer.tokenType() == SYMBOL && tokenizer.symbol() == ',') {
                matchSymbol(',');
                compileExpression(); // push exp
                argCount++;
            }
        }

        writeLabelClose("expressionList");

        return argCount;
    }

    private void defineVariable() {
        symbolTable.define(varName, varType, varKind);
    }

    private boolean isVarName() {
        return symbolTable.indexOf(varName) != -1; // 符号表找不到该“变量”，则说明是个类名或方法名
    }

    private String getVarType() throws CompileException {
        String type = symbolTable.typeOf(varName);
        if (type == null) {
            error("variable " + varName + "not defined yet");
        }
        return type;
    }

    private int getVarIndex() throws CompileException {
        int index = symbolTable.indexOf(varName);
        if (index == -1) {
            error("variable " + varName + "not defined yet");
        }
        return index;
    }

    private Segment getVarSegment() throws CompileException {
        Kind kind = symbolTable.kindOf(varName);
        if (kind == Kind.NONE) {
            error("variable " + varName + "not defined yet");
        }
        switch (kind) {
            case VAR:
                return Segment.LOCAL;
            case ARG:
                return Segment.ARG;
            case FIELD:
                return Segment.THIS;
            case STATIC:
                return Segment.STATIC;
            default:
                error("bug");
        }
        return null; // 不会执行到这儿
    }

    private void matchSubroutineDec() throws CompileException, IOException {
        ensureKeyword();
        if (tokenizer.keyword() == CONSTRUCTOR) {
            subroutineType = SubroutineType.CONSTRUCTOR;
        } else if (tokenizer.keyword() == FUNCTION) {
            subroutineType = SubroutineType.FUNCTION;
        } else if (tokenizer.keyword() == METHOD) {
            subroutineType = SubroutineType.METHOD;
        } else {
            error(String.format("expected keyword constructor, function or method, but get '%s'", tokenizer.keyword()));
        }
        writeTokenThenAdvance();
    }

    private void matchClassVarDec() throws CompileException, IOException {
        ensureKeyword();
        if (!(tokenizer.keyword() == STATIC || tokenizer.keyword() == FIELD)) {
            error(String.format("expected keyword static or field, but get '%s'", tokenizer.keyword()));
        }
        varKind = tokenizer.keyword() == STATIC ? Kind.STATIC : Kind.FIELD;
        writeTokenThenAdvance();
    }

    // int, char, boolean, ClassName
    private void matchVarType() throws CompileException, IOException {
        ensureMore();
        if ((tokenizer.tokenType() == KEYWORD && (tokenizer.keyword() == INT ||
                tokenizer.keyword() == CHAR || tokenizer.keyword() == BOOLEAN)) ||
                tokenizer.tokenType() == IDENTIFIER) {
            varType = tokenizer.tokenType() == KEYWORD ? tokenizer.keyword().getName() : tokenizer.identifier();
            writeTokenThenAdvance();
        } else {
            error(String.format("expected int, char, boolean or ClassName, but get '%s'", tokenizer.token()));
        }
    }

    // void, int, char, boolean, ClassName
    private void matchSubroutineType() throws CompileException, IOException {
        ensureMore();
        if ((tokenizer.tokenType() == KEYWORD && (tokenizer.keyword() == INT ||
                tokenizer.keyword() == CHAR || tokenizer.keyword() == BOOLEAN ||
                tokenizer.keyword() == VOID)) || tokenizer.tokenType() == IDENTIFIER) {
            writeTokenThenAdvance();
        } else {
            error(String.format("expected void, int, char, boolean or ClassName, but get '%s'", tokenizer.token()));
        }
    }

    private void matchStringConst() throws CompileException, IOException {
        ensureMore();
        if (tokenizer.tokenType() != STRING_CONST) {
            error(String.format("expect string const, but get '%s'", tokenizer.tokenType()));
        }
        writeTokenThenAdvance();
    }

    private void matchIntConst() throws CompileException, IOException {
        ensureMore();
        if (tokenizer.tokenType() != INT_CONST) {
            error(String.format("expect int const, but get '%s'", tokenizer.tokenType()));
        }
        writeTokenThenAdvance();
    }

    private void matchClassName() throws CompileException, IOException {
        ensureIdentifier();
        className = identifier = tokenizer.identifier();
        writeTokenThenAdvance();
    }

    private void matchVarName() throws CompileException, IOException {
        ensureIdentifier();
        varName = identifier = tokenizer.identifier();
        writeTokenThenAdvance();
    }

    private void matchSubroutineName() throws CompileException, IOException {
        ensureIdentifier();
        subroutineName = identifier = tokenizer.identifier();
        writeTokenThenAdvance();
    }

    // 有时候在不知道下一个 token 前，无法知道当前的 identifier 到底是 varName 还是 subroutineName
    // 就先用 identifier 储存
    private void matchIdentifier() throws CompileException, IOException {
        ensureIdentifier();
        identifier = tokenizer.identifier();
        writeTokenThenAdvance();
    }

    private void matchKeyword() throws CompileException, IOException {
        ensureKeyword();
        writeTokenThenAdvance();
    }

    private void matchKeyword(Keyword keyword) throws IOException, CompileException {
        ensureKeyword();
        if (tokenizer.keyword() != keyword) {
            error(String.format("expect keyword '%s', but get '%s'", keyword, tokenizer.keyword()));
        }
        writeTokenThenAdvance();
    }

    private void matchSymbol() throws CompileException, IOException {
        ensureSymbol();
        writeTokenThenAdvance();
    }

    private void matchSymbol(char symbol) throws CompileException, IOException {
        ensureSymbol();
        if (tokenizer.symbol() != symbol) {
            error(String.format("expect symbol '%s', but get '%s'", symbol, tokenizer.symbol()));
        }
        writeTokenThenAdvance();
    }

    private void ensureKeyword() throws CompileException {
        ensureMore();
        if (tokenizer.tokenType() != KEYWORD) {
            error(String.format("expect keyword, but get %s", tokenizer.tokenType()));
        }
    }

    private void ensureSymbol() throws CompileException {
        ensureMore();
        if (tokenizer.tokenType() != SYMBOL) {
            error(String.format("expect symbol, but get %s", tokenizer.tokenType()));
        }
    }

    private void ensureIdentifier() throws CompileException {
        ensureMore();
        if (tokenizer.tokenType() != IDENTIFIER) {
            error(String.format("expect identifier, but get %s", tokenizer.tokenType()));
        }
    }

    private void ensureMore() throws CompileException {
        if (!tokenizer.hasMoreTokens()) {
            error("unexpect EOF");
        }
    }

    private void error(String msg) throws CompileException {
        throw new CompileException(String.format("compile error at line %d: %s", tokenizer.lineno(), msg));
    }

    private void writeTokenThenAdvance() throws IOException { // <tokenType> value </tokenType>
        writeIndents();
        xmlWriter.write("<" + tokenizer.tokenType() + "> ");
        xmlWriter.write(escape(tokenizer.token()));
        xmlWriter.write(" </" + tokenizer.tokenType() + ">\n");
        xmlWriter.flush(); // flush 便于编译错误时根据 xml 分析错误原因
        tokenizer.advance();
    }

    private String escape(String s) {
        if ("<".equals(s)) {
            return "&lt;";
        } else if (">".equals(s)) {
            return "&gt;";
        } else if ("&".equals(s)) {
            return "&amp;";
        } else {
            return s;
        }
    }

    private void writeLabelOpen(String label) throws IOException {
        writeIndents();
        xmlWriter.write("<" + label + ">\n");
        xmlWriter.flush();
        indent++;
    }

    private void writeLabelClose(String label) throws IOException {
        indent--;
        writeIndents();
        xmlWriter.write("</" + label + ">\n");
        xmlWriter.flush();
    }

    private void writeIndents() throws IOException {
        for (int i = 0; i < indent; i++) {
            xmlWriter.write("  ");
        }
    }

    @Override
    public void close() throws IOException {
        xmlWriter.close();
        vmWriter.close();
        tokenizer.close();
    }

    private enum SubroutineType {
        CONSTRUCTOR,
        FUNCTION,
        METHOD;
    }
}
