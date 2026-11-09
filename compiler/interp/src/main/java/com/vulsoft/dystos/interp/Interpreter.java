package com.vulsoft.dystos.interp;

import com.vulsoft.dystos.ast.Ast.*;
import com.vulsoft.dystos.interp.Values.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Interpréteur d'AST de Dystos (arbre déjà validé par l'analyse sémantique). */
final class Interpreter {

    // ---- signaux de contrôle et erreurs ---------------------------------

    static final class ReturnSignal extends RuntimeException {
        final transient Object value;

        ReturnSignal(Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    static final class BreakSignal extends RuntimeException {
        BreakSignal() {
            super(null, null, false, false);
        }
    }

    static final class ContinueSignal extends RuntimeException {
        ContinueSignal() {
            super(null, null, false, false);
        }
    }

    /** Exception Dystos en cours de propagation. */
    static final class DystosThrow extends RuntimeException {
        final transient DObject value;
        final int line;

        DystosThrow(DObject value, int line) {
            super(null, null, false, false);
            this.value = value;
            this.line = line;
        }
    }

    /** Dépassement d'une limite (instructions, temps, sortie) : non rattrapable. */
    static final class LimitExceeded extends RuntimeException {
        LimitExceeded(String message) {
            super(message, null, false, false);
        }
    }

    private static final BreakSignal BREAK = new BreakSignal();
    private static final ContinueSignal CONTINUE = new ContinueSignal();
    private static final Map<String, Object> NO_NAMED = Map.of();

    static final class ModuleRt {
        final String name;
        final SourceFile file;
        final Env env = new Env(null);
        boolean loaded;
        final List<ClassDef> classes = new ArrayList<>();
        final List<Object> classDecls = new ArrayList<>();

        ModuleRt(String name, SourceFile file) {
            this.name = name;
            this.file = file;
        }
    }

    // ---- état ---------------------------------------------------------

    final Runner.Options opts;
    final StringBuilder out = new StringBuilder();
    final JavaBridge java;
    final Map<String, ModuleRt> modules = new LinkedHashMap<>();
    private final Map<String, Object> builtins = new LinkedHashMap<>();
    final ClassDef exceptionClass = new ClassDef("Exception");
    final ClassDef valueErrorClass = new ClassDef("ValueError");
    final ClassDef runtimeErrorClass = new ClassDef("RuntimeError");
    private final long deadline;
    private long steps;
    private int depth;
    private Pos curPos = new Pos(1, 1);

    Interpreter(Runner.Options opts) {
        this.opts = opts;
        this.java = new JavaBridge(opts.javaPrefixes(), opts.javaDenied());
        this.deadline = System.nanoTime() + opts.timeoutMillis() * 1_000_000L;
        for (ClassDef c : List.of(exceptionClass, valueErrorClass, runtimeErrorClass)) {
            c.builtin = true;
            builtins.put(c.name, c);
        }
        valueErrorClass.superclass = exceptionClass;
        runtimeErrorClass.superclass = exceptionClass;
        for (String f : List.of("print", "len", "range", "sum", "sorted", "filter", "enumerate", "abs", "min", "max",
                "str", "int", "float", "bool")) {
            builtins.put(f, new BuiltinFn(f));
        }
    }

    // ------------------------------------------------------------------
    // Chargement des modules
    // ------------------------------------------------------------------

    void load(List<SourceFileUnit> units) {
        for (SourceFileUnit u : units) {
            modules.put(u.module(), new ModuleRt(u.module(), u.file()));
        }
        for (ModuleRt m : modules.values()) {
            declare(m);
        }
        for (ModuleRt m : modules.values()) {
            bindImports(m);
        }
        for (ModuleRt m : modules.values()) {
            for (int i = 0; i < m.classes.size(); i++) {
                link(m, m.classes.get(i), m.classDecls.get(i));
            }
        }
    }

    record SourceFileUnit(String module, SourceFile file) {
    }

    private void declare(ModuleRt m) {
        for (TopLevel d : m.file.decls()) {
            if (d instanceof FuncDecl f) {
                m.env.define(f.name(), new FuncValue(f, m.env, null), false);
                continue;
            }
            ClassDef cd;
            if (d instanceof ClassDecl c) {
                cd = new ClassDef(c.name());
                cd.isAbstract = c.modifiers().contains("abstract");
            } else if (d instanceof InterfaceDecl i) {
                cd = new ClassDef(i.name());
                cd.isInterface = true;
                cd.isAbstract = true;
            } else {
                EnumDecl e = (EnumDecl) d;
                cd = new ClassDef(e.name());
                cd.isEnum = true;
            }
            cd.env = m.env;
            m.env.define(cd.name, cd, false);
            m.classes.add(cd);
            m.classDecls.add(d);
        }
    }

    private void bindImports(ModuleRt m) {
        for (ImportDecl imp : m.file.imports()) {
            String path = imp.path();
            if (path.endsWith(".*")) {
                if (m.env.starPackages == null) {
                    m.env.starPackages = new ArrayList<>();
                }
                m.env.starPackages.add(path.substring(0, path.length() - 2));
                continue;
            }
            Object target;
            ModuleRt dep = modules.get(path);
            if (dep != null) {
                target = new ModuleValue(path, dep.env, null);
            } else if (path.equals("io")) {
                target = new ModuleValue("io", null, Natives.ioModule(this));
            } else if (path.equals("concurrent")) {
                target = new ModuleValue("concurrent", null, Natives.concurrentModule(this));
            } else {
                Class<?> c = java.loadClass(path);
                if (c == null) {
                    throw new IllegalStateException("import introuvable : " + path);
                }
                target = new JavaClassRef(c);
            }
            String name = imp.alias() != null ? imp.alias() : path.substring(path.lastIndexOf('.') + 1);
            m.env.define(name, target, false);
        }
    }

    private void link(ModuleRt m, ClassDef cd, Object decl) {
        List<Member> members;
        if (decl instanceof ClassDecl c) {
            linkSupers(cd, c.supers(), m.env);
            members = c.members();
        } else if (decl instanceof InterfaceDecl i) {
            linkSupers(cd, i.supers(), m.env);
            members = i.members();
        } else {
            EnumDecl e = (EnumDecl) decl;
            members = e.members();
            int ord = 0;
            for (EnumConstant k : e.constants()) {
                DObject o = new DObject(cd);
                o.fields.put("name", k.name());
                o.fields.put("ordinal", (long) ord++);
                cd.enumConstants.put(k.name(), o);
            }
        }
        for (Member mem : members) {
            if (mem instanceof FuncDecl f) {
                FuncValue fv = new FuncValue(f, m.env, cd);
                boolean isStatic = f.decorators().stream().anyMatch(d -> d.name().equals("static"));
                if (isStatic) {
                    cd.statics.put(f.name(), fv);
                } else if (f.name().equals("init")) {
                    cd.ctor = fv;
                } else {
                    cd.methods.put(f.name(), fv);
                }
            } else if (mem instanceof VarDecl v) {
                cd.fields.add(v);
                if (isFloatType(v.type())) {
                    cd.floatFields.add(v.name());
                }
            }
        }
    }

    private void linkSupers(ClassDef cd, List<TypeRef> supers, Env env) {
        for (TypeRef t : supers) {
            Object o = resolveTypeName(t, env);
            if (!(o instanceof ClassDef sc)) {
                throw new IllegalStateException("héritage d'un type non supporté à l'exécution : " + ((NamedType) t).name());
            }
            if (sc.isInterface) {
                cd.interfaces.add(sc);
            } else {
                cd.superclass = sc;
            }
        }
    }

    private Object resolveTypeName(TypeRef t, Env env) {
        if (!(t instanceof NamedType nt)) {
            return null;
        }
        String[] parts = nt.name().split("\\.");
        Object cur = lookupName(parts[0], env);
        for (int i = 1; i < parts.length; i++) {
            cur = getMember(cur, parts[i]);
        }
        return cur;
    }

    // ------------------------------------------------------------------
    // Exécution d'un point d'entrée
    // ------------------------------------------------------------------

    void runModule(ModuleRt m) {
        if (m.loaded) {
            return;
        }
        m.loaded = true;
        for (ImportDecl imp : m.file.imports()) {
            ModuleRt dep = modules.get(imp.path());
            if (dep != null) {
                runModule(dep);
            }
        }
        execBlock(m.file.statements(), m.env);
    }

    void callMain(ModuleRt entry) {
        Env.Slot s = entry.env.vars.get("main");
        if (s != null && s.value instanceof FuncValue f) {
            callFunc(f, null, new ArrayList<>(), NO_NAMED);
        }
    }

    int currentLine() {
        return curPos.line();
    }

    // ------------------------------------------------------------------
    // Limites et erreurs
    // ------------------------------------------------------------------

    void tick() {
        if (++steps > opts.maxSteps()) {
            throw new LimitExceeded("limite d'instructions dépassée (" + opts.maxSteps() + ")");
        }
        if ((steps & 0x3FF) == 0) {
            if (System.nanoTime() > deadline) {
                throw new LimitExceeded("durée d'exécution dépassée (" + opts.timeoutMillis() + " ms)");
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new LimitExceeded("exécution interrompue");
            }
        }
    }

    void print(String s) {
        out.append(s);
        if (out.length() > opts.maxOutputChars()) {
            out.setLength((int) opts.maxOutputChars());
            throw new LimitExceeded("sortie trop volumineuse (limite : " + opts.maxOutputChars() + " caractères)");
        }
    }

    DObject newError(ClassDef cls, String message) {
        DObject o = new DObject(cls);
        o.fields.put("message", message);
        return o;
    }

    DystosThrow rt(String message) {
        return new DystosThrow(newError(runtimeErrorClass, message), curPos.line());
    }

    DystosThrow valueError(String message) {
        return new DystosThrow(newError(valueErrorClass, message), curPos.line());
    }

    /** Convertit une erreur Java interne en exception Dystos rattrapable ; null si le signal n'est pas rattrapable. */
    DystosThrow asThrow(Throwable ex) {
        if (ex instanceof DystosThrow t) {
            return t;
        }
        if (ex instanceof ReturnSignal || ex instanceof BreakSignal || ex instanceof ContinueSignal
                || ex instanceof LimitExceeded) {
            return null;
        }
        if (ex instanceof StackOverflowError) {
            return rt("profondeur de récursion maximale dépassée");
        }
        if (ex instanceof RuntimeException) {
            String msg = ex.getMessage();
            return rt(ex.getClass().getSimpleName() + (msg != null ? " : " + msg : ""));
        }
        return null;
    }

    private <T> T guard(Supplier<T> s) {
        try {
            return s.get();
        } catch (DystosThrow | ReturnSignal | BreakSignal | ContinueSignal | LimitExceeded e) {
            throw e;
        } catch (RuntimeException e) {
            throw asThrow(e);
        }
    }

    // ------------------------------------------------------------------
    // Instructions
    // ------------------------------------------------------------------

    void execBlock(List<Stmt> body, Env env) {
        for (Stmt st : body) {
            exec(st, env);
        }
    }

    private void execScoped(List<Stmt> body, Env env) {
        execBlock(body, new Env(env));
    }

    private void exec(Stmt st, Env env) {
        tick();
        curPos = st.pos();
        if (st instanceof ExprStmt e) {
            eval(e.expr(), env);
        } else if (st instanceof Assign a) {
            execAssign(a, env);
        } else if (st instanceof VarDecl d) {
            Object v = d.init() != null ? eval(d.init(), env) : null;
            boolean fl = isFloatType(d.type()) || v instanceof Double;
            if (fl && v instanceof Long l) {
                v = (double) l;
            }
            env.define(d.name(), v, fl);
        } else if (st instanceof IfStmt i) {
            if (cond(i.cond(), env)) {
                execScoped(i.then(), env);
            } else {
                execScoped(i.orElse(), env);
            }
        } else if (st instanceof WhileStmt w) {
            while (cond(w.cond(), env)) {
                tick();
                try {
                    execScoped(w.body(), env);
                } catch (BreakSignal b) {
                    break;
                } catch (ContinueSignal c) {
                    continue;
                }
            }
        } else if (st instanceof ForStmt f) {
            execFor(f, env);
        } else if (st instanceof ReturnStmt r) {
            throw new ReturnSignal(r.value() == null ? null : eval(r.value(), env));
        } else if (st instanceof BreakStmt) {
            throw BREAK;
        } else if (st instanceof ContinueStmt) {
            throw CONTINUE;
        } else if (st instanceof PassStmt) {
            return;
        } else if (st instanceof RaiseStmt r) {
            Object v = eval(r.value(), env);
            if (v instanceof DObject o && isException(o.cls)) {
                throw new DystosThrow(o, st.pos().line());
            }
            throw rt("'raise' attend une exception");
        } else if (st instanceof AssertStmt a) {
            if (!cond(a.cond(), env)) {
                throw rt("assertion échouée" + (a.message() != null ? " : " + str(eval(a.message(), env)) : ""));
            }
        } else if (st instanceof TryStmt t) {
            execTry(t, env);
        } else if (st instanceof WithStmt w) {
            Object ctx = eval(w.context(), env);
            Env we = new Env(env);
            if (w.name() != null) {
                we.define(w.name(), ctx, false);
            }
            try {
                execBlock(w.body(), we);
            } finally {
                invokeMethod(ctx, "close", new ArrayList<>(), NO_NAMED);
            }
        } else if (st instanceof MatchStmt m) {
            Object subject = eval(m.subject(), env);
            outer:
            for (MatchCase c : m.cases()) {
                for (Expr p : c.patterns()) {
                    boolean hit = p instanceof Name n && n.name().equals("_") || valuesEqual(eval(p, env), subject);
                    if (hit) {
                        execScoped(c.body(), env);
                        break outer;
                    }
                }
            }
        } else {
            throw new IllegalStateException("instruction non supportée : " + st.getClass().getSimpleName());
        }
    }

    private boolean cond(Expr e, Env env) {
        Object v = eval(e, env);
        if (v instanceof Boolean b) {
            return b;
        }
        throw rt("la condition doit être un bool, trouvé " + str(v));
    }

    private void execFor(ForStmt f, Env env) {
        for (Object item : iterableToList(eval(f.iterable(), env))) {
            tick();
            Env le = new Env(env);
            bindVars(f.vars(), item, le);
            try {
                execBlock(f.body(), le);
            } catch (BreakSignal b) {
                break;
            } catch (ContinueSignal c) {
                continue;
            }
        }
    }

    private void bindVars(List<String> vars, Object item, Env le) {
        if (vars.size() == 1) {
            le.define(vars.get(0), item, item instanceof Double);
        } else if (item instanceof EntryValue e && vars.size() == 2) {
            le.define(vars.get(0), e.key(), e.key() instanceof Double);
            le.define(vars.get(1), e.value(), e.value() instanceof Double);
        } else {
            throw rt("impossible de déstructurer la valeur en " + vars.size() + " variables");
        }
    }

    private void execTry(TryStmt t, Env env) {
        try {
            execScoped(t.body(), env);
        } catch (RuntimeException | StackOverflowError ex) {
            DystosThrow thrown = asThrow(ex);
            if (thrown == null) {
                throw ex instanceof RuntimeException r ? r : new IllegalStateException(ex);
            }
            for (ExceptClause h : t.handlers()) {
                boolean match = h.type() == null;
                if (!match) {
                    Object cls = resolveTypeName(h.type(), env);
                    match = cls instanceof ClassDef cd && thrown.value.cls.isSubclassOf(cd);
                }
                if (match) {
                    Env he = new Env(env);
                    if (h.name() != null) {
                        he.define(h.name(), thrown.value, false);
                    }
                    execBlock(h.body(), he);
                    return;
                }
            }
            throw thrown;
        } finally {
            if (t.finallyBody() != null) {
                execScoped(t.finallyBody(), env);
            }
        }
    }

    private void execAssign(Assign a, Env env) {
        Expr t = a.target();
        if (t instanceof Name n) {
            Env.Slot slot = env.find(n.name());
            if (a.op() == AssignOp.ASSIGN) {
                Object v = eval(a.value(), env);
                if (slot == null) {
                    env.define(n.name(), v, v instanceof Double);
                } else {
                    slot.value = coerce(slot, v);
                }
            } else {
                if (slot == null) {
                    throw rt("variable inconnue '" + n.name() + "'");
                }
                slot.value = coerce(slot, binaryOp(opOf(a.op()), slot.value, eval(a.value(), env)));
            }
        } else if (t instanceof Attribute at) {
            Object target = eval(at.target(), env);
            if (!(target instanceof DObject o)) {
                throw rt("affectation à un attribut d'une valeur qui n'est pas un objet");
            }
            Object v;
            if (a.op() == AssignOp.ASSIGN) {
                v = eval(a.value(), env);
            } else {
                if (!o.fields.containsKey(at.name())) {
                    throw rt("champ '" + at.name() + "' non initialisé");
                }
                v = binaryOp(opOf(a.op()), o.fields.get(at.name()), eval(a.value(), env));
            }
            if (v instanceof Long l && (o.cls.isFloatField(at.name()) || o.fields.get(at.name()) instanceof Double)) {
                v = (double) l;
            }
            o.fields.put(at.name(), v);
        } else if (t instanceof Index ix) {
            Object target = eval(ix.target(), env);
            Object idx = eval(ix.indices().get(0), env);
            Object v;
            if (a.op() == AssignOp.ASSIGN) {
                v = eval(a.value(), env);
            } else {
                v = binaryOp(opOf(a.op()), getItem(target, idx), eval(a.value(), env));
            }
            setItem(target, idx, v);
        } else {
            throw rt("cible d'affectation invalide");
        }
    }

    private static Object coerce(Env.Slot slot, Object v) {
        return slot.isFloat && v instanceof Long l ? (Object) (double) l : v;
    }

    private static BinaryOp opOf(AssignOp op) {
        switch (op) {
            case ADD: return BinaryOp.ADD;
            case SUB: return BinaryOp.SUB;
            case MUL: return BinaryOp.MUL;
            case DIV: return BinaryOp.DIV;
            default: return BinaryOp.MOD;
        }
    }

    static boolean isFloatType(TypeRef t) {
        return t instanceof NamedType nt && nt.name().equals("float");
    }

    boolean isException(ClassDef c) {
        return c.isSubclassOf(exceptionClass);
    }

    // ------------------------------------------------------------------
    // Expressions
    // ------------------------------------------------------------------

    Object eval(Expr e, Env env) {
        if (e instanceof IntLit i) {
            return parseInt(i.text());
        }
        if (e instanceof FloatLit f) {
            return Double.parseDouble(f.text());
        }
        if (e instanceof StringLit s) {
            return s.value();
        }
        if (e instanceof BoolLit b) {
            return b.value();
        }
        if (e instanceof NullLit) {
            return null;
        }
        if (e instanceof FStringLit f) {
            StringBuilder sb = new StringBuilder();
            for (FStringPart p : f.parts()) {
                if (p instanceof FText t) {
                    sb.append(t.text());
                } else {
                    FExpr fe = (FExpr) p;
                    sb.append(Formatter.format(this, eval(fe.expr(), env), fe.spec()));
                }
            }
            return sb.toString();
        }
        if (e instanceof Name n) {
            return lookupName(n.name(), env);
        }
        if (e instanceof Binary b) {
            return evalBinary(b, env);
        }
        if (e instanceof Unary u) {
            Object v = eval(u.operand(), env);
            if (u.op() == UnaryOp.NOT) {
                return !(v instanceof Boolean bo ? bo : false);
            }
            if (v instanceof Long l) {
                return u.op() == UnaryOp.NEG ? (Object) (-l) : l;
            }
            if (v instanceof Double d) {
                return u.op() == UnaryOp.NEG ? (Object) (-d) : d;
            }
            throw rt("opérateur unaire invalide");
        }
        if (e instanceof Conditional c) {
            return cond(c.cond(), env) ? eval(c.thenValue(), env) : eval(c.elseValue(), env);
        }
        if (e instanceof Call c) {
            return evalCall(c, env);
        }
        if (e instanceof Attribute a) {
            Object t = eval(a.target(), env);
            if (a.safe() && t == null) {
                return null;
            }
            return getMember(t, a.name());
        }
        if (e instanceof Index ix) {
            return evalIndex(ix, env);
        }
        if (e instanceof ListLit l) {
            List<Object> out = new ArrayList<>();
            for (Expr x : l.elements()) {
                out.add(eval(x, env));
            }
            return out;
        }
        if (e instanceof SetLit l) {
            Set<Object> out = new LinkedHashSet<>();
            for (Expr x : l.elements()) {
                out.add(eval(x, env));
            }
            return out;
        }
        if (e instanceof MapLit m) {
            Map<Object, Object> out = new LinkedHashMap<>();
            for (MapEntry en : m.entries()) {
                out.put(eval(en.key(), env), eval(en.value(), env));
            }
            return out;
        }
        if (e instanceof Lambda l) {
            return new LambdaValue(l, env);
        }
        if (e instanceof ListComp c) {
            List<Object> out = new ArrayList<>();
            comprehend(c.clauses(), 0, env, en -> out.add(eval(c.element(), en)));
            return out;
        }
        if (e instanceof GeneratorExp c) {
            List<Object> out = new ArrayList<>();
            comprehend(c.clauses(), 0, env, en -> out.add(eval(c.element(), en)));
            return out;
        }
        if (e instanceof SetComp c) {
            Set<Object> out = new LinkedHashSet<>();
            comprehend(c.clauses(), 0, env, en -> out.add(eval(c.element(), en)));
            return out;
        }
        if (e instanceof MapComp c) {
            Map<Object, Object> out = new LinkedHashMap<>();
            comprehend(c.clauses(), 0, env, en -> out.put(eval(c.key(), en), eval(c.value(), en)));
            return out;
        }
        throw new IllegalStateException("expression non supportée : " + e.getClass().getSimpleName());
    }

    private Object parseInt(String text) {
        try {
            if (text.startsWith("0b") || text.startsWith("0B")) {
                return Long.parseLong(text.substring(2), 2);
            }
            if (text.startsWith("0x") || text.startsWith("0X")) {
                return Long.parseLong(text.substring(2), 16);
            }
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw rt("entier trop grand : " + text);
        }
    }

    private void comprehend(List<CompClause> clauses, int idx, Env env, java.util.function.Consumer<Env> sink) {
        if (idx == clauses.size()) {
            sink.accept(env);
            return;
        }
        CompClause c = clauses.get(idx);
        for (Object item : iterableToList(eval(c.iterable(), env))) {
            tick();
            Env le = new Env(env);
            bindVars(c.vars(), item, le);
            boolean ok = true;
            for (Expr cond : c.conditions()) {
                if (!cond(cond, le)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                comprehend(clauses, idx + 1, le, sink);
            }
        }
    }

    Object lookupName(String name, Env env) {
        Env.Slot s = env.find(name);
        if (s != null) {
            return s.value;
        }
        for (String pkg : env.allStarPackages()) {
            Class<?> c = java.loadClass(pkg + "." + name);
            if (c != null) {
                return new JavaClassRef(c);
            }
        }
        Object b = builtins.get(name);
        if (b != null) {
            return b;
        }
        throw rt("nom inconnu '" + name + "'");
    }

    // ---- opérateurs -----------------------------------------------------

    private Object evalBinary(Binary b, Env env) {
        switch (b.op()) {
            case AND: {
                Object l = eval(b.left(), env);
                return Boolean.TRUE.equals(l) && Boolean.TRUE.equals(eval(b.right(), env));
            }
            case OR: {
                Object l = eval(b.left(), env);
                return Boolean.TRUE.equals(l) || Boolean.TRUE.equals(eval(b.right(), env));
            }
            case ELVIS: {
                Object l = eval(b.left(), env);
                return l != null ? l : eval(b.right(), env);
            }
            default:
                return binaryOp(b.op(), eval(b.left(), env), eval(b.right(), env));
        }
    }

    Object binaryOp(BinaryOp op, Object l, Object r) {
        switch (op) {
            case EQ: return valuesEqual(l, r);
            case NE: return !valuesEqual(l, r);
            case IS: return same(l, r);
            case IS_NOT: return !same(l, r);
            case IN: return contains(r, l);
            case NOT_IN: return !contains(r, l);
            case LT: return compare(l, r) < 0;
            case GT: return compare(l, r) > 0;
            case LE: return compare(l, r) <= 0;
            case GE: return compare(l, r) >= 0;
            default: break;
        }
        if (op == BinaryOp.ADD && l instanceof String a && r instanceof String b) {
            return a + b;
        }
        if (l instanceof Long a && r instanceof Long b) {
            try {
                switch (op) {
                    case ADD: return Math.addExact(a, b);
                    case SUB: return Math.subtractExact(a, b);
                    case MUL: return Math.multiplyExact(a, b);
                    case DIV:
                        if (b == 0) {
                            throw rt("division par zéro");
                        }
                        return (double) a / (double) b;
                    case MOD:
                        if (b == 0) {
                            throw rt("modulo par zéro");
                        }
                        return Math.floorMod(a, b);
                    case POW: return power(a, b);
                    default: break;
                }
            } catch (ArithmeticException ex) {
                throw rt("débordement d'entier");
            }
        }
        if ((l instanceof Long || l instanceof Double) && (r instanceof Long || r instanceof Double)) {
            double a = ((Number) l).doubleValue();
            double b = ((Number) r).doubleValue();
            switch (op) {
                case ADD: return a + b;
                case SUB: return a - b;
                case MUL: return a * b;
                case DIV:
                    if (b == 0) {
                        throw rt("division par zéro");
                    }
                    return a / b;
                case MOD:
                    if (b == 0) {
                        throw rt("modulo par zéro");
                    }
                    return a - Math.floor(a / b) * b;
                case POW: return Math.pow(a, b);
                default: break;
            }
        }
        throw rt("opérateur invalide pour " + str(l) + " et " + str(r));
    }

    private Object power(long base, long exp) {
        if (exp < 0) {
            throw rt("exposant négatif : utilisez un flottant (ex. 2.0 ** -1)");
        }
        long result = 1;
        for (long i = 0; i < exp; i++) {
            result = Math.multiplyExact(result, base);
            if (i > 4096) {
                throw new ArithmeticException();
            }
        }
        return result;
    }

    boolean valuesEqual(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        if ((a instanceof Long || a instanceof Double) && (b instanceof Long || b instanceof Double)) {
            if (a instanceof Long x && b instanceof Long y) {
                return x.longValue() == y.longValue();
            }
            return ((Number) a).doubleValue() == ((Number) b).doubleValue();
        }
        if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) {
                return false;
            }
            for (int i = 0; i < x.size(); i++) {
                if (!valuesEqual(x.get(i), y.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof DObject || b instanceof DObject) {
            return a == b;
        }
        return a.equals(b);
    }

    private boolean same(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a == b || (a instanceof Long || a instanceof Boolean || a instanceof String) && a.equals(b);
    }

    int compare(Object a, Object b) {
        if (a instanceof Long x && b instanceof Long y) {
            return Long.compare(x, y);
        }
        if ((a instanceof Long || a instanceof Double) && (b instanceof Long || b instanceof Double)) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        if (a instanceof String x && b instanceof String y) {
            return x.compareTo(y);
        }
        throw rt("comparaison impossible entre " + str(a) + " et " + str(b));
    }

    private boolean contains(Object container, Object item) {
        if (container instanceof String s && item instanceof String i) {
            return s.contains(i);
        }
        if (container instanceof List<?> l) {
            for (Object o : l) {
                if (valuesEqual(o, item)) {
                    return true;
                }
            }
            return false;
        }
        if (container instanceof Set<?> s) {
            if (s.contains(item)) {
                return true;
            }
            for (Object o : s) {
                if (valuesEqual(o, item)) {
                    return true;
                }
            }
            return false;
        }
        if (container instanceof Map<?, ?> m) {
            if (m.containsKey(item)) {
                return true;
            }
            for (Object o : m.keySet()) {
                if (valuesEqual(o, item)) {
                    return true;
                }
            }
            return false;
        }
        throw rt("'in' non applicable à " + str(container));
    }

    // ---- accès aux membres et aux éléments -----------------------------------

    Object getMember(Object t, String name) {
        if (t == null) {
            throw rt("accès à '." + name + "' sur null");
        }
        if (t instanceof DObject o) {
            if (o.fields.containsKey(name)) {
                return o.fields.get(name);
            }
            FuncValue m = o.cls.findMethod(name);
            if (m != null) {
                return new BoundMethod(m, o);
            }
            throw rt("champ '" + name + "' non initialisé ou inconnu dans " + o.cls.name);
        }
        if (t instanceof ClassDef cd) {
            DObject c = cd.enumConstants.get(name);
            if (c != null) {
                return c;
            }
            FuncValue s = cd.statics.get(name);
            if (s != null) {
                return s;
            }
            throw rt("'" + cd.name + "' n'a pas de membre statique '" + name + "'");
        }
        if (t instanceof ModuleValue mv) {
            return moduleMember(mv, name);
        }
        if (t instanceof JavaClassRef jr) {
            Object v = guard(() -> java.getStatic(jr.cls(), name));
            if (v != Values.NOT_FOUND) {
                return v;
            }
            if (java.hasStaticMethod(jr.cls(), name)) {
                return new JavaStaticMethod(jr.cls(), name);
            }
            throw rt("'" + jr.cls().getSimpleName() + "' n'a pas de membre statique '" + name + "'");
        }
        Object p = guard(() -> Natives.property(this, t, name));
        if (p != Values.NOT_FOUND) {
            return p;
        }
        return new BoundNative(t, name);
    }

    private Object moduleMember(ModuleValue mv, String name) {
        if (mv.natives != null) {
            Object o = mv.natives.get(name);
            if (o == null) {
                throw rt("le module '" + mv.name + "' n'a pas de membre '" + name + "'");
            }
            return o;
        }
        Env.Slot s = mv.env.vars.get(name);
        if (s == null) {
            throw rt("le module '" + mv.name + "' n'a pas de membre '" + name + "'");
        }
        return s.value;
    }

    private Object evalIndex(Index ix, Env env) {
        Object t = eval(ix.target(), env);
        if (t instanceof ClassDef || t instanceof JavaClassRef || t instanceof FuncValue
                || t instanceof BuiltinFn || t instanceof NativeFn) {
            return t; // application de type : Boite[int], ArrayList[str]
        }
        Expr ie = ix.indices().get(0);
        if (ie instanceof Slice sl) {
            return slice(t, sl, env);
        }
        return getItem(t, eval(ie, env));
    }

    Object getItem(Object t, Object idx) {
        if (t instanceof List<?> l && idx instanceof Long i) {
            return l.get(checkIndex(i, l.size()));
        }
        if (t instanceof Map<?, ?> m) {
            if (m.containsKey(idx)) {
                return m.get(idx);
            }
            for (Map.Entry<?, ?> en : m.entrySet()) {
                if (valuesEqual(en.getKey(), idx)) {
                    return en.getValue();
                }
            }
            throw rt("clé introuvable : " + quoted(idx));
        }
        if (t instanceof String s && idx instanceof Long i) {
            return String.valueOf(s.charAt(checkIndex(i, s.length())));
        }
        throw rt("indexation impossible de " + str(t));
    }

    @SuppressWarnings("unchecked")
    void setItem(Object t, Object idx, Object v) {
        if (t instanceof List<?> l && idx instanceof Long i) {
            ((List<Object>) l).set(checkIndex(i, l.size()), v);
        } else if (t instanceof Map<?, ?> m) {
            ((Map<Object, Object>) m).put(idx, v);
        } else {
            throw rt("affectation par indice impossible sur " + str(t));
        }
    }

    private int checkIndex(long i, int size) {
        long k = i < 0 ? i + size : i;
        if (k < 0 || k >= size) {
            throw rt("indice hors limites : " + i + " (taille " + size + ")");
        }
        return (int) k;
    }

    private Object slice(Object t, Slice sl, Env env) {
        int n = t instanceof String s ? s.length() : t instanceof List<?> l ? l.size() : -1;
        if (n < 0) {
            throw rt("tranche impossible sur " + str(t));
        }
        Long lo = sl.lower() == null ? null : (Long) eval(sl.lower(), env);
        Long hi = sl.upper() == null ? null : (Long) eval(sl.upper(), env);
        Long st = sl.step() == null ? null : (Long) eval(sl.step(), env);
        long step = st == null ? 1 : st;
        if (step == 0) {
            throw rt("le pas d'une tranche ne peut pas être 0");
        }
        List<Integer> idx = new ArrayList<>();
        if (step > 0) {
            long a = lo == null ? 0 : clamp(lo < 0 ? lo + n : lo, 0, n);
            long b = hi == null ? n : clamp(hi < 0 ? hi + n : hi, 0, n);
            for (long i = a; i < b; i += step) {
                idx.add((int) i);
            }
        } else {
            long a = lo == null ? n - 1 : clamp(lo < 0 ? lo + n : lo, -1, n - 1);
            long b = hi == null ? -1 : clamp(hi < 0 ? hi + n : hi, -1, n - 1);
            for (long i = a; i > b; i += step) {
                idx.add((int) i);
            }
        }
        if (t instanceof String s) {
            StringBuilder sb = new StringBuilder();
            for (int i : idx) {
                sb.append(s.charAt(i));
            }
            return sb.toString();
        }
        List<?> l = (List<?>) t;
        List<Object> out = new ArrayList<>();
        for (int i : idx) {
            out.add(l.get(i));
        }
        return out;
    }

    private static long clamp(long v, long lo, long hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    List<Object> iterableToList(Object v) {
        if (v instanceof List<?> l) {
            return new ArrayList<>(l);
        }
        if (v instanceof Set<?> s) {
            return new ArrayList<>(s);
        }
        if (v instanceof Map<?, ?> m) {
            return new ArrayList<>(m.keySet());
        }
        if (v instanceof String s) {
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < s.length(); i++) {
                out.add(String.valueOf(s.charAt(i)));
            }
            return out;
        }
        if (v instanceof Iterable<?> it) {
            List<Object> out = new ArrayList<>();
            for (Object o : it) {
                out.add(java.fromJava(o));
            }
            return out;
        }
        throw rt("valeur non itérable : " + str(v));
    }

    // ------------------------------------------------------------------
    // Appels
    // ------------------------------------------------------------------

    private void evalArgs(List<Arg> args, Env env, List<Object> pos, Map<String, Object> named) {
        for (Arg a : args) {
            Object v = eval(a.value(), env);
            if (a.name() != null) {
                named.put(a.name(), v);
            } else {
                pos.add(v);
            }
        }
    }

    private boolean isSuper(Expr e) {
        return e instanceof Call sc && sc.args().isEmpty() && sc.callee() instanceof Name n && n.name().equals("super");
    }

    private Object evalCall(Call c, Env env) {
        Expr callee = c.callee();
        List<Object> pos = new ArrayList<>();
        Map<String, Object> named = new LinkedHashMap<>();
        if (callee instanceof Attribute a && isSuper(a.target())) {
            evalArgs(c.args(), env, pos, named);
            return superCall(a.name(), pos, named, env);
        }
        if (callee instanceof Attribute a) {
            Object tv = eval(a.target(), env);
            if (a.safe() && tv == null) {
                return null;
            }
            evalArgs(c.args(), env, pos, named);
            curPos = c.pos();
            return invokeMethod(tv, a.name(), pos, named);
        }
        Object f = eval(callee, env);
        evalArgs(c.args(), env, pos, named);
        curPos = c.pos();
        return callValue(f, pos, named);
    }

    Object invokeMethod(Object tv, String name, List<Object> pos, Map<String, Object> named) {
        if (tv == null) {
            throw rt("appel de '." + name + "()' sur null");
        }
        if (tv instanceof DObject o) {
            FuncValue m = o.cls.findMethod(name);
            if (m != null) {
                return callFunc(m, o, pos, named);
            }
            Object field = o.fields.get(name);
            if (field != null) {
                return callValue(field, pos, named);
            }
            if (name.equals("toString") && pos.isEmpty()) {
                return str(o);
            }
            throw rt("méthode '" + name + "' inconnue pour " + o.cls.name);
        }
        if (tv instanceof ClassDef cd) {
            FuncValue sm = cd.statics.get(name);
            if (sm != null) {
                return callFunc(sm, null, pos, named);
            }
            throw rt("'" + cd.name + "' n'a pas de méthode statique '" + name + "'");
        }
        if (tv instanceof ModuleValue mv) {
            return callValue(moduleMember(mv, name), pos, named);
        }
        if (tv instanceof JavaClassRef jr) {
            return guard(() -> java.invokeStatic(jr.cls(), name, pos));
        }
        return guard(() -> Natives.invoke(this, tv, name, pos));
    }

    Object callValue(Object f, List<Object> pos, Map<String, Object> named) {
        if (f instanceof FuncValue fv) {
            return callFunc(fv, null, pos, named);
        }
        if (f instanceof BoundMethod bm) {
            return callFunc(bm.func(), bm.self(), pos, named);
        }
        if (f instanceof LambdaValue lv) {
            return callLambda(lv, pos);
        }
        if (f instanceof NativeFn nf) {
            return guard(() -> nf.fn().call(pos, named));
        }
        if (f instanceof BuiltinFn bf) {
            return guard(() -> Natives.builtin(this, bf.name(), pos, named));
        }
        if (f instanceof ClassDef cd) {
            return instantiate(cd, pos, named);
        }
        if (f instanceof JavaClassRef jr) {
            return guard(() -> java.construct(jr.cls(), pos));
        }
        if (f instanceof JavaStaticMethod js) {
            return guard(() -> java.invokeStatic(js.cls(), js.name(), pos));
        }
        if (f instanceof BoundNative bn) {
            return guard(() -> Natives.invoke(this, bn.receiver(), bn.name(), pos));
        }
        throw rt("valeur non appelable : " + str(f));
    }

    Object callFunc(FuncValue f, DObject self, List<Object> pos, Map<String, Object> named) {
        FuncDecl d = f.decl;
        if (d.body() == null) {
            throw rt("méthode abstraite '" + d.name() + "' appelée");
        }
        tick();
        if (++depth > opts.maxDepth()) {
            depth--;
            throw rt("profondeur de récursion maximale dépassée (" + opts.maxDepth() + ")");
        }
        try {
            Env env = new Env(f.closure);
            env.cls = f.owner;
            List<Param> params = d.params();
            int start = 0;
            if (self != null) {
                env.define("self", self, false);
                start = 1;
            }
            if (pos.size() > params.size() - start) {
                throw rt("trop d'arguments pour '" + d.name() + "'");
            }
            for (int i = start; i < params.size(); i++) {
                Param p = params.get(i);
                int idx = i - start;
                Object v;
                if (idx < pos.size()) {
                    v = pos.get(idx);
                } else if (named.containsKey(p.name())) {
                    v = named.get(p.name());
                } else if (p.defaultValue() != null) {
                    v = eval(p.defaultValue(), f.closure);
                } else {
                    throw rt("argument manquant : '" + p.name() + "'");
                }
                boolean fl = isFloatType(p.type());
                if (fl && v instanceof Long l) {
                    v = (double) l;
                }
                env.define(p.name(), v, fl);
            }
            try {
                execBlock(d.body(), env);
            } catch (ReturnSignal r) {
                Object v = r.value;
                return isFloatType(d.returnType()) && v instanceof Long l ? (Object) (double) l : v;
            }
            return null;
        } finally {
            depth--;
        }
    }

    private Object callLambda(LambdaValue lv, List<Object> pos) {
        List<Param> params = lv.lambda.params();
        if (params.size() != pos.size()) {
            throw rt("la fonction attend " + params.size() + " argument(s), " + pos.size() + " fourni(s)");
        }
        tick();
        if (++depth > opts.maxDepth()) {
            depth--;
            throw rt("profondeur de récursion maximale dépassée (" + opts.maxDepth() + ")");
        }
        try {
            Env env = new Env(lv.env);
            for (int i = 0; i < params.size(); i++) {
                Object v = pos.get(i);
                boolean fl = isFloatType(params.get(i).type());
                if (fl && v instanceof Long l) {
                    v = (double) l;
                }
                env.define(params.get(i).name(), v, fl);
            }
            return eval(lv.lambda.body(), env);
        } finally {
            depth--;
        }
    }

    Object instantiate(ClassDef cd, List<Object> pos, Map<String, Object> named) {
        if (cd.isAbstract || cd.isInterface || cd.isEnum) {
            throw rt("'" + cd.name + "' ne peut pas être instancié");
        }
        DObject o = new DObject(cd);
        initFields(o, cd);
        FuncValue ctor = cd.findCtor();
        if (ctor != null) {
            callFunc(ctor, o, pos, named);
        } else if (isException(cd)) {
            setMessage(o, pos);
        } else if (!pos.isEmpty()) {
            throw rt("'" + cd.name + "' n'a pas de constructeur acceptant des arguments");
        }
        return o;
    }

    private void initFields(DObject o, ClassDef cd) {
        if (cd.superclass != null) {
            initFields(o, cd.superclass);
        }
        Env env = new Env(cd.env);
        env.cls = cd;
        for (VarDecl f : cd.fields) {
            if (f.init() != null) {
                Object v = eval(f.init(), env);
                if (isFloatType(f.type()) && v instanceof Long l) {
                    v = (double) l;
                }
                o.fields.put(f.name(), v);
            }
        }
    }

    private void setMessage(DObject o, List<Object> pos) {
        o.fields.put("message", pos.isEmpty() ? "" : str(pos.get(0)));
    }

    private Object superCall(String name, List<Object> pos, Map<String, Object> named, Env env) {
        ClassDef cur = env.frameClass();
        Env.Slot selfSlot = env.find("self");
        if (cur == null || selfSlot == null) {
            throw rt("super() hors d'une méthode");
        }
        DObject self = (DObject) selfSlot.value;
        if (name.equals("init")) {
            ClassDef sup = cur.superclass;
            if (sup == null) {
                return null;
            }
            FuncValue ctor = sup.findCtor();
            if (ctor != null) {
                callFunc(ctor, self, pos, named);
            } else if (isException(sup)) {
                setMessage(self, pos);
            }
            return null;
        }
        FuncValue m = cur.superclass != null ? cur.superclass.findMethod(name) : null;
        for (ClassDef i : cur.interfaces) {
            if (m == null) {
                m = i.findMethod(name);
            }
        }
        if (m == null) {
            throw rt("la superclasse de '" + cur.name + "' n'a pas de méthode '" + name + "'");
        }
        return callFunc(m, self, pos, named);
    }

    // ------------------------------------------------------------------
    // Texte
    // ------------------------------------------------------------------

    String str(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String s) {
            return s;
        }
        return repr(v, 0);
    }

    String repr(Object v) {
        return repr(v, 0);
    }

    /** Comme {@link #str} mais avec des guillemets autour des chaînes (messages d'erreur). */
    String quoted(Object v) {
        return v instanceof String s ? Values.quote(s) : str(v);
    }

    private String repr(Object v, int level) {
        if (v == null) {
            return "null";
        }
        if (v instanceof String s) {
            return level == 0 ? s : Values.quote(s);
        }
        if (v instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (v instanceof Long) {
            return v.toString();
        }
        if (v instanceof Double d) {
            return Values.formatDouble(d);
        }
        if (level > 12) {
            return "...";
        }
        if (v instanceof DObject o) {
            return objectString(o);
        }
        if (v instanceof List<?> l) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < l.size(); i++) {
                sb.append(i > 0 ? ", " : "").append(repr(l.get(i), level + 1));
            }
            return sb.append(']').toString();
        }
        if (v instanceof Set<?> s) {
            if (s.isEmpty()) {
                return "set()";
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Object o : s) {
                sb.append(first ? "" : ", ").append(repr(o, level + 1));
                first = false;
            }
            return sb.append('}').toString();
        }
        if (v instanceof Map<?, ?> m) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> en : m.entrySet()) {
                sb.append(first ? "" : ", ").append(repr(en.getKey(), level + 1)).append(": ")
                        .append(repr(en.getValue(), level + 1));
                first = false;
            }
            return sb.append('}').toString();
        }
        if (v instanceof EntryValue e) {
            return "Entry(" + repr(e.key(), level + 1) + ", " + repr(e.value(), level + 1) + ")";
        }
        if (v instanceof ClassDef c) {
            return "<classe " + c.name + ">";
        }
        if (v instanceof FuncValue f) {
            return "<fonction " + f.decl.name() + ">";
        }
        if (v instanceof LambdaValue || v instanceof NativeFn || v instanceof BuiltinFn
                || v instanceof BoundMethod || v instanceof BoundNative) {
            return "<fonction>";
        }
        if (v instanceof ModuleValue m) {
            return "<module " + m.name + ">";
        }
        String s = String.valueOf(v);
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private String objectString(DObject o) {
        FuncValue m = o.cls.findMethod("toString");
        if (m != null && m.decl.params().size() == 1) {
            return str(callFunc(m, o, new ArrayList<>(), NO_NAMED));
        }
        if (o.cls.isEnum) {
            return o.cls.name + "." + o.fields.get("name");
        }
        if (isException(o.cls)) {
            return String.valueOf(o.fields.getOrDefault("message", ""));
        }
        return "<" + o.cls.name + ">";
    }
}
