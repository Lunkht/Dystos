package com.vulsoft.dystos.jvm;

import com.vulsoft.dystos.ast.Ast.*;
import com.vulsoft.dystos.jvm.classfile.ClassWriter;
import com.vulsoft.dystos.jvm.classfile.Code;
import com.vulsoft.dystos.sema.Symbols.ClassKind;
import com.vulsoft.dystos.sema.Symbols.ClassSymbol;
import com.vulsoft.dystos.sema.Symbols.FuncSymbol;
import com.vulsoft.dystos.sema.Symbols.ParamSym;
import com.vulsoft.dystos.sema.Symbols.VarSymbol;
import com.vulsoft.dystos.sema.Type;
import com.vulsoft.dystos.sema.Type.ClassType;
import com.vulsoft.dystos.sema.TypedProgram;
import com.vulsoft.dystos.sema.TypedProgram.CallInfo;
import com.vulsoft.dystos.sema.TypedProgram.CallKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Génération d'une classe, d'une interface ou d'une énumération Dystos. */
final class ClassGen {

    private final TypedProgram typed;
    private final ClassSymbol cs;
    private final Map<String, byte[]> out;
    private final String self;
    private final LambdaGen lambdas;
    private ClassWriter cw;

    ClassGen(TypedProgram typed, ClassSymbol cs, Map<String, byte[]> out, LambdaGen lambdas) {
        this.lambdas = lambdas;
        this.typed = typed;
        this.cs = cs;
        this.out = out;
        this.self = Jt.internalName(cs);
    }

    private void rejectJavaSupertypes() {
        boolean java = cs.superType != null && cs.superType.cls().kind == ClassKind.JAVA;
        for (ClassType i : cs.interfaces) {
            java |= i.cls().kind == ClassKind.JAVA;
        }
        if (java) {
            throw new UnsupportedException("hériter d'une classe Java ou implémenter une interface Java n'est pas encore "
                    + "supporté par le backend JVM");
        }
    }

    void generate() {
        try {
            rejectJavaSupertypes();
            if (cs.decl instanceof InterfaceDecl i) {
                genInterface(i);
            } else if (cs.decl instanceof EnumDecl e) {
                genEnum(e);
            } else {
                genClass((ClassDecl) cs.decl);
            }
            out.put(self, cw.toBytes());
        } catch (UnsupportedException e) {
            throw e.at(((Node) cs.decl).pos());
        }
    }

    private List<String> interfaceNames() {
        List<String> names = new ArrayList<>();
        for (ClassType i : cs.interfaces) {
            names.add(Jt.internalName(i.cls()));
        }
        return names;
    }

    // ------------------------------------------------------------------
    // Interfaces
    // ------------------------------------------------------------------

    private ClassWriter defaults;

    private void genInterface(InterfaceDecl d) {
        cw = new ClassWriter(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_INTERFACE | ClassWriter.ACC_ABSTRACT,
                self, Jt.OBJECT, interfaceNames());
        for (Member m : d.members()) {
            if (!(m instanceof FuncDecl f)) {
                continue;
            }
            FuncSymbol fs = method(f);
            if (!fs.isStatic) {
                cw.method(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_ABSTRACT, fs.name, MethodGen.funcDesc(fs));
            }
            if (f.body() != null) {
                genCompanion(fs, f);
            }
        }
        if (defaults != null) {
            out.put(self + "$Defaults", defaults.toBytes());
        }
    }

    /**
     * Le format de classe 49 interdit une méthode d'interface avec corps : le code d'une méthode par défaut (ou
     * statique) vit dans une méthode statique de la classe compagnon {@code Interface$Defaults} ; chaque classe qui
     * implémente l'interface reçoit une méthode de redirection (voir {@link #genDefaultForwarders}).
     */
    private void genCompanion(FuncSymbol fs, FuncDecl d) {
        if (defaults == null) {
            defaults = new ClassWriter(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_SUPER | ClassWriter.ACC_FINAL,
                    self + "$Defaults", Jt.OBJECT, List.of());
        }
        String desc = fs.isStatic ? MethodGen.funcDesc(fs) : "(L" + self + ";" + MethodGen.funcDesc(fs).substring(1);
        Code code = defaults.method(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC, fs.name, desc).code();
        MethodGen mg = new MethodGen(typed, code, cs, fs.returnType, lambdas);
        TypedProgram.FuncInfo fi = typed.funcs.get(d);
        int slot = 0;
        if (!fs.isStatic) {
            mg.bindParam(fi.self(), 0);
            slot = 1;
        }
        for (VarSymbol p : fi.params()) {
            mg.bindParam(p, slot);
            slot += Code.size(Jt.kind(p.type));
        }
        mg.block(d.body());
        mg.finish();
    }

