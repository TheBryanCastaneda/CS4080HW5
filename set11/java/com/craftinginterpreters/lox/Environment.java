package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class Environment {
  private static final Object UNINITIALIZED = new Object();

  final Environment enclosing;

  private final Map<String, Object> values = new HashMap<>();
  private Object[] locals = new Object[8];
  private int localCount = 0;

  Environment() {
    enclosing = null;
  }

  Environment(Environment enclosing) {
    this.enclosing = enclosing;
  }

  void define(String name, Object value) {
    values.put(name, value);
  }

  void defineUninitialized(String name) {
    values.put(name, UNINITIALIZED);
  }

  void defineLocal(Object value) {
    if (localCount == locals.length) {
      locals = Arrays.copyOf(locals, locals.length * 2);
    }

    locals[localCount] = value;
    localCount++;
  }

  void defineLocalUninitialized() {
    defineLocal(UNINITIALIZED);
  }

  Object get(Token name) {
    if (values.containsKey(name.lexeme)) {
      return checkedValue(name, values.get(name.lexeme));
    }

    if (enclosing != null) {
      return enclosing.get(name);
    }

    throw new RuntimeError(
        name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  void assign(Token name, Object value) {
    if (values.containsKey(name.lexeme)) {
      values.put(name.lexeme, value);
      return;
    }

    if (enclosing != null) {
      enclosing.assign(name, value);
      return;
    }

    throw new RuntimeError(
        name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  Object getAt(int distance, int index, Token name) {
    Environment target = ancestor(distance);
    return checkedValue(name, target.locals[index]);
  }

  void assignAt(int distance, int index, Object value) {
    Environment target = ancestor(distance);
    target.locals[index] = value;
  }

  private Environment ancestor(int distance) {
    Environment environment = this;

    for (int i = 0; i < distance; i++) {
      environment = environment.enclosing;
    }

    return environment;
  }

  private Object checkedValue(Token name, Object value) {
    if (value == UNINITIALIZED) {
      throw new RuntimeError(
          name,
          "Variable '" + name.lexeme
              + "' has not been initialized.");
    }

    return value;
  }
}