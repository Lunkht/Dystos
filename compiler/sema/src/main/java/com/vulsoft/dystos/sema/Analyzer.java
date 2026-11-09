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
import com.vulsoft.dystos.sema.Type.TypeVar;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestration de l'analyse : modules, imports, déclarations, signatures, héritage,
 * puis vérification des corps.
 */
final class Analyzer {

    private static final Pos START = new Pos(1, 1);

    private final Ctx ctx;
    private final List<Unit> units = new ArrayList<>();
    private final List<ClassInfo> classes = new ArrayList<>();
    private final List<Job> jobs = new ArrayList<>();
    private final List<FieldInit> fieldInits = new ArrayList<>();

    private static final class Unit {
        String module;
        SourceFile file;
        ModuleSymbol sym;
        Scope scope;
        Checker checker;
        List<FuncDecl> functions = new ArrayList<>();
    }

    private static final class ClassInfo {
        Unit unit;
        ClassSymbol sym;
        Object decl;
        Scope scope;
        Pos pos;
    }

    private record Job(Unit unit, FuncSymbol f, FuncDecl d, Scope scope) {
    }

    private record FieldInit(Unit unit, ClassInfo ci, VarSymbol v, Expr init, boolean typed) {
    }

    Analyzer(Ctx ctx) {
        this.ctx = ctx;
    }

    void run(List<SemanticAnalyzer.SourceUnit> inputs) {
        Scope global = Builtins.globalScope();
        ctx.multiFile = inputs.size() > 1;

        for (SemanticAnalyzer.SourceUnit in : inputs) {
            Unit u = new Unit();
            u.module = in.module();
            u.file = in.file();
            u.sym = new ModuleSymbol(in.module(), false);
            u.scope = new Scope(global);
            u.checker = new Checker(ctx, u.sym);
            ctx.currentModule = u.module;
            if (ctx.modules.containsKey(u.module)) {
                ctx.error("E0102", "le module '" + u.module + "' est défini deux fois", START);
            }
            ctx.modules.put(u.module, u.sym);
            units.add(u);
        }
        for (Unit u : units) {
            ctx.currentModule = u.module;
            processImports(u);
        }
        for (Unit u : units) {
            ctx.currentModule = u.module;
            declareTopLevel(u);
        }
        for (ClassInfo ci : classes) {
            ctx.currentModule = ci.unit.module;
            resolveHeader(ci);
        }
        checkCycles();
        for (ClassInfo ci : classes) {
            ctx.currentModule = ci.unit.module;
            declareMembers(ci);
        }
        for (Unit u : units) {
            ctx.currentModule = u.module;
            declareFunctions(u);
        }
        for (FieldInit fi : fieldInits) {
            if (!fi.typed()) {
                ctx.currentModule = fi.unit().module;
                fi.v().type = fi.unit().checker.inferFieldType(fi.v().name, fi.init(), fi.ci().scope, fi.ci().sym);
            }
        }
        for (ClassInfo ci : classes) {
            ctx.currentModule = ci.unit.module;
            checkClass(ci);
        }
        for (Unit u : units) {
            ctx.currentModule = u.module;
            if (u.file.isScript()) {
                u.checker.checkScript(u.file.statements(), u.scope);
            }
        }
        for (Job j : jobs) {
            ctx.currentModule = j.unit().module;
            j.unit().checker.checkFunction(j.f(), j.d(), j.scope());
        }
        for (FieldInit fi : fieldInits) {
            if (fi.typed() && fi.init() != null) {
                ctx.currentModule = fi.unit().module;
                fi.unit().checker.checkFieldInit(fi.v(), fi.init(), fi.ci().scope, fi.ci().sym);
            }
        }
    }

    // ------------------------------------------------------------------
    // Imports
    // ------------------------------------------------------------------

