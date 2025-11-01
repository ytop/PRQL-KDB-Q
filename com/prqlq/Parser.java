package com.prqlq;

import com.prqlq.ASTNode.*;
import com.prqlq.Lexer.*;

import java.util.*;

/**
 * Parser for PRQL-Q language
 * Converts tokens into an Abstract Syntax Tree
 */
public class Parser {
    
    private final List<Token> tokens;
    private int position = 0;
    
    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }
    
    public Pipeline parse() {
        // Parse table name
        String tableName = parseTableName();
        
        List<Operation> operations = new ArrayList<>();
        
        // Parse pipeline operations
        while (!isAtEnd()) {
            if (match(TokenType.PIPE)) {
                Operation op = parseOperation();
                if (op != null) {
                    operations.add(op);
                }
            } else if (peek().type == TokenType.EOF) {
                break;
            } else {
                throw error("Expected pipe operator |", peek());
            }
        }
        
        return new Pipeline(tableName, operations);
    }
    
    private String parseTableName() {
        Token token = advance();
        if (token.type == TokenType.IDENTIFIER) {
            return token.value;
        }
        throw error("Expected table name", token);
    }
    
    private Operation parseOperation() {
        Token token = peek();
        
        switch (token.type) {
            case FILTER:
                return parseFilter();
            case SELECT:
                return parseSelect();
            case SORT_ASC:
            case SORT_DESC:
                return parseSort();
            case GROUP:
                return parseGroup();
            case LIMIT:
                return parseLimit();
            case DROP:
                return parseDrop();
            default:
                throw error("Unknown operation", token);
        }
    }
    
    // Parse filter: ?[condition]
    private FilterOp parseFilter() {
        consume(TokenType.FILTER, "Expected ?");
        consume(TokenType.LBRACKET, "Expected [");
        
        Expression condition = parseExpression();
        
        consume(TokenType.RBRACKET, "Expected ]");
        
        return new FilterOp(condition);
    }
    
    // Parse select: ![columns]
    private SelectOp parseSelect() {
        consume(TokenType.SELECT, "Expected !");
        consume(TokenType.LBRACKET, "Expected [");
        
        List<ColumnSpec> columns = new ArrayList<>();
        
        do {
            if (match(TokenType.MULTIPLY)) {
                // Wildcard *
                columns.add(new ColumnSpec(true));
            } else {
                // Check for alias (name:expr)
                Token first = peek();
                if (first.type == TokenType.IDENTIFIER && peekAhead(1).type == TokenType.ASSIGN) {
                    String alias = advance().value;
                    consume(TokenType.ASSIGN, "Expected :");
                    Expression expr = parseExpression();
                    columns.add(new ColumnSpec(alias, expr));
                } else {
                    // Just expression (will use variable name or generate name)
                    Expression expr = parseExpression();
                    columns.add(new ColumnSpec(null, expr));
                }
            }
            
            if (!match(TokenType.SEMICOLON) && !match(TokenType.COMMA)) {
                break;
            }
        } while (!check(TokenType.RBRACKET));
        
        consume(TokenType.RBRACKET, "Expected ]");
        
        return new SelectOp(columns);
    }
    
    // Parse sort: ^[col] or v[col]
    private SortOp parseSort() {
        boolean ascending = match(TokenType.SORT_ASC);
        if (!ascending) {
            consume(TokenType.SORT_DESC, "Expected ^ or v");
        }
        
        consume(TokenType.LBRACKET, "Expected [");
        
        Token colToken = consume(TokenType.IDENTIFIER, "Expected column name");
        
        consume(TokenType.RBRACKET, "Expected ]");
        
        return new SortOp(colToken.value, ascending);
    }
    
    // Parse group: @[columns]
    private GroupOp parseGroup() {
        consume(TokenType.GROUP, "Expected @");
        consume(TokenType.LBRACKET, "Expected [");
        
        List<String> columns = new ArrayList<>();
        
        do {
            Token col = consume(TokenType.IDENTIFIER, "Expected column name");
            columns.add(col.value);
            
            if (!match(TokenType.SEMICOLON) && !match(TokenType.COMMA)) {
                break;
            }
        } while (!check(TokenType.RBRACKET));
        
        consume(TokenType.RBRACKET, "Expected ]");
        
        return new GroupOp(columns);
    }
    
    // Parse limit: #[n]
    private LimitOp parseLimit() {
        consume(TokenType.LIMIT, "Expected #");
        consume(TokenType.LBRACKET, "Expected [");
        
        Token num = consume(TokenType.NUMBER, "Expected number");
        int limit = (int) Double.parseDouble(num.value);
        
        consume(TokenType.RBRACKET, "Expected ]");
        
        return new LimitOp(limit);
    }
    
    // Parse drop: _[n]
    private DropOp parseDrop() {
        consume(TokenType.DROP, "Expected _");
        consume(TokenType.LBRACKET, "Expected [");
        
        Token num = consume(TokenType.NUMBER, "Expected number");
        int count = (int) Double.parseDouble(num.value);
        
        consume(TokenType.RBRACKET, "Expected ]");
        
        return new DropOp(count);
    }
    
    // Expression parsing with operator precedence
    private Expression parseExpression() {
        return parseOr();
    }
    
    private Expression parseOr() {
        Expression expr = parseAnd();
        
        while (match(TokenType.OR)) {
            Expression right = parseAnd();
            expr = new BinaryOp(BinaryOp.Operator.OR, expr, right);
        }
        
        return expr;
    }
    
    private Expression parseAnd() {
        Expression expr = parseComparison();
        
        while (match(TokenType.AND)) {
            Expression right = parseComparison();
            expr = new BinaryOp(BinaryOp.Operator.AND, expr, right);
        }
        
        return expr;
    }
    
    private Expression parseComparison() {
        Expression expr = parseAdditive();
        
        while (true) {
            if (match(TokenType.EQ)) {
                Expression right = parseAdditive();
                expr = new BinaryOp(BinaryOp.Operator.EQ, expr, right);
            } else if (match(TokenType.NE)) {
                Expression right = parseAdditive();
                expr = new BinaryOp(BinaryOp.Operator.NE, expr, right);
            } else if (match(TokenType.LT)) {
                Expression right = parseAdditive();
                expr = new BinaryOp(BinaryOp.Operator.LT, expr, right);
            } else if (match(TokenType.GT)) {
                Expression right = parseAdditive();
                expr = new BinaryOp(BinaryOp.Operator.GT, expr, right);
            } else if (match(TokenType.LE)) {
                Expression right = parseAdditive();
                expr = new BinaryOp(BinaryOp.Operator.LE, expr, right);
            } else if (match(TokenType.GE)) {
                Expression right = parseAdditive();
                expr = new BinaryOp(BinaryOp.Operator.GE, expr, right);
            } else {
                break;
            }
        }
        
        return expr;
    }
    
    private Expression parseAdditive() {
        Expression expr = parseMultiplicative();
        
        while (true) {
            if (match(TokenType.PLUS)) {
                Expression right = parseMultiplicative();
                expr = new BinaryOp(BinaryOp.Operator.ADD, expr, right);
            } else if (match(TokenType.MINUS)) {
                Expression right = parseMultiplicative();
                expr = new BinaryOp(BinaryOp.Operator.SUBTRACT, expr, right);
            } else {
                break;
            }
        }
        
        return expr;
    }
    
    private Expression parseMultiplicative() {
        Expression expr = parseUnary();
        
        while (true) {
            if (match(TokenType.MULTIPLY)) {
                Expression right = parseUnary();
                expr = new BinaryOp(BinaryOp.Operator.MULTIPLY, expr, right);
            } else if (match(TokenType.DIVIDE)) {
                Expression right = parseUnary();
                expr = new BinaryOp(BinaryOp.Operator.DIVIDE, expr, right);
            } else {
                break;
            }
        }
        
        return expr;
    }
    
    private Expression parseUnary() {
        if (match(TokenType.NOT)) {
            Expression expr = parseUnary();
            return new UnaryOp(UnaryOp.Operator.NOT, expr);
        }
        
        if (match(TokenType.MINUS)) {
            Expression expr = parseUnary();
            return new UnaryOp(UnaryOp.Operator.NEGATE, expr);
        }
        
        return parsePrimary();
    }
    
    private Expression parsePrimary() {
        // Numbers
        if (match(TokenType.NUMBER)) {
            Token token = previous();
            double value = Double.parseDouble(token.value);
            return new Literal(value);
        }
        
        // Strings
        if (match(TokenType.STRING)) {
            Token token = previous();
            return new Literal(token.value);
        }
        
        // Symbols
        if (match(TokenType.SYMBOL)) {
            Token token = previous();
            return new Literal(token.value);
        }
        
        // Identifiers (variables or functions)
        if (match(TokenType.IDENTIFIER)) {
            Token token = previous();
            
            // Check if it's a function call
            if (match(TokenType.LBRACKET)) {
                List<Expression> args = new ArrayList<>();
                
                if (!check(TokenType.RBRACKET)) {
                    do {
                        args.add(parseExpression());
                    } while (match(TokenType.SEMICOLON) || match(TokenType.COMMA));
                }
                
                consume(TokenType.RBRACKET, "Expected ]");
                return new FunctionCall(token.value, args);
            }
            
            return new Variable(token.value);
        }
        
        // Parentheses
        if (match(TokenType.LPAREN)) {
            Expression expr = parseExpression();
            consume(TokenType.RPAREN, "Expected )");
            return expr;
        }
        
        throw error("Expected expression", peek());
    }
    
    // Helper methods
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }
    
    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }
    
    private Token advance() {
        if (!isAtEnd()) position++;
        return previous();
    }
    
    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }
    
    private Token peek() {
        return tokens.get(position);
    }
    
    private Token peekAhead(int offset) {
        int pos = position + offset;
        if (pos >= tokens.size()) {
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(pos);
    }
    
    private Token previous() {
        return tokens.get(position - 1);
    }
    
    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(message, peek());
    }
    
    private RuntimeException error(String message, Token token) {
        return new RuntimeException(String.format(
            "Parse error at line %d, column %d: %s (found: %s '%s')",
            token.line, token.column, message, token.type, token.value
        ));
    }
}
