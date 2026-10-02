package com.craftinginterpreters.lox;

class RuntimeError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    final Token token;

    RuntimeError(Token token, String message) {
        super(message);
        this.token = token;
    }
}
