package com.vulsoft.dystos.jvm;

import com.vulsoft.dystos.sema.Symbols.FuncSymbol;
import com.vulsoft.dystos.sema.Symbols.VarSymbol;
import com.vulsoft.dystos.sema.Type;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Correspondance entre fonctions/méthodes prédéfinies de Dystos et méthodes statiques de {@code dystos.rt.Rt}. */
final class RuntimeMap {

    /** Méthode statique de {@code Rt} à appeler. */
    record RtCall(String name, String desc) {
    }

    private static final Set<String> AVAILABLE = new HashSet<>();

    static {
        try {
            for (Method m : Class.forName("dystos.rt.Rt").getMethods()) {
                if (Modifier.isStatic(m.getModifiers())) {
                    AVAILABLE.add(m.getName() + MethodType.methodType(m.getReturnType(), m.getParameterTypes())
                            .toMethodDescriptorString());
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("bibliothèque d'exécution dystos.rt introuvable", e);
        }
    }

    private RuntimeMap() {
    }

    static RtCall require(String name, String desc) {
        if (!AVAILABLE.contains(name + desc)) {
            throw new UnsupportedException("fonction de la bibliothèque d'exécution manquante : Rt." + name + desc);
        }
        return new RtCall(name, desc);
    }

    /** Fonction prédéfinie ({@code len}, {@code range}...) appelée avec {@code argc} arguments. */
    static RtCall function(FuncSymbol f, int argc) {
        String rtName;
        switch (f.name) {
            case "print": rtName = "print"; break;
            case "len": rtName = "len"; break;
            case "range": rtName = "range" + argc; break;
            case "sum": rtName = Jt.isFloat(f.returnType) ? "sumFloat" : "sumInt"; break;
            case "abs": rtName = Jt.isFloat(f.returnType) ? "absFloat" : "absInt"; break;
            case "min": rtName = Jt.isFloat(f.returnType) ? "minFloat" : "minInt"; break;
            case "max": rtName = Jt.isFloat(f.returnType) ? "maxFloat" : "maxInt"; break;
            case "sorted": rtName = argc == 1 ? "sorted1" : "sorted2"; break;
            case "filter": rtName = "filter"; break;
            case "open": rtName = "io_open"; break;
            case "run": rtName = "concurrent_run"; break;
            case "enumerate": rtName = "enumerate"; break;
            default:
                throw new UnsupportedException("la fonction prédéfinie '" + f.name + "' n'est pas encore supportée par le backend JVM");
        }
        if (f.name.equals("print")) {
            return require("print", "([Ljava/lang/Object;)V");
        }
        List<Type> ps = new ArrayList<>();
        for (int i = 0; i < argc; i++) {
            ps.add(f.params.get(i).type());
        }
        return require(rtName, Jt.methodDesc(ps, f.returnType));
    }

    /** Méthode d'une classe prédéfinie ({@code str.upper}, {@code list.add}...) : le récepteur devient le 1er argument. */
    static RtCall method(FuncSymbol f) {
        String owner = f.owner.name;
        if (owner.equals("any") && f.name.equals("toString")) {
            return require("str", "(Ljava/lang/Object;)Ljava/lang/String;");
        }
        if (!List.of("str", "list", "map", "set", "File", "Task").contains(owner)) {
            throw new UnsupportedException("la méthode " + owner + "." + f.name + " n'est pas encore supportée par le backend JVM");
        }
        StringBuilder sb = new StringBuilder("(").append("L").append(Jt.internalName(f.owner)).append(';');
        for (var p : f.params) {
            sb.append(Jt.desc(p.type()));
        }
        String desc = sb.append(')').append(Jt.desc(f.returnType)).toString();
        return require(owner + "_" + f.name, desc);
    }

    /** Propriété d'une classe prédéfinie lue par une méthode de {@code Rt} (ex. {@code str.length}). */
    static RtCall property(VarSymbol v) {
        String owner = v.owner.name;
        if (owner.equals("str") && v.name.equals("length")) {
            return require("str_length", "(Ljava/lang/String;)J");
        }
        throw new UnsupportedException("la propriété " + owner + "." + v.name + " n'est pas encore supportée par le backend JVM");
    }
}
