package com.vulsoft.dystos.jvm;

import static com.vulsoft.dystos.jvm.classfile.Code.*;

import com.vulsoft.dystos.ast.Ast.*;
import com.vulsoft.dystos.jvm.classfile.ClassWriter;
import com.vulsoft.dystos.jvm.classfile.Code;
import com.vulsoft.dystos.jvm.classfile.Label;
import com.vulsoft.dystos.sema.Symbols.ClassSymbol;
import com.vulsoft.dystos.sema.Symbols.FuncGroup;
import com.vulsoft.dystos.sema.Symbols.FuncSymbol;
import com.vulsoft.dystos.sema.Symbols.ModuleSymbol;
import com.vulsoft.dystos.sema.Symbols.Symbol;
import com.vulsoft.dystos.sema.Symbols.VarSymbol;
import com.vulsoft.dystos.sema.Type;
import com.vulsoft.dystos.sema.Type.UnitType;
import com.vulsoft.dystos.sema.TypedProgram;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Génération des classes d'un module : classe {@code $Module} (fonctions, script, point d'entrée) et classes utilisateur. */
final class ModuleGen {

    /** Erreur de génération rattachée à un module. */
    record Problem(String module, UnsupportedException error) {
    }

    private final TypedProgram typed;
    private final String moduleName;
    private final SourceFile file;
    private final ModuleSymbol module;
    private final Map<String, byte[]> out;
    private final List<Problem> problems;
    private final LambdaGen lambdas;

    ModuleGen(TypedProgram typed, String moduleName, SourceFile file, Map<String, byte[]> out, List<Problem> problems) {
        this(typed, moduleName, file, out, problems, new LambdaGen(typed, out, moduleName));
    }

    ModuleGen(TypedProgram typed, String moduleName, SourceFile file, Map<String, byte[]> out, List<Problem> problems,
              LambdaGen lambdas) {
        this.lambdas = lambdas;
        this.typed = typed;
        this.moduleName = moduleName;
        this.file = file;
        this.module = typed.modules.get(moduleName);
        this.out = out;
        this.problems = problems;
    }

    void generate(boolean entry) {
        String self = Jt.moduleClass(moduleName);
        ClassWriter cw = new ClassWriter(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_SUPER | ClassWriter.ACC_FINAL,
                self, Jt.OBJECT, List.of());

        declareScriptGlobals(cw);
        for (TopLevel d : file.decls()) {
            guard(() -> {
                if (d instanceof FuncDecl f) {
                    FuncSymbol fs = funcOf(f);
                    genMethod(cw, ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC, fs, f, null);
                } else if (d instanceof ClassDecl c) {
                    new ClassGen(typed, classOf(c.name()), out, lambdas).generate();
                } else if (d instanceof InterfaceDecl i) {
                    new ClassGen(typed, classOf(i.name()), out, lambdas).generate();
                } else if (d instanceof EnumDecl e) {
                    new ClassGen(typed, classOf(e.name()), out, lambdas).generate();
                }
            });
        }
        guard(() -> genRun(cw, self));
        if (entry) {
            genEntry(cw, self);
        }
        if (problems.stream().noneMatch(p -> p.module().equals(moduleName))) {
            guard(() -> out.put(self, cw.toBytes()));
        }
    }

    /**
     * Variables de niveau fichier d'un script lues ou modifiées par une fonction ou une méthode : champs statiques de
     * la classe du module (les autres restent des variables locales de {@code $run}, plus rapides).
     */
    private void declareScriptGlobals(ClassWriter cw) {
        if (!file.isScript()) {
            return;
        }
        java.util.Set<Symbol> used = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (TopLevel d : file.decls()) {
            AstWalk.forEach(d, n -> {
                if (n instanceof Name nm && typed.names.get(nm) != null) {
                    used.add(typed.names.get(nm));
                }
            });
        }
        for (Stmt st : file.statements()) {
            if (!(st instanceof VarDecl || st instanceof Assign)) {
                continue;
            }
            List<VarSymbol> declared = typed.declared.get(st);
            if (declared == null) {
                continue;
            }
            VarSymbol v = declared.get(0);
            if (used.contains(v)) {
                String field = "$g_" + v.name;
                lambdas.declareGlobal(v, field);
                guard(() -> cw.field(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC, field, Jt.desc(v.type)));
            }
        }
    }