    private void processImports(Unit u) {
        for (ImportDecl imp : u.file.imports()) {
            String path = imp.path();
            if (path.endsWith(".*")) {
                u.scope.addStarPackage(path.substring(0, path.length() - 2));
                continue;
            }
            Symbol target = ctx.modules.get(path);
            if (target == null) {
                target = Builtins.module(path);
            }
            if (target == null) {
                target = ctx.java.load(path);
            }
            if (target == null) {
                ctx.error("E0105", "import introuvable : '" + path + "'", imp.pos());
                continue;
            }
            String name = imp.alias() != null ? imp.alias() : path.substring(path.lastIndexOf('.') + 1);
            if (!u.scope.declareAs(name, target)) {
                ctx.error("E0102", "'" + name + "' est déjà défini", imp.pos());
            }
        }
    }

    // ------------------------------------------------------------------
    // Déclarations de premier niveau
    // ------------------------------------------------------------------

    private void declareTopLevel(Unit u) {
        for (TopLevel d : u.file.decls()) {
            if (d instanceof FuncDecl f) {
                u.functions.add(f);
                continue;
            }
            ClassSymbol cs;
            Pos pos;
            if (d instanceof ClassDecl c) {
                cs = new ClassSymbol(c.name(), ClassKind.CLASS);
                cs.isAbstract = c.modifiers().contains("abstract");
                pos = c.pos();
                if (!c.decorators().isEmpty()) {
                    ctx.error("E0306", "décorateur '@" + c.decorators().get(0).name() + "' non supporté sur une classe", c.decorators().get(0).pos());
                }
            } else if (d instanceof InterfaceDecl i) {
                cs = new ClassSymbol(i.name(), ClassKind.INTERFACE);
                cs.isAbstract = true;
                pos = i.pos();
            } else if (d instanceof EnumDecl e) {
                cs = new ClassSymbol(e.name(), ClassKind.ENUM);
                pos = e.pos();
            } else {
                continue;
            }
            cs.module = u.sym;
            cs.decl = d;
            if (!u.scope.declare(cs)) {
                ctx.error("E0102", "'" + cs.name + "' est déjà défini", pos);
                continue;
            }
            u.sym.members.put(cs.name, cs);
            ClassInfo ci = new ClassInfo();
            ci.unit = u;
            ci.sym = cs;
            ci.decl = d;
            ci.scope = new Scope(u.scope);
            ci.pos = pos;
            classes.add(ci);
        }
    }

    private void resolveHeader(ClassInfo ci) {
        ClassSymbol cs = ci.sym;
        List<String> tps = List.of();
        List<TypeRef> supers = List.of();
        if (ci.decl instanceof ClassDecl c) {
            tps = c.typeParams();
            supers = c.supers();
        } else if (ci.decl instanceof InterfaceDecl i) {
            tps = i.typeParams();
            supers = i.supers();
        }
        for (String name : tps) {
            TypeVar tv = Types.freshVar(name);
            cs.typeParams.add(tv);
            ci.scope.declareTypeVar(tv);
        }
        for (TypeRef sr : supers) {
            Type t = ctx.resolveType(sr, ci.scope);
            if (t instanceof ErrorType) {
                continue;
            }
            if (!(t instanceof ClassType ct) || ct.nullable()) {
                ctx.error("E0305", "type de base invalide '" + t + "'", sr.pos());
                continue;
            }
            ClassSymbol sc = ct.cls();
            if (cs.kind == ClassKind.INTERFACE) {
                if (sc.isInterfaceLike()) {
                    cs.interfaces.add(ct);
                } else {
                    ctx.error("E0305", "une interface ne peut étendre qu'une interface ('" + sc.name + "' n'en est pas une)", sr.pos());
                }
            } else if (sc.isInterfaceLike()) {
                cs.interfaces.add(ct);
            } else if (FINAL_CLASSES.contains(sc) || sc.kind == ClassKind.ENUM) {
                ctx.error("E0305", "'" + sc.name + "' ne peut pas être étendu", sr.pos());
            } else if (cs.superType != null) {
                ctx.error("E0305", "une classe ne peut avoir qu'une seule superclasse", sr.pos());
            } else {
                cs.superType = ct;
            }
        }
    }

