package com.vulsoft.dystos.interp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Environnement d'exécution : variables d'une portée et lien vers la portée englobante. */
final class Env {

    /** Variable. {@code isFloat} : les entiers affectés sont convertis en flottants (type déclaré ou inféré float). */
    static final class Slot {
        Object value;
        boolean isFloat;
    }

    final Env parent;
    final Map<String, Slot> vars = new HashMap<>();
    /** Classe de la méthode en cours (pour super()), sur l'environnement d'appel. */
    Values.ClassDef cls;
    /** Imports « package.* » (environnement de module). */
    List<String> starPackages;

    Env(Env parent) {
        this.parent = parent;
    }

    Slot find(String name) {
        for (Env e = this; e != null; e = e.parent) {
            Slot s = e.vars.get(name);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    Slot define(String name, Object value, boolean isFloat) {
        Slot s = new Slot();
        s.value = value;
        s.isFloat = isFloat;
        vars.put(name, s);
        return s;
    }

    Values.ClassDef frameClass() {
        for (Env e = this; e != null; e = e.parent) {
            if (e.cls != null) {
                return e.cls;
            }
        }
        return null;
    }

    List<String> allStarPackages() {
        List<String> out = new ArrayList<>();
        for (Env e = this; e != null; e = e.parent) {
            if (e.starPackages != null) {
                out.addAll(e.starPackages);
            }
        }
        return out;
    }
}
