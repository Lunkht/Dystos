package com.vulsoft.dystos.sema;

import static com.vulsoft.dystos.sema.Builtins.*;

import com.vulsoft.dystos.ast.Ast.*;
import com.vulsoft.dystos.sema.Symbols.ClassKind;
import com.vulsoft.dystos.sema.Symbols.ClassSymbol;
import com.vulsoft.dystos.sema.Symbols.FuncGroup;
import com.vulsoft.dystos.sema.Symbols.FuncSymbol;
import com.vulsoft.dystos.sema.Symbols.ModuleSymbol;
import com.vulsoft.dystos.sema.Symbols.ParamSym;
import com.vulsoft.dystos.sema.Symbols.Symbol;
import com.vulsoft.dystos.sema.Symbols.VarKind;
import com.vulsoft.dystos.sema.Symbols.VarSymbol;
import com.vulsoft.dystos.sema.Type.ClassType;
import com.vulsoft.dystos.sema.Type.ErrorType;
import com.vulsoft.dystos.sema.Type.FunctionType;
import com.vulsoft.dystos.sema.Type.NullType;
import com.vulsoft.dystos.sema.Type.TypeVar;
import com.vulsoft.dystos.sema.Type.UnitType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Vérification des corps : résolution des noms, typage des expressions et des instructions. */
final class Checker {

    private final Ctx ctx;
    private final ModuleSymbol module;

    private ClassSymbol currentClass;
    private Type returnType = Type.UNIT;
    private boolean inInit;
    private boolean inFunction;
    private int loopDepth;
    private final Set<MatchStmt> exhaustiveMatches = Collections.newSetFromMap(new IdentityHashMap<>());

    Checker(Ctx ctx, ModuleSymbol module) {
        this.ctx = ctx;
        this.module = module;
    }

    // ------------------------------------------------------------------
    // Points d'entrée
    // ------------------------------------------------------------------

    void checkFunction(FuncSymbol f, FuncDecl d, Scope declScope) {
        if (d.body() == null) {
            return;
        }
        Scope s = new Scope(declScope);
        VarSymbol selfSym = null;
        if (f.owner != null && !f.isStatic) {
            selfSym = new VarSymbol("self", f.owner.thisType(), false, VarKind.PARAM);
            s.declare(selfSym);
        }
        int start = f.astOffset;
        List<Param> declared = d.params();
        List<VarSymbol> paramSyms = new ArrayList<>();
        for (int i = 0; i < f.params.size(); i++) {
            ParamSym p = f.params.get(i);
            VarSymbol pv = new VarSymbol(p.name(), p.type(), false, VarKind.PARAM);
            s.declare(pv);
            paramSyms.add(pv);
            Param ast = declared.get(i + start);
            if (ast.defaultValue() != null) {
                Type dt = expr(ast.defaultValue(), declScope, p.type());
                requireAssignable(dt, p.type(), ast.defaultValue().pos());
            }
        }
        ctx.typed.funcs.put(d, new TypedProgram.FuncInfo(selfSym, paramSyms));

        ClassSymbol savedClass = currentClass;
        Type savedReturn = returnType;
        boolean savedInit = inInit;
        boolean savedIn = inFunction;
        int savedLoop = loopDepth;
        currentClass = f.owner;
        returnType = f.returnType;
        inInit = f.isConstructor;
        inFunction = true;
        loopDepth = 0;
        block(d.body(), s);
        if (!(returnType instanceof UnitType) && !(returnType instanceof ErrorType) && !alwaysReturns(d.body())) {
            ctx.error("E0208", "la fonction '" + f.name + "' doit retourner une valeur de type '"
                    + returnType + "' dans tous les cas", d.pos());
        }
        currentClass = savedClass;
        returnType = savedReturn;
        inInit = savedInit;
        inFunction = savedIn;
        loopDepth = savedLoop;
    }

    void checkScript(List<Stmt> stmts, Scope scope) {
        inFunction = true;
        returnType = Type.UNIT;
        currentClass = null;
        loopDepth = 0;
        for (Stmt st : stmts) {
            stmt(st, scope);
        }
    }

    /** Type d'une valeur initiale de champ sans annotation. */
    Type inferFieldType(String name, Expr init, Scope scope, ClassSymbol owner) {
        currentClass = owner;
        Type t = expr(init, scope, null);
        if (t instanceof NullType) {
            ctx.error("E0207", "impossible de déduire le type de '" + name + "' à partir de 'null' : ajoutez une annotation", init.pos());
            return Type.ERROR;
        }
        if (t instanceof UnitType) {
            ctx.error("E0207", "'" + name + "' est initialisé avec une expression sans valeur", init.pos());
            return Type.ERROR;
        }
        return t;
    }

    void checkFieldInit(VarSymbol v, Expr init, Scope scope, ClassSymbol owner) {
        currentClass = owner;
        Type t = expr(init, scope, v.type);
        requireAssignable(t, v.type, init.pos());
    }

    // ------------------------------------------------------------------
    // Instructions
    // ------------------------------------------------------------------

    private void block(List<Stmt> body, Scope s) {
        for (Stmt st : body) {
            stmt(st, s);
        }
    }

    private void stmt(Stmt st, Scope s) {
        if (st instanceof ExprStmt e) {
            expr(e.expr(), s, null);
        } else if (st instanceof Assign a) {
            assign(a, s);
        } else if (st instanceof VarDecl d) {
            varDecl(d, s);
        } else if (st instanceof IfStmt i) {
            condition(i.cond(), s);
            block(i.then(), new Scope(s));
            block(i.orElse(), new Scope(s));
        } else if (st instanceof WhileStmt w) {
            condition(w.cond(), s);
            loopDepth++;
            block(w.body(), new Scope(s));
            loopDepth--;
        } else if (st instanceof ForStmt f) {
            forStmt(f, s);
        } else if (st instanceof ReturnStmt r) {
            returnStmt(r, s);
        } else if (st instanceof BreakStmt b) {
            if (loopDepth == 0) {
                ctx.error("E0215", "'break' en dehors d'une boucle", b.pos());
            }
        } else if (st instanceof ContinueStmt c) {
            if (loopDepth == 0) {
                ctx.error("E0215", "'continue' en dehors d'une boucle", c.pos());
            }
        } else if (st instanceof PassStmt) {
            return;
        } else if (st instanceof RaiseStmt r) {
            Type t = expr(r.value(), s, null);
            if (!(t instanceof ErrorType) && !Types.isAssignable(t, EXCEPTION_T)) {
                ctx.error("E0213", "'raise' attend une Exception, trouvé '" + t + "'", r.value().pos());
            }
        } else if (st instanceof AssertStmt a) {
            condition(a.cond(), s);
            if (a.message() != null) {
                expr(a.message(), s, null);
            }
        } else if (st instanceof TryStmt t) {
            tryStmt(t, s);
        } else if (st instanceof WithStmt w) {
            withStmt(w, s);
        } else if (st instanceof MatchStmt m) {
            matchStmt(m, s);
        } else if (st instanceof FuncDecl f) {
            ctx.error("E0399", "les fonctions locales ne sont pas encore supportées", f.pos());
        } else {
            throw new IllegalStateException("instruction inconnue : " + st);
        }
    }

    private void condition(Expr e, Scope s) {
        Type t = expr(e, s, BOOL_T);
        if (!(t instanceof ErrorType) && !Types.isBool(t)) {
            ctx.error("E0204", "la condition doit être de type 'bool', trouvé '" + t + "'", e.pos());
        }
    }

    private void varDecl(VarDecl d, Scope s) {
        Type declared = d.type() != null ? ctx.resolveType(d.type(), s) : null;
        Type init = d.init() != null ? expr(d.init(), s, declared) : null;
        Type type;
        if (declared != null) {
            type = declared;
            if (init != null) {
                requireAssignable(init, declared, d.init().pos());
            }
        } else {
            type = inferred(d.name(), init, d.init().pos());
        }
        VarSymbol v = new VarSymbol(d.name(), type, d.mutability() != Mutability.VAL, VarKind.LOCAL);
        ctx.typed.declare(d, v);
        if (!s.declare(v)) {
            ctx.error("E0102", "'" + d.name() + "' est déjà défini dans cette portée", d.pos());
        }
    }

    private Type inferred(String name, Type init, Pos pos) {
        if (init instanceof NullType) {
            ctx.error("E0207", "impossible de déduire le type de '" + name + "' à partir de 'null' : ajoutez une annotation", pos);
            return Type.ERROR;
        }
        if (init instanceof UnitType) {
            ctx.error("E0207", "'" + name + "' est initialisé avec une expression sans valeur", pos);
            return Type.ERROR;
        }
        return init;
    }

