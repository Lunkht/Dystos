package com.vulsoft.dystos.jvm;

import static com.vulsoft.dystos.jvm.classfile.Code.*;

import com.vulsoft.dystos.ast.Ast.*;
import com.vulsoft.dystos.jvm.classfile.Code;
import com.vulsoft.dystos.jvm.classfile.Label;
import com.vulsoft.dystos.sema.Symbols.ClassKind;
import com.vulsoft.dystos.sema.Symbols.ClassSymbol;
import com.vulsoft.dystos.sema.Symbols.FuncGroup;
import com.vulsoft.dystos.sema.Symbols.FuncSymbol;
import com.vulsoft.dystos.sema.Symbols.ParamSym;
import com.vulsoft.dystos.sema.Symbols.Symbol;
import com.vulsoft.dystos.sema.Symbols.VarKind;
import com.vulsoft.dystos.sema.Symbols.VarSymbol;
import com.vulsoft.dystos.sema.Type;
import com.vulsoft.dystos.sema.Type.ClassType;
import com.vulsoft.dystos.sema.Type.FunctionType;
import com.vulsoft.dystos.sema.Type.NullType;
import com.vulsoft.dystos.sema.Type.TypeVar;
import com.vulsoft.dystos.sema.Type.UnitType;
import com.vulsoft.dystos.sema.TypedProgram;
import com.vulsoft.dystos.sema.TypedProgram.CallInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Génération du bytecode du corps d'une fonction, d'une méthode ou d'un constructeur. */
final class MethodGen {

    /** Type représentant « Object » après effacement des génériques. */
    static final Type ERASED = new TypeVar("Object", -1, false);

    /** Emplacement d'une variable locale ; {@code boxed} : l'emplacement contient une boîte {@code dystos.rt.Ref}. */
    record Local(int slot, char kind, boolean boxed) {
    }

    private static final class LoopCtx {
        final Label breakLabel;
        final Label continueLabel;
        final int tryDepth;

        LoopCtx(Label breakLabel, Label continueLabel, int tryDepth) {
            this.breakLabel = breakLabel;
            this.continueLabel = continueLabel;
            this.tryDepth = tryDepth;
        }
    }

    private static final class TryCtx {
        final Runnable finallyBody;
        final List<Label[]> gaps = new ArrayList<>();

        TryCtx(Runnable finallyBody) {
            this.finallyBody = finallyBody;
        }
    }

    private record Segment(Label start, Label end, TryCtx ctx) {
    }

    private record Handler(String type, VarSymbol var, List<Stmt> body) {
    }

    final TypedProgram typed;
    final Code code;
    final ClassSymbol curClass;
    final Type returnType;
    final LambdaGen lambdas;
    private final Map<VarSymbol, Local> locals = new IdentityHashMap<>();
    private final Deque<LoopCtx> loops = new ArrayDeque<>();
    private final List<TryCtx> tries = new ArrayList<>();

    MethodGen(TypedProgram typed, Code code, ClassSymbol curClass, Type returnType, LambdaGen lambdas) {
        this.typed = typed;
        this.code = code;
        this.curClass = curClass;
        this.returnType = returnType;
        this.lambdas = lambdas;
    }

    // ------------------------------------------------------------------
    // Variables locales
    // ------------------------------------------------------------------

    /** Lie un paramètre déjà placé à {@code slot} (paramètres : emplacements fixés par la signature). */
    void bindParam(VarSymbol v, int slot) {
        locals.put(v, new Local(slot, Jt.kind(v.type), false));
    }

    private Local declare(VarSymbol v) {
        boolean boxed = typed.isBoxed(v);
        char k = boxed ? 'A' : Jt.kind(v.type);
        Local l = new Local(code.newLocal(k), k, boxed);
        locals.put(v, l);
        return l;
    }

    /** Variable capturée, dont la valeur (ou la boîte, si elle est partagée) est au sommet de la pile. */
    void declareCaptured(VarSymbol v) {
        Local l = declare(v);
        code.store(l.kind, l.slot);
    }

    /** Paramètre de lambda reçu sous forme d'{@code Object} (au sommet de la pile). */
    void declareParamFromObject(VarSymbol v) {
        coerce(ERASED, v.type);
        Local l = declare(v);
        code.store(l.kind, l.slot);
    }

    /** Termine le corps d'une lambda : retourne la valeur encadrée, ou null pour une fonction sans résultat. */
    void lambdaReturn(Expr body, Type result) {
        code.line(body.pos().line());
        if (Jt.kind(result) == 'V') {
            expr(body);
            code.aconstNull();
        } else {
            exprAs(body, result);
            boxAny(result);
        }
        code.ret('A');
    }

    private void loadVar(VarSymbol v) {
        if (lambdas.isGlobal(v)) {
            code.getstatic(lambdas.moduleClass(), lambdas.globalField(v), Jt.desc(v.type));
            return;
        }
        Local l = local(v);
        code.load(l.kind, l.slot);
        if (l.boxed) {
            code.getfield(Jt.REF, "v", "Ljava/lang/Object;");
            coerce(ERASED, v.type);
        }
    }

    /** La valeur initiale est au sommet : l'enregistre dans la variable (dans une boîte si elle est partagée). */
    private void storeNew(Local l, VarSymbol v) {
        if (l.boxed) {
            boxAny(v.type);
            code.anew(Jt.REF);
            code.simple(DUP_X1, 1);
            code.simple(SWAP, 0);
            code.invokespecial(Jt.REF, "<init>", "(Ljava/lang/Object;)V");
        }
        code.store(l.kind, l.slot);
    }

    private Local local(VarSymbol v) {
        Local l = locals.get(v);
        if (l == null) {
            throw new UnsupportedException("la variable '" + v.name + "' est définie hors de cette fonction "
                    + "(variable globale d'un script ou capturée) : non supporté par le backend JVM");
        }
        return l;
    }

    private int temp(char kind) {
        return code.newLocal(kind);
    }

    // ------------------------------------------------------------------
    // Conversions de valeurs
    // ------------------------------------------------------------------

    void box(char kind) {
        switch (kind) {
            case 'J': code.invokestatic(Jt.LONG, "valueOf", "(J)Ljava/lang/Long;"); break;
            case 'D': code.invokestatic(Jt.DOUBLE, "valueOf", "(D)Ljava/lang/Double;"); break;
            case 'I': code.invokestatic(Jt.BOOLEAN, "valueOf", "(Z)Ljava/lang/Boolean;"); break;
            default: break;
        }
    }

    private void unbox(char kind) {
        switch (kind) {
            case 'J':
                code.checkcast(Jt.LONG);
                code.invokevirtual(Jt.LONG, "longValue", "()J");
                break;
            case 'D':
                code.checkcast(Jt.DOUBLE);
                code.invokevirtual(Jt.DOUBLE, "doubleValue", "()D");
                break;
            case 'I':
                code.checkcast(Jt.BOOLEAN);
                code.invokevirtual(Jt.BOOLEAN, "booleanValue", "()Z");
                break;
            default: break;
        }
    }

    /** Convertit la valeur au sommet de la pile du type {@code from} vers le type {@code to}. */
    void coerce(Type from, Type to) {
        if (to instanceof UnitType || from instanceof UnitType) {
            return;
        }
        if (from instanceof NullType) {
            return;
        }
        char kf = Jt.kind(from);
        char kt = Jt.kind(to);
        if (kf != 'A' && kt != 'A') {
            if (kf == 'J' && kt == 'D') {
                code.simple(L2D, 0);
            }
            return;
        }
        if (kf != 'A') {                       // primitif -> référence : encadrement
            if (kf == 'J' && Jt.isBuiltin(to, "float")) {
                code.simple(L2D, 0);
                kf = 'D';
            }
            box(kf);
            return;
        }
        if (kt != 'A') {                       // référence -> primitif : désencadrement
            if (kt == 'D' && Jt.isBuiltin(from, "int")) {
                unbox('J');
                code.simple(L2D, 0);
            } else {
                unbox(kt);
            }
            return;
        }
        // référence -> référence
        if (Jt.isBuiltin(from, "int") && Jt.isBuiltin(to, "float")) {
            throw new UnsupportedException("conversion de int? vers float? non supportée par le backend JVM");
        }
        if (Jt.isObjectLike(from) && !Jt.isObjectLike(to)) {
            code.checkcast(Jt.internal(to));
        }
    }

    /** Évalue {@code e} puis la convertit vers {@code target}. */
    private void exprAs(Expr e, Type target) {
        expr(e);
        coerce(typed.typeOf(e), target);
    }

    /** Encadre la valeur du type donné en référence {@code Object}. */
    private void boxAny(Type from) {
        char k = Jt.kind(from);
        if (k != 'A' && k != 'V') {
            box(k);
        }
    }

    // ------------------------------------------------------------------
    // Points d'entrée pour la génération des classes
    // ------------------------------------------------------------------

    /** Initialise un champ d'instance ({@code this.f = init}). */
    void fieldInit(String owner, VarSymbol f, Expr init) {
        code.line(init.pos().line());
        code.load('A', 0);
        exprAs(init, f.type);
        code.putfield(owner, f.name, Jt.desc(f.type));
    }