    private void guard(Runnable r) {
        try {
            r.run();
        } catch (UnsupportedException e) {
            problems.add(new Problem(moduleName, e));
        } catch (Code.MethodTooLargeException e) {
            problems.add(new Problem(moduleName, new UnsupportedException(e.getMessage())));
        }
    }

    private FuncSymbol funcOf(FuncDecl d) {
        Symbol s = module.members.get(d.name());
        if (s instanceof FuncGroup g) {
            for (FuncSymbol f : g.funcs) {
                if (f.decl == d) {
                    return f;
                }
            }
        }
        throw new IllegalStateException("fonction non résolue : " + d.name());
    }

    private ClassSymbol classOf(String name) {
        return (ClassSymbol) module.members.get(name);
    }

    /** Génère une fonction ou une méthode (corps compris). */
    void genMethod(ClassWriter cw, int access, FuncSymbol f, FuncDecl d, ClassSymbol owner) {
        try {
            genMethodInner(cw, access, f, d, owner);
        } catch (UnsupportedException e) {
            throw e.at(d.pos());
        }
    }

    private void genMethodInner(ClassWriter cw, int access, FuncSymbol f, FuncDecl d, ClassSymbol owner) {
        ClassWriter.Method m = cw.method(access, f.name, MethodGen.funcDesc(f));
        if (m.code() == null) {
            return;
        }
        MethodGen mg = new MethodGen(typed, m.code(), owner, f.returnType, lambdas);
        TypedProgram.FuncInfo fi = typed.funcs.get(d);
        int slot = 0;
        if ((access & ClassWriter.ACC_STATIC) == 0) {
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

    // ---- $run : script ou appel de main ---------------------------------------------

    private void genRun(ClassWriter cw, String self) {
        ClassWriter.Method m = cw.method(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC, "$run", "()V");
        Code code = m.code();
        MethodGen mg = new MethodGen(typed, code, null, Type.UNIT, lambdas);
        if (file.isScript()) {
            mg.block(file.statements());
        } else {
            Symbol main = module.members.get("main");
            if (main instanceof FuncGroup g && g.funcs.size() == 1 && g.funcs.get(0).params.isEmpty()
                    && g.funcs.get(0).returnType instanceof UnitType) {
                code.invokestatic(self, "main", "()V");
            }
        }
        mg.finish();
    }

    // ---- point d'entrée Java : main(String[]) --------------------------------------------

    private void genEntry(ClassWriter cw, String self) {
        ClassWriter.Method m = cw.method(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC, "main", "([Ljava/lang/String;)V");
        Code code = m.code();
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();
        Label after = new Label();
        code.label(start);
        for (String dep : dependencies()) {
            code.invokestatic(Jt.moduleClass(dep), "$run", "()V");
        }
        code.invokestatic(self, "$run", "()V");
        code.label(end);
        code.jump(GOTO, after);
        code.handlerLabel(handler);
        code.invokestatic(Jt.RT, "crash", "(Ljava/lang/Throwable;)V");
        code.label(after);
        code.invokestatic(Jt.RT, "flush", "()V");
        code.ret('V');
        code.tryCatch(start, end, handler, "java/lang/Throwable");
    }

    /** Modules importés (récursivement), dans l'ordre d'initialisation : dépendances d'abord. */
    private List<String> dependencies() {
        Set<String> seen = new LinkedHashSet<>();
        collect(moduleName, seen, new LinkedHashSet<>());
        seen.remove(moduleName);
        return new ArrayList<>(seen);
    }

    private void collect(String name, Set<String> done, Set<String> visiting) {
        if (!visiting.add(name)) {
            return;
        }
        for (var u : typed.units) {
            if (u.module().equals(name)) {
                for (ImportDecl imp : u.file().imports()) {
                    if (typed.modules.containsKey(imp.path())) {
                        collect(imp.path(), done, visiting);
                    }
                }
            }
        }
        done.add(name);
    }
}