    private void assign(Assign a, Scope s) {
        Expr target = a.target();
        if (target instanceof Name n) {
            Symbol sym = ctx.lookup(s, n.name());
            if (sym != null) {
                ctx.typed.names.put(n, sym);
                if (sym instanceof VarSymbol rv) {
                    ctx.typed.reassigned.add(rv);
                }
            }
            if (sym == null) {
                if (a.op() != AssignOp.ASSIGN) {
                    ctx.error("E0101", "nom inconnu '" + n.name() + "'." + Suggest.hint(n.name(), s.allNames()), n.pos());
                    expr(a.value(), s, null);
                    return;
                }
                Type vt = expr(a.value(), s, null);
                VarSymbol nv = new VarSymbol(n.name(), inferred(n.name(), vt, a.value().pos()), true, VarKind.LOCAL);
                s.declare(nv);
                ctx.typed.declare(a, nv);
                return;
            }
            if (!(sym instanceof VarSymbol v)) {
                ctx.error("E0212", "'" + n.name() + "' n'est pas une variable et ne peut pas être affecté", n.pos());
                expr(a.value(), s, null);
                return;
            }
            if (!v.mutable) {
                ctx.error("E0212", "impossible de réaffecter '" + n.name() + "' : "
                        + (v.kind == VarKind.PARAM ? "les paramètres sont immuables" : "déclaré avec 'val'"), n.pos());
            }
            assignValue(a, v.type, s);
        } else if (target instanceof Attribute at) {
            attributeAssign(a, at, s);
        } else if (target instanceof Index ix) {
            indexAssign(a, ix, s);
        } else {
            ctx.error("E0212", "cible d'affectation invalide", target.pos());
        }
    }

    private void assignValue(Assign a, Type targetType, Scope s) {
        if (a.op() == AssignOp.ASSIGN) {
            Type vt = expr(a.value(), s, targetType);
            requireAssignable(vt, targetType, a.value().pos());
        } else {
            Type vt = expr(a.value(), s, null);
            BinaryOp op = switch (a.op()) {
                case ADD -> BinaryOp.ADD;
                case SUB -> BinaryOp.SUB;
                case MUL -> BinaryOp.MUL;
                case DIV -> BinaryOp.DIV;
                default -> BinaryOp.MOD;
            };
            Type rt = arithmetic(op, targetType, vt, a.pos());
            requireAssignable(rt, targetType, a.value().pos());
        }
    }

    private void attributeAssign(Assign a, Attribute at, Scope s) {
        Object tgt = symbolTarget(at.target(), s);
        if (tgt instanceof ModuleSymbol || tgt instanceof ClassSymbol) {
            ctx.error("E0212", "impossible d'affecter '" + at.name() + "' : membre de module ou de classe", at.pos());
            expr(a.value(), s, null);
            return;
        }
        Type rt = expr(at.target(), s, null);
        if (rt instanceof ErrorType) {
            expr(a.value(), s, null);
            return;
        }
        if (rt.nullable()) {
            ctx.error("E0201", "affectation à '." + at.name() + "' sur un type nullable '" + rt + "'", at.pos());
        }
        Type base = Types.nonNull(rt);
        FieldRef ref = base instanceof ClassType ct ? findField(ct, at.name()) : null;
        if (ref == null) {
            ctx.error("E0209", "champ inconnu '" + at.name() + "' pour le type '" + rt
                    + "' (les champs se déclarent dans le corps de la classe)", at.pos());
            expr(a.value(), s, null);
            return;
        }
        checkPrivate(ref.sym.name, ref.sym.owner, at.pos());
        ctx.typed.members.put(at, new TypedProgram.MemberInfo(ref.sym));
        boolean sameClass = inInit && currentClass != null && ref.sym.owner == currentClass;
        if (!ref.sym.mutable && !sameClass) {
            ctx.error("E0212", "impossible de modifier '" + at.name() + "' : champ immuable", at.pos());
        }
        assignValue(a, ref.type, s);
    }

    private void indexAssign(Assign a, Index ix, Scope s) {
        Type t = expr(ix.target(), s, null);
        if (ix.indices().size() != 1 || ix.indices().get(0) instanceof Slice) {
            ctx.error("E0211", "affectation par indice : un seul indice attendu", ix.pos());
            expr(a.value(), s, null);
            return;
        }
        Expr idx = ix.indices().get(0);
        if (t instanceof ErrorType) {
            expr(idx, s, null);
            expr(a.value(), s, null);
            return;
        }
        if (t instanceof ClassType ct && !ct.nullable() && ct.cls() == LIST) {
            Type it = expr(idx, s, INT_T);
            requireAssignable(it, INT_T, idx.pos());
            assignValue(a, ct.args().get(0), s);
        } else if (t instanceof ClassType ct && !ct.nullable() && ct.cls() == MAP) {
            Type kt = expr(idx, s, ct.args().get(0));
            requireAssignable(kt, ct.args().get(0), idx.pos());
            assignValue(a, ct.args().get(1), s);
        } else {
            ctx.error("E0211", "le type '" + t + "' ne permet pas l'affectation par indice", ix.pos());
            expr(idx, s, null);
            expr(a.value(), s, null);
        }
    }

    private void returnStmt(ReturnStmt r, Scope s) {
        if (!inFunction) {
            ctx.error("E0208", "'return' en dehors d'une fonction", r.pos());
            return;
        }
        if (r.value() == null) {
            if (!(returnType instanceof UnitType) && !(returnType instanceof ErrorType)) {
                ctx.error("E0208", "'return' sans valeur dans une fonction qui retourne '" + returnType + "'", r.pos());
            }
            return;
        }
        Type t = expr(r.value(), s, returnType instanceof UnitType ? null : returnType);
        if (returnType instanceof UnitType) {
            ctx.error("E0208", "cette fonction ne déclare pas de type de retour : ajoutez '-> " + t + "'", r.value().pos());
            return;
        }
        requireAssignable(t, returnType, r.value().pos());
    }

    private void forStmt(ForStmt f, Scope s) {
        Type it = expr(f.iterable(), s, null);
        Type elem = elementType(it, f.iterable().pos());
        List<Type> types = new ArrayList<>();
        if (f.vars().size() == 1) {
            types.add(elem);
        } else if (elem instanceof ErrorType) {
            for (int i = 0; i < f.vars().size(); i++) {
                types.add(Type.ERROR);
            }
        } else if (f.vars().size() == 2 && elem instanceof ClassType ec && ec.cls() == ENTRY) {
            types.add(ec.args().get(0));
            types.add(ec.args().get(1));
        } else {
            ctx.error("E0210", "impossible de déstructurer '" + elem + "' en " + f.vars().size()
                    + " variables (utilisez items() ou enumerate())", f.pos());
            for (int i = 0; i < f.vars().size(); i++) {
                types.add(Type.ERROR);
            }
        }
        Scope body = new Scope(s);
        for (int i = 0; i < f.vars().size(); i++) {
            VarSymbol fv = new VarSymbol(f.vars().get(i), types.get(i), false, VarKind.LOCAL);
            ctx.typed.declare(f, fv);
            if (!body.declare(fv)) {
                ctx.error("E0102", "variable de boucle '" + f.vars().get(i) + "' en double", f.pos());
            }
        }
        loopDepth++;
        block(f.body(), body);
        loopDepth--;
    }

    private Type elementType(Type it, Pos pos) {
        if (it instanceof ErrorType) {
            return Type.ERROR;
        }
        if (it.nullable()) {
            ctx.error("E0201", "impossible de parcourir une valeur nullable '" + it + "'", pos);
            return Type.ERROR;
        }
        if (it instanceof ClassType c) {
            if (c.cls() == LIST || c.cls() == SET || c.cls() == MAP) {
                return c.args().get(0);
            }
            if (c.cls() == STR) {
                return STR_T;
            }
            ClassSymbol iterable = ctx.java.load("java.lang.Iterable");
            if (iterable != null) {
                ClassType sup = Types.asSuper(c, iterable);
                if (sup != null && sup.args().size() == 1) {
                    return sup.args().get(0);
                }
            }
        }
        ctx.error("E0210", "le type '" + it + "' n'est pas itérable", pos);
        return Type.ERROR;
    }