    /** {@code super().init(...)} : empile {@code this}, les arguments, et appelle le constructeur parent. */
    void superInit(String sup, Call c, CallInfo ci) {
        code.line(c.pos().line());
        FuncSymbol f = ci.func();
        if (f != null && containsLoop(c.args())) {
            int[] tmp = spillArgs(f, c.args(), ci.bindings());
            code.load('A', 0);
            reload(f, tmp);
        } else {
            code.load('A', 0);
            if (f != null) {
                pushArgs(f, c.args(), ci.bindings());
            }
        }
        code.invokespecial(sup, "<init>", ctorDesc(f));
    }

    /**
     * Évalue les arguments dans des variables temporaires. Nécessaire quand ils contiennent une boucle : le
     * vérificateur de classes de version 49 interdit un saut en arrière tant qu'un objet non initialisé est sur la pile.
     */
    private int[] spillArgs(FuncSymbol f, List<Arg> args, Map<TypeVar, Type> bindings) {
        pushArgs(f, args, bindings);
        int n = f.params.size();
        int[] tmp = new int[n];
        for (int i = n - 1; i >= 0; i--) {
            char k = Jt.kind(f.params.get(i).type());
            tmp[i] = temp(k);
            code.store(k, tmp[i]);
        }
        return tmp;
    }

    private void reload(FuncSymbol f, int[] tmp) {
        for (int i = 0; i < tmp.length; i++) {
            code.load(Jt.kind(f.params.get(i).type()), tmp[i]);
        }
    }

