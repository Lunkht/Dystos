package com.vulsoft.dystos.jvm;

import com.vulsoft.dystos.ast.Ast.Pos;

/** Construction du langage pas encore prise en charge par le backend JVM (diagnostic {@code E0401}). */
public final class UnsupportedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient Pos pos;

    public UnsupportedException(String message, Pos pos) {
        super(message);
        this.pos = pos;
    }

    public UnsupportedException(String message) {
        this(message, null);
    }

    public Pos pos() {
        return pos;
    }

    UnsupportedException at(Pos p) {
        return pos != null || p == null ? this : new UnsupportedException(getMessage(), p);
    }
}