    private void tryStmt(TryStmt t, Scope s) {
        block(t.body(), new Scope(s));
        for (ExceptClause h : t.handlers()) {
            Scope hs = new Scope(s);
            Type et = EXCEPTION_T;
            if (h.type() != null) {
                et = ctx.resolveType(h.type(), s);
                if (!(et instanceof ErrorType) && !Types.isAssignable(et, EXCEPTION_T)) {
                    ctx.error("E0213", "'except' attend un type d'exception, trouvé '" + et + "'", h.type().pos());
                    et = Type.ERROR;
                }
            }
            ctx.typed.exceptTypes.put(h, et);
            if (h.name() != null) {
                VarSymbol hv = new VarSymbol(h.name(), Types.nonNull(et), false, VarKind.LOCAL);
                ctx.typed.declare(h, hv);
                hs.declare(hv);
            }
            block(h.body(), hs);
        }
        if (t.finallyBody() != null) {
            block(t.finallyBody(), new Scope(s));
        }
    }

    private void withStmt(WithStmt w, Scope s) {
        Type ct = expr(w.context(), s, null);
        if (!(ct instanceof ErrorType)) {
            boolean hasClose = ct instanceof ClassType c && !c.nullable()
                    && !methodCandidates(c, "close", false, w.pos()).isEmpty();
            if (!hasClose) {
                ctx.error("E0214", "l'objet de 'with' doit avoir une méthode close() (type '" + ct + "')", w.context().pos());
            }
        }
        Scope ws = new Scope(s);
        if (w.name() != null) {
            VarSymbol wv = new VarSymbol(w.name(), ct, false, VarKind.LOCAL);
            ctx.typed.declare(w, wv);
            ws.declare(wv);
        }
        block(w.body(), ws);
    }

    private void matchStmt(MatchStmt m, Scope s) {
        Type subject = expr(m.subject(), s, null);
        boolean wildcard = false;
        Set<String> covered = new HashSet<>();
        for (MatchCase c : m.cases()) {
            for (Expr p : c.patterns()) {
                if (p instanceof Name n && n.name().equals("_")) {
                    wildcard = true;
                    continue;
                }
                Type pt = expr(p, s, subject);
                if (!(pt instanceof ErrorType) && !(subject instanceof ErrorType)
                        && !Types.isAssignable(pt, subject) && !Types.isAssignable(subject, pt)) {
                    ctx.error("E0202", "motif de type '" + pt + "' incompatible avec le sujet de type '" + subject + "'", p.pos());
                }
                if (p instanceof Attribute at) {
                    covered.add(at.name());
                }
            }
            block(c.body(), new Scope(s));
        }
        boolean exhaustive = wildcard;
        if (!exhaustive && subject instanceof ClassType sc && sc.cls().kind == ClassKind.ENUM) {
            exhaustive = covered.containsAll(sc.cls().enumConstants);
        }
        if (exhaustive) {
            exhaustiveMatches.add(m);
        }
    }

    // ------------------------------------------------------------------
    // Retour dans tous les cas
    // ------------------------------------------------------------------

    private boolean alwaysReturns(List<Stmt> body) {
        for (Stmt st : body) {
            if (terminates(st)) {
                return true;
            }
        }
        return false;
    }

