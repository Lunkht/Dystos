package dystos.rt;

/** Base des énumérations Dystos : {@code Couleur.ROUGE}, {@code .name}, {@code .ordinal}. */
public class DEnum extends DObject {
    public final String name;
    public final long ordinal;
    private final String type;

    public DEnum(String type, String name, long ordinal) {
        this.type = type;
        this.name = name;
        this.ordinal = ordinal;
    }

    @Override
    public String toString() {
        return type + "." + name;
    }
}
