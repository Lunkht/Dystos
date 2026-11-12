package dev.dystos.app.language;

/**
 * Erreur de compilation ou d'exécution, avec la ligne fautive.
 *
 * Le message est en français : c'est ce que l'apprenant lit.
 */
public final class DystosException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int line;

    public DystosException(String message) {
        this(message, 0);
    }

    public DystosException(String message, int line) {
        super(message);
        this.line = line;
    }

    /** Ligne fautive, ou 0 si l'erreur ne se rapporte pas à une ligne précise. */
    public int getLine() {
        return line;
    }
}