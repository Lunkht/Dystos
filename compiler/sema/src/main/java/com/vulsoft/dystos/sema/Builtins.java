package com.vulsoft.dystos.sema;

import com.vulsoft.dystos.sema.Symbols.ClassKind;
import com.vulsoft.dystos.sema.Symbols.ClassSymbol;
import com.vulsoft.dystos.sema.Symbols.FuncGroup;
import com.vulsoft.dystos.sema.Symbols.FuncSymbol;
import com.vulsoft.dystos.sema.Symbols.ModuleSymbol;
import com.vulsoft.dystos.sema.Symbols.ParamSym;
import com.vulsoft.dystos.sema.Symbols.VarKind;
import com.vulsoft.dystos.sema.Symbols.VarSymbol;
import com.vulsoft.dystos.sema.Type.ClassType;
import com.vulsoft.dystos.sema.Type.FunctionType;
import com.vulsoft.dystos.sema.Type.TypeVar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Types, fonctions et modules prédéfinis (embryon de la bibliothèque standard : Phase 4).
 */
final class Builtins {

    private Builtins() {
    }

    private static ClassSymbol builtin(String name, String... typeParams) {
        ClassSymbol c = new ClassSymbol(name, ClassKind.BUILTIN);
        for (String t : typeParams) {
            c.typeParams.add(Types.freshVar(t));
        }
        return c;
    }

    static final ClassSymbol ANY = builtin("any");
    static final ClassSymbol INT = builtin("int");
    static final ClassSymbol FLOAT = builtin("float");
    static final ClassSymbol BOOL = builtin("bool");
    static final ClassSymbol STR = builtin("str");
    static final ClassSymbol LIST = builtin("list", "T");
    static final ClassSymbol MAP = builtin("map", "K", "V");
    static final ClassSymbol SET = builtin("set", "T");
    static final ClassSymbol ENTRY = builtin("Entry", "K", "V");
    static final ClassSymbol EXCEPTION = builtin("Exception");
    static final ClassSymbol VALUE_ERROR = builtin("ValueError");
    static final ClassSymbol RUNTIME_ERROR = builtin("RuntimeError");
    static final ClassSymbol FILE = builtin("File");
    static final ClassSymbol TASK = builtin("Task", "T");

    static final ClassType ANY_T = new ClassType(ANY, List.of(), false);
    static final ClassType ANY_N = new ClassType(ANY, List.of(), true);
    static final ClassType INT_T = new ClassType(INT, List.of(), false);
    static final ClassType FLOAT_T = new ClassType(FLOAT, List.of(), false);
    static final ClassType BOOL_T = new ClassType(BOOL, List.of(), false);
    static final ClassType STR_T = new ClassType(STR, List.of(), false);
    static final ClassType EXCEPTION_T = new ClassType(EXCEPTION, List.of(), false);

    /** Classes qu'on ne peut pas étendre. */
    static final Set<ClassSymbol> FINAL_CLASSES = Set.of(ANY, INT, FLOAT, BOOL, STR, LIST, MAP, SET, ENTRY);

    private static final Map<String, FuncGroup> FUNCTIONS = new LinkedHashMap<>();
    private static final Map<String, ModuleSymbol> MODULES = new LinkedHashMap<>();

    // ------------------------------------------------------------------
    // Aides de construction
    // ------------------------------------------------------------------

    static ClassType ty(ClassSymbol c, Type... args) {
        return new ClassType(c, List.of(args), false);
    }