    private boolean terminates(Stmt st) {
        if (st instanceof ReturnStmt || st instanceof RaiseStmt) {
            return true;
        }
        if (st instanceof IfStmt i) {
            return !i.orElse().isEmpty() && alwaysReturns(i.then()) && alwaysReturns(i.orElse());
        }
        if (st instanceof TryStmt t) {
            if (t.finallyBody() != null && alwaysReturns(t.finallyBody())) {
                return true;
            }
            if (!alwaysReturns(t.body())) {
                return false;
            }
            for (ExceptClause h : t.handlers()) {
                if (!alwaysReturns(h.body())) {
                    return false;
                }
            }
            return true;
        }
        if (st instanceof MatchStmt m) {
            if (!exhaustiveMatches.contains(m)) {
                return false;
            }
            for (MatchCase c : m.cases()) {
                if (!alwaysReturns(c.body())) {
                    return false;
                }
            }
            return true;
        }
        if (st instanceof WithStmt w) {
            return alwaysReturns(w.body());
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Compatibilité
    // ------------------------------------------------------------------

    private void requireAssignable(Type from, Type to, Pos pos) {
        if (!Types.isAssignable(from, to)) {
            ctx.error(Ctx.codeFor(to, from), Ctx.mismatch(to, from), pos);
        }
    }

    private void checkPrivate(String name, ClassSymbol owner, Pos pos) {
        if (Symbols.isPrivateName(name) && owner != null && owner != currentClass && owner.kind != ClassKind.JAVA) {
            ctx.error("E0104", "'" + name + "' est privé dans '" + owner.name + "'", pos);
        }
    }

    // ------------------------------------------------------------------
    // Expressions
    // ------------------------------------------------------------------

    Type expr(Expr e, Scope s, Type expected) {
        Type t = exprInner(e, s, expected);
        ctx.typed.types.put(e, t);
        return t;
    }

    private Type exprInner(Expr e, Scope s, Type expected) {
        if (e instanceof IntLit) {
            return INT_T;
        }
        if (e instanceof FloatLit) {
            return FLOAT_T;
        }
        if (e instanceof StringLit) {
            return STR_T;
        }
        if (e instanceof BoolLit) {
            return BOOL_T;
        }
        if (e instanceof NullLit) {
            return Type.NULL;
        }
        if (e instanceof FStringLit f) {
            for (FStringPart p : f.parts()) {
                if (p instanceof FExpr fe) {
                    Type t = expr(fe.expr(), s, null);
                    if (t instanceof UnitType) {
                        ctx.error("E0202", "une expression sans valeur ne peut pas être insérée dans une f-string", f.pos());
                    }
                }
            }
            return STR_T;
        }
        if (e instanceof Name n) {
            return name(n, s);
        }
        if (e instanceof Binary b) {
            return binary(b, s);
        }
        if (e instanceof Unary u) {
            return unary(u, s);
        }
        if (e instanceof Conditional c) {
            condition(c.cond(), s);
            Type a = expr(c.thenValue(), s, expected);
            Type b = expr(c.elseValue(), s, expected);
            return Types.join(a, b);
        }
        if (e instanceof Call c) {
            return call(c, s, expected);
        }
        if (e instanceof Attribute a) {
            return attribute(a, s);
        }
        if (e instanceof Index i) {
            return index(i, s);
        }
        if (e instanceof Slice sl) {
            ctx.error("E0211", "une tranche n'est autorisée qu'entre crochets", sl.pos());
            return Type.ERROR;
        }
        if (e instanceof ListLit l) {
            return listLit(l, s, expected);
        }
        if (e instanceof SetLit l) {
            return setLit(l, s, expected);
        }
        if (e instanceof MapLit m) {
            return mapLit(m, s, expected);
        }
        if (e instanceof Lambda l) {
            return lambda(l, s, expected);
        }
        if (e instanceof ListComp c) {
            Scope cs = comprehensionScope(c.clauses(), s);
            return ty(LIST, elementOf(expr(c.element(), cs, null), c.element().pos()));
        }
        if (e instanceof SetComp c) {
            Scope cs = comprehensionScope(c.clauses(), s);
            return ty(SET, elementOf(expr(c.element(), cs, null), c.element().pos()));
        }
        if (e instanceof MapComp c) {
            Scope cs = comprehensionScope(c.clauses(), s);
            Type k = elementOf(expr(c.key(), cs, null), c.key().pos());
            Type v = elementOf(expr(c.value(), cs, null), c.value().pos());
            return ty(MAP, k, v);
        }
        if (e instanceof GeneratorExp c) {
            Scope cs = comprehensionScope(c.clauses(), s);
            return ty(LIST, elementOf(expr(c.element(), cs, null), c.element().pos()));
        }
        throw new IllegalStateException("expression inconnue : " + e);
    }

    private Type elementOf(Type t, Pos pos) {
        if (t instanceof NullType || t instanceof UnitType) {
            ctx.error("E0207", "impossible de déduire le type des éléments", pos);
            return Type.ERROR;
        }
        return t;
    }

    private void compVar(Scope inner, CompClause c, String name, Type t) {
        VarSymbol v = new VarSymbol(name, t, false, VarKind.LOCAL);
        ctx.typed.declare(c, v);
        inner.declare(v);
    }

    private Scope comprehensionScope(List<CompClause> clauses, Scope s) {
        Scope cur = s;
        for (CompClause c : clauses) {
            Type it = expr(c.iterable(), cur, null);
            Type elem = elementType(it, c.iterable().pos());
            Scope inner = new Scope(cur);
            if (c.vars().size() == 1) {
                compVar(inner, c, c.vars().get(0), elem);
            } else if (c.vars().size() == 2 && elem instanceof ClassType ec && ec.cls() == ENTRY) {
                compVar(inner, c, c.vars().get(0), ec.args().get(0));
                compVar(inner, c, c.vars().get(1), ec.args().get(1));
            } else {
                if (!(elem instanceof ErrorType)) {
                    ctx.error("E0210", "impossible de déstructurer '" + elem + "' en " + c.vars().size() + " variables", c.pos());
                }
                for (String v : c.vars()) {
                    compVar(inner, c, v, Type.ERROR);
                }
            }
            for (Expr cond : c.conditions()) {
                condition(cond, inner);
            }
            cur = inner;
        }
        return cur;
    }

    // ---- noms -------------------------------------------------------

    // ---- capture des variables par les lambdas -----------------------------------

    private static final class LambdaFrame {
        final Scope scope;
        final java.util.LinkedHashSet<VarSymbol> captured = new java.util.LinkedHashSet<>();

        LambdaFrame(Scope scope) {
            this.scope = scope;
        }
    }

    private final java.util.ArrayDeque<LambdaFrame> lambdaFrames = new java.util.ArrayDeque<>();

    /** La variable est-elle déclarée entre {@code from} et la portée {@code boundary} (incluse) ? */
    private static boolean declaredWithin(VarSymbol v, Scope from, Scope boundary) {
        for (Scope sc = from; sc != null; sc = sc.parent()) {
            if (sc.lookupLocal(v.name) == v) {
                return true;
            }
            if (sc == boundary) {
                return false;
            }
        }
        return false;
    }

    private void noteCapture(VarSymbol v, Scope from) {
        if (v.kind != VarKind.LOCAL && v.kind != VarKind.PARAM) {
            return;
        }
        for (LambdaFrame f : lambdaFrames) {
            if (!declaredWithin(v, from, f.scope)) {
                f.captured.add(v);
            }
        }
    }

    private Type name(Name n, Scope s) {
        Symbol sym = ctx.lookup(s, n.name());
        if (sym != null) {
            ctx.typed.names.put(n, sym);
            if (sym instanceof VarSymbol cv && !lambdaFrames.isEmpty()) {
                noteCapture(cv, s);
            }
        }
        if (sym == null) {
            ctx.error("E0101", "nom inconnu '" + n.name() + "'." + Suggest.hint(n.name(), s.allNames()), n.pos());
            return Type.ERROR;
        }
        if (sym instanceof VarSymbol v) {
            return v.type;
        }
        if (sym instanceof FuncGroup g) {
            if (g.funcs.size() == 1) {
                return functionValue(g.funcs.get(0));
            }
            ctx.error("E0205", "'" + n.name() + "' a plusieurs surcharges : appelez-la directement", n.pos());
            return Type.ERROR;
        }
        if (sym instanceof ClassSymbol) {
            ctx.error("E0206", "'" + n.name() + "' est une classe : appelez-la ou accédez à l'un de ses membres", n.pos());
            return Type.ERROR;
        }
        ctx.error("E0206", "'" + n.name() + "' est un module : accédez à l'un de ses membres", n.pos());
        return Type.ERROR;
    }

    private static Type functionValue(FuncSymbol f) {
        List<Type> ps = new ArrayList<>();
        for (ParamSym p : f.params) {
            ps.add(p.type());
        }
        return new FunctionType(ps, f.returnType, false);
    }

    /** Si l'expression désigne un symbole non-valeur (module, classe, fonction), le retourne ; sinon null. */
    private Symbol symbolTarget(Expr e, Scope s) {
        if (e instanceof Name n) {
            Symbol sym = ctx.lookup(s, n.name());
            return sym instanceof VarSymbol ? null : sym;
        }
        if (e instanceof Attribute a && !a.safe()) {
            Symbol inner = symbolTarget(a.target(), s);
            if (inner instanceof ModuleSymbol m) {
                Symbol member = m.members.get(a.name());
                return member instanceof VarSymbol ? null : member;
            }
        }
        return null;
    }

    // ---- opérateurs -------------------------------------------------

    private Type unary(Unary u, Scope s) {
        Type t = expr(u.operand(), s, null);
        if (t instanceof ErrorType) {
            return u.op() == UnaryOp.NOT ? BOOL_T : Type.ERROR;
        }
        if (u.op() == UnaryOp.NOT) {
            if (!Types.isBool(t)) {
                ctx.error("E0203", "'not' attend un 'bool', trouvé '" + t + "'", u.pos());
            }
            return BOOL_T;
        }
        if (!Types.isNumeric(t)) {
            ctx.error("E0203", "opérateur unaire '" + (u.op() == UnaryOp.NEG ? "-" : "+")
                    + "' non applicable à '" + t + "'", u.pos());
            return Type.ERROR;
        }
        return t;
    }

    private static String opText(BinaryOp op) {
        switch (op) {
            case ADD: return "+";
            case SUB: return "-";
            case MUL: return "*";
            case DIV: return "/";
            case MOD: return "%";
            case POW: return "**";
            case EQ: return "==";
            case NE: return "!=";
            case LT: return "<";
            case GT: return ">";
            case LE: return "<=";
            case GE: return ">=";
            case AND: return "and";
            case OR: return "or";
            case IN: return "in";
            case NOT_IN: return "not in";
            case IS: return "is";
            case IS_NOT: return "is not";
            default: return "?:";
        }
    }

    private Type binary(Binary b, Scope s) {
        BinaryOp op = b.op();
        Type l = expr(b.left(), s, null);
        Type r = expr(b.right(), s, op == BinaryOp.ELVIS && !(l instanceof ErrorType || l instanceof NullType)
                ? Types.nonNull(l) : null);
        boolean bad = l instanceof ErrorType || r instanceof ErrorType;
        switch (op) {
            case ADD: case SUB: case MUL: case DIV: case MOD: case POW:
                return bad ? Type.ERROR : arithmetic(op, l, r, b.pos());
            case EQ: case NE:
                if (!bad && !comparable(l, r)) {
                    ctx.error("E0203", "comparaison impossible entre '" + l + "' et '" + r + "'", b.pos());
                }
                return BOOL_T;
            case LT: case GT: case LE: case GE:
                if (!bad && !((Types.isNumeric(l) && Types.isNumeric(r)) || (Types.isStr(l) && Types.isStr(r)))) {
                    ctx.error("E0203", "opérateur '" + opText(op) + "' non applicable à '" + l + "' et '" + r + "'", b.pos());
                }
                return BOOL_T;
            case AND: case OR:
                if (!bad && !(Types.isBool(l) && Types.isBool(r))) {
                    ctx.error("E0203", "opérateur '" + opText(op) + "' attend des 'bool', trouvé '" + l + "' et '" + r + "'", b.pos());
                }
                return BOOL_T;
            case IN: case NOT_IN:
                if (!bad) {
                    checkContains(l, r, b.pos());
                }
                return BOOL_T;
            case IS: case IS_NOT:
                return BOOL_T;
            default: // ELVIS
                if (l instanceof ErrorType) {
                    return r;
                }
                if (!l.nullable()) {
                    ctx.warning("W0202", "l'opérande gauche de '?:' n'est pas nullable ('" + l + "') : l'opérateur est inutile", b.pos());
                    return l;
                }
                if (r instanceof NullType) {
                    return l;
                }
                return Types.join(Types.nonNull(l), r);
        }
    }

    private boolean comparable(Type l, Type r) {
        if (l instanceof NullType || r instanceof NullType) {
            return true;
        }
        return Types.isAssignable(l, r) || Types.isAssignable(r, l)
                || Types.isAssignable(Types.nonNull(l), Types.nonNull(r))
                || Types.isAssignable(Types.nonNull(r), Types.nonNull(l));
    }

    private void checkContains(Type item, Type container, Pos pos) {
        if (container instanceof ClassType c && !c.nullable()) {
            if (c.cls() == STR) {
                if (!Types.isStr(item)) {
                    ctx.error("E0203", "'in' sur un 'str' attend un 'str', trouvé '" + item + "'", pos);
                }
                return;
            }
            if (c.cls() == LIST || c.cls() == SET || c.cls() == MAP) {
                if (!Types.isAssignable(item, c.args().get(0))) {
                    ctx.error("E0203", "'in' : élément de type '" + item + "' incompatible avec '" + container + "'", pos);
                }
                return;
            }
        }
        ctx.error("E0203", "'in' non applicable au type '" + container + "'", pos);
    }

    /** Résultat d'un opérateur arithmétique. Règles : int op int -> int ; avec float -> float ; '/' donne toujours un float. */
    private Type arithmetic(BinaryOp op, Type l, Type r, Pos pos) {
        if (l instanceof ErrorType || r instanceof ErrorType) {
            return Type.ERROR;
        }
        if (op == BinaryOp.ADD && Types.isStr(l) && Types.isStr(r)) {
            return STR_T;
        }
        if (Types.isNumeric(l) && Types.isNumeric(r)) {
            if (op == BinaryOp.DIV) {
                return FLOAT_T;
            }
            return Types.isInt(l) && Types.isInt(r) ? INT_T : FLOAT_T;
        }
        ctx.error("E0203", "opérateur '" + opText(op) + "' non applicable à '" + l + "' et '" + r + "'"
                + (op == BinaryOp.ADD && (Types.isStr(l) || Types.isStr(r))
                ? " (utilisez une f-string pour assembler du texte)" : ""), pos);
        return Type.ERROR;
    }

    // ---- accès aux membres ----------------------------------------

    private record FieldRef(VarSymbol sym, Type type) {
    }

    private FieldRef findField(ClassType ct, String name) {
        for (ClassType k : Types.linearize(ct)) {
            k.cls().ensureLoaded();
            VarSymbol v = k.cls().fields.get(name);
            if (v != null && !v.isStatic) {
                return new FieldRef(v, Types.substitute(v.type, Types.mapping(k)));
            }
        }
        return null;
    }

    private Type attribute(Attribute a, Scope s) {
        Symbol tgt = symbolTarget(a.target(), s);
        if (tgt instanceof ModuleSymbol m) {
            Symbol member = moduleMember(m, a.name(), a.pos());
            if (member == null) {
                return Type.ERROR;
            }
            if (member instanceof VarSymbol v) {
                ctx.typed.members.put(a, new TypedProgram.MemberInfo(v));
                return v.type;
            }
            if (member instanceof FuncGroup g && g.funcs.size() == 1) {
                return functionValue(g.funcs.get(0));
            }
            ctx.error("E0206", "'" + a.name() + "' ne peut pas être utilisé comme valeur ici", a.pos());
            return Type.ERROR;
        }
        if (tgt instanceof ClassSymbol cs) {
            cs.ensureLoaded();
            VarSymbol v = cs.fields.get(a.name());
            if (v != null && v.isStatic) {
                ctx.typed.members.put(a, new TypedProgram.MemberInfo(v));
                return v.type;
            }
            ctx.error("E0209", "'" + cs.name + "' n'a pas de membre statique '" + a.name() + "'"
                    + Suggest.hint(a.name(), cs.fields.keySet()), a.pos());
            return Type.ERROR;
        }
        if (tgt instanceof FuncGroup) {
            ctx.error("E0209", "une fonction n'a pas de membre '" + a.name() + "'", a.pos());
            return Type.ERROR;
        }
        Type rt = expr(a.target(), s, null);
        return memberValue(rt, a.name(), a.safe(), a.pos(), a);
    }

    private Type memberValue(Type rt, String name, boolean safe, Pos pos, Attribute node) {
        if (rt instanceof ErrorType) {
            return Type.ERROR;
        }
        if (rt instanceof NullType) {
            ctx.error("E0201", "accès à '." + name + "' sur 'null'", pos);
            return Type.ERROR;
        }
        if (rt.nullable() && !safe) {
            ctx.error("E0201", "accès à '." + name + "' sur le type nullable '" + rt + "' : utilisez '?." + name
                    + "' ou vérifiez d'abord que la valeur n'est pas null", pos);
        } else if (!rt.nullable() && safe) {
            ctx.warning("W0201", "'?.' inutile : '" + rt + "' n'est pas nullable", pos);
        }
        Type base = Types.nonNull(rt);
        if (base instanceof TypeVar) {
            base = ANY_T;
        }
        if (!(base instanceof ClassType ct)) {
            ctx.error("E0209", "le type '" + rt + "' n'a pas de membre '" + name + "'", pos);
            return Type.ERROR;
        }
        FieldRef ref = findField(ct, name);
        if (ref == null) {
            if (!methodCandidates(ct, name, false, pos).isEmpty()) {
                ctx.error("E0206", "'" + name + "' est une méthode : appelez-la avec ()", pos);
            } else {
                ctx.error("E0209", "le type '" + ct + "' n'a pas de membre '" + name + "'"
                        + Suggest.hint(name, memberNames(ct)), pos);
            }
            return Type.ERROR;
        }
        checkPrivate(name, ref.sym.owner, pos);
        if (node != null) {
            ctx.typed.members.put(node, new TypedProgram.MemberInfo(ref.sym));
        }
        return rt.nullable() && safe ? ref.type.withNullable(true) : ref.type;
    }

    private Set<String> memberNames(ClassType ct) {
        Set<String> out = new LinkedHashSet<>();
        for (ClassType k : Types.linearize(ct)) {
            k.cls().ensureLoaded();
            out.addAll(k.cls().fields.keySet());
            out.addAll(k.cls().methods.keySet());
        }
        return out;
    }

    private Symbol moduleMember(ModuleSymbol m, String name, Pos pos) {
        Symbol member = m.members.get(name);
        if (member == null) {
            ctx.error("E0103", "le module '" + m.name + "' n'a pas de membre '" + name + "'"
                    + Suggest.hint(name, m.members.keySet()), pos);
            return null;
        }
        if (Symbols.isPrivateName(name) && m != module) {
            ctx.error("E0104", "'" + name + "' est privé dans le module '" + m.name + "'", pos);
            return null;
        }
        return member;
    }

    // ---- indices ----------------------------------------------------

    private Type index(Index i, Scope s) {
        Symbol tgt = symbolTarget(i.target(), s);
        if (tgt instanceof ClassSymbol || tgt instanceof FuncGroup) {
            ctx.error("E0206", "une application de type ('" + tgt.name + "[...]') doit être suivie d'un appel", i.pos());
            return Type.ERROR;
        }
        Type t = expr(i.target(), s, null);
        if (i.indices().size() != 1) {
            ctx.error("E0211", "un seul indice est attendu", i.pos());
            return Type.ERROR;
        }
        Expr idx = i.indices().get(0);
        if (idx instanceof Slice sl) {
            for (Expr bound : new Expr[] {sl.lower(), sl.upper(), sl.step()}) {
                if (bound != null) {
                    Type bt = expr(bound, s, INT_T);
                    if (!(bt instanceof ErrorType) && !Types.isInt(bt)) {
                        ctx.error("E0211", "les bornes d'une tranche doivent être des 'int', trouvé '" + bt + "'", bound.pos());
                    }
                }
            }
            if (t instanceof ErrorType) {
                return Type.ERROR;
            }
            if (t instanceof ClassType c && !c.nullable() && (c.cls() == LIST || c.cls() == STR)) {
                return t;
            }
            ctx.error("E0211", "le type '" + t + "' ne supporte pas les tranches", i.pos());
            return Type.ERROR;
        }
        if (t instanceof ErrorType) {
            expr(idx, s, null);
            return Type.ERROR;
        }
        if (t.nullable()) {
            ctx.error("E0201", "indexation d'un type nullable '" + t + "'", i.pos());
        }
        if (t instanceof ClassType c && (c.cls() == LIST || c.cls() == STR)) {
            Type it = expr(idx, s, INT_T);
            requireAssignable(it, INT_T, idx.pos());
            return c.cls() == LIST ? c.args().get(0) : STR_T;
        }
        if (t instanceof ClassType c && c.cls() == MAP) {
            Type kt = expr(idx, s, c.args().get(0));
            requireAssignable(kt, c.args().get(0), idx.pos());
            return c.args().get(1);
        }
        expr(idx, s, null);
        ctx.error("E0211", "le type '" + t + "' ne supporte pas l'indexation", i.pos());
        return Type.ERROR;
    }

    // ---- littéraux de collection -----------------------------------

    private Type listLit(ListLit l, Scope s, Type expected) {
        Type elemExpected = expectedArg(expected, LIST, 0);
        if (l.elements().isEmpty()) {
            return emptyCollection(expected, LIST, "[]", l.pos());
        }
        Type elem = elements(l.elements(), s, elemExpected, "liste");
        return ty(LIST, elem);
    }

    private Type setLit(SetLit l, Scope s, Type expected) {
        Type elem = elements(l.elements(), s, expectedArg(expected, SET, 0), "ensemble");
        return ty(SET, elem);
    }

    private Type elements(List<Expr> elems, Scope s, Type elemExpected, String what) {
        Type result = null;
        for (Expr el : elems) {
            Type t = expr(el, s, elemExpected);
            if (elemExpected != null && !(elemExpected instanceof ErrorType)) {
                if (!Types.isAssignable(t, elemExpected)) {
                    ctx.error(Ctx.codeFor(elemExpected, t), "élément de " + what + " : " + Ctx.mismatch(elemExpected, t), el.pos());
                }
                result = elemExpected;
            } else {
                result = result == null ? elementOf(t, el.pos()) : Types.join(result, t);
            }
        }
        return result == null ? Type.ERROR : result;
    }

    private Type mapLit(MapLit m, Scope s, Type expected) {
        Type ke = expectedArg(expected, MAP, 0);
        Type ve = expectedArg(expected, MAP, 1);
        if (m.entries().isEmpty()) {
            return emptyCollection(expected, MAP, "{}", m.pos());
        }
        Type k = null;
        Type v = null;
        for (MapEntry en : m.entries()) {
            Type kt = expr(en.key(), s, ke);
            Type vt = expr(en.value(), s, ve);
            if (ke != null) {
                if (!Types.isAssignable(kt, ke)) {
                    ctx.error(Ctx.codeFor(ke, kt), "clé de map : " + Ctx.mismatch(ke, kt), en.key().pos());
                }
                k = ke;
            } else {
                k = k == null ? elementOf(kt, en.key().pos()) : Types.join(k, kt);
            }
            if (ve != null) {
                if (!Types.isAssignable(vt, ve)) {
                    ctx.error(Ctx.codeFor(ve, vt), "valeur de map : " + Ctx.mismatch(ve, vt), en.value().pos());
                }
                v = ve;
            } else {
                v = v == null ? elementOf(vt, en.value().pos()) : Types.join(v, vt);
            }
        }
        return ty(MAP, k, v);
    }

    private Type emptyCollection(Type expected, ClassSymbol cls, String text, Pos pos) {
        if (expected instanceof ClassType c && c.cls() == cls && !c.nullable()) {
            return c;
        }
        if (!(expected instanceof ErrorType) || expected == null) {
            ctx.error("E0207", "impossible de déduire le type de '" + text + "' : annotez la variable (ex. x: "
                    + (cls == LIST ? "list[int]" : "map[str, int]") + " = " + text + ")", pos);
        }
        List<Type> args = new ArrayList<>();
        for (int i = 0; i < cls.typeParams.size(); i++) {
            args.add(Type.ERROR);
        }
        return new ClassType(cls, args, false);
    }

    private static Type expectedArg(Type expected, ClassSymbol cls, int idx) {
        if (expected instanceof ClassType c && c.cls() == cls && c.args().size() > idx) {
            Type a = c.args().get(idx);
            return a instanceof ErrorType ? null : a;
        }
        return null;
    }

    // ---- lambdas ----------------------------------------------------

    private Type lambda(Lambda l, Scope s, Type expected) {
        FunctionType et = expected instanceof FunctionType f ? f : null;
        if (et != null && et.params().size() != l.params().size()) {
            ctx.error("E0205", "la fonction attendue a " + et.params().size() + " paramètre(s), la lambda en déclare "
                    + l.params().size(), l.pos());
            et = null;
        }
        Scope ls = new Scope(s);
        LambdaFrame frame = new LambdaFrame(ls);
        lambdaFrames.push(frame);
        List<VarSymbol> paramSyms = new ArrayList<>();
        List<Type> ptypes = new ArrayList<>();
        for (int i = 0; i < l.params().size(); i++) {
            Param p = l.params().get(i);
            Type pt;
            if (p.type() != null) {
                pt = ctx.resolveType(p.type(), s);
            } else if (et != null) {
                pt = et.params().get(i);
            } else {
                ctx.error("E0207", "impossible de déduire le type du paramètre '" + p.name() + "' : ajoutez une annotation", p.pos());
                pt = Type.ERROR;
            }
            ptypes.add(pt);
            VarSymbol pv = new VarSymbol(p.name(), pt, false, VarKind.PARAM);
            paramSyms.add(pv);
            if (!ls.declare(pv)) {
                ctx.error("E0102", "paramètre '" + p.name() + "' en double", p.pos());
            }
        }
        Type resultExpected = et != null && !(et.result() instanceof UnitType) && !(et.result() instanceof ErrorType)
                ? et.result() : null;
        Type body;
        try {
            body = expr(l.body(), ls, resultExpected);
        } finally {
            lambdaFrames.pop();
        }
        ctx.typed.lambdas.put(l, new TypedProgram.LambdaInfo(paramSyms, new ArrayList<>(frame.captured)));
        ctx.typed.capturedVars.addAll(frame.captured);
        return new FunctionType(ptypes, body, false);
    }

    // ------------------------------------------------------------------
    // Appels
    // ------------------------------------------------------------------

    private record Callable(String display, List<TypeVar> typeParams, List<ParamSym> params, Type result,
                            boolean variadic, FuncSymbol func, ClassSymbol cls) {
    }

    private record Binding(Arg[] slots, List<Arg> extras, String error) {
    }

    private Type call(Call c, Scope s, Type expected) {
        Expr callee = c.callee();
        List<Expr> typeArgs = null;
        if (callee instanceof Index ix) {
            Symbol base = symbolTarget(ix.target(), s);
            if (base instanceof ClassSymbol || base instanceof FuncGroup) {
                typeArgs = ix.indices();
                callee = ix.target();
            }
        }

        if (callee instanceof Attribute a && isSuperCall(a.target())) {
            return superCall(a, c, s);
        }

        Symbol sym = null;
        if (callee instanceof Attribute a) {
            Symbol recvSym = symbolTarget(a.target(), s);
            if (recvSym instanceof ModuleSymbol m) {
                Symbol member = moduleMember(m, a.name(), a.pos());
                if (member == null) {
                    evaluateArgs(c, s);
                    return Type.ERROR;
                }
                sym = member instanceof VarSymbol ? null : member;
            } else if (recvSym instanceof ClassSymbol cs) {
                return staticCall(cs, a, c, s);
            }
        } else {
            sym = symbolTarget(callee, s);
        }
        if (sym instanceof ClassSymbol cs) {
            return construct(cs, typeArgs, c, s, expected);
        }
        if (sym instanceof FuncGroup g) {
            return callFunctions(g, typeArgs, c, s);
        }
        if (sym instanceof ModuleSymbol) {
            ctx.error("E0206", "un module n'est pas appelable", c.pos());
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        if (typeArgs != null) {
            ctx.error("E0207", "arguments de type inattendus ici", c.pos());
        }

        if (callee instanceof Attribute a) {
            return methodCall(a, c, s);
        }

        Type ft = expr(callee, s, null);
        if (ft instanceof ErrorType) {
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        return callValue(ft, "valeur", c, s);
    }

    private void evaluateArgs(Call c, Scope s) {
        for (Arg a : c.args()) {
            if (!(a.value() instanceof Lambda)) {
                expr(a.value(), s, null);
            }
        }
    }

    private Type callValue(Type ft, String display, Call c, Scope s) {
        if (!(ft instanceof FunctionType f) || ft.nullable()) {
            ctx.error("E0206", "le type '" + ft + "' n'est pas appelable", c.pos());
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        List<ParamSym> ps = new ArrayList<>();
        for (int i = 0; i < f.params().size(); i++) {
            ps.add(new ParamSym("p" + i, f.params().get(i), false));
        }
        return resolve(List.of(new Callable(display, List.of(), ps, f.result(), false, null, null)), c, s, Map.of(),
                TypedProgram.CallKind.FUNC_VALUE);
    }

    private boolean isSuperCall(Expr e) {
        return e instanceof Call sc && sc.args().isEmpty() && sc.callee() instanceof Name n && n.name().equals("super");
    }

    private Type superCall(Attribute a, Call c, Scope s) {
        if (currentClass == null || !inInit && a.name().equals("init")) {
            ctx.error("E0308", "'super()' n'est utilisable que dans une méthode de classe"
                    + (a.name().equals("init") ? ", et 'super().init(...)' seulement dans 'init'" : ""), a.pos());
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        ClassType sup = currentClass.superType != null ? currentClass.superType : ANY_T;
        if (a.name().equals("init")) {
            List<Callable> cands = constructorCandidates(sup, a.pos());
            if (cands == null) {
                evaluateArgs(c, s);
                return Type.ERROR;
            }
            resolve(cands, c, s, Map.of(), TypedProgram.CallKind.SUPER_INIT);
            return Type.UNIT;
        }
        List<Callable> cands = methodCandidates(sup, a.name(), false, a.pos());
        if (cands.isEmpty()) {
            ctx.error("E0209", "la superclasse '" + sup + "' n'a pas de méthode '" + a.name() + "'", a.pos());
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        return resolve(cands, c, s, Map.of(), TypedProgram.CallKind.SUPER_METHOD);
    }

    // ---- fonctions ---------------------------------------------------

    private Type callFunctions(FuncGroup g, List<Expr> typeArgs, Call c, Scope s) {
        List<Callable> cands = new ArrayList<>();
        Map<TypeVar, Type> init = new HashMap<>();
        for (FuncSymbol f : g.funcs) {
            cands.add(new Callable(g.name, f.typeParams, f.params, f.returnType, f.variadic, f, null));
        }
        if (typeArgs != null) {
            if (g.funcs.size() != 1 || g.funcs.get(0).typeParams.size() != typeArgs.size()) {
                ctx.error("E0207", "nombre d'arguments de type incorrect pour '" + g.name + "'", c.pos());
            } else {
                for (int i = 0; i < typeArgs.size(); i++) {
                    init.put(g.funcs.get(0).typeParams.get(i).key(), exprToType(typeArgs.get(i), s));
                }
            }
        }
        return resolve(cands, c, s, init, TypedProgram.CallKind.FUNCTION);
    }

    private Type staticCall(ClassSymbol cs, Attribute a, Call c, Scope s) {
        cs.ensureLoaded();
        List<Callable> cands = methodCandidates(cs.thisType(), a.name(), true, a.pos());
        if (cands.isEmpty()) {
            ctx.error("E0209", "'" + cs.name + "' n'a pas de méthode statique '" + a.name() + "'"
                    + Suggest.hint(a.name(), cs.methods.keySet()), a.pos());
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        return resolve(cands, c, s, Map.of(), TypedProgram.CallKind.STATIC_METHOD);
    }

    private Type methodCall(Attribute a, Call c, Scope s) {
        Type rt = expr(a.target(), s, null);
        if (rt instanceof ErrorType) {
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        if (rt instanceof NullType) {
            ctx.error("E0201", "appel de '." + a.name() + "()' sur 'null'", a.pos());
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        if (rt.nullable() && !a.safe()) {
            ctx.error("E0201", "appel de '." + a.name() + "()' sur le type nullable '" + rt + "' : utilisez '?."
                    + a.name() + "()' ou vérifiez d'abord que la valeur n'est pas null", a.pos());
        } else if (!rt.nullable() && a.safe()) {
            ctx.warning("W0201", "'?.' inutile : '" + rt + "' n'est pas nullable", a.pos());
        }
        Type base = Types.nonNull(rt);
        if (base instanceof TypeVar) {
            base = ANY_T;
        }
        if (!(base instanceof ClassType ct)) {
            ctx.error("E0209", "le type '" + rt + "' n'a pas de méthode '" + a.name() + "'", a.pos());
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        List<Callable> cands = methodCandidates(ct, a.name(), false, a.pos());
        if (cands.isEmpty()) {
            FieldRef ref = findField(ct, a.name());
            if (ref != null && ref.type instanceof FunctionType) {
                Type r = callValue(ref.type, a.name(), c, s);
                return rt.nullable() && a.safe() ? r.withNullable(true) : r;
            }
            if (!privateHit) {
                ctx.error("E0209", "le type '" + ct + "' n'a pas de méthode '" + a.name() + "'"
                        + Suggest.hint(a.name(), memberNames(ct)), a.pos());
            }
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        Type r = resolve(cands, c, s, Map.of(), TypedProgram.CallKind.METHOD);
        return rt.nullable() && a.safe() ? r.withNullable(true) : r;
    }

    private boolean privateHit;

    /** Méthodes {@code name} de {@code ct} (et de ses super-types), avec substitution des arguments de type. */
    private List<Callable> methodCandidates(ClassType ct, String name, boolean staticCall, Pos pos) {
        privateHit = false;
        List<Callable> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ClassType k : Types.linearize(ct)) {
            k.cls().ensureLoaded();
            List<FuncSymbol> ms = k.cls().methods.get(name);
            if (ms == null) {
                continue;
            }
            Map<TypeVar, Type> m = Types.mapping(k);
            for (FuncSymbol f : ms) {
                if (f.isStatic != staticCall) {
                    continue;
                }
                if (Symbols.isPrivateName(name) && f.owner != currentClass && f.owner.kind != ClassKind.JAVA) {
                    ctx.error("E0104", "'" + name + "' est privé dans '" + f.owner.name + "'", pos);
                    privateHit = true;
                    continue;
                }
                List<ParamSym> ps = new ArrayList<>();
                StringBuilder sig = new StringBuilder();
                for (ParamSym p : f.params) {
                    Type pt = Types.substitute(p.type(), m);
                    ps.add(new ParamSym(p.name(), pt, p.hasDefault()));
                    sig.append(pt).append(',');
                }
                if (seen.add(sig.toString())) {
                    out.add(new Callable(name, f.typeParams, ps, Types.substitute(f.returnType, m), f.variadic, f, null));
                }
            }
        }
        return out;
    }

    // ---- constructeurs -----------------------------------------------

    private List<FuncSymbol> effectiveConstructors(ClassSymbol cs) {
        ClassSymbol cur = cs;
        for (int guard = 0; cur != null && guard < 64; guard++) {
            cur.ensureLoaded();
            if (!cur.constructors.isEmpty()) {
                return cur.constructors;
            }
            if (cur.kind == ClassKind.JAVA || cur.superType == null) {
                break;
            }
            cur = cur.superType.cls();
        }
        return List.of();
    }

    /** Candidats « constructeur » du type donné ; null (avec diagnostic) si impossible. */
    private List<Callable> constructorCandidates(ClassType ct, Pos pos) {
        ClassSymbol cs = ct.cls();
        List<FuncSymbol> ctors = effectiveConstructors(cs);
        List<Callable> out = new ArrayList<>();
        Map<TypeVar, Type> m = Types.mapping(ct);
        if (ctors.isEmpty()) {
            if (cs.kind == ClassKind.JAVA) {
                ctx.error("E0308", "'" + cs.name + "' n'a pas de constructeur public", pos);
                return null;
            }
            out.add(new Callable(cs.name, List.of(), List.of(), Type.UNIT, false, null, cs));
            return out;
        }
        for (FuncSymbol f : ctors) {
            List<ParamSym> ps = new ArrayList<>();
            for (ParamSym p : f.params) {
                ps.add(new ParamSym(p.name(), Types.substitute(p.type(), m), p.hasDefault()));
            }
            out.add(new Callable(cs.name, f.typeParams, ps, Type.UNIT, f.variadic, f, cs));
        }
        return out;
    }

    private Type construct(ClassSymbol cs, List<Expr> typeArgs, Call c, Scope s, Type expected) {
        cs.ensureLoaded();
        if (c.args().size() == 1 && (cs == INT || cs == FLOAT || cs == STR || cs == BOOL) && typeArgs == null) {
            expr(c.args().get(0).value(), s, null);
            ctx.typed.calls.put(c, new TypedProgram.CallInfo(TypedProgram.CallKind.CONVERSION, null, cs, Map.of()));
            return ty(cs);
        }
        if (cs.isInterfaceLike() || cs.isAbstract || cs.kind == ClassKind.ENUM || cs == ANY) {
            ctx.error("E0308", "'" + cs.name + "' ne peut pas être instancié"
                    + (cs.kind == ClassKind.ENUM ? " (utilisez ses constantes)" : " (classe abstraite ou interface)"), c.pos());
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        Map<TypeVar, Type> init = new HashMap<>();
        if (typeArgs != null) {
            if (typeArgs.size() != cs.typeParams.size()) {
                ctx.error("E0207", "le type '" + cs.name + "' attend " + cs.typeParams.size() + " argument(s) de type", c.pos());
            } else {
                for (int i = 0; i < typeArgs.size(); i++) {
                    init.put(cs.typeParams.get(i).key(), exprToType(typeArgs.get(i), s));
                }
            }
        } else if (expected instanceof ClassType ec && ec.cls() == cs && ec.args().size() == cs.typeParams.size()) {
            for (int i = 0; i < cs.typeParams.size(); i++) {
                if (!(ec.args().get(i) instanceof ErrorType)) {
                    init.put(cs.typeParams.get(i).key(), ec.args().get(i));
                }
            }
        }
        ClassType self = cs.thisType();
        List<Callable> cands = constructorCandidates(self, c.pos());
        if (cands == null) {
            evaluateArgs(c, s);
            return Type.ERROR;
        }
        List<Callable> withResult = new ArrayList<>();
        for (Callable k : cands) {
            List<TypeVar> tps = new ArrayList<>(cs.typeParams);
            tps.addAll(k.typeParams());
            withResult.add(new Callable(k.display(), tps, k.params(), self, k.variadic(), k.func(), cs));
        }
        return resolve(withResult, c, s, init, TypedProgram.CallKind.CONSTRUCT);
    }

    /** Convertit une expression écrite dans « Boite[int] » en type. */
    private Type exprToType(Expr e, Scope s) {
        if (e instanceof Name n) {
            TypeVar tv = s.lookupTypeVar(n.name());
            if (tv != null) {
                return tv;
            }
            if (n.name().equals("unit")) {
                return Type.UNIT;
            }
            Symbol sym = ctx.lookup(s, n.name());
            if (sym instanceof ClassSymbol cs) {
                if (!cs.typeParams.isEmpty()) {
                    ctx.error("E0207", "le type '" + cs.name + "' attend " + cs.typeParams.size() + " argument(s) de type", n.pos());
                }
                return cs.typeParams.isEmpty() ? new ClassType(cs, List.of(), false) : Type.ERROR;
            }
            ctx.error("E0101", "type inconnu '" + n.name() + "'." + Suggest.hint(n.name(), s.allNames()), n.pos());
            return Type.ERROR;
        }
        if (e instanceof Index ix && ix.target() instanceof Name n) {
            Symbol sym = ctx.lookup(s, n.name());
            if (sym instanceof ClassSymbol cs) {
                List<Type> args = new ArrayList<>();
                for (Expr a : ix.indices()) {
                    args.add(exprToType(a, s));
                }
                if (args.size() != cs.typeParams.size()) {
                    ctx.error("E0207", "le type '" + cs.name + "' attend " + cs.typeParams.size() + " argument(s) de type", ix.pos());
                    return Type.ERROR;
                }
                return new ClassType(cs, args, false);
            }
        }
        ctx.error("E0207", "argument de type invalide", e.pos());
        return Type.ERROR;
    }

    // ---- sélection de surcharge et inférence -------------------------

    private Binding bind(Callable c, List<Arg> args) {
        int n = c.params().size();
        Arg[] slots = new Arg[n];
        List<Arg> extras = new ArrayList<>();
        int pos = 0;
        for (Arg a : args) {
            if (a.name() != null) {
                int idx = -1;
                for (int i = 0; i < n; i++) {
                    if (c.params().get(i).name().equals(a.name())) {
                        idx = i;
                    }
                }
                if (idx < 0) {
                    return new Binding(slots, extras, "paramètre inconnu '" + a.name() + "'");
                }
                if (slots[idx] != null) {
                    return new Binding(slots, extras, "argument '" + a.name() + "' fourni deux fois");
                }
                slots[idx] = a;
            } else if (c.variadic() && pos >= n - 1) {
                extras.add(a);
            } else if (pos < n) {
                if (slots[pos] != null) {
                    return new Binding(slots, extras, "argument '" + c.params().get(pos).name() + "' fourni deux fois");
                }
                slots[pos++] = a;
            } else {
                return new Binding(slots, extras, "trop d'arguments : " + n + " attendu(s), " + args.size() + " fourni(s)");
            }
        }
        for (int i = 0; i < n; i++) {
            ParamSym p = c.params().get(i);
            if (slots[i] == null && !p.hasDefault() && !(c.variadic() && i == n - 1)) {
                return new Binding(slots, extras, "argument manquant : '" + p.name() + "'");
            }
        }
        return new Binding(slots, extras, null);
    }

    private Type resolve(List<Callable> cands, Call c, Scope s, Map<TypeVar, Type> init, TypedProgram.CallKind kind) {
        List<Callable> feasible = new ArrayList<>();
        Binding firstBad = null;
        for (Callable k : cands) {
            Binding b = bind(k, c.args());
            if (b.error() == null) {
                feasible.add(k);
            } else if (firstBad == null) {
                firstBad = b;
            }
        }
        if (feasible.isEmpty()) {
            Callable k0 = cands.get(0);
            ctx.error("E0205", "appel de '" + k0.display() + "' : " + firstBad.error() + signatureHint(cands), c.pos());
            evaluateArgs(c, s);
            return cands.size() == 1 ? k0.result() : Type.ERROR;
        }
        if (feasible.size() == 1) {
            Callable k = feasible.get(0);
            Outcome o = apply(k, bind(k, c.args()), s, init, null, true, c);
            ctx.typed.calls.put(c, new TypedProgram.CallInfo(kind, k.func(), k.cls(), o.bindings));
            return o.type;
        }
        // Plusieurs surcharges : on type chaque argument une fois, puis on choisit.
        Map<Arg, Type> pre = new IdentityHashMap<>();
        for (Arg a : c.args()) {
            if (!(a.value() instanceof Lambda)) {
                pre.put(a, expr(a.value(), s, null));
            }
        }
        for (Callable k : feasible) {
            Outcome o = apply(k, bind(k, c.args()), s, init, pre, false, c);
            if (o.ok) {
                Outcome fin = apply(k, bind(k, c.args()), s, init, pre, true, c);
                ctx.typed.calls.put(c, new TypedProgram.CallInfo(kind, k.func(), k.cls(), fin.bindings));
                return fin.type;
            }
        }
        StringBuilder types = new StringBuilder();
        for (Arg a : c.args()) {
            if (types.length() > 0) {
                types.append(", ");
            }
            types.append(pre.containsKey(a) ? pre.get(a).toString() : "lambda");
        }
        ctx.error("E0205", "aucune surcharge de '" + cands.get(0).display() + "' ne convient pour (" + types + ")"
                + signatureHint(feasible), c.pos());
        return Type.ERROR;
    }

    private static String signatureHint(List<Callable> cands) {
        StringBuilder sb = new StringBuilder(" ; signature(s) :");
        for (Callable k : cands) {
            sb.append(" ").append(k.display()).append('(');
            for (int i = 0; i < k.params().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                ParamSym p = k.params().get(i);
                sb.append(p.name()).append(": ").append(p.type());
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private static final class Outcome {
        Type type = Type.ERROR;
        boolean ok = true;
        Map<TypeVar, Type> bindings = Map.of();
    }

    private Outcome apply(Callable k, Binding bd, Scope s, Map<TypeVar, Type> init, Map<Arg, Type> pre,
                          boolean report, Call c) {
        Outcome out = new Outcome();
        Map<TypeVar, Type> b = new HashMap<>(init);
        Set<TypeVar> inferable = new HashSet<>();
        for (TypeVar tv : k.typeParams()) {
            inferable.add(tv.key());
        }
        List<Arg> lambdas = new ArrayList<>();
        List<Integer> lambdaParam = new ArrayList<>();
        int n = k.params().size();
        for (int i = 0; i < n; i++) {
            ParamSym ps = k.params().get(i);
            List<Arg> supplied = new ArrayList<>();
            if (bd.slots()[i] != null) {
                supplied.add(bd.slots()[i]);
            }
            if (k.variadic() && i == n - 1) {
                supplied.addAll(bd.extras());
            }
            for (Arg a : supplied) {
                if (a.value() instanceof Lambda) {
                    lambdas.add(a);
                    lambdaParam.add(i);
                    continue;
                }
                Type pt = Types.substitute(ps.type(), b);
                Type at;
                if (pre != null && pre.containsKey(a)) {
                    at = pre.get(a);
                } else {
                    at = expr(a.value(), s, Types.containsVar(pt, inferable) ? null : pt);
                }
                if (!Types.unify(ps.type(), at, inferable, b)) {
                    out.ok = false;
                    if (report) {
                        Type expectedT = Types.eraseVars(Types.substitute(ps.type(), b), inferable);
                        ctx.error(Ctx.codeFor(expectedT, at), "argument '" + ps.name() + "' de '" + k.display() + "' : "
                                + Ctx.mismatch(expectedT, at), a.value().pos());
                    }
                }
            }
        }
        out.bindings = new HashMap<>(b);
        if (pre != null && !report) {
            out.type = Type.ERROR;
            return out;
        }
        for (int j = 0; j < lambdas.size(); j++) {
            Arg a = lambdas.get(j);
            ParamSym ps = k.params().get(lambdaParam.get(j));
            Type pt = Types.eraseVars(Types.substitute(ps.type(), b), inferable);
            Type lt;
            if (pt instanceof FunctionType || pt instanceof ErrorType) {
                lt = expr(a.value(), s, pt);
            } else {
                ctx.error("E0205", "argument '" + ps.name() + "' de '" + k.display() + "' : une fonction n'est pas attendue ici", a.value().pos());
                out.ok = false;
                continue;
            }
            if (!Types.unify(ps.type(), lt, inferable, b)) {
                out.ok = false;
                if (report) {
                    ctx.error("E0202", "argument '" + ps.name() + "' de '" + k.display() + "' : "
                            + Ctx.mismatch(pt, lt), a.value().pos());
                }
            }
        }
        Type r = Types.substitute(k.result(), b);
        if (Types.containsVar(r, inferable)) {
            if (report) {
                ctx.error("E0207", "impossible de déduire les paramètres de type de '" + k.display()
                        + "' : précisez-les (ex. " + k.display() + "[...](...))", c.pos());
            }
            r = Types.eraseVars(r, inferable);
        }
        out.type = r;
        out.bindings = new HashMap<>(b);
        return out;
    }
}
