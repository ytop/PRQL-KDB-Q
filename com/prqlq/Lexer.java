package com.prqlq;

import java.util.*;
import java.util.regex.*;

/**
 * Lexer for PRQL-Q language
 * Tokenizes input into a stream of tokens
 */
public class Lexer {
    
    public enum TokenType {
        // Operators
        PIPE,           // |
        FILTER,         // ?
        SELECT,         // !
        SORT_ASC,       // ^
        SORT_DESC,      // v
        GROUP,          // @
        LIMIT,          // #
        DROP,           // _
        JOIN_INNER,     // ,
        JOIN_LEFT,      // ,\:
        JOIN_RIGHT,     // ,/:
        JOIN_FULL,      // ,\:/:
        
        // Delimiters
        LPAREN,         // (
        RPAREN,         // )
        LBRACKET,       // [
        RBRACKET,       // ]
        SEMICOLON,      // ;
        COMMA,          // ,
        
        // Operators
        ASSIGN,         // :
        PLUS,           // +
        MINUS,          // -
        MULTIPLY,       // *
        DIVIDE,         // %
        
        // Comparison
        EQ,             // =
        NE,             // <>
        LT,             // <
        GT,             // >
        LE,             // <=
        GE,             // >=
        
        // Logical
        AND,            // &
        OR,             // |
        NOT,            // not
        
        // Literals
        IDENTIFIER,     // name, age, etc.
        NUMBER,         // 123, 45.67
        STRING,         // "hello"
        SYMBOL,         // `sales, `active
        
        // Special
        EOF,
        NEWLINE
    }
    
    public static class Token {
        public final TokenType type;
        public final String value;
        public final int line;
        public final int column;
        
        public Token(TokenType type, String value, int line, int column) {
            this.type = type;
            this.value = value;
            this.line = line;
            this.column = column;
        }
        
        @Override
        public String toString() {
            return String.format("Token(%s, '%s', %d:%d)", type, value, line, column);
        }
    }
    
    private final String input;
    private int position = 0;
    private int line = 1;
    private int column = 1;
    
