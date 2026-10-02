package com.craftinginterpreters.lox;

import java.util.List;


class LoxFunction implements LoxCallable {
  private final String name;
  private final List<Token> params;
  private final List<Stmt> body;
  private final Environment closure;

  LoxFunction(
      String name,
      List<Token> params,
      List<Stmt> body,
      Environment closure) {
    this.name = name;
    this.params = params;
    this.body = body;
    this.closure = closure;
  }

  @Override
  public int arity() {
    return params.size();
  }

  @Override
  public Object call(
      Interpreter interpreter,
      List<Object> arguments) {
    Environment environment = new Environment(closure);

    for (int i )
