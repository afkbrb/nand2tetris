package com.github.afkbrb.hack.jack.compiler;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Tokenizer implements AutoCloseable {

    private Reader reader;

    private boolean hasMoreTokens = true;

    private TokenType tokenType;

    private int lineno = 1;

    private Keyword keyword;
    private char symbol;
    private String identifier;
    private int intVal;
    private String stringVal;

    private String token;

    public Tokenizer(File sourceFile) throws FileNotFoundException {
        reader = new BufferedReader(new FileReader(sourceFile));
    }

    public boolean hasMoreTokens() {
        return hasMoreTokens;
    }

    public void advance() throws IOException {
        StringBuilder sb = new StringBuilder();
        State state = State.START;

        while (state != State.DONE) {
            boolean save = true;
            int ch = nextChar();
            if (ch == '\n') lineno++;
            switch (state) {
                case START:
                    if (isAlpha(ch)) {
                        state = State.ID;
                    } else if (isDigit(ch)) {
                        state = State.NUM;
                    } else if (isBlank(ch)) {
                        save = false; // ignore blank
                    } else if (ch == '"') {
                        save = false;
                        state = State.STRING;
                    } else if (ch == '/') {
                        save = false;
                        state = State.IN_COMMENT;
                    } else if (isValidSymbol(ch)) {
                        state = State.DONE;
                        tokenType = TokenType.SYMBOL;
                    } else if (ch == -1) { // EOF
                        save = false;
                        state = State.DONE;
                        hasMoreTokens = false;
                        tokenType = TokenType.ERROR;
                    } else {
                        throw new IllegalStateException("line " + lineno + ": unexpected character '" + (char) ch + "'");
                    }
                    break;
                case ID:
                    if (!(isDigit(ch) || isAlpha(ch))) {
                        rollback();
                        save = false;
                        state = State.DONE;
                        tokenType = TokenType.IDENTIFIER; // may be keyword, we'll deal with it later
                    }
                    break;
                case NUM:
                    if (!isDigit(ch)) {
                        rollback();
                        save = false;
                        state = State.DONE;
                        tokenType = TokenType.INT_CONST;
                    }
                    break;
                case STRING:
                    if (ch == '"') {
                        save = false;
                        state = State.DONE;
                        tokenType = TokenType.STRING_CONST;
                    }
                    break;
                case IN_COMMENT:
                    save = false;
                    if (ch == '/') {
                        state = State.COMMENT_LINE;
                    } else if (ch == '*') {
                        state = State.COMMENT_LINES;
                    } else {
                        rollback();
                        state = State.DONE;
                        tokenType = TokenType.SYMBOL;
                        sb.append('/');
                    }
                    break;
                case COMMENT_LINE:
                    save = false;
                    if (ch == '\n') {
                        state = State.START;
                    }
                    break;
                case COMMENT_LINES:
                    save = false;
                    if (ch == '*') {
                        state = State.COMMENT_LINES_IN_END;
                    }
                    break;
                case COMMENT_LINES_IN_END:
                    save = false;
                    if (ch == '/') {
                        state = State.START;
                    } else if (ch != '*') {
                        state = State.COMMENT_LINES;
                    }
                    break;
                default:
                    throw new IllegalStateException("line " + lineno + ": bug");
            }
            if (save) {
                sb.append((char) ch);
            }
        } // end while
        if (!hasMoreTokens) return;

        token = sb.toString();
        if (tokenType == TokenType.IDENTIFIER && Keyword.keywordMap.containsKey(token)) { // may be keyword
            tokenType = TokenType.KEYWORD;
        }
        switch (tokenType) {
            case SYMBOL:
                symbol = token.charAt(0);
                break;
            case KEYWORD:
                keyword = Keyword.keywordMap.get(token);
                break;
            case INT_CONST:
                intVal = Integer.parseInt(token);
                break;
            case IDENTIFIER:
                identifier = token;
                break;
            case STRING_CONST:
                stringVal = token;
                break;
            default:
                throw new IllegalStateException("bug");
        }

        // System.out.println("line " + lineno + ": tokenType: " + tokenType + ", token: " + token);
    }

    public int lineno() {
        return lineno;
    }

    public TokenType tokenType() {
        return tokenType;
    }

    public Keyword keyword() {
        return keyword;
    }

    public char symbol() {
        return symbol;
    }

    public String identifier() {
        return identifier;
    }

    public int intVal() {
        return intVal;
    }

    public String stringVal() {
        return stringVal;
    }

    public String token() {
        return token;
    }

    private boolean isAlpha(int ch) {
        return ('a' <= ch && ch <= 'z') || ('A' <= ch && ch <= 'Z') || ch == '_';
    }

    private boolean isDigit(int ch) {
        return '0' <= ch && ch <= '9';
    }

    private boolean isBlank(int ch) {
        return ch == '\t' || ch == ' ' || ch == '\r' || ch == '\n';
    }

    private static char[] validSymbols = {'{', '}', '(', ')', '[', ']', '.', ',', ';', '+', '-', '*', '/', '&', '|', '<', '>', '=', '~'};

    private boolean isValidSymbol(int ch) {
        for (char validSymbol : validSymbols) {
            if (validSymbol == ch) {
                return true;
            }
        }
        return false;
    }

    private int nextChar() throws IOException {
        assert reader.markSupported();
        reader.mark(1);
        return reader.read();
    }

    private void rollback() throws IOException {
        assert reader.markSupported();
        reader.reset();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    private enum State {
        START,
        ID,
        NUM,
        IN_COMMENT, // '/' => '/' | '//xxx' | '/*xxx*/'
        COMMENT_LINE, // '//'
        COMMENT_LINES, // '/*xxx*/'
        COMMENT_LINES_IN_END,
        STRING, // '"'
        DONE
    }

    enum TokenType {

        KEYWORD("keyword"),
        SYMBOL("symbol"),
        INT_CONST("intConst"),
        STRING_CONST("stringConst"),
        IDENTIFIER("identifier"),
        ERROR("error"); // used by the compiler, not the Jack language

        private String name;

        TokenType(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    enum Keyword {

        CLASS("class"),
        METHOD("method"),
        FUNCTION("function"),
        CONSTRUCTOR("constructor"),
        INT("int"),
        BOOLEAN("boolean"),
        CHAR("char"),
        VOID("void"),
        VAR("var"),
        STATIC("static"),
        FIELD("field"),
        LET("let"),
        DO("do"),
        IF("if"),
        ELSE("else"),
        WHILE("while"),
        RETURN("return"),
        TRUE("true"),
        FALSE("false"),
        NULL("null"),
        THIS("this");

        public static Map<String, Keyword> keywordMap = new HashMap<>();

        static {
            keywordMap.put("class", CLASS);
            keywordMap.put("method", METHOD);
            keywordMap.put("function", FUNCTION);
            keywordMap.put("constructor", CONSTRUCTOR);
            keywordMap.put("int", INT);
            keywordMap.put("boolean", BOOLEAN);
            keywordMap.put("char", CHAR);
            keywordMap.put("void", VOID);
            keywordMap.put("var", VAR);
            keywordMap.put("static", STATIC);
            keywordMap.put("field", FIELD);
            keywordMap.put("let", LET);
            keywordMap.put("do", DO);
            keywordMap.put("if", IF);
            keywordMap.put("else", ELSE);
            keywordMap.put("while", WHILE);
            keywordMap.put("return", RETURN);
            keywordMap.put("true", TRUE);
            keywordMap.put("false", FALSE);
            keywordMap.put("null", NULL);
            keywordMap.put("this", THIS);
        }

        private String name;

        Keyword(String name) {
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
