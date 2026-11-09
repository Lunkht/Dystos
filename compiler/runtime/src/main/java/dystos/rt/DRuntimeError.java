package dystos.rt;

public class DRuntimeError extends DException {
    private static final long serialVersionUID = 1L;

    public DRuntimeError() {
        super();
    }

    public DRuntimeError(String message) {
        super(message);
    }

    @Override
    public String typeName() {
        return "RuntimeError";
    }
}