    private static List<ParamSym> ps(Type... types) {
        List<ParamSym> out = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            out.add(new ParamSym("p" + i, types[i], false));
        }
        return out;
    }

    private static FuncSymbol method(ClassSymbol owner, String name, Type ret, Type... params) {
        FuncSymbol f = new FuncSymbol(name);
        f.owner = owner;
        f.params.addAll(ps(params));
        f.returnType = ret;
        owner.addMethod(f);
        return f;
    }

    private static void field(ClassSymbol owner, String name, Type t) {
        VarSymbol v = new VarSymbol(name, t, false, VarKind.PROPERTY);
        v.owner = owner;
        owner.fields.put(name, v);
    }

    private static void ctor(ClassSymbol owner, Type... params) {
        FuncSymbol f = new FuncSymbol("init");
        f.owner = owner;
        f.isConstructor = true;
        f.params.addAll(ps(params));
        owner.constructors.add(f);
    }

    private static FuncSymbol function(String name, Type ret, Type... params) {
        FuncSymbol f = new FuncSymbol(name);
        f.params.addAll(ps(params));
        f.returnType = ret;
        FUNCTIONS.computeIfAbsent(name, FuncGroup::new).funcs.add(f);
        return f;
    }

    // ------------------------------------------------------------------
    // Définitions
    // ------------------------------------------------------------------

    static {
        for (ClassSymbol c : List.of(INT, FLOAT, BOOL, STR, LIST, MAP, SET, ENTRY, EXCEPTION, FILE, TASK)) {
            c.superType = ANY_T;
        }
        VALUE_ERROR.superType = EXCEPTION_T;
        RUNTIME_ERROR.superType = EXCEPTION_T;

        method(ANY, "toString", STR_T);

        // str
        field(STR, "length", INT_T);
        method(STR, "upper", STR_T);
        method(STR, "lower", STR_T);
        method(STR, "strip", STR_T);
        method(STR, "split", ty(LIST, STR_T), STR_T);
        method(STR, "join", STR_T, ty(LIST, STR_T));
        method(STR, "startswith", BOOL_T, STR_T);
        method(STR, "endswith", BOOL_T, STR_T);
        method(STR, "contains", BOOL_T, STR_T);
        method(STR, "replace", STR_T, STR_T, STR_T);
        method(STR, "lines", ty(LIST, STR_T));

        // list[T]
        TypeVar lt = LIST.typeParams.get(0);
        method(LIST, "add", Type.UNIT, lt);
        method(LIST, "contains", BOOL_T, lt);
        method(LIST, "pop", lt);
        method(LIST, "clear", Type.UNIT);
        method(LIST, "extend", Type.UNIT, ty(LIST, lt));

        // map[K, V]
        TypeVar mk = MAP.typeParams.get(0);
        TypeVar mv = MAP.typeParams.get(1);
        method(MAP, "items", ty(LIST, ty(ENTRY, mk, mv)));
        method(MAP, "keys", ty(LIST, mk));
        method(MAP, "values", ty(LIST, mv));
        method(MAP, "get", mv.withNullable(true), mk);
        method(MAP, "contains", BOOL_T, mk);
        method(MAP, "remove", Type.UNIT, mk);

        // set[T]
        TypeVar st = SET.typeParams.get(0);
        method(SET, "add", Type.UNIT, st);
        method(SET, "contains", BOOL_T, st);
        method(SET, "remove", Type.UNIT, st);

        // Entry[K, V]
        field(ENTRY, "key", ENTRY.typeParams.get(0));
        field(ENTRY, "value", ENTRY.typeParams.get(1));

        // Exceptions
        ctor(EXCEPTION, STR_T);
        ctor(EXCEPTION);
        field(EXCEPTION, "message", STR_T);

        // File, Task
        method(FILE, "write", Type.UNIT, STR_T);
        method(FILE, "read", STR_T);
        method(FILE, "lines", ty(LIST, STR_T));
        method(FILE, "close", Type.UNIT);
        method(TASK, "await", TASK.typeParams.get(0));

        // Fonctions globales
        FuncSymbol print = function("print", Type.UNIT, ANY_N);
        print.variadic = true;
        function("len", INT_T, ANY_T);
        function("range", ty(LIST, INT_T), INT_T);
        function("range", ty(LIST, INT_T), INT_T, INT_T);
        function("range", ty(LIST, INT_T), INT_T, INT_T, INT_T);
        function("sum", INT_T, ty(LIST, INT_T));
        function("sum", FLOAT_T, ty(LIST, FLOAT_T));
        function("abs", INT_T, INT_T);
        function("abs", FLOAT_T, FLOAT_T);
        function("min", INT_T, INT_T, INT_T);
        function("min", FLOAT_T, FLOAT_T, FLOAT_T);
        function("max", INT_T, INT_T, INT_T);
        function("max", FLOAT_T, FLOAT_T, FLOAT_T);

        // sorted[T, K](items: list[T], key: (T) -> K = ...) -> list[T]
        FuncSymbol sorted = new FuncSymbol("sorted");
        TypeVar sT = Types.freshVar("T");
        TypeVar sK = Types.freshVar("K");
        sorted.typeParams.add(sT);
        sorted.typeParams.add(sK);
        sorted.params.add(new ParamSym("items", ty(LIST, sT), false));
        sorted.params.add(new ParamSym("key", new FunctionType(List.of(sT), sK, false), true));
        sorted.returnType = ty(LIST, sT);
        FUNCTIONS.computeIfAbsent("sorted", FuncGroup::new).funcs.add(sorted);

        // filter[T](pred: (T) -> bool, items: list[T]) -> list[T]
        FuncSymbol filter = new FuncSymbol("filter");
        TypeVar fT = Types.freshVar("T");
        filter.typeParams.add(fT);
        filter.params.add(new ParamSym("pred", new FunctionType(List.of(fT), BOOL_T, false), false));
        filter.params.add(new ParamSym("items", ty(LIST, fT), false));
        filter.returnType = ty(LIST, fT);
        FUNCTIONS.computeIfAbsent("filter", FuncGroup::new).funcs.add(filter);

        // enumerate[T](items: list[T]) -> list[Entry[int, T]]
        FuncSymbol enumerate = new FuncSymbol("enumerate");
        TypeVar eT = Types.freshVar("T");
        enumerate.typeParams.add(eT);
        enumerate.params.add(new ParamSym("items", ty(LIST, eT), false));
        enumerate.returnType = ty(LIST, ty(ENTRY, INT_T, eT));
        FUNCTIONS.computeIfAbsent("enumerate", FuncGroup::new).funcs.add(enumerate);

        // Module io
        ModuleSymbol io = new ModuleSymbol("io", true);
        FuncSymbol open = new FuncSymbol("open");
        open.params.add(new ParamSym("path", STR_T, false));
        open.params.add(new ParamSym("mode", STR_T, true));
        open.returnType = ty(FILE);
        FuncGroup openGroup = new FuncGroup("open");
        openGroup.funcs.add(open);
        io.members.put("open", openGroup);
        MODULES.put("io", io);

        // Module concurrent
        ModuleSymbol conc = new ModuleSymbol("concurrent", true);
        FuncSymbol run = new FuncSymbol("run");
        TypeVar rT = Types.freshVar("T");
        run.typeParams.add(rT);
        run.params.add(new ParamSym("task", new FunctionType(List.of(), rT, false), false));
        run.returnType = ty(TASK, rT);
        FuncGroup runGroup = new FuncGroup("run");
        runGroup.funcs.add(run);
        conc.members.put("run", runGroup);
        MODULES.put("concurrent", conc);
    }

    static ModuleSymbol module(String name) {
        return MODULES.get(name);
    }

    /** Portée globale : types et fonctions prédéfinis. */
    static Scope globalScope() {
        Scope g = new Scope(null);
        for (ClassSymbol c : List.of(ANY, INT, FLOAT, BOOL, STR, LIST, MAP, SET, ENTRY,
                EXCEPTION, VALUE_ERROR, RUNTIME_ERROR)) {
            g.declare(c);
        }
        for (FuncGroup fg : FUNCTIONS.values()) {
            g.declare(fg);
        }
        return g;
    }
}
