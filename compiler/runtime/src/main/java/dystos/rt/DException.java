package dystos.rt;

/** Exception Dystos de base ({@code Exception}). Le champ {@code message} est lu par {@code e.message}. */
public class DException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public String message;

    public DException() {
        this("");
    }

    public DException(String message) {
        super(null, null, false, true);   // pile conservée : sert à retrouver la ligne source de l'erreur
        this.message = message == null ? "" : message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    /** Nom affiché dans « Erreur non rattrapée ». */
    public String typeName() {
        return getClass() == DException.class ? "Exception" : getClass().getSimpleName();
    }

    @Override
    public String toString() {
        return message;
    }
}