    private record DefaultRef(ClassSymbol iface, FuncSymbol f) {
    }

    private static boolean isDefault(FuncSymbol f) {
        return !f.isStatic && f.decl != null && f.decl.body() != null;
    }

    /** Méthodes par défaut héritées d'une interface et de ses parentes (la plus proche l'emporte). */
    private void collectDefaults(ClassSymbol iface, Map<String, DefaultRef> out) {
        if (iface.kind != ClassKind.INTERFACE) {
            return;
        }
        for (var list : iface.methods.values()) {
            for (FuncSymbol f : list) {
                if (isDefault(f)) {
                    out.putIfAbsent(f.name + "/" + f.params.size(), new DefaultRef(iface, f));
                }
            }
        }
        for (ClassType p : iface.interfaces) {
            collectDefaults(p.cls(), out);
        }
    }

    private static boolean declares(ClassSymbol c, String key) {
        for (var list : c.methods.values()) {
            for (FuncSymbol f : list) {
                if (!f.isStatic && (f.name + "/" + f.params.size()).equals(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** La classe (ou une de ses superclasses) déclare ou redirige-t-elle déjà cette méthode ? */
    private boolean providedBySuper(ClassType sup, String key) {
        for (ClassType c = sup; c != null && c.cls().kind == ClassKind.CLASS; c = c.cls().superType) {
            if (declares(c.cls(), key)) {
                return true;
            }
            Map<String, DefaultRef> inherited = new java.util.LinkedHashMap<>();
            for (ClassType i : c.cls().interfaces) {
                collectDefaults(i.cls(), inherited);
            }
            if (inherited.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /** Redirige vers la classe compagnon les méthodes par défaut que la classe n'a pas redéfinies. */
    private void genDefaultForwarders() {
        Map<String, DefaultRef> defs = new java.util.LinkedHashMap<>();
        for (ClassType i : cs.interfaces) {
            collectDefaults(i.cls(), defs);
        }
        for (Map.Entry<String, DefaultRef> en : defs.entrySet()) {
            if (declares(cs, en.getKey()) || providedBySuper(cs.superType, en.getKey())) {
                continue;
            }
            FuncSymbol f = en.getValue().f();
            Code code = cw.method(ClassWriter.ACC_PUBLIC, f.name, MethodGen.funcDesc(f)).code();
            code.load('A', 0);
            int slot = 1;
            for (ParamSym p : f.params) {
                char k = Jt.kind(p.type());
                code.load(k, slot);
                slot += Code.size(k);
            }
            String owner = Jt.internalName(en.getValue().iface());
            code.invokestatic(owner + "$Defaults", f.name, "(L" + owner + ";" + MethodGen.funcDesc(f).substring(1));
            code.ret(Jt.kind(f.returnType));
        }
    }

    // ------------------------------------------------------------------
    // Classes
    // ------------------------------------------------------------------

    private void genClass(ClassDecl d) {
        String sup = cs.superType != null ? Jt.internalName(cs.superType.cls()) : Jt.DOBJECT;
        int access = ClassWriter.ACC_PUBLIC | ClassWriter.ACC_SUPER | (cs.isAbstract ? ClassWriter.ACC_ABSTRACT : 0);
        cw = new ClassWriter(access, self, sup, interfaceNames());
        genFields(d.members());
        genConstructors(d);
        genMethods(d.members());
        genDefaultForwarders();
        genBridges();
    }

    private void genFields(List<Member> members) {
        for (Member m : members) {
            if (m instanceof VarDecl v) {
                VarSymbol f = cs.fields.get(v.name());
                cw.field(ClassWriter.ACC_PUBLIC, f.name, Jt.desc(f.type));
            }
        }
    }

    private FuncSymbol method(FuncDecl d) {
        List<FuncSymbol> all = new ArrayList<>(cs.constructors);
        cs.methods.values().forEach(all::addAll);
        for (FuncSymbol f : all) {
            if (f.decl == d) {
                return f;
            }
        }
        throw new IllegalStateException("méthode non résolue : " + d.name());
    }

    private void genMethods(List<Member> members) {
        ModuleGen helper = new ModuleGen(typed, cs.module.name, null, out, new ArrayList<>(), lambdas);
        for (Member m : members) {
            if (!(m instanceof FuncDecl f)) {
                continue;
            }
            FuncSymbol fs = method(f);
            if (fs.isConstructor) {
                continue;
            }
            int access = ClassWriter.ACC_PUBLIC | (fs.isStatic ? ClassWriter.ACC_STATIC : 0)
                    | (fs.isAbstract ? ClassWriter.ACC_ABSTRACT : 0);
            helper.genMethod(cw, access, fs, f, cs);
        }
    }

    /**
     * Une redéfinition dont la signature JVM diffère de celle de la méthode parente (génériques effacés, type de
     * retour plus précis) ne la remplace pas pour la JVM : on génère une méthode « pont » qui a la signature du parent,
     * convertit les arguments et appelle la redéfinition.
     */
    private void genBridges() {
        java.util.Set<String> defined = new java.util.HashSet<>();
        for (var list : cs.methods.values()) {
            for (FuncSymbol f : list) {
                if (!f.isStatic) {
                    defined.add(f.name + MethodGen.funcDesc(f));
                }
            }
        }
        for (var list : cs.methods.values()) {
            for (FuncSymbol f : list) {
                if (f.isStatic) {
                    continue;
                }
                bridgeAgainst(f, cs.superType == null ? null : cs.superType.cls(), defined);
                for (ClassType i : cs.interfaces) {
                    bridgeAgainst(f, i.cls(), defined);
                }
            }
        }
    }

    private void bridgeAgainst(FuncSymbol f, ClassSymbol ancestor, java.util.Set<String> defined) {
        if (ancestor == null || ancestor.kind == ClassKind.BUILTIN || ancestor.kind == ClassKind.JAVA) {
            return;
        }
        var list = ancestor.methods.get(f.name);
        if (list != null) {
            for (FuncSymbol g : list) {
                if (!g.isStatic && g.params.size() == f.params.size() && defined.add(g.name + MethodGen.funcDesc(g))) {
                    genBridge(f, g);
                }
            }
        }
        if (ancestor.superType != null) {
            bridgeAgainst(f, ancestor.superType.cls(), defined);
        }
        for (ClassType i : ancestor.interfaces) {
            bridgeAgainst(f, i.cls(), defined);
        }
    }

    private void genBridge(FuncSymbol f, FuncSymbol g) {
        Code code = cw.method(ClassWriter.ACC_PUBLIC, g.name, MethodGen.funcDesc(g)).code();
        MethodGen mg = new MethodGen(typed, code, cs, g.returnType, lambdas);
        code.load('A', 0);
        int slot = 1;
        for (int i = 0; i < g.params.size(); i++) {
            Type gp = g.params.get(i).type();
            char k = Jt.kind(gp);
            code.load(k, slot);
            slot += Code.size(k);
            mg.coerce(gp, f.params.get(i).type());
        }
        code.invokevirtual(self, f.name, MethodGen.funcDesc(f));
        mg.coerce(f.returnType, g.returnType);
        code.ret(Jt.kind(g.returnType));
    }

    // ------------------------------------------------------------------
    // Constructeurs
    // ------------------------------------------------------------------

    private void genConstructors(ClassDecl d) {
        String sup = cs.superType != null ? Jt.internalName(cs.superType.cls()) : Jt.DOBJECT;
        if (!cs.constructors.isEmpty()) {
            genDeclaredCtor(cs.constructors.get(0), sup, d);
            return;
        }
        List<FuncSymbol> inherited = inheritedCtors();
        if (inherited.isEmpty()) {
            genForwardingCtor(null, sup, d);
        } else {
            for (FuncSymbol f : inherited) {
                genForwardingCtor(f, sup, d);
            }
        }
    }

    /** Constructeurs du plus proche ancêtre qui en déclare. */
    private List<FuncSymbol> inheritedCtors() {
        ClassType t = cs.superType;
        while (t != null) {
            if (!t.cls().constructors.isEmpty()) {
                return t.cls().constructors;
            }
            t = t.cls().superType;
        }
        return List.of();
    }

    private void genDeclaredCtor(FuncSymbol f, String sup, ClassDecl d) {
        FuncDecl decl = f.decl;
        ClassWriter.Method m = cw.method(ClassWriter.ACC_PUBLIC, "<init>", MethodGen.ctorDesc(f));
        Code code = m.code();
        MethodGen mg = new MethodGen(typed, code, cs, Type.UNIT, lambdas);
        TypedProgram.FuncInfo fi = typed.funcs.get(decl);
        mg.bindParam(fi.self(), 0);
        int slot = 1;
        for (VarSymbol p : fi.params()) {
            mg.bindParam(p, slot);
            slot += Code.size(Jt.kind(p.type));
        }
        int start = 0;
        List<Stmt> body = decl.body();
        CallInfo superCall = body.isEmpty() ? null : superInit(body.get(0));
        if (superCall != null) {
            Call c = (Call) ((ExprStmt) body.get(0)).expr();
            mg.superInit(sup, c, superCall);
            start = 1;
        } else {
            requireNoArgSuper(decl);
            code.load('A', 0);
            code.invokespecial(sup, "<init>", "()V");
        }
        fieldInits(mg, d);
        mg.blockFrom(body, start);
        mg.finish();
    }

    private CallInfo superInit(Stmt s) {
        if (s instanceof ExprStmt e && e.expr() instanceof Call c) {
            CallInfo ci = typed.calls.get(c);
            if (ci != null && ci.kind() == CallKind.SUPER_INIT) {
                return ci;
            }
        }
        return null;
    }

    /** Sans appel explicite à {@code super().init(...)}, le constructeur parent doit pouvoir être appelé sans argument. */
    private void requireNoArgSuper(FuncDecl at) {
        ClassType t = cs.superType;
        while (t != null) {
            if (!t.cls().constructors.isEmpty()) {
                if (t.cls().constructors.stream().noneMatch(c -> c.params.isEmpty())) {
                    throw new UnsupportedException("le constructeur de '" + t.cls().name + "' exige des arguments : "
                            + "appelez super().init(...) en première instruction de init", at.pos());
                }
                return;
            }
            t = t.cls().superType;
        }
    }

    private void genForwardingCtor(FuncSymbol parent, String sup, ClassDecl d) {
        String desc = MethodGen.ctorDesc(parent);
        ClassWriter.Method m = cw.method(ClassWriter.ACC_PUBLIC, "<init>", desc);
        Code code = m.code();
        MethodGen mg = new MethodGen(typed, code, cs, Type.UNIT, lambdas);
        code.load('A', 0);
        int slot = 1;
        if (parent != null) {
            for (ParamSym p : parent.params) {
                char k = Jt.kind(p.type());
                code.load(k, slot);
                slot += Code.size(k);
            }
        }
        code.invokespecial(sup, "<init>", desc);
        fieldInits(mg, d);
        mg.finish();
    }

    private void fieldInits(MethodGen mg, ClassDecl d) {
        for (Member m : d.members()) {
            if (m instanceof VarDecl v && v.init() != null) {
                mg.fieldInit(self, cs.fields.get(v.name()), v.init());
            }
        }
    }

    // ------------------------------------------------------------------
    // Énumérations
    // ------------------------------------------------------------------

    private void genEnum(EnumDecl d) {
        cw = new ClassWriter(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_SUPER | ClassWriter.ACC_FINAL,
                self, Jt.DENUM, interfaceNames());
        String selfDesc = "L" + self + ";";
        for (Member m : d.members()) {
            if (m instanceof VarDecl v) {
                throw new UnsupportedException("les champs d'instance d'une énumération ne sont pas encore supportés "
                        + "par le backend JVM", v.pos());
            }
        }
        for (EnumConstant k : d.constants()) {
            cw.field(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC | ClassWriter.ACC_FINAL, k.name(), selfDesc);
        }
        // constructeur : (nom, ordinal)
        Code ctor = cw.method(ClassWriter.ACC_PUBLIC, "<init>", "(Ljava/lang/String;J)V").code();
        ctor.load('A', 0);
        ctor.ldcString(cs.name);
        ctor.load('A', 1);
        ctor.load('J', 2);
        ctor.invokespecial(Jt.DENUM, "<init>", "(Ljava/lang/String;Ljava/lang/String;J)V");
        ctor.ret('V');
        // initialisation statique des constantes
        Code clinit = cw.method(ClassWriter.ACC_STATIC, "<clinit>", "()V").code();
        long ord = 0;
        for (EnumConstant k : d.constants()) {
            clinit.anew(self);
            clinit.dup('A');
            clinit.ldcString(k.name());
            clinit.lconst(ord++);
            clinit.invokespecial(self, "<init>", "(Ljava/lang/String;J)V");
            clinit.putstatic(self, k.name(), selfDesc);
        }
        clinit.ret('V');
        genMethods(d.members());
        genDefaultForwarders();
        genBridges();
    }
}
