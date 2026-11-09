package com.vulsoft.dystos.jvm;

import static com.vulsoft.dystos.jvm.classfile.Code.*;

import com.vulsoft.dystos.ast.Ast.Lambda;
import com.vulsoft.dystos.jvm.classfile.ClassWriter;
import com.vulsoft.dystos.jvm.classfile.Code;
import com.vulsoft.dystos.sema.Symbols.FuncSymbol;
import com.vulsoft.dystos.sema.Symbols.ParamSym;
import com.vulsoft.dystos.sema.Symbols.VarSymbol;
import com.vulsoft.dystos.sema.Type.FunctionType;
import com.vulsoft.dystos.sema.TypedProgram;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Génération des fonctions comme valeurs. Une lambda devient une classe synthétique {@code <module>/$Lambda<n>}
 * qui implémente {@code dystos.rt.Fn} ({@code Object call(Object[])}) : arguments et résultat sont toujours encadrés.
 * Les variables capturées sont copiées dans des champs ; celles qui sont réaffectées (voir
 * {@link TypedProgram#isBoxed}) sont partagées par l'intermédiaire d'une boîte {@code dystos.rt.Ref}.
 * Une fonction nommée passée en valeur est enveloppée par une classe adaptatrice.
 */
final class LambdaGen {

    private static final String CALL_DESC = "([Ljava/lang/Object;)Ljava/lang/Object;";

    private final TypedProgram typed;
    private final Map<String, byte[]> out;
    private final String modulePath;
    private final Map<FuncSymbol, String> adapters = new IdentityHashMap<>();
    private final Map<VarSymbol, String> globals = new IdentityHashMap<>();
    private final String moduleClass;
    private int counter;

    LambdaGen(TypedProgram typed, Map<String, byte[]> out, String moduleName) {
        this.typed = typed;
        this.out = out;
        this.modulePath = Jt.modulePath(moduleName);
        this.moduleClass = Jt.moduleClass(moduleName);
    }

    // ---- variables globales d'un script (champs statiques de la classe du module) -------------------------

    void declareGlobal(VarSymbol v, String field) {
        globals.put(v, field);
    }

    boolean isGlobal(VarSymbol v) {
        return globals.containsKey(v);
    }

    String globalField(VarSymbol v) {
        return globals.get(v);
    }

    String moduleClass() {
        return moduleClass;
    }

    /** Variables à copier dans la lambda : les globales sont lues directement dans leur champ statique. */
    List<VarSymbol> captures(TypedProgram.LambdaInfo li) {
        return li.captured().stream().filter(v -> !globals.containsKey(v)).toList();
    }

    /** Descripteur du champ (et du paramètre de constructeur) qui porte une variable capturée. */
    static String captureDesc(TypedProgram typed, VarSymbol v) {
        return typed.isBoxed(v) ? "L" + Jt.REF + ";" : Jt.desc(v.type);
    }

    private static char captureKind(TypedProgram typed, VarSymbol v) {
        return typed.isBoxed(v) ? 'A' : Jt.kind(v.type);
    }

    /** Génère la classe d'une lambda et retourne son nom interne. */
    String define(Lambda l, TypedProgram.LambdaInfo li, FunctionType ft) {
        String name = modulePath + "/$Lambda" + counter++;
        ClassWriter cw = new ClassWriter(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_SUPER | ClassWriter.ACC_FINAL,
                name, Jt.OBJECT, List.of(Jt.FN));
        List<VarSymbol> caps = captures(li);

        StringBuilder ctorDesc = new StringBuilder("(");
        for (int i = 0; i < caps.size(); i++) {
            String d = captureDesc(typed, caps.get(i));
            cw.field(ClassWriter.ACC_PRIVATE | ClassWriter.ACC_FINAL, "c" + i, d);
            ctorDesc.append(d);
        }
        ctorDesc.append(")V");

        Code ctor = cw.method(ClassWriter.ACC_PUBLIC, "<init>", ctorDesc.toString()).code();
        ctor.load('A', 0);
        ctor.invokespecial(Jt.OBJECT, "<init>", "()V");
        int slot = 1;
        for (int i = 0; i < caps.size(); i++) {
            char k = captureKind(typed, caps.get(i));
            ctor.load('A', 0);
            ctor.load(k, slot);
            ctor.putfield(name, "c" + i, captureDesc(typed, caps.get(i)));
            slot += Code.size(k);
        }
        ctor.ret('V');

        Code code = cw.method(ClassWriter.ACC_PUBLIC, "call", CALL_DESC).code();
        MethodGen mg = new MethodGen(typed, code, null, ft.result(), this);
        for (int i = 0; i < caps.size(); i++) {
            code.load('A', 0);
            code.getfield(name, "c" + i, captureDesc(typed, caps.get(i)));
            mg.declareCaptured(caps.get(i));
        }
        for (int j = 0; j < li.params().size(); j++) {
            code.load('A', 1);
            code.iconst(j);
            code.simple(AALOAD, -1);
            mg.declareParamFromObject(li.params().get(j));
        }
        mg.lambdaReturn(l.body(), ft.result());
        out.put(name, cw.toBytes());
        return name;
    }

    /** Classe qui enveloppe une fonction de module pour la passer en valeur ; retourne son nom interne. */
    String adapter(FuncSymbol f) {
        String existing = adapters.get(f);
        if (existing != null) {
            return existing;
        }
        String name = modulePath + "/$Lambda" + counter++;
        adapters.put(f, name);
        ClassWriter cw = new ClassWriter(ClassWriter.ACC_PUBLIC | ClassWriter.ACC_SUPER | ClassWriter.ACC_FINAL,
                name, Jt.OBJECT, List.of(Jt.FN));
        Code ctor = cw.method(ClassWriter.ACC_PUBLIC, "<init>", "()V").code();
        ctor.load('A', 0);
        ctor.invokespecial(Jt.OBJECT, "<init>", "()V");
        ctor.ret('V');

        Code code = cw.method(ClassWriter.ACC_PUBLIC, "call", CALL_DESC).code();
        MethodGen mg = new MethodGen(typed, code, null, f.returnType, this);
        int i = 0;
        for (ParamSym p : f.params) {
            code.load('A', 1);
            code.iconst(i++);
            code.simple(AALOAD, -1);
            mg.coerce(MethodGen.ERASED, p.type());
        }
        code.invokestatic(Jt.moduleClass(f.module.name), f.name, MethodGen.funcDesc(f));
        char k = Jt.kind(f.returnType);
        if (k == 'V') {
            code.aconstNull();
        } else {
            mg.box(k);
        }
        code.ret('A');
        out.put(name, cw.toBytes());
        return name;
    }
}
