package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Resolver
    implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

  private enum FunctionType {
    NONE,
    FUNCTION
  }

  private static class Variable {
    final Token name;
    final int index;
    boolean defined;
    boolean used;

    Variable(Token name, int index) {
      this.name = name;
      this.index = index;
    }
  }

  private final Interpreter interpreter;
  private final List<Map<String, Variable>> scopes =
      new ArrayList<>();

  private FunctionType currentFunction = FunctionType.NONE;

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  private void resolve(Stmt statement) {
    statement.accept(this);
  }

  private void resolve(Expr expression) {
    expression.accept(this);
  }

  private void beginScope() {
    scopes.add(new HashMap<>());
  }

  private void endScope() {
    Map<String, Variable> scope =
        scopes.remove(scopes.size() - 1);

    for (Variable variable : scope.values()) {
      if (!variable.used) {
        Lox.error(
            variable.name.line,
            "Local variable '" + variable.name.lexeme
                + "' is never used.");
      }
    }
  }

  private void declare(Token name) {
    if (scopes.isEmpty()) {
      return;
    }

    Map<String, Variable> scope =
        scopes.get(scopes.size() - 1);

    if (scope.containsKey(name.lexeme)) {
      Lox.error(
          name.line,
          "Already a variable with this name in this scope.");
      return;
    }

    scope.put(
        name.lexeme,
        new Variable(name, scope.size()));
  }

  private void define(Token name) {
    if (scopes.isEmpty()) {
      return;
    }

    scopes.get(scopes.size() - 1)
        .get(name.lexeme).defined = true;
  }

  private void resolveLocal(
      Expr expression,
      Token name,
      boolean isRead) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      Variable variable = scopes.get(i).get(name.lexeme);

      if (variable != null) {
        if (isRead) {
          variable.used = true;
        }

        interpreter.resolve(
            expression,
            scopes.size() - 1 - i,
            variable.index);
        return;
      }
    }
  }

  private void resolveFunction(
      List<Token> params,
      List<Stmt> body) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = FunctionType.FUNCTION;

    beginScope();

    for (Token param : params) {
      declare(param);
      define(param);
    }

    resolve(body);
    endScope();

    currentFunction = enclosingFunction;
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();
    return null;
  }

  @Override
  public Void visitBreakStmt(Stmt.Break stmt) {
    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    declare(stmt.name);
    define(stmt.name);
    resolveFunction(stmt.params, stmt.body);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);

    if (stmt.elseBranch != null) {
      resolve(stmt.elseBranch);
    }

    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitReplExpressionStmt(
      Stmt.ReplExpression stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    if (currentFunction == FunctionType.NONE) {
      Lox.error(
          stmt.keyword.line,
          "Can't return from top-level code.");
    }

    if (stmt.value != null) {
      resolve(stmt.value);
    }

    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);

    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }

    define(stmt.name);
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    resolve(stmt.condition);
    resolve(stmt.body);
    return null;
  }

  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    resolve(expr.value);
    resolveLocal(expr, expr.name, false);
    return null;
  }

  @Override
  public Void visitBinaryExpr(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitCallExpr(Expr.Call expr) {
    resolve(expr.callee);

    for (Expr argument : expr.arguments) {
      resolve(argument);
    }

    return null;
  }

  @Override
  public Void visitConditionalExpr(
      Expr.Conditional expr) {
    resolve(expr.condition);
    resolve(expr.thenBranch);
    resolve(expr.elseBranch);
    return null;
  }

  @Override
  public Void visitFunctionExpr(Expr.Function expr) {
    resolveFunction(expr.params, expr.body);
    return null;
  }

  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    resolve(expr.expression);
    return null;
  }

  @Override
  public Void visitLiteralExpr(Expr.Literal expr) {
    return null;
  }

  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitVariableExpr(Expr.Variable expr) {
    if (!scopes.isEmpty()) {
      Variable variable = scopes.get(scopes.size() - 1)
          .get(expr.name.lexeme);

      if (variable != null && !variable.defined) {
        Lox.error(
            expr.name.line,
            "Can't read local variable in its own initializer.");
      }
    }

    resolveLocal(expr, expr.name, true);
    return null;
  }
}