    public Lexer(String input) {
        this.input = input;
    }
    
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        
        while (position < input.length()) {
            char current = input.charAt(position);
            
            // Skip whitespace (except newlines)
            if (current == ' ' || current == '\t' || current == '\r') {
                advance();
                continue;
            }
            
            // Handle newlines
            if (current == '\n') {
                advance();
                line++;
                column = 1;
                continue;
            }
            
            // Handle comments
            if (current == '/') {
                if (peek() == '\n' || position == input.length() - 1) {
                    skipComment();
                    continue;
                } else if (peek() == '/') {
                    skipComment();
                    continue;
                }
            }
            
            // Multi-character operators
            if (matchSequence(",\\:/:")) {
                tokens.add(new Token(TokenType.JOIN_FULL, ",\\:/:", line, column));
                position += 5;
                column += 5;
                continue;
            }
            
            if (matchSequence(",\\:")) {
                tokens.add(new Token(TokenType.JOIN_LEFT, ",\\:", line, column));
                position += 3;
                column += 3;
                continue;
            }
            
            if (matchSequence(",/:")) {
                tokens.add(new Token(TokenType.JOIN_RIGHT, ",/:", line, column));
                position += 3;
                column += 3;
                continue;
            }
            
            if (matchSequence("<=")) {
                tokens.add(new Token(TokenType.LE, "<=", line, column));
                position += 2;
                column += 2;
                continue;
            }
            
            if (matchSequence(">=")) {
                tokens.add(new Token(TokenType.GE, ">=", line, column));
                position += 2;
                column += 2;
                continue;
            }
            
            if (matchSequence("<>")) {
                tokens.add(new Token(TokenType.NE, "<>", line, column));
                position += 2;
                column += 2;
                continue;
            }
            
            // Single character tokens
            switch (current) {
                case '|':
                    tokens.add(new Token(TokenType.PIPE, "|", line, column));
                    advance();
                    break;
                case '?':
                    tokens.add(new Token(TokenType.FILTER, "?", line, column));
                    advance();
                    break;
                case '!':
                    tokens.add(new Token(TokenType.SELECT, "!", line, column));
                    advance();
                    break;
                case '^':
                    tokens.add(new Token(TokenType.SORT_ASC, "^", line, column));
                    advance();
                    break;
                case 'v':
                    // Check if it's the sort operator or identifier
                    if (position + 1 < input.length() && input.charAt(position + 1) == '[') {
                        tokens.add(new Token(TokenType.SORT_DESC, "v", line, column));
                        advance();
                    } else {
                        tokens.add(readIdentifier());
                    }
                    break;
                case '@':
                    tokens.add(new Token(TokenType.GROUP, "@", line, column));
                    advance();
                    break;
                case '#':
                    tokens.add(new Token(TokenType.LIMIT, "#", line, column));
                    advance();
                    break;
                case '_':
                    tokens.add(new Token(TokenType.DROP, "_", line, column));
                    advance();
                    break;
                case '(':
                    tokens.add(new Token(TokenType.LPAREN, "(", line, column));
                    advance();
                    break;
                case ')':
                    tokens.add(new Token(TokenType.RPAREN, ")", line, column));
                    advance();
                    break;
                case '[':
                    tokens.add(new Token(TokenType.LBRACKET, "[", line, column));
                    advance();
                    break;
                case ']':
                    tokens.add(new Token(TokenType.RBRACKET, "]", line, column));
                    advance();
                    break;
                case ';':
                    tokens.add(new Token(TokenType.SEMICOLON, ";", line, column));
                    advance();
                    break;
                case ',':
                    tokens.add(new Token(TokenType.COMMA, ",", line, column));
                    advance();
                    break;
                case ':':
                    tokens.add(new Token(TokenType.ASSIGN, ":", line, column));
                    advance();
                    break;
                case '+':
                    tokens.add(new Token(TokenType.PLUS, "+", line, column));
                    advance();
                    break;
                case '-':
                    tokens.add(new Token(TokenType.MINUS, "-", line, column));
                    advance();
                    break;
                case '*':
                    tokens.add(new Token(TokenType.MULTIPLY, "*", line, column));
                    advance();
                    break;
                case '%':
                    tokens.add(new Token(TokenType.DIVIDE, "%", line, column));
                    advance();
                    break;
                case '=':
                    tokens.add(new Token(TokenType.EQ, "=", line, column));
                    advance();
                    break;
                case '<':
                    tokens.add(new Token(TokenType.LT, "<", line, column));
                    advance();
                    break;
                case '>':
                    tokens.add(new Token(TokenType.GT, ">", line, column));
                    advance();
                    break;
                case '&':
                    tokens.add(new Token(TokenType.AND, "&", line, column));
                    advance();
                    break;
                case '"':
                    tokens.add(readString());
                    break;
                case '`':
                    tokens.add(readSymbol());
                    break;
                default:
                    if (Character.isDigit(current)) {
                        tokens.add(readNumber());
                    } else if (Character.isLetter(current) || current == '.') {
                        tokens.add(readIdentifier());
                    } else {
                        throw new RuntimeException(String.format(
                            "Unexpected character '%c' at line %d, column %d", current, line, column));
                    }
            }
        }
        
        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }
    
    private Token readIdentifier() {
        int startColumn = column;
        StringBuilder sb = new StringBuilder();
        
        while (position < input.length() && 
               (Character.isLetterOrDigit(input.charAt(position)) || 
                input.charAt(position) == '_' ||
                input.charAt(position) == '.')) {
            sb.append(input.charAt(position));
            advance();
        }
        
        String value = sb.toString();
        
        // Check for keywords
        if (value.equals("not")) {
            return new Token(TokenType.NOT, value, line, startColumn);
        }
        
        return new Token(TokenType.IDENTIFIER, value, line, startColumn);
    }
    
    private Token readNumber() {
        int startColumn = column;
        StringBuilder sb = new StringBuilder();
        boolean hasDecimal = false;
        
        while (position < input.length()) {
            char c = input.charAt(position);
            if (Character.isDigit(c)) {
                sb.append(c);
                advance();
            } else if (c == '.' && !hasDecimal) {
                hasDecimal = true;
                sb.append(c);
                advance();
            } else {
                break;
            }
        }
        
        return new Token(TokenType.NUMBER, sb.toString(), line, startColumn);
    }
    
    private Token readString() {
        int startColumn = column;
        StringBuilder sb = new StringBuilder();
        advance(); // Skip opening quote
        
        while (position < input.length() && input.charAt(position) != '"') {
            if (input.charAt(position) == '\\' && position + 1 < input.length()) {
                advance();
                char escaped = input.charAt(position);
                switch (escaped) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    case '"': sb.append('"'); break;
                    default: sb.append(escaped);
                }
                advance();
            } else {
                sb.append(input.charAt(position));
                advance();
            }
        }
        
        if (position >= input.length()) {
            throw new RuntimeException("Unterminated string at line " + line);
        }
        
        advance(); // Skip closing quote
        return new Token(TokenType.STRING, sb.toString(), line, startColumn);
    }
    
    private Token readSymbol() {
        int startColumn = column;
        advance(); // Skip backtick
        StringBuilder sb = new StringBuilder();
        
        while (position < input.length() && 
               (Character.isLetterOrDigit(input.charAt(position)) || 
                input.charAt(position) == '_')) {
            sb.append(input.charAt(position));
            advance();
        }
        
        return new Token(TokenType.SYMBOL, sb.toString(), line, startColumn);
    }
    
    private void skipComment() {
        while (position < input.length() && input.charAt(position) != '\n') {
            advance();
        }
    }
    
    private boolean matchSequence(String sequence) {
        if (position + sequence.length() > input.length()) {
            return false;
        }
        return input.substring(position, position + sequence.length()).equals(sequence);
    }
    
    private char peek() {
        if (position + 1 < input.length()) {
            return input.charAt(position + 1);
        }
        return '\0';
    }
    
    private void advance() {
        position++;
        column++;
    }
}
