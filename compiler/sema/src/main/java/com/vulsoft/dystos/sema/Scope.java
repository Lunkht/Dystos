package com.vulsoft.dystos.sema;

import com.vulsoft.dystos.sema.Symbols.Symbol;
import com.vulsoft.dystos.sema.Type.TypeVar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Portée lexicale : symboles, paramètres de type et imports « package.* ». */
public final class Scope {

    private final Scope parent;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private final Map<String, TypeVar> typeVars = new LinkedHashMap<>();
    private final List<String> starPackages = new ArrayList<>();

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public Scope parent() {
        return parent;
    }

    public Symbol lookup(String name) {
        for (Scope s = this; s != null; s = s.parent) {
            Symbol x = s.symbols.get(name);
            if (x != null) {
                return x;
            }
        }
        return null;
    }

    public Symbol lookupLocal(String name) {
        return symbols.get(name);
    }

    /** Retourne false si le nom existe déjà dans cette portée. */
    public boolean declare(Symbol s) {
        if (symbols.containsKey(s.name)) {
            return false;
        }
        symbols.put(s.name, s);
        return true;
    }

    /** Déclare un symbole sous un autre nom (import avec alias ou nom court d'un module). */
    public boolean declareAs(String name, Symbol s) {
        if (symbols.containsKey(name)) {
            return false;
        }
        symbols.put(name, s);
        return true;
    }

    public void declareTypeVar(TypeVar tv) {
        typeVars.put(tv.name(), tv.key());
    }

    public TypeVar lookupTypeVar(String name) {
        for (Scope s = this; s != null; s = s.parent) {
            TypeVar tv = s.typeVars.get(name);
            if (tv != null) {
                return tv;
            }
        }
        return null;
    }

    public void addStarPackage(String pkg) {
        starPackages.add(pkg);
    }

    public List<String> starPackages() {
        List<String> out = new ArrayList<>();
        for (Scope s = this; s != null; s = s.parent) {
            out.addAll(s.starPackages);
        }
        return out;
    }

    public Set<String> allNames() {
        Set<String> out = new LinkedHashSet<>();
        for (Scope s = this; s != null; s = s.parent) {
            out.addAll(s.symbols.keySet());
        }
        return out;
    }
}
