package com.vulsoft.dystos.sema;

import com.vulsoft.dystos.ast.Ast;
import com.vulsoft.dystos.sema.Type.ClassType;
import com.vulsoft.dystos.sema.Type.TypeVar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Symboles : variables, fonctions, classes, modules. */
public final class Symbols {

    private Symbols() {
    }

    /** Un nom commençant par « _ » (et plus long que « _ » seul) est privé. */
    public static boolean isPrivateName(String n) {
        return n.length() > 1 && n.charAt(0) == '_';
    }

    public abstract static class Symbol {
        public final String name;

        protected Symbol(String name) {
            this.name = name;
        }
    }

    public enum VarKind { LOCAL, PARAM, FIELD, PROPERTY, ENUM_CONSTANT }

    public static final class VarSymbol extends Symbol {
        public Type type;
        public boolean mutable;
        public final VarKind kind;
        public ClassSymbol owner;
        public boolean isStatic;
        /** Pour une propriété issue d'un accesseur Java : nom de la méthode getX()/isX(). */
        public String javaGetter;
        /** Accesseur ({@link java.lang.reflect.Method}) ou champ ({@link java.lang.reflect.Field}) Java réel. */
        public java.lang.reflect.Member javaMember;

        public VarSymbol(String name, Type type, boolean mutable, VarKind kind) {
            super(name);
            this.type = type;
            this.mutable = mutable;
            this.kind = kind;
        }
    }

    public record ParamSym(String name, Type type, boolean hasDefault) {
    }

    public static final class FuncSymbol extends Symbol {
        public final List<TypeVar> typeParams = new ArrayList<>();
        public final List<ParamSym> params = new ArrayList<>();
        public Type returnType = Type.UNIT;
        public ClassSymbol owner;
        public ModuleSymbol module;
        public boolean isStatic;
        public boolean isAbstract;
        public boolean isConstructor;
        public boolean variadic;
        public boolean isTest;
        /** Constructeur ou méthode Java réel (interopérabilité), pour que le backend calcule le descripteur exact. */
        public java.lang.reflect.Executable javaMember;
        /** Nombre de paramètres de l'AST qui ne figurent pas dans {@code params} (1 pour « self »). */
        public int astOffset;
        public Ast.FuncDecl decl;

        public FuncSymbol(String name) {
            super(name);
        }
    }

    /** Fonctions portant le même nom (surcharges, pour les fonctions prédéfinies et Java). */
    public static final class FuncGroup extends Symbol {
        public final List<FuncSymbol> funcs = new ArrayList<>();

        public FuncGroup(String name) {
            super(name);
        }
    }

    public enum ClassKind { CLASS, INTERFACE, ENUM, BUILTIN, JAVA }

    public static final class ClassSymbol extends Symbol {
        public final ClassKind kind;
        public String qualifiedName;
        public boolean isAbstract;
        public boolean javaInterface;
        public final List<TypeVar> typeParams = new ArrayList<>();
        public ClassType superType;
        public final List<ClassType> interfaces = new ArrayList<>();
        public final Map<String, VarSymbol> fields = new LinkedHashMap<>();
        public final Map<String, List<FuncSymbol>> methods = new LinkedHashMap<>();
        public final List<FuncSymbol> constructors = new ArrayList<>();
        public final List<String> enumConstants = new ArrayList<>();
        public ModuleSymbol module;
        public Object decl;
        /** Chargement différé des membres (classes Java). */
        public Runnable lazyLoader;

        public ClassSymbol(String name, ClassKind kind) {
            super(name);
            this.kind = kind;
            this.qualifiedName = name;
        }

        public void ensureLoaded() {
            if (lazyLoader != null) {
                Runnable r = lazyLoader;
                lazyLoader = null;
                r.run();
            }
        }

        public ClassType thisType() {
            return new ClassType(this, new ArrayList<Type>(typeParams), false);
        }

        public boolean isInterfaceLike() {
            return kind == ClassKind.INTERFACE || javaInterface;
        }

        public void addMethod(FuncSymbol f) {
            methods.computeIfAbsent(f.name, k -> new ArrayList<>()).add(f);
        }
    }

    public static final class ModuleSymbol extends Symbol {
        public final Map<String, Symbol> members = new LinkedHashMap<>();
        public final boolean builtin;

        public ModuleSymbol(String name, boolean builtin) {
            super(name);
            this.builtin = builtin;
        }
    }
}
