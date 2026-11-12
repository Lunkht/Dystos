package dev.dystos.app.language;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Portée : les variables locales d'un bloc ou d'une fonction. */
final class Scope {

    private final Scope parent;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, Boolean> mutability = new LinkedHashMap<>();
    private final Map<String, String> declaredTypes = new LinkedHashMap<>();

    Scope(Scope parent) {
        this.parent = parent;
    }

    boolean has(String name) {
        if (values.containsKey(name)) {
            return true;
        }
        return parent != null && parent.has(name);
    }

    Object get(String name) {
        if (values.containsKey(name)) {
            return values.get(name);
        }
        if (parent != null) {
            return parent.get(name);
        }
        throw new DystosException("variable inconnue : '" + name + "'");
    }

    String getDeclaredType(String name) {
        if (declaredTypes.containsKey(name)) {
            return declaredTypes.get(name);
        }
        return parent == null ? null : parent.getDeclaredType(name);
    }

    boolean isMutable(String name) {
        if (mutability.containsKey(name)) {
            return Boolean.TRUE.equals(mutability.get(name));
        }
        return parent != null && parent.isMutable(name);
    }

    boolean isLocal(String name) {
        return values.containsKey(name);
    }

    void declare(String name, Object value, boolean mutable, String declaredType) {
        values.put(name, value);
        mutability.put(name, mutable);
        declaredTypes.put(name, declaredType);
    }

    void assign(String name, Object value) {
        if (values.containsKey(name)) {
            values.put(name, value);
            return;
        }
        if (parent != null && parent.has(name)) {
            parent.assign(name, value);
            return;
        }
        throw new DystosException(
                "affectation à une variable inconnue : '" + name + "'");
    }

    /** Copie les déclarations de {@code other} dans cette portée. */
    void copyFrom(Scope other) {
        values.putAll(other.values);
        mutability.putAll(other.mutability);
        declaredTypes.putAll(other.declaredTypes);
    }

    /** Noms déclarés, dans l'ordre, pour l'affichage. */
    List<String> names() {
        return new ArrayList<>(values.keySet());
    }

    Scope parent() {
        return parent;
    }
}