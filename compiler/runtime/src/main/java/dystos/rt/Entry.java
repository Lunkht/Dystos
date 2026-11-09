package dystos.rt;

/** Paire clé/valeur : éléments de {@code map.items()} et de {@code enumerate()}. */
public final class Entry {
    public final Object key;
    public final Object value;

    public Entry(Object key, Object value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Entry(" + Rt.reprQuoted(key) + ", " + Rt.reprQuoted(value) + ")";
    }
}
