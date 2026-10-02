package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
  private static class ParseError extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  private final List<Token> tokens;
  private final boolean replMode;

  private int current = 0;
  private int loopDepth = 0;
  private int functionDepth = 0;

  Parser(List<Token> tokens, boolean replMode) {
    this.tokens = tokens;
    this.replMode = replMode;
  }

  List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();

    while (!isAtEnd()) {
      statements.add(declaration());
    }

    return statements;
  }

  private Stmt declaration() {
    try {
      if (check(FUN) && checkNext(IDENTIFIER)) {
        advance();
        return functionDeclaration();
      }

      if (match(VAR)) {
        return varDeclaration();
      }

      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt functionDeclaration() {
    Token name = consume(
        IDENTIFIER,
        "Expect function name.");

    Expr.Function function = functionBody();

    return new Stmt.Function(
        name,
        function.params,
        function.body);
  }

  private Expr.Function functionBody() {
    consume(LEFT_PAREN, "Expect '(' before parameters.");

    List<Token> parameters = new ArrayList<>();

    if (!check(RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          error(peek(), "Can't have more than 255 parameters.");
        }

        parameters.add(
            consume(IDENTIFIER, "Expect parameter name."));
      } while (match(COMMA));
    }

    consume(RIGHT_PAREN, "Expect ')' after parameters.");
    consume(LEFT_BRACE, "Expect '{' before function body.");

    int enclosingLoopDepth = loopDepth;
    loopDepth = 0;
    functionDepth++;

    try {
      List<Stmt> body = block();
      return new Expr.Function(parameters, body);
    } finally {
      functionDepth--;
      loopDepth = enclosingLoopDepth;
    }
  }

  private Stmt varDeclaration() {
    Token name = consume(
        IDENTIFIER,
        "Expect variable name.");

    Expr initializer = null;

    if (match(EQUAL)) {
      initializer = expression();
    }

    consume(
        SEMICOLON,
        "Expect ';' after variable declaration.");

    return new Stmt.Var(name, initializer);
  }

  private Stmt statement() {
    if (match(BREAK)) {
      return breakStatement();
    }

    if (match(FOR)) {
      return forStatement();
    }

    if (match(IF)) {
      return ifStatement();
    }

    if (match(PRINT)) {
      return printStatement();
    }

    if (match(RETURN)) {
      return returnStatement();
    }

    if (match(WHILE)) {
      return whileStatement();
    }

    if (match(LEFT_BRACE)) {
      return new Stmt.Block(block());
    }

    return expressionStatement();
  }

  private Stmt breakStatement() {
    Token keyword = previous();

    if (loopDepth == 0) {
      throw error(
          keyword,
          "Can't use 'break' outside of a loop.");
    }

    consume(SEMICOLON, "Expect ';' after 'break'.");
    return new Stmt.Break(keyword);
  }

  private Stmt returnStatement() {
    Token keyword = previous();

    if (functionDepth == 0) {
      throw error(
          keyword,
          "Can't return from top-level code.");
    }

    Expr value = null;

    if (!check(SEMICOLON)) {
      value = expression();
    }

    consume(SEMICOLON, "Expect ';' after return value.");
    return new Stmt.Return(keyword, value);
  }

  private Stmt forStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'for'.");

    Stmt initializer;

    if (match(SEMICOLON)) {
      initializer = null;
    } else if (match(VAR)) {
      initializer = varDeclaration();
    } else {
      initializer = expressionStatement();
    }

    Expr condition = null;

    if (!check(SEMICOLON)) {
      condition = expression();
    }

    consume(SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = null;

    if (!check(RIGHT_PAREN)) {
      increment = expression();
    }

    consume(RIGHT_PAREN, "Expect ')' after for clauses.");

    Stmt body;
    loopDepth++;

    try {
      body = statement();
    } finally {
      loopDepth--;
    }

    if (increment != null) {
      body = new Stmt.Block(
          Arrays.asList(
              body,
              new Stmt.Expression(increment)));
    }

    if (condition == null) {
      condition = new Expr.Literal(true);
    }

    body = new Stmt.While(condition, body);

    if (initializer != null) {
      body = new Stmt.Block(
          Arrays.asList(initializer, body));
    }

    return body;
  }

  private Stmt ifStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'if'.");

    Expr condition = expression();

    consume(RIGHT_PAREN, "Expect ')' after if condition.");

    Stmt thenBranch = statement();
    Stmt elseBranch = null;

    if (match(ELSE)) {
      elseBranch = statement();
    }

    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  private Stmt printStatement() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  private Stmt whileStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'while'.");

    Expr condition = expression();

    consume(RIGHT_PAREN, "Expect ')' after condition.");

    Stmt body;
    loopDepth++;

    try {
      body = statement();
    } finally {
      loopDepth--;
    }

    return new Stmt.While(condition, body);
  }

  private Stmt expressionStatement() {
    Expr expr = expression();

    if (replMode && isAtEnd()) {
      return new Stmt.ReplExpression(expr);
    }

    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  private List<Stmt> block() {
    List<Stmt> statements = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }

    consume(RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  private Expr expression() {
    return comma();
  }

  private Expr comma() {
    Expr expr = assignment();

    while (match(COMMA)) {
      Token operator = previous();
      Expr right = assignment();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr assignment() {
    Expr expr = conditional();

    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable) expr).name;
        return new Expr.Assign(name, value);
      }

      error(equals, "Invalid assignment target.");
    }

    return expr;
  }

  private Expr conditional() {
    Expr expr = or();

    if (match(QUESTION)) {
      Expr thenBranch = expression();

      consume(COLON, "Expect ':' after conditional branch.");

      Expr elseBranch = conditional();

      expr = new Expr.Conditional(
          expr,
          thenBranch,
          elseBranch);
    }

    return expr;
  }

  private Expr or() {
    Expr expr = and();

    while (match(OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr and() {
    Expr expr = equality();

    while (match(AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr equality() {
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr comparison() {
    Expr expr = term();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr term() {
    Expr expr = factor();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr factor() {
    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }

    if (match(COMMA)) {
      Token operator = previous();
      missingLeftOperand(operator);
      assignment();
      return new Expr.Literal(null);
    }

    if (match(OR)) {
      Token operator = previous();
      missingLeftOperand(operator);
      and();
      return new Expr.Literal(null);
    }

    if (match(AND)) {
      Token operator = previous();
      missingLeftOperand(operator);
      equality();
      return new Expr.Literal(null);
    }

    if (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      missingLeftOperand(operator);
      comparison();
      return new Expr.Literal(null);
    }

    if (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      missingLeftOperand(operator);
      term();
      return new Expr.Literal(null);
    }

    if (match(PLUS)) {
      Token operator = previous();
      missingLeftOperand(operator);
      factor();
      return new Expr.Literal(null);
    }

    if (match(SLASH, STAR)) {
      Token operator = previous();
      missingLeftOperand(operator);
      unary();
      return new Expr.Literal(null);
    }

    return call();
  }

  private void missingLeftOperand(Token operator) {
    error(
        operator,
        "Missing left-hand operand before '"
            + operator.lexeme + "'.");
  }

  private Expr call() {
    Expr expr = primary();

    while (match(LEFT_PAREN)) {
      expr = finishCall(expr);
    }

    return expr;
  }

  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();

    if (!check(RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 255) {
          error(peek(), "Can't have more than 255 arguments.");
        }

        arguments.add(assignment());
      } while (match(COMMA));
    }

    Token paren = consume(
        RIGHT_PAREN,
        "Expect ')' after arguments.");

    return new Expr.Call(callee, paren, arguments);
  }

  private Expr primary() {
    if (match(FALSE)) {
      return new Expr.Literal(false);
    }

    if (match(TRUE)) {
      return new Expr.Literal(true);
    }

    if (match(NIL)) {
      return new Expr.Literal(null);
    }

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(FUN)) {
      return functionBody();
    }

    if (match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();

      consume(RIGHT_PAREN, "Expect ')' after expression.");

      return new Expr.Grouping(expr);
    }

    throw error(peek(), "Expect expression.");
  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }

    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) {
      return advance();
    }

    throw error(peek(), message);
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) {
      return false;
    }

    return peek().type == type;
  }

  private boolean checkNext(TokenType type) {
    if (current + 1 >= tokens.size()) {
      return false;
    }

    return tokens.get(current + 1).type == type;
  }

  private Token advance() {
    if (!isAtEnd()) {
      current++;
    }

    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token.line, message);
    return new ParseError();
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) {
        return;
      }

      switch (peek().type) {
        case BREAK:
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;

        default:
          break;
      }

      advance();
    }
  }
}