    /** L'expression contient-elle une compréhension (donc une boucle) en dehors des lambdas ? */
    private static boolean containsLoop(Object node) {
        if (node instanceof ListComp || node instanceof SetComp || node instanceof MapComp || node instanceof GeneratorExp) {
            return true;
        }
        if (node instanceof Lambda) {
            return false;
        }
        if (node instanceof List<?> l) {
            for (Object o : l) {
                if (containsLoop(o)) {
                    return true;
                }
            }
            return false;
        }
        if (node != null && node.getClass().isRecord() && node.getClass().getName().startsWith("com.vulsoft.dystos.ast")) {
            for (var rc : node.getClass().getRecordComponents()) {
                try {
                    if (containsLoop(rc.getAccessor().invoke(node))) {
                        return true;
                    }
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return false;
    }

    void blockFrom(List<Stmt> body, int from) {
        for (int i = from; i < body.size(); i++) {
            stmt(body.get(i));
        }
    }

    /** Termine la méthode : retour implicite pour {@code unit}, sinon erreur si la fin est atteinte. */
    void finish() {
        if (!code.reachable()) {
            return;
        }
        if (Jt.kind(returnType) == 'V') {
            code.ret('V');
        } else {
            code.ldcString("fin de fonction atteinte sans valeur de retour");
            code.invokestatic(Jt.RT, "rt", "(Ljava/lang/String;)Ldystos/rt/DRuntimeError;");
            code.simple(ATHROW, -1);
        }
    }

    // ------------------------------------------------------------------
    // Instructions
    // ------------------------------------------------------------------

    void block(List<Stmt> body) {
        for (Stmt s : body) {
            stmt(s);
        }
    }

    void stmt(Stmt s) {
        if (!code.reachable()) {
            return;
        }
        code.line(s.pos().line());
        try {
            stmtInner(s);
        } catch (UnsupportedException e) {
            throw e.at(s.pos());
        }
    }

    private void stmtInner(Stmt s) {
        if (s instanceof ExprStmt e) {
            discarding = e.expr() instanceof Call dc ? dc : null;
            expr(e.expr());
            if (discarding == null) {
                return;                     // un appel Java a déjà jeté son résultat sans le convertir
            }
            discarding = null;
            Type t = typed.typeOf(e.expr());
            char k = Jt.kind(t);
            if (k != 'V') {
                code.pop(k);
            }
        } else if (s instanceof VarDecl d) {
            varDecl(d);
        } else if (s instanceof Assign a) {
            assign(a);
        } else if (s instanceof IfStmt i) {
            ifStmt(i);
        } else if (s instanceof WhileStmt w) {
            whileStmt(w);
        } else if (s instanceof ForStmt f) {
            forStmt(f);
        } else if (s instanceof ReturnStmt r) {
            returnStmt(r);
        } else if (s instanceof BreakStmt) {
            LoopCtx l = loops.peek();
            unwind(l.tryDepth);
            code.jump(GOTO, l.breakLabel);
        } else if (s instanceof ContinueStmt) {
            LoopCtx l = loops.peek();
            unwind(l.tryDepth);
            code.jump(GOTO, l.continueLabel);
        } else if (s instanceof PassStmt) {
            return;
        } else if (s instanceof RaiseStmt r) {
            expr(r.value());
            code.simple(ATHROW, -1);
        } else if (s instanceof AssertStmt a) {
            Label ok = new Label();
            jumpIfTrue(a.cond(), ok);
            if (a.message() != null) {
                expr(a.message());
                boxAny(typed.typeOf(a.message()));
            } else {
                code.aconstNull();
            }
            code.invokestatic(Jt.RT, "assertFail", "(Ljava/lang/Object;)V");
            code.label(ok);
        } else if (s instanceof TryStmt t) {
            tryStmt(t);
        } else if (s instanceof WithStmt w) {
            withStmt(w);
        } else if (s instanceof MatchStmt m) {
            matchStmt(m);
        } else if (s instanceof FuncDecl) {
            throw new UnsupportedException("les fonctions locales ne sont pas supportées");
        } else {
            throw new UnsupportedException("instruction non supportée : " + s.getClass().getSimpleName());
        }
    }

    private void varDecl(VarDecl d) {
        VarSymbol v = typed.declared.get(d).get(0);
        if (lambdas.isGlobal(v)) {
            if (d.init() != null) {
                exprAs(d.init(), v.type);
            } else {
                defaultValue(v.type);
            }
            code.putstatic(lambdas.moduleClass(), lambdas.globalField(v), Jt.desc(v.type));
            return;
        }
        Local l = declare(v);
        if (d.init() != null) {
            exprAs(d.init(), v.type);
        } else {
            defaultValue(v.type);
        }
        storeNew(l, v);
    }

    private void defaultValue(Type t) {
        switch (Jt.kind(t)) {
            case 'J': code.lconst(0); break;
            case 'D': code.dconst(0); break;
            case 'I': code.iconst(0); break;
            default: code.aconstNull();
        }
    }

    // ---- affectations --------------------------------------------------

    private void assign(Assign a) {
        Expr target = a.target();
        if (target instanceof Name n) {
            List<VarSymbol> newVars = typed.declared.get(a);
            if (newVars != null) {
                VarSymbol v = newVars.get(0);
                if (lambdas.isGlobal(v)) {
                    exprAs(a.value(), v.type);
                    code.putstatic(lambdas.moduleClass(), lambdas.globalField(v), Jt.desc(v.type));
                    return;
                }
                Local l = declare(v);
                exprAs(a.value(), v.type);
                storeNew(l, v);
                return;
            }
            Symbol sym = typed.names.get(n);
            if (!(sym instanceof VarSymbol v)) {
                throw new UnsupportedException("affectation à '" + n.name() + "' non supportée");
            }
            if (lambdas.isGlobal(v)) {
                if (a.op() == AssignOp.ASSIGN) {
                    exprAs(a.value(), v.type);
                } else {
                    loadVar(v);
                    augmented(a.op(), v.type, a.value());
                }
                code.putstatic(lambdas.moduleClass(), lambdas.globalField(v), Jt.desc(v.type));
                return;
            }
            Local l = local(v);
            if (l.boxed) {
                code.load('A', l.slot);
                if (a.op() == AssignOp.ASSIGN) {
                    exprAs(a.value(), v.type);
                } else {
                    code.dup('A');
                    code.getfield(Jt.REF, "v", "Ljava/lang/Object;");
                    coerce(ERASED, v.type);
                    augmented(a.op(), v.type, a.value());
                }
                boxAny(v.type);
                code.putfield(Jt.REF, "v", "Ljava/lang/Object;");
                return;
            }
            if (a.op() == AssignOp.ASSIGN) {
                exprAs(a.value(), v.type);
            } else {
                code.load(l.kind, l.slot);
                augmented(a.op(), v.type, a.value());
            }
            code.store(l.kind, l.slot);
        } else if (target instanceof Attribute at) {
            attributeAssign(a, at);
        } else if (target instanceof Index ix) {
            indexAssign(a, ix);
        } else {
            throw new UnsupportedException("cible d'affectation non supportée");
        }
    }

    /** La valeur courante (de type {@code cur}) est au sommet ; calcule {@code cur op value} converti vers {@code cur}. */
    private void augmented(AssignOp aop, Type cur, Expr value) {
        BinaryOp op = switch (aop) {
            case ADD -> BinaryOp.ADD;
            case SUB -> BinaryOp.SUB;
            case MUL -> BinaryOp.MUL;
            case DIV -> BinaryOp.DIV;
            default -> BinaryOp.MOD;
        };
        Type vt = typed.typeOf(value);
        if (op == BinaryOp.ADD && Jt.isStr(cur)) {
            expr(value);
            code.invokevirtual(Jt.STRING, "concat", "(Ljava/lang/String;)Ljava/lang/String;");
            return;
        }
        Type opType = operandType(op, cur, vt);
        coerce(cur, opType);
        exprAs(value, opType);
        char rk = emitArith(op, opType);
        if (rk == 'J' && Jt.kind(cur) == 'D') {
            code.simple(L2D, 0);
        }
    }

    private void attributeAssign(Assign a, Attribute at) {
        VarSymbol f = typed.members.get(at).field();
        if (f.owner != null && f.owner.kind == ClassKind.JAVA) {
            throw new UnsupportedException("l'affectation d'un champ Java n'est pas encore supportée", at.pos());
        }
        Type recv = typed.typeOf(at.target());
        Type fieldStatic = Jt.subst(f.type, Jt.mapping(recv));
        String owner = Jt.internalName(f.owner);
        String desc = Jt.desc(f.type);
        expr(at.target());
        if (a.op() == AssignOp.ASSIGN) {
            exprAs(a.value(), fieldStatic);
            coerce(fieldStatic, f.type);
        } else {
            code.dup('A');
            code.getfield(owner, f.name, desc);
            coerce(f.type, fieldStatic);
            augmented(a.op(), fieldStatic, a.value());
            coerce(fieldStatic, f.type);
        }
        code.putfield(owner, f.name, desc);
    }

    private void indexAssign(Assign a, Index ix) {
        Type ct = typed.typeOf(ix.target());
        boolean isList = Jt.isBuiltin(ct, "list");
        boolean isMap = Jt.isBuiltin(ct, "map");
        if (!isList && !isMap) {
            throw new UnsupportedException("affectation par indice sur " + ct + " non supportée");
        }
        Type elem = ((ClassType) ct).args().get(isList ? 0 : 1);
        Type keyT = isList ? null : ((ClassType) ct).args().get(0);
        Expr idx = ix.indices().get(0);
        if (a.op() == AssignOp.ASSIGN) {
            expr(ix.target());
            pushKey(idx, isList, keyT);
            exprAs(a.value(), elem);
            boxAny(elem);
            setItemCall(isList);
            return;
        }
        int tc = temp('A');
        char kk = isList ? 'J' : 'A';
        int tk = temp(kk);
        expr(ix.target());
        code.store('A', tc);
        pushKey(idx, isList, keyT);
        code.store(kk, tk);
        code.load('A', tc);
        code.load(kk, tk);
        code.load('A', tc);
        code.load(kk, tk);
        getItemCall(isList);
        coerce(ERASED, elem);
        augmented(a.op(), elem, a.value());
        boxAny(elem);
        setItemCall(isList);
    }

    private void pushKey(Expr idx, boolean isList, Type keyT) {
        if (isList) {
            exprAs(idx, typed.typeOf(idx));
        } else {
            exprAs(idx, keyT);
            boxAny(keyT);
        }
    }

    private void getItemCall(boolean isList) {
        if (isList) {
            code.invokestatic(Jt.RT, "list_getItem", "(Ljava/util/List;J)Ljava/lang/Object;");
        } else {
            code.invokestatic(Jt.RT, "map_getItem", "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object;");
        }
    }

    private void setItemCall(boolean isList) {
        if (isList) {
            code.invokestatic(Jt.RT, "list_setItem", "(Ljava/util/List;JLjava/lang/Object;)V");
        } else {
            code.invokestatic(Jt.RT, "map_setItem", "(Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Object;)V");
        }
    }

    // ---- structures de contrôle ----------------------------------------------

    private void ifStmt(IfStmt i) {
        Label elseL = new Label();
        Label end = new Label();
        jumpIfFalse(i.cond(), elseL);
        block(i.then());
        if (!i.orElse().isEmpty()) {
            code.jump(GOTO, end);
        }
        code.label(elseL);
        block(i.orElse());
        code.label(end);
    }

    private void whileStmt(WhileStmt w) {
        Label top = new Label();
        Label end = new Label();
        code.label(top);
        jumpIfFalse(w.cond(), end);
        loops.push(new LoopCtx(end, top, tries.size()));
        block(w.body());
        loops.pop();
        code.jump(GOTO, top);
        code.label(end);
    }

    private boolean isRange(Expr e) {
        if (!(e instanceof Call c)) {
            return false;
        }
        CallInfo ci = typed.calls.get(c);
        return ci != null && ci.kind() == TypedProgram.CallKind.FUNCTION && ci.func() != null
                && ci.func().module == null && ci.func().owner == null && ci.func().name.equals("range")
                && c.args().stream().allMatch(x -> x.name() == null);
    }

    private void forStmt(ForStmt f) {
        List<VarSymbol> vars = typed.declared.get(f);
        if (vars.size() == 1 && isRange(f.iterable()) && rangeStepIsSimple((Call) f.iterable())) {
            rangeLoop(f, (Call) f.iterable(), vars.get(0));
            return;
        }
        Type itT = typed.typeOf(f.iterable());
        expr(f.iterable());
        boxAny(itT);
        code.invokestatic(Jt.RT, "iter", "(Ljava/lang/Object;)Ljava/util/Iterator;");
        int it = temp('A');
        code.store('A', it);
        Label top = new Label();
        Label end = new Label();
        code.label(top);
        code.load('A', it);
        code.invokeinterface("java/util/Iterator", "hasNext", "()Z");
        code.jump(IFEQ, end);
        code.load('A', it);
        code.invokeinterface("java/util/Iterator", "next", "()Ljava/lang/Object;");
        bindLoopVars(vars);
        loops.push(new LoopCtx(end, top, tries.size()));
        block(f.body());
        loops.pop();
        code.jump(GOTO, top);
        code.label(end);
    }

    private boolean rangeStepIsSimple(Call c) {
        if (c.args().size() < 3) {
            return c.args().size() >= 1;
        }
        return c.args().get(2).value() instanceof IntLit il && Long.parseLong(il.text().replace("_", "")) > 0;
    }

    private void rangeLoop(ForStmt f, Call c, VarSymbol v) {
        List<Arg> args = c.args();
        Type intT = typed.typeOf(args.get(0).value());
        int counter = temp('J');
        int limit = temp('J');
        int step = temp('J');
        if (args.size() == 1) {
            code.lconst(0);
            code.store('J', counter);
            exprAs(args.get(0).value(), intT);
            code.store('J', limit);
        } else {
            exprAs(args.get(0).value(), intT);
            code.store('J', counter);
            exprAs(args.get(1).value(), intT);
            code.store('J', limit);
        }
        if (args.size() == 3) {
            exprAs(args.get(2).value(), intT);
        } else {
            code.lconst(1);
        }
        code.store('J', step);
        Local var = declare(v);
        Label top = new Label();
        Label next = new Label();
        Label end = new Label();
        code.label(top);
        code.load('J', counter);
        code.load('J', limit);
        code.simple(LCMP, -3);
        code.jump(IFGE, end);
        code.load('J', counter);
        code.store('J', var.slot);
        loops.push(new LoopCtx(end, next, tries.size()));
        block(f.body());
        loops.pop();
        code.label(next);
        code.load('J', counter);
        code.load('J', step);
        code.invokestatic(Jt.RT, "add", "(JJ)J");
        code.store('J', counter);
        code.jump(GOTO, top);
        code.label(end);
    }

    /** Un élément (Object) est au sommet : le range dans les variables de boucle déclarées. */
    private void bindLoopVars(List<VarSymbol> vars) {
        if (vars.size() == 1) {
            VarSymbol v = vars.get(0);
            Local l = declare(v);
            coerce(ERASED, v.type);
            code.store(l.kind, l.slot);
        } else {
            code.checkcast(Jt.ENTRY);
            code.dup('A');
            code.getfield(Jt.ENTRY, "key", "Ljava/lang/Object;");
            VarSymbol k = vars.get(0);
            Local lk = declare(k);
            coerce(ERASED, k.type);
            code.store(lk.kind, lk.slot);
            code.getfield(Jt.ENTRY, "value", "Ljava/lang/Object;");
            VarSymbol val = vars.get(1);
            Local lv = declare(val);
            coerce(ERASED, val.type);
            code.store(lv.kind, lv.slot);
        }
    }

    private void returnStmt(ReturnStmt r) {
        char k = Jt.kind(returnType);
        boolean hasFinally = tries.stream().anyMatch(t -> t.finallyBody != null);
        if (r.value() == null) {
            unwind(0);
            code.ret('V');
            return;
        }
        exprAs(r.value(), returnType);
        if (hasFinally) {
            int tmp = temp(k);
            code.store(k, tmp);
            unwind(0);
            code.load(k, tmp);
        }
        code.ret(k);
    }

    // ---- try / finally / with ----------------------------------------------

    /** Exécute les blocs « finally » des contextes actifs, du plus interne jusqu'à {@code depth} (inclus). */
    private void unwind(int depth) {
        for (int j = tries.size() - 1; j >= depth; j--) {
            TryCtx t = tries.get(j);
            if (t.finallyBody == null) {
                continue;
            }
            Label gs = new Label();
            Label ge = new Label();
            code.label(gs);
            List<TryCtx> saved = new ArrayList<>(tries);
            while (tries.size() > j) {
                tries.remove(tries.size() - 1);
            }
            t.finallyBody.run();
            tries.clear();
            tries.addAll(saved);
            code.label(ge);
            for (int k = j; k < tries.size(); k++) {
                tries.get(k).gaps.add(new Label[] {gs, ge});
            }
        }
    }

    private void tryStmt(TryStmt t) {
        List<Handler> hs = new ArrayList<>();
        for (ExceptClause h : t.handlers()) {
            String type = Jt.internal(typed.exceptTypes.get(h));
            VarSymbol v = typed.declared.get(h) == null ? null : typed.declared.get(h).get(0);
            hs.add(new Handler(type, v, h.body()));
        }
        Runnable fin = t.finallyBody() == null ? null : () -> block(t.finallyBody());
        genTry(() -> block(t.body()), hs, fin);
    }

    private void withStmt(WithStmt w) {
        Type ct = typed.typeOf(w.context());
        expr(w.context());
        int ctx = temp('A');
        code.store('A', ctx);
        if (w.name() != null) {
            VarSymbol v = typed.declared.get(w).get(0);
            Local l = declare(v);
            code.load('A', ctx);
            code.store(l.kind, l.slot);
        }
        Runnable close = () -> {
            code.load('A', ctx);
            boolean userClose = ct instanceof ClassType c && (c.cls().kind == ClassKind.CLASS
                    || c.cls().kind == ClassKind.INTERFACE) && c.cls().methods.containsKey("close");
            if (userClose) {
                ClassSymbol cs = ((ClassType) ct).cls();
                if (Jt.isInterface(cs)) {
                    code.invokeinterface(Jt.internalName(cs), "close", "()V");
                } else {
                    code.invokevirtual(Jt.internalName(cs), "close", "()V");
                }
            } else {
                code.invokestatic(Jt.RT, "close", "(Ljava/lang/Object;)V");
            }
        };
        genTry(() -> block(w.body()), List.of(), close);
    }

    private void genTry(Runnable body, List<Handler> handlers, Runnable fin) {
        TryCtx bodyCtx = new TryCtx(fin);
        Label start = new Label();
        Label bodyEnd = new Label();
        Label after = new Label();
        List<Segment> catchAll = new ArrayList<>();

        tries.add(bodyCtx);
        code.label(start);
        body.run();
        code.label(bodyEnd);
        tries.remove(tries.size() - 1);
        catchAll.add(new Segment(start, bodyEnd, bodyCtx));
        if (code.reachable()) {
            if (fin != null) {
                fin.run();
            }
            code.jump(GOTO, after);
        }

        for (Handler h : handlers) {
            Label hl = new Label();
            code.handlerLabel(hl);
            registerRanges(start, bodyEnd, bodyCtx.gaps, hl, h.type);
            if (h.var != null) {
                Local l = declare(h.var);
                code.store(l.kind, l.slot);
            } else {
                code.pop('A');
            }
            TryCtx hctx = new TryCtx(fin);
            Label hs = new Label();
            Label he = new Label();
            tries.add(hctx);
            code.label(hs);
            block(h.body);
            code.label(he);
            tries.remove(tries.size() - 1);
            catchAll.add(new Segment(hs, he, hctx));
            if (code.reachable()) {
                if (fin != null) {
                    fin.run();
                }
                code.jump(GOTO, after);
            }
        }

        if (fin != null) {
            Label ca = new Label();
            code.handlerLabel(ca);
            for (Segment s : catchAll) {
                registerRanges(s.start, s.end, s.ctx.gaps, ca, null);
            }
            int exc = temp('A');
            code.store('A', exc);
            fin.run();
            code.load('A', exc);
            code.simple(ATHROW, -1);
        }
        code.label(after);
    }

    /** Déclare la zone [start, end) moins les « trous » (code de nettoyage dupliqué) comme protégée. */
    private void registerRanges(Label start, Label end, List<Label[]> gaps, Label handler, String type) {
        List<Label[]> sorted = new ArrayList<>(gaps);
        sorted.sort((a, b) -> Integer.compare(Code.positionOf(a[0]), Code.positionOf(b[0])));
        Label from = start;
        for (Label[] g : sorted) {
            if (Code.positionOf(g[0]) >= Code.positionOf(from)) {
                code.tryCatch(from, g[0], handler, type);
                from = g[1];
            }
        }
        code.tryCatch(from, end, handler, type);
    }

    // ---- match -------------------------------------------------------------------------

    private void matchStmt(MatchStmt m) {
        Type st = typed.typeOf(m.subject());
        char k = Jt.kind(st);
        int subject = temp(k);
        expr(m.subject());
        code.store(k, subject);
        Label end = new Label();
        for (MatchCase c : m.cases()) {
            Label body = new Label();
            Label next = new Label();
            for (Expr p : c.patterns()) {
                if (p instanceof Name n && n.name().equals("_")) {
                    code.jump(GOTO, body);
                    continue;
                }
                patternTest(st, k, subject, p, body);
            }
            code.jump(GOTO, next);
            code.label(body);
            block(c.body());
            if (code.reachable()) {
                code.jump(GOTO, end);
            }
            code.label(next);
        }
        code.label(end);
    }

    private void patternTest(Type st, char k, int subject, Expr p, Label body) {
        switch (k) {
            case 'J':
                code.load('J', subject);
                exprAs(p, st);
                code.simple(LCMP, -3);
                code.jump(IFEQ, body);
                break;
            case 'D':
                code.load('D', subject);
                exprAs(p, st);
                code.simple(DCMPL, -3);
                code.jump(IFEQ, body);
                break;
            case 'I':
                code.load('I', subject);
                exprAs(p, st);
                code.simple(IXOR, -1);
                code.jump(IFEQ, body);
                break;
            default:
                code.load('A', subject);
                if (st instanceof ClassType ct && ct.cls().kind == ClassKind.ENUM) {
                    expr(p);
                    code.jump(IF_ACMPEQ, body);
                } else {
                    expr(p);
                    boxAny(typed.typeOf(p));
                    code.invokestatic(Jt.RT, "eq", "(Ljava/lang/Object;Ljava/lang/Object;)Z");
                    code.jump(IFNE, body);
                }
        }
    }

    // ------------------------------------------------------------------
    // Expressions : chaque méthode laisse sur la pile une valeur du type statique de l'expression
    // ------------------------------------------------------------------

    void expr(Expr e) {
        if (e instanceof IntLit i) {
            code.lconst(parseInt(i.text()));
        } else if (e instanceof FloatLit f) {
            code.dconst(Double.parseDouble(f.text()));
        } else if (e instanceof StringLit s) {
            code.ldcString(s.value());
        } else if (e instanceof BoolLit b) {
            code.iconst(b.value() ? 1 : 0);
        } else if (e instanceof NullLit) {
            code.aconstNull();
        } else if (e instanceof FStringLit f) {
            fstring(f);
        } else if (e instanceof Name n) {
            name(n);
        } else if (e instanceof Binary b) {
            binary(b);
        } else if (e instanceof Unary u) {
            unary(u);
        } else if (e instanceof Conditional c) {
            Type res = typed.typeOf(c);
            Label elseL = new Label();
            Label end = new Label();
            jumpIfFalse(c.cond(), elseL);
            exprAs(c.thenValue(), res);
            code.jump(GOTO, end);
            code.label(elseL);
            exprAs(c.elseValue(), res);
            code.label(end);
        } else if (e instanceof Call c) {
            call(c);
        } else if (e instanceof Attribute a) {
            attribute(a);
        } else if (e instanceof Index ix) {
            index(ix);
        } else if (e instanceof ListLit l) {
            literal(l.elements(), "java/util/ArrayList", "java/util/List", elemOf(l, 0));
        } else if (e instanceof SetLit l) {
            literal(l.elements(), "java/util/LinkedHashSet", "java/util/Set", elemOf(l, 0));
        } else if (e instanceof MapLit m) {
            mapLiteral(m);
        } else if (e instanceof ListComp c) {
            Type el = elemOf(c, 0);
            comprehension(c, c.clauses(), () -> addElement(c.element(), el, false));
        } else if (e instanceof GeneratorExp c) {
            Type el = elemOf(c, 0);
            comprehension(c, c.clauses(), () -> addElement(c.element(), el, false));
        } else if (e instanceof SetComp c) {
            Type el = elemOf(c, 0);
            comprehension(c, c.clauses(), () -> addElement(c.element(), el, true));
        } else if (e instanceof MapComp c) {
            Type kt = elemOf(c, 0);
            Type vt = elemOf(c, 1);
            comprehension(c, c.clauses(), () -> {
                exprAs(c.key(), kt);
                boxAny(kt);
                exprAs(c.value(), vt);
                boxAny(vt);
                code.invokeinterface("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
                code.pop('A');
            });
        } else if (e instanceof Lambda l) {
            lambdaValue(l);
        } else {
            throw new UnsupportedException("expression non supportée : " + e.getClass().getSimpleName(), e.pos());
        }
    }

    private static long parseInt(String text) {
        try {
            if (text.startsWith("0b") || text.startsWith("0B")) {
                return Long.parseLong(text.substring(2), 2);
            }
            if (text.startsWith("0x") || text.startsWith("0X")) {
                return Long.parseLong(text.substring(2), 16);
            }
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new UnsupportedException("entier trop grand : " + text);
        }
    }

    private Type elemOf(Expr collection, int i) {
        return ((ClassType) typed.typeOf(collection)).args().get(i);
    }

    // ---- f-strings --------------------------------------------------------------

    private void fstring(FStringLit f) {
        code.anew("java/lang/StringBuilder");
        code.dup('A');
        code.invokespecial("java/lang/StringBuilder", "<init>", "()V");
        for (FStringPart p : f.parts()) {
            if (p instanceof FText t) {
                code.ldcString(t.text());
            } else {
                FExpr fe = (FExpr) p;
                Type t = typed.typeOf(fe.expr());
                expr(fe.expr());
                if (fe.spec() == null) {
                    toStr(t);
                } else {
                    boxAny(t);
                    code.ldcString(fe.spec());
                    code.invokestatic(Jt.RT, "format", "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;");
                }
            }
            code.invokevirtual("java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        }
        code.invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
    }

    /** Convertit la valeur au sommet (de type {@code t}) en String. */
    private void toStr(Type t) {
        switch (Jt.kind(t)) {
            case 'J': code.invokestatic(Jt.RT, "strLong", "(J)Ljava/lang/String;"); break;
            case 'D': code.invokestatic(Jt.RT, "strDouble", "(D)Ljava/lang/String;"); break;
            case 'I': code.invokestatic(Jt.RT, "strBool", "(Z)Ljava/lang/String;"); break;
            default:
                if (!(Jt.isStr(t) && !t.nullable())) {
                    code.invokestatic(Jt.RT, "str", "(Ljava/lang/Object;)Ljava/lang/String;");
                }
        }
    }

    // ---- noms --------------------------------------------------------------------

    private void name(Name n) {
        Symbol s = typed.names.get(n);
        if (s instanceof VarSymbol v) {
            loadVar(v);
            return;
        }
        if (s instanceof FuncGroup g && g.funcs.size() == 1) {
            FuncSymbol f = g.funcs.get(0);
            if (f.module == null) {
                throw new UnsupportedException("la fonction prédéfinie '" + f.name
                        + "' passée en valeur n'est pas encore supportée par le backend JVM", n.pos());
            }
            String cls = lambdas.adapter(f);
            code.anew(cls);
            code.dup('A');
            code.invokespecial(cls, "<init>", "()V");
            return;
        }
        throw new UnsupportedException("'" + n.name() + "' utilisé comme valeur : non supporté par le backend JVM", n.pos());
    }

    // ---- opérateurs ------------------------------------------------------------------

    private void unary(Unary u) {
        Type t = typed.typeOf(u.operand());
        switch (u.op()) {
            case NOT:
                boolValue(u);
                break;
            case POS:
                expr(u.operand());
                break;
            default:
                expr(u.operand());
                if (Jt.isFloat(t)) {
                    code.simple(DNEG, 0);
                } else {
                    code.invokestatic(Jt.RT, "neg", "(J)J");
                }
        }
    }

    /** Produit 0/1 en évaluant l'expression booléenne par sauts. */
    private void boolValue(Expr e) {
        Label f = new Label();
        Label end = new Label();
        jumpIfFalse(e, f);
        code.iconst(1);
        code.jump(GOTO, end);
        code.label(f);
        code.iconst(0);
        code.label(end);
    }

    private void binary(Binary b) {
        switch (b.op()) {
            case ADD: case SUB: case MUL: case DIV: case MOD: case POW:
                arithmetic(b);
                break;
            case EQ: case NE: case LT: case GT: case LE: case GE: {
                Label t = new Label();
                Label end = new Label();
                int op = compareJump(b);
                code.jump(op, t);
                code.iconst(0);
                code.jump(GOTO, end);
                code.label(t);
                code.iconst(1);
                code.label(end);
                break;
            }
            case AND: case OR:
                boolValue(b);
                break;
            case IN: case NOT_IN:
                expr(b.left());
                boxAny(typed.typeOf(b.left()));
                expr(b.right());
                code.simple(SWAP, 0);
                code.invokestatic(Jt.RT, "contains", "(Ljava/lang/Object;Ljava/lang/Object;)Z");
                if (b.op() == BinaryOp.NOT_IN) {
                    code.iconst(1);
                    code.simple(IXOR, -1);
                }
                break;
            case IS: case IS_NOT:
                expr(b.left());
                boxAny(typed.typeOf(b.left()));
                expr(b.right());
                boxAny(typed.typeOf(b.right()));
                code.invokestatic(Jt.RT, "same", "(Ljava/lang/Object;Ljava/lang/Object;)Z");
                if (b.op() == BinaryOp.IS_NOT) {
                    code.iconst(1);
                    code.simple(IXOR, -1);
                }
                break;
            default:
                elvis(b);
        }
    }

    private void elvis(Binary b) {
        Type res = typed.typeOf(b);
        Type lt = typed.typeOf(b.left());
        if (!lt.nullable()) {                 // opérande gauche non nullable : l'opérateur est sans effet
            exprAs(b.left(), res);
            return;
        }
        Label isNull = new Label();
        Label end = new Label();
        expr(b.left());
        code.dup('A');
        code.jump(IFNULL, isNull);
        coerce(lt, res);
        code.jump(GOTO, end);
        code.label(isNull);
        code.pop('A');
        exprAs(b.right(), res);
        code.label(end);
    }

    /** Type dans lequel les deux opérandes d'un opérateur arithmétique sont évalués. */
    private Type operandType(BinaryOp op, Type lt, Type rt) {
        boolean lf = Jt.isFloat(lt);
        boolean rf = Jt.isFloat(rt);
        if (op == BinaryOp.DIV) {
            if (Jt.isInt(lt) && Jt.isInt(rt)) {
                return lt;
            }
            return lf ? lt : rt;
        }
        if (lf || rf) {
            return lf ? lt : rt;
        }
        return lt;
    }

    private void arithmetic(Binary b) {
        Type lt = typed.typeOf(b.left());
        Type rt = typed.typeOf(b.right());
        if (b.op() == BinaryOp.ADD && Jt.isStr(lt)) {
            expr(b.left());
            expr(b.right());
            code.invokevirtual(Jt.STRING, "concat", "(Ljava/lang/String;)Ljava/lang/String;");
            return;
        }
        Type ot = operandType(b.op(), lt, rt);
        exprAs(b.left(), ot);
        exprAs(b.right(), ot);
        emitArith(b.op(), ot);
    }

    /** Opérandes de type {@code ot} sur la pile : émet l'opération et retourne le genre du résultat. */
    private char emitArith(BinaryOp op, Type ot) {
        if (Jt.isInt(ot)) {
            switch (op) {
                case ADD: code.invokestatic(Jt.RT, "add", "(JJ)J"); return 'J';
                case SUB: code.invokestatic(Jt.RT, "sub", "(JJ)J"); return 'J';
                case MUL: code.invokestatic(Jt.RT, "mul", "(JJ)J"); return 'J';
                case MOD: code.invokestatic(Jt.RT, "modInt", "(JJ)J"); return 'J';
                case POW: code.invokestatic(Jt.RT, "powInt", "(JJ)J"); return 'J';
                default: code.invokestatic(Jt.RT, "divInt", "(JJ)D"); return 'D';
            }
        }
        switch (op) {
            case ADD: code.simple(DADD, -2); break;
            case SUB: code.simple(DSUB, -2); break;
            case MUL: code.simple(DMUL, -2); break;
            case MOD: code.invokestatic(Jt.RT, "modFloat", "(DD)D"); break;
            case POW: code.invokestatic(Jt.RT, "powFloat", "(DD)D"); break;
            default: code.invokestatic(Jt.RT, "divFloat", "(DD)D");
        }
        return 'D';
    }

    /** Émet les opérandes et la comparaison ; retourne l'instruction de saut à utiliser quand la condition est VRAIE. */
    private int compareJump(Binary b) {
        BinaryOp op = b.op();
        Type lt = typed.typeOf(b.left());
        Type rt = typed.typeOf(b.right());
        boolean eq = op == BinaryOp.EQ || op == BinaryOp.NE;
        if (eq && Jt.isBool(lt) && Jt.isBool(rt)) {
            expr(b.left());
            expr(b.right());
            code.simple(IXOR, -1);
            return op == BinaryOp.EQ ? IFEQ : IFNE;
        }
        boolean numeric = (Jt.isInt(lt) || Jt.isFloat(lt)) && (Jt.isInt(rt) || Jt.isFloat(rt));
        if (numeric) {
            Type ot = Jt.isInt(lt) && Jt.isInt(rt) ? lt : (Jt.isFloat(lt) ? lt : rt);
            exprAs(b.left(), ot);
            exprAs(b.right(), ot);
            if (Jt.isInt(ot)) {
                code.simple(LCMP, -3);
            } else {
                code.simple(op == BinaryOp.LT || op == BinaryOp.LE ? DCMPG : DCMPL, -3);
            }
            return jumpFor(op);
        }
        if (!eq && Jt.isStr(lt) && Jt.isStr(rt)) {
            expr(b.left());
            expr(b.right());
            code.invokestatic(Jt.RT, "cmpStr", "(Ljava/lang/String;Ljava/lang/String;)I");
            return jumpFor(op);
        }
        if (eq) {
            expr(b.left());
            boxAny(lt);
            expr(b.right());
            boxAny(rt);
            code.invokestatic(Jt.RT, "eq", "(Ljava/lang/Object;Ljava/lang/Object;)Z");
            return op == BinaryOp.EQ ? IFNE : IFEQ;
        }
        throw new UnsupportedException("comparaison non supportée entre " + lt + " et " + rt, b.pos());
    }

    private static int jumpFor(BinaryOp op) {
        switch (op) {
            case LT: return IFLT;
            case LE: return IFLE;
            case GT: return IFGT;
            case GE: return IFGE;
            case EQ: return IFEQ;
            default: return IFNE;
        }
    }

    private static int invert(int op) {
        switch (op) {
            case IFEQ: return IFNE;
            case IFNE: return IFEQ;
            case IFLT: return IFGE;
            case IFGE: return IFLT;
            case IFGT: return IFLE;
            case IFLE: return IFGT;
            default: throw new IllegalStateException("saut non inversible : " + op);
        }
    }

    private static boolean isComparison(BinaryOp op) {
        return op == BinaryOp.EQ || op == BinaryOp.NE || op == BinaryOp.LT || op == BinaryOp.GT
                || op == BinaryOp.LE || op == BinaryOp.GE;
    }

    void jumpIfFalse(Expr e, Label f) {
        if (e instanceof Binary b) {
            if (b.op() == BinaryOp.AND) {
                jumpIfFalse(b.left(), f);
                jumpIfFalse(b.right(), f);
                return;
            }
            if (b.op() == BinaryOp.OR) {
                Label ok = new Label();
                jumpIfTrue(b.left(), ok);
                jumpIfFalse(b.right(), f);
                code.label(ok);
                return;
            }
            if (isComparison(b.op())) {
                code.jump(invert(compareJump(b)), f);
                return;
            }
        }
        if (e instanceof Unary u && u.op() == UnaryOp.NOT) {
            jumpIfTrue(u.operand(), f);
            return;
        }
        if (e instanceof BoolLit bl) {
            if (!bl.value()) {
                code.jump(GOTO, f);
            }
            return;
        }
        expr(e);
        code.jump(IFEQ, f);
    }

    void jumpIfTrue(Expr e, Label t) {
        if (e instanceof Binary b) {
            if (b.op() == BinaryOp.OR) {
                jumpIfTrue(b.left(), t);
                jumpIfTrue(b.right(), t);
                return;
            }
            if (b.op() == BinaryOp.AND) {
                Label skip = new Label();
                jumpIfFalse(b.left(), skip);
                jumpIfTrue(b.right(), t);
                code.label(skip);
                return;
            }
            if (isComparison(b.op())) {
                code.jump(compareJump(b), t);
                return;
            }
        }
        if (e instanceof Unary u && u.op() == UnaryOp.NOT) {
            jumpIfFalse(u.operand(), t);
            return;
        }
        if (e instanceof BoolLit bl) {
            if (bl.value()) {
                code.jump(GOTO, t);
            }
            return;
        }
        expr(e);
        code.jump(IFNE, t);
    }

    // ---- appels -----------------------------------------------------------------------

    private void call(Call c) {
        CallInfo ci = typed.calls.get(c);
        if (ci == null) {
            throw new UnsupportedException("appel non résolu", c.pos());
        }
        switch (ci.kind()) {
            case FUNCTION: functionCall(c, ci); break;
            case METHOD: case STATIC_METHOD: case SUPER_METHOD: methodCall(c, ci); break;
            case CONSTRUCT: construct(c, ci); break;
            case CONVERSION: conversion(c, ci); break;
            case SUPER_INIT:
                throw new UnsupportedException("super().init(...) doit être la première instruction de init", c.pos());
            case FUNC_VALUE: funcValueCall(c); break;
            default:
                throw new UnsupportedException("appel non supporté", c.pos());
        }
    }

    /** Crée l'objet fonction d'une lambda en lui passant les variables capturées. */
    private void lambdaValue(Lambda l) {
        TypedProgram.LambdaInfo li = typed.lambdas.get(l);
        FunctionType ft = (FunctionType) typed.typeOf(l);
        String cls = lambdas.define(l, li, ft);
        code.anew(cls);
        code.dup('A');
        StringBuilder desc = new StringBuilder("(");
        for (VarSymbol v : lambdas.captures(li)) {
            Local lv = local(v);
            code.load(lv.kind, lv.slot);
            desc.append(LambdaGen.captureDesc(typed, v));
        }
        code.invokespecial(cls, "<init>", desc.append(")V").toString());
    }

    /** Appel d'une valeur fonction : arguments encadrés dans un tableau, résultat déballé selon le type statique. */
    private void funcValueCall(Call c) {
        Type ct = typed.types.get(c.callee());
        if (!(ct instanceof FunctionType ft)) {
            throw new UnsupportedException("l'appel d'un champ ou d'une expression de type fonction n'est pas encore supporté", c.pos());
        }
        expr(c.callee());
        code.iconst(c.args().size());
        code.anewarray(Jt.OBJECT);
        for (int i = 0; i < c.args().size(); i++) {
            Arg a = c.args().get(i);
            if (a.name() != null) {
                throw new UnsupportedException("arguments nommés non supportés pour une valeur fonction", c.pos());
            }
            Type pt = ft.params().get(i);
            code.dup('A');
            code.iconst(i);
            exprAs(a.value(), pt);
            boxAny(pt);
            code.simple(AASTORE, -3);
        }
        code.invokeinterface(Jt.FN, "call", "([Ljava/lang/Object;)Ljava/lang/Object;");
        Type res = typed.typeOf(c);
        if (Jt.kind(res) == 'V') {
            code.pop('A');
        } else {
            coerce(ERASED, res);
        }
    }

    /** Valeur laissée par un appel : type déclaré (effacé) converti vers le type statique de l'expression. */
    private void afterCall(Type natural, Call c) {
        if (natural instanceof UnitType) {
            return;
        }
        coerce(natural, typed.typeOf(c));
    }

    private void functionCall(Call c, CallInfo ci) {
        FuncSymbol f = ci.func();
        if (f.module != null) {
            pushArgs(f, c.args(), ci.bindings());
            code.invokestatic(Jt.moduleClass(f.module.name), f.name, funcDesc(f));
            afterCall(f.returnType, c);
            return;
        }
        Expr[] bound = f.name.equals("print") ? null : bindBuiltinArgs(f, c);
        int argc = bound == null ? c.args().size() : bound.length;
        RuntimeMap.RtCall rc = RuntimeMap.function(f, argc);
        if (bound == null) {
            packVariadic(c.args());
        } else {
            for (int i = 0; i < argc; i++) {
                Type p = Jt.subst(f.params.get(i).type(), ci.bindings());
                exprAs(bound[i], p);
                coerce(p, f.params.get(i).type());
            }
        }
        code.invokestatic(Jt.RT, rc.name(), rc.desc());
        afterCall(f.returnType, c);
    }

    /** Associe arguments positionnels ou nommés aux paramètres d'une fonction prédéfinie (jusqu'au dernier fourni). */
    private Expr[] bindBuiltinArgs(FuncSymbol f, Call c) {
        Expr[] slots = new Expr[f.params.size()];
        int pos = 0;
        for (Arg a : c.args()) {
            if (a.name() != null) {
                int idx = -1;
                for (int i = 0; i < slots.length; i++) {
                    if (f.params.get(i).name().equals(a.name())) {
                        idx = i;
                    }
                }
                if (idx < 0) {
                    throw new UnsupportedException("paramètre inconnu '" + a.name() + "' pour " + f.name, c.pos());
                }
                slots[idx] = a.value();
            } else {
                slots[pos++] = a.value();
            }
        }
        int n = slots.length;
        while (n > 0 && slots[n - 1] == null) {
            n--;
        }
        for (int i = 0; i < n; i++) {
            if (slots[i] == null) {
                throw new UnsupportedException("argument manquant pour '" + f.params.get(i).name() + "' dans " + f.name, c.pos());
            }
        }
        return java.util.Arrays.copyOf(slots, n);
    }

    private void packVariadic(List<Arg> args) {
        code.iconst(args.size());
        code.anewarray(Jt.OBJECT);
        for (int i = 0; i < args.size(); i++) {
            code.dup('A');
            code.iconst(i);
            Expr e = args.get(i).value();
            expr(e);
            boxAny(typed.typeOf(e));
            code.simple(AASTORE, -3);
        }
    }

    static String funcDesc(FuncSymbol f) {
        List<Type> ps = new ArrayList<>();
        for (ParamSym p : f.params) {
            ps.add(p.type());
        }
        return Jt.methodDesc(ps, f.returnType);
    }

    static String ctorDesc(FuncSymbol f) {
        if (f == null) {
            return "()V";
        }
        StringBuilder sb = new StringBuilder("(");
        for (ParamSym p : f.params) {
            sb.append(Jt.desc(p.type()));
        }
        return sb.append(")V").toString();
    }

    /** Empile les arguments dans l'ordre des paramètres (arguments nommés et valeurs par défaut inclus). */
    private void pushArgs(FuncSymbol f, List<Arg> args, Map<TypeVar, Type> subst) {
        int n = f.params.size();
        Expr[] slots = new Expr[n];
        int pos = 0;
        for (Arg a : args) {
            if (a.name() != null) {
                for (int i = 0; i < n; i++) {
                    if (f.params.get(i).name().equals(a.name())) {
                        slots[i] = a.value();
                    }
                }
            } else {
                slots[pos++] = a.value();
            }
        }
        for (int i = 0; i < n; i++) {
            ParamSym p = f.params.get(i);
            Expr e = slots[i];
            if (e == null && f.decl != null) {
                e = f.decl.params().get(f.astOffset + i).defaultValue();
            }
            if (e == null) {
                throw new UnsupportedException("argument manquant pour '" + p.name() + "'");
            }
            Type st = Jt.subst(p.type(), subst);
            exprAs(e, st);
            coerce(st, p.type());
        }
    }

    private void methodCall(Call c, CallInfo ci) {
        FuncSymbol f = ci.func();
        Attribute a = (Attribute) c.callee();
        if (f.owner != null && f.owner.kind == ClassKind.JAVA) {
            javaMethodCall(c, ci, a);
            return;
        }
        switch (ci.kind()) {
            case STATIC_METHOD:
                pushArgs(f, c.args(), ci.bindings());
                code.invokestatic(Jt.internalName(f.owner) + (f.owner.kind == ClassKind.INTERFACE ? "$Defaults" : ""),
                        f.name, funcDesc(f));
                afterCall(f.returnType, c);
                return;
            case SUPER_METHOD: {
                if (curClass == null) {
                    throw new UnsupportedException("super.methode() dans une lambda n'est pas encore supporté", c.pos());
                }
                code.load('A', 0);
                pushArgs(f, c.args(), ci.bindings());
                String sup = curClass.superType != null ? Jt.internalName(curClass.superType.cls()) : Jt.DOBJECT;
                code.invokespecial(sup, f.name, funcDesc(f));
                afterCall(f.returnType, c);
                return;
            }
            default:
                break;
        }
        Type recvT = typed.typeOf(a.target());
        Type resT = typed.typeOf(c);
        expr(a.target());
        Map<TypeVar, Type> subst = new java.util.HashMap<>(Jt.mapping(recvT.withNullable(false)));
        subst.putAll(ci.bindings());
        safe(a.safe() && recvT.nullable(), resT, () -> {
            if (f.owner.kind == ClassKind.BUILTIN) {
                RuntimeMap.RtCall rc = RuntimeMap.method(f);
                for (int i = 0; i < f.params.size(); i++) {
                    pushOneArg(f, i, c.args(), subst);
                }
                code.invokestatic(Jt.RT, rc.name(), rc.desc());
            } else {
                pushArgs(f, c.args(), subst);
                String owner = Jt.internalName(f.owner);
                if (Jt.isInterface(f.owner)) {
                    code.invokeinterface(owner, f.name, funcDesc(f));
                } else {
                    code.invokevirtual(owner, f.name, funcDesc(f));
                }
            }
            afterCall(f.returnType, c);
        });
    }

    private void pushOneArg(FuncSymbol f, int i, List<Arg> args, Map<TypeVar, Type> subst) {
        if (i >= args.size() || args.get(i).name() != null) {
            throw new UnsupportedException("arguments nommés ou omis non supportés pour " + f.owner.name + "." + f.name);
        }
        ParamSym p = f.params.get(i);
        Type st = Jt.subst(p.type(), subst);
        exprAs(args.get(i).value(), st);
        coerce(st, p.type());
    }

    /** Exécute {@code access} sur le récepteur au sommet ; si {@code guard}, saute l'accès quand il est null. */
    private void safe(boolean guard, Type resultType, Runnable access) {
        if (!guard) {
            access.run();
            return;
        }
        Label isNull = new Label();
        Label end = new Label();
        code.dup('A');
        code.jump(IFNULL, isNull);
        access.run();
        code.jump(GOTO, end);
        code.label(isNull);
        code.pop('A');
        if (Jt.kind(resultType) != 'V') {
            code.aconstNull();
        }
        code.label(end);
    }

    // ---- appels de code Java -----------------------------------------------------------

    private java.lang.reflect.Executable javaMember(FuncSymbol f, Call c) {
        if (!(f != null && f.javaMember != null)) {
            throw new UnsupportedException("membre Java introuvable pour l'appel", c.pos());
        }
        if (f.variadic) {
            throw new UnsupportedException("les méthodes Java à nombre variable d'arguments ne sont pas encore supportées", c.pos());
        }
        return f.javaMember;
    }

    private Class<?>[] checkJavaArgs(Call c, java.lang.reflect.Executable e) {
        Class<?>[] pts = e.getParameterTypes();
        if (c.args().size() != pts.length || c.args().stream().anyMatch(a -> a.name() != null)) {
            throw new UnsupportedException("appel Java : arguments nommés ou omis non supportés", c.pos());
        }
        return pts;
    }

    /** Empile les arguments d'un appel Java, convertis vers les types des paramètres Java. */
    private void pushJavaArgs(Call c, FuncSymbol f, java.lang.reflect.Executable e, Map<TypeVar, Type> subst) {
        Class<?>[] pts = checkJavaArgs(c, e);
        for (int i = 0; i < pts.length; i++) {
            Type st = Jt.subst(f.params.get(i).type(), subst);
            exprAs(c.args().get(i).value(), st);
            JavaCall.toJava(this, st, pts[i]);
        }
    }

    private void javaConstruct(Call c, CallInfo ci) {
        FuncSymbol f = ci.func();
        java.lang.reflect.Executable k = javaMember(f, c);
        String internal = Jt.internalName(ci.cls());
        if (containsLoop(c.args())) {
            // évalue d'abord les arguments (types Dystos) dans des variables temporaires
            Class<?>[] pts = checkJavaArgs(c, k);
            Type[] sts = new Type[pts.length];
            int[] tmp = new int[pts.length];
            for (int i = 0; i < pts.length; i++) {
                sts[i] = Jt.subst(f.params.get(i).type(), ci.bindings());
                exprAs(c.args().get(i).value(), sts[i]);
                tmp[i] = temp(Jt.kind(sts[i]));
                code.store(Jt.kind(sts[i]), tmp[i]);
            }
            code.anew(internal);
            code.dup('A');
            for (int i = 0; i < pts.length; i++) {
                code.load(Jt.kind(sts[i]), tmp[i]);
                JavaCall.toJava(this, sts[i], pts[i]);
            }
        } else {
            code.anew(internal);
            code.dup('A');
            pushJavaArgs(c, f, k, ci.bindings());
        }
        code.invokespecial(internal, "<init>", JavaCall.methodDesc(k));
    }

    /** Appel d'instruction dont le résultat est ignoré (voir {@link #javaMethodCall}). */
    private Call discarding;

    /** Jette le résultat brut d'un appel Java sans le convertir (un {@code null} Java est alors sans conséquence). */
    private void dropJavaResult(Class<?> r) {
        if (r == void.class) {
            return;
        }
        code.pop(r == long.class || r == double.class ? 'J' : 'A');
    }

    private void javaMethodCall(Call c, CallInfo ci, Attribute a) {
        FuncSymbol f = ci.func();
        java.lang.reflect.Method m = (java.lang.reflect.Method) javaMember(f, c);
        String desc = JavaCall.methodDesc(m);
        Type resT = typed.typeOf(c);
        boolean discard = discarding == c && !a.safe();
        if (discarding != null && !discard) {
            discarding = discarding == c ? null : discarding;
        }
        if (f.isStatic) {
            if (m.getDeclaringClass().isInterface()) {
                throw new UnsupportedException("les méthodes statiques d'interface Java nécessitent des classes de version ≥ 52 "
                        + "(pas encore supporté)", c.pos());
            }
            pushJavaArgs(c, f, m, ci.bindings());
            code.invokestatic(Jt.internalName(f.owner), m.getName(), desc);
            if (discard) {
                dropJavaResult(m.getReturnType());
                discarding = null;
                return;
            }
            Type natural = Jt.subst(f.returnType, ci.bindings());
            JavaCall.fromJava(this, m.getReturnType(), natural);
            afterCall(natural, c);
            return;
        }
        Type recvT = typed.typeOf(a.target());
        ClassSymbol rc = recvT instanceof ClassType rct && rct.cls().kind == ClassKind.JAVA ? rct.cls() : f.owner;
        Map<TypeVar, Type> subst = new java.util.HashMap<>(Jt.mapping(recvT.withNullable(false)));
        subst.putAll(ci.bindings());
        expr(a.target());
        safe(a.safe() && recvT.nullable(), resT, () -> {
            pushJavaArgs(c, f, m, subst);
            String owner = Jt.internalName(rc);
            if (Jt.isInterface(rc)) {
                code.invokeinterface(owner, m.getName(), desc);
            } else {
                code.invokevirtual(owner, m.getName(), desc);
            }
            if (discard) {
                dropJavaResult(m.getReturnType());
                discarding = null;
                return;
            }
            Type natural = Jt.subst(f.returnType, subst);
            JavaCall.fromJava(this, m.getReturnType(), natural);
            afterCall(natural, c);
        });
    }

    /** Propriété (accesseur {@code getX()}) ou champ public d'une classe Java. */
    private void javaAttribute(Attribute a, VarSymbol f, Type res) {
        java.lang.reflect.Member jm = f.javaMember;
        if (jm instanceof java.lang.reflect.Method m) {
            Type recvT = typed.typeOf(a.target());
            ClassSymbol rc = recvT instanceof ClassType rct && rct.cls().kind == ClassKind.JAVA ? rct.cls() : f.owner;
            Type natural = Jt.subst(f.type, Jt.mapping(recvT.withNullable(false)));
            expr(a.target());
            safe(a.safe() && recvT.nullable(), res, () -> {
                String owner = Jt.internalName(rc);
                String desc = JavaCall.methodDesc(m);
                if (Jt.isInterface(rc)) {
                    code.invokeinterface(owner, m.getName(), desc);
                } else {
                    code.invokevirtual(owner, m.getName(), desc);
                }
                JavaCall.fromJava(this, m.getReturnType(), natural);
                coerce(natural, res);
            });
        } else if (jm instanceof java.lang.reflect.Field fd) {
            Type natural = f.type;
            String owner = Jt.internalName(f.owner);
            String desc = JavaCall.typeDesc(fd.getType());
            if (f.isStatic) {
                code.getstatic(owner, fd.getName(), desc);
                JavaCall.fromJava(this, fd.getType(), natural);
                coerce(natural, res);
            } else {
                Type recvT = typed.typeOf(a.target());
                Type nat = Jt.subst(f.type, Jt.mapping(recvT.withNullable(false)));
                expr(a.target());
                safe(a.safe() && recvT.nullable(), res, () -> {
                    code.getfield(owner, fd.getName(), desc);
                    JavaCall.fromJava(this, fd.getType(), nat);
                    coerce(nat, res);
                });
            }
        } else {
            throw new UnsupportedException("membre Java '" + f.name + "' non supporté", a.pos());
        }
    }

    private void construct(Call c, CallInfo ci) {
        if (ci.cls().kind == ClassKind.JAVA) {
            javaConstruct(c, ci);
            return;
        }
        String internal = Jt.internalName(ci.cls());
        FuncSymbol ctor = ci.func();
        if (ctor != null && containsLoop(c.args())) {
            int[] tmp = spillArgs(ctor, c.args(), ci.bindings());
            code.anew(internal);
            code.dup('A');
            reload(ctor, tmp);
        } else {
            code.anew(internal);
            code.dup('A');
            if (ctor != null) {
                pushArgs(ctor, c.args(), ci.bindings());
            }
        }
        code.invokespecial(internal, "<init>", ctorDesc(ctor));
    }

    private void conversion(Call c, CallInfo ci) {
        Expr arg = c.args().get(0).value();
        expr(arg);
        boxAny(typed.typeOf(arg));
        switch (ci.cls().name) {
            case "int": code.invokestatic(Jt.RT, "toInt", "(Ljava/lang/Object;)J"); break;
            case "float": code.invokestatic(Jt.RT, "toFloat", "(Ljava/lang/Object;)D"); break;
            case "bool": code.invokestatic(Jt.RT, "toBool", "(Ljava/lang/Object;)Z"); break;
            default: code.invokestatic(Jt.RT, "str", "(Ljava/lang/Object;)Ljava/lang/String;");
        }
    }

    // ---- membres ----------------------------------------------------------------------

    private void attribute(Attribute a) {
        TypedProgram.MemberInfo mi = typed.members.get(a);
        if (mi == null) {
            throw new UnsupportedException("accès à '" + a.name() + "' non supporté par le backend JVM", a.pos());
        }
        VarSymbol f = mi.field();
        Type res = typed.typeOf(a);
        if (f.owner != null && f.owner.kind == ClassKind.JAVA) {
            javaAttribute(a, f, res);
            return;
        }
        if (f.isStatic) {
            code.getstatic(Jt.internalName(f.owner), f.name, Jt.desc(f.type));
            coerce(f.type, res);
            return;
        }
        Type recvT = typed.typeOf(a.target());
        expr(a.target());
        safe(a.safe() && recvT.nullable(), res, () -> {
            if (f.kind == VarKind.PROPERTY && f.owner.kind == ClassKind.BUILTIN
                    && !f.owner.name.equals("Entry") && !f.owner.name.equals("Exception")) {
                RuntimeMap.RtCall rc = RuntimeMap.property(f);
                code.invokestatic(Jt.RT, rc.name(), rc.desc());
                coerce(f.type, res);
            } else {
                code.getfield(Jt.internalName(f.owner), f.name, Jt.desc(f.type));
                coerce(f.type, res);
            }
        });
    }

    private void index(Index ix) {
        Type tt = typed.typeOf(ix.target());
        Type res = typed.typeOf(ix);
        Expr idx = ix.indices().get(0);
        expr(ix.target());
        boolean isList = Jt.isBuiltin(tt, "list");
        if (idx instanceof Slice sl) {
            bound(sl.lower());
            bound(sl.upper());
            bound(sl.step());
            if (isList) {
                code.invokestatic(Jt.RT, "list_slice", "(Ljava/util/List;Ljava/lang/Long;Ljava/lang/Long;Ljava/lang/Long;)Ljava/util/List;");
            } else {
                code.invokestatic(Jt.RT, "str_slice", "(Ljava/lang/String;Ljava/lang/Long;Ljava/lang/Long;Ljava/lang/Long;)Ljava/lang/String;");
            }
            return;
        }
        if (isList) {
            expr(idx);
            getItemCall(true);
            coerce(ERASED, res);
        } else if (Jt.isBuiltin(tt, "map")) {
            Type kt = ((ClassType) tt).args().get(0);
            exprAs(idx, kt);
            boxAny(kt);
            getItemCall(false);
            coerce(ERASED, res);
        } else if (Jt.isStr(tt)) {
            expr(idx);
            code.invokestatic(Jt.RT, "str_getItem", "(Ljava/lang/String;J)Ljava/lang/String;");
        } else {
            throw new UnsupportedException("indexation de " + tt + " non supportée", ix.pos());
        }
    }

    private void bound(Expr b) {
        if (b == null) {
            code.aconstNull();
        } else {
            expr(b);
            box('J');
        }
    }

    // ---- collections ---------------------------------------------------------------------

    private void literal(List<Expr> elems, String impl, String iface, Type elemType) {
        code.anew(impl);
        code.dup('A');
        code.invokespecial(impl, "<init>", "()V");
        for (Expr el : elems) {
            code.dup('A');
            exprAs(el, elemType);
            boxAny(elemType);
            code.invokeinterface(iface, "add", "(Ljava/lang/Object;)Z");
            code.pop('I');
        }
    }

    private void mapLiteral(MapLit m) {
        Type kt = elemOf(m, 0);
        Type vt = elemOf(m, 1);
        code.anew("java/util/LinkedHashMap");
        code.dup('A');
        code.invokespecial("java/util/LinkedHashMap", "<init>", "()V");
        for (MapEntry en : m.entries()) {
            code.dup('A');
            exprAs(en.key(), kt);
            boxAny(kt);
            exprAs(en.value(), vt);
            boxAny(vt);
            code.invokeinterface("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
            code.pop('A');
        }
    }

    /** L'accumulateur est au sommet de la pile ; ajoute l'élément. */
    private void addElement(Expr element, Type elemType, boolean isSet) {
        exprAs(element, elemType);
        boxAny(elemType);
        code.invokeinterface(isSet ? "java/util/Set" : "java/util/List", "add", "(Ljava/lang/Object;)Z");
        code.pop('I');
    }

    private void comprehension(Expr whole, List<CompClause> clauses, Runnable element) {
        Type t = typed.typeOf(whole);
        boolean isMap = Jt.isBuiltin(t, "map");
        boolean isSet = Jt.isBuiltin(t, "set");
        String impl = isMap ? "java/util/LinkedHashMap" : isSet ? "java/util/LinkedHashSet" : "java/util/ArrayList";
        code.anew(impl);
        code.dup('A');
        code.invokespecial(impl, "<init>", "()V");
        int acc = temp('A');
        code.store('A', acc);
        loopClause(clauses, 0, () -> {
            code.load('A', acc);
            element.run();
        });
        code.load('A', acc);
    }

    private void loopClause(List<CompClause> clauses, int idx, Runnable emit) {
        if (idx == clauses.size()) {
            emit.run();
            return;
        }
        CompClause c = clauses.get(idx);
        expr(c.iterable());
        boxAny(typed.typeOf(c.iterable()));
        code.invokestatic(Jt.RT, "iter", "(Ljava/lang/Object;)Ljava/util/Iterator;");
        int it = temp('A');
        code.store('A', it);
        Label top = new Label();
        Label done = new Label();
        code.label(top);
        code.load('A', it);
        code.invokeinterface("java/util/Iterator", "hasNext", "()Z");
        code.jump(IFEQ, done);
        code.load('A', it);
        code.invokeinterface("java/util/Iterator", "next", "()Ljava/lang/Object;");
        bindLoopVars(typed.declared.get(c));
        for (Expr cond : c.conditions()) {
            jumpIfFalse(cond, top);
        }
        loopClause(clauses, idx + 1, emit);
        code.jump(GOTO, top);
        code.label(done);
    }
}
