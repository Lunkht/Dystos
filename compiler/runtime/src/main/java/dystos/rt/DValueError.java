package dystos.rt;

public class DValueError extends DException {
    private static final long serialVersionUID = 1L;

    public DValueError() {
        super();
    }

    public DValueError(String message) {
        super(message);
    }

    @Override
    public String typeName() {
        return "ValueError";
    }
}
