package dev.dystos.app.language;

/** Interruption normale du flot : {@code return}, {@code break}, {@code continue}. */
final class ControlSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    enum Kind {
        RETURN,
        BREAK,
        CONTINUE
    }

    private final Kind kind;
    private final transient Object value;

    ControlSignal(Kind kind, Object value) {
        super(null, null, false, false);
        this.kind = kind;
        this.value = value;
    }

    static ControlSignal ret(Object value) {
        return new ControlSignal(Kind.RETURN, value);
    }

    static ControlSignal brk() {
        return new ControlSignal(Kind.BREAK, null);
    }

    static ControlSignal cont() {
        return new ControlSignal(Kind.CONTINUE, null);
    }

    Kind kind() {
        return kind;
    }

    Object value() {
        return value;
    }
}