    private void checkCycles() {
        for (ClassInfo ci : classes) {
            if (reaches(ci.sym, ci.sym, new HashSet<>())) {
                ctx.currentModule = ci.unit.module;
                ctx.error("E0305", "cycle d'héritage autour de '" + ci.sym.name + "'", ci.pos);
                ci.sym.superType = null;
                ci.sym.interfaces.clear();
            }
        }
    }

    private boolean reaches(ClassSymbol from, ClassSymbol target, Set<ClassSymbol> seen) {
        List<ClassType> sups = new ArrayList<>(from.interfaces);
        if (from.superType != null) {
            sups.add(from.superType);
        }
        for (ClassType st : sups) {
            if (st.cls() == target) {
                return true;
            }
            if (st.cls().kind != ClassKind.JAVA && seen.add(st.cls()) && reaches(st.cls(), target, seen)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Membres et signatures
    // ------------------------------------------------------------------

    private void declareMembers(ClassInfo ci) {
        ClassSymbol cs = ci.sym;
        List<Member> members;
        if (ci.decl instanceof ClassDecl c) {
            members = c.members();
        } else if (ci.decl instanceof InterfaceDecl i) {
            members = i.members();
        } else {
            EnumDecl e = (EnumDecl) ci.decl;
            members = e.members();
            for (EnumConstant k : e.constants()) {
                VarSymbol v = new VarSymbol(k.name(), cs.thisType(), false, VarKind.ENUM_CONSTANT);
                v.owner = cs;
                v.isStatic = true;
                if (cs.fields.put(k.name(), v) != null) {
                    ctx.error("E0102", "constante '" + k.name() + "' en double", k.pos());
                }
                cs.enumConstants.add(k.name());
            }
            for (String p : List.of("name", "ordinal")) {
                if (!cs.fields.containsKey(p)) {
                    VarSymbol v = new VarSymbol(p, p.equals("name") ? STR_T : INT_T, false, VarKind.PROPERTY);
                    v.owner = cs;
                    cs.fields.put(p, v);
                }
            }
        }
        for (Member m : members) {
            if (m instanceof VarDecl d) {
                declareField(ci, d);
            } else if (m instanceof FuncDecl f) {
                FuncSymbol fs = makeFunc(ci.unit, f, cs, ci.scope);
                if (fs.isConstructor) {
                    if (!cs.constructors.isEmpty()) {
                        ctx.error("E0102", "'" + cs.name + "' a déjà un constructeur 'init' (un seul est supporté)", f.pos());
                    } else {
                        cs.constructors.add(fs);
                    }
                } else if (cs.methods.containsKey(fs.name)) {
                    ctx.error("E0102", "la méthode '" + fs.name + "' est déjà définie dans '" + cs.name + "' (surcharge non supportée)", f.pos());
                } else {
                    cs.addMethod(fs);
                }
            }
        }
    }

    private void declareField(ClassInfo ci, VarDecl d) {
        ClassSymbol cs = ci.sym;
        if (cs.kind == ClassKind.INTERFACE || cs.kind == ClassKind.ENUM && false) {
            ctx.error("E0301", "une interface ne peut pas déclarer de champ", d.pos());
            return;
        }
        Type type = d.type() != null ? ctx.resolveType(d.type(), ci.scope) : Type.ERROR;
        VarSymbol v = new VarSymbol(d.name(), type, d.mutability() != Mutability.VAL, VarKind.FIELD);
        v.owner = cs;
        if (cs.fields.containsKey(d.name())) {
            ctx.error("E0102", "le champ '" + d.name() + "' est déjà défini", d.pos());
            return;
        }
        cs.fields.put(d.name(), v);
        fieldInits.add(new FieldInit(ci.unit, ci, v, d.init(), d.type() != null));
    }

    private void declareFunctions(Unit u) {
        for (FuncDecl d : u.functions) {
            FuncSymbol f = makeFunc(u, d, null, u.scope);
            Symbol existing = u.scope.lookupLocal(d.name());
            if (existing == null) {
                FuncGroup g = new FuncGroup(d.name());
                g.funcs.add(f);
                u.scope.declare(g);
                u.sym.members.put(d.name(), g);
            } else {
                ctx.error("E0102", "'" + d.name() + "' est déjà défini"
                        + (existing instanceof FuncGroup ? " (surcharge non supportée)" : ""), d.pos());
            }
        }
        if (u.file.isScript() && u.sym.members.get("main") instanceof FuncGroup) {
            ctx.error("E0307", "point d'entrée ambigu : ce fichier contient des instructions de niveau fichier "
                    + "et une fonction 'main'", u.file.statements().get(0).pos());
        }
    }

    private FuncSymbol makeFunc(Unit u, FuncDecl d, ClassSymbol owner, Scope declScope) {
        FuncSymbol f = new FuncSymbol(d.name());
        f.decl = d;
        f.owner = owner;
        f.module = u.sym;
        for (Decorator dc : d.decorators()) {
            switch (dc.name()) {
                case "static":
                    if (owner == null) {
                        ctx.error("E0306", "'@static' n'a de sens que dans une classe", dc.pos());
                    }
                    f.isStatic = owner != null;
                    break;
                case "test":
                    f.isTest = true;
                    break;
                default:
                    ctx.error("E0306", "décorateur inconnu '@" + dc.name() + "'", dc.pos());
            }
        }
        Scope fs = new Scope(declScope);
        for (String tp : d.typeParams()) {
            TypeVar tv = Types.freshVar(tp);
            f.typeParams.add(tv);
            fs.declareTypeVar(tv);
        }
        List<Param> ps = d.params();
        int start = 0;
        if (owner != null && !f.isStatic) {
            if (ps.isEmpty() || !ps.get(0).name().equals("self") || ps.get(0).type() != null) {
                ctx.error("E0303", "la méthode '" + d.name() + "' doit avoir 'self' comme premier paramètre (ou être '@static')", d.pos());
            } else {
                start = 1;
            }
        }
        f.astOffset = start;
        Set<String> names = new HashSet<>();
        for (int i = start; i < ps.size(); i++) {
            Param p = ps.get(i);
            Type pt;
            if (p.type() == null) {
                ctx.error("E0304", "le paramètre '" + p.name() + "' de '" + d.name() + "' doit avoir un type", p.pos());
                pt = Type.ERROR;
            } else {
                pt = ctx.resolveType(p.type(), fs);
            }
            if (!names.add(p.name())) {
                ctx.error("E0102", "paramètre '" + p.name() + "' en double", p.pos());
            }
            f.params.add(new ParamSym(p.name(), pt, p.defaultValue() != null));
        }
        f.isConstructor = owner != null && d.name().equals("init");
        if (f.isConstructor && d.returnType() != null) {
            ctx.error("E0302", "un constructeur 'init' ne déclare pas de type de retour", d.returnType().pos());
        }
        f.returnType = d.returnType() != null && !f.isConstructor ? ctx.resolveType(d.returnType(), fs) : Type.UNIT;
        boolean explicitAbstract = d.modifiers().contains("abstract");
        f.isAbstract = d.body() == null;
        if (d.body() == null && owner != null && owner.kind == ClassKind.CLASS && !owner.isAbstract && !explicitAbstract) {
            ctx.error("E0301", "la méthode '" + d.name() + "' n'a pas de corps : la classe '" + owner.name
                    + "' devrait être 'abstract'", d.pos());
        }
        if (d.body() != null && explicitAbstract) {
            ctx.error("E0301", "une méthode 'abstract' ne peut pas avoir de corps", d.pos());
        }
        if (d.body() == null && owner == null) {
            ctx.error("E0301", "la fonction '" + d.name() + "' doit avoir un corps", d.pos());
        }
        if (f.isTest && (!f.params.isEmpty() || !(f.returnType instanceof Type.UnitType))) {
            ctx.error("E0306", "une fonction '@test' ne prend pas de paramètre et ne retourne rien", d.pos());
        }
        if (d.body() != null) {
            jobs.add(new Job(u, f, d, fs));
        }
        return f;
    }

    // ------------------------------------------------------------------
    // Vérifications de classes
    // ------------------------------------------------------------------

    private void checkClass(ClassInfo ci) {
        ClassSymbol cs = ci.sym;
        ClassType self = cs.thisType();
        List<ClassType> lin = Types.linearize(self);

        // Compatibilité des redéfinitions
        for (List<FuncSymbol> ms : cs.methods.values()) {
            for (FuncSymbol f : ms) {
                for (ClassType k : lin) {
                    if (k.cls() == cs) {
                        continue;
                    }
                    List<FuncSymbol> gs = k.cls().methods.get(f.name);
                    if (gs == null || f.isStatic) {
                        continue;
                    }
                    for (FuncSymbol g : gs) {
                        if (g.isStatic) {
                            continue;
                        }
                        checkOverride(ci, f, g, k);
                    }
                }
            }
        }

        // Méthodes abstraites à implémenter
        if (cs.kind != ClassKind.CLASS || cs.isAbstract) {
            return;
        }
        Set<String> reported = new HashSet<>();
        for (ClassType k : lin) {
            for (List<FuncSymbol> ms : k.cls().methods.values()) {
                for (FuncSymbol f : ms) {
                    if (!f.isAbstract || f.isStatic || f.owner == null || f.owner.kind == ClassKind.JAVA) {
                        continue;
                    }
                    boolean implemented = false;
                    for (ClassType k2 : lin) {
                        List<FuncSymbol> gs = k2.cls().methods.get(f.name);
                        if (gs == null) {
                            continue;
                        }
                        for (FuncSymbol g : gs) {
                            if (!g.isAbstract && g.params.size() == f.params.size()) {
                                implemented = true;
                            }
                        }
                    }
                    if (!implemented && reported.add(f.name)) {
                        ctx.error("E0301", "la classe '" + cs.name + "' doit implémenter la méthode '" + f.name
                                + "' (déclarée dans '" + f.owner.name + "') ou être 'abstract'", ci.pos);
                    }
                }
            }
        }
    }

    private void checkOverride(ClassInfo ci, FuncSymbol f, FuncSymbol g, ClassType superType) {
        Pos pos = f.decl != null ? f.decl.pos() : ci.pos;
        if (f.params.size() != g.params.size()) {
            ctx.error("E0302", "la méthode '" + f.name + "' redéfinit '" + superType.cls().name + "." + g.name
                    + "' avec " + f.params.size() + " paramètre(s) au lieu de " + g.params.size(), pos);
            return;
        }
        var m = Types.mapping(superType);
        for (int i = 0; i < f.params.size(); i++) {
            Type expected = Types.substitute(g.params.get(i).type(), m);
            if (!Types.sameType(expected, f.params.get(i).type())) {
                ctx.error("E0302", "la méthode '" + f.name + "' redéfinit '" + superType.cls().name + "." + g.name
                        + "' : le paramètre '" + f.params.get(i).name() + "' doit être de type '" + expected
                        + "', trouvé '" + f.params.get(i).type() + "'", pos);
                return;
            }
        }
        Type expectedRet = Types.substitute(g.returnType, m);
        if (!Types.isAssignable(f.returnType, expectedRet)) {
            ctx.error("E0302", "la méthode '" + f.name + "' redéfinit '" + superType.cls().name + "." + g.name
                    + "' : le type de retour '" + f.returnType + "' n'est pas compatible avec '" + expectedRet + "'", pos);
        }
    }
}
