package com.vulsoft.dystos.sema;

import static com.vulsoft.dystos.sema.Builtins.*;

import com.vulsoft.dystos.ast.Ast;
import com.vulsoft.dystos.ast.Ast.NamedType;
import com.vulsoft.dystos.ast.Ast.Pos;
import com.vulsoft.dystos.ast.Ast.TypeRef;
import com.vulsoft.dystos.sema.Symbols.ClassSymbol;
import com.vulsoft.dystos.sema.Symbols.ModuleSymbol;
import com.vulsoft.dystos.sema.Symbols.Symbol;
import com.vulsoft.dystos.sema.Type.ClassType;
import com.vulsoft.dystos.sema.Type.FunctionType;
import com.vulsoft.dystos.sema.Type.TypeVar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** État partagé d'une analyse : diagnostics, modules, résolution des noms et des types. */
final class Ctx {

    final List<Diagnostic> diagnostics = new ArrayList<>();
    final TypedProgram typed = new TypedProgram();
    final JavaInterop java;
    final Map<String, ModuleSymbol> modules = new LinkedHashMap<>();
    boolean multiFile;
    String currentModule;

    Ctx(JavaInterop java) {
        this.java = java;
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    void error(String code, String message, Pos pos) {
        add(Diagnostic.Severity.ERROR, code, message, pos);
    }

    void warning(String code, String message, Pos pos) {
        add(Diagnostic.Severity.WARNING, code, message, pos);
    }

    private void add(Diagnostic.Severity sev, String code, String message, Pos pos) {
        int line = pos == null ? 0 : pos.line();
        int col = pos == null ? 0 : pos.column();
        diagnostics.add(new Diagnostic(sev, code, message, line, col, multiFile ? currentModule : null));
    }

    // ------------------------------------------------------------------
    // Noms
    // ------------------------------------------------------------------

    /** Cherche un nom dans la portée, puis dans les imports « package.* ». */
    Symbol lookup(Scope scope, String name) {
        Symbol s = scope.lookup(name);
        if (s != null) {
            return s;
        }
        for (String pkg : scope.starPackages()) {
            ClassSymbol c = java.load(pkg + "." + name);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /** Résout un nom pointé (a.b.C) : module, classe, ou classe Java complète. */
    Symbol resolveQualified(String dotted, Scope scope) {
        String[] parts = dotted.split("\\.");
        Symbol cur = lookup(scope, parts[0]);
        if (cur == null && parts.length > 1) {
            ClassSymbol c = java.load(dotted);
            if (c != null) {
                return c;
            }
        }
        for (int i = 1; i < parts.length && cur != null; i++) {
            if (cur instanceof ModuleSymbol m) {
                cur = m.members.get(parts[i]);
            } else {
                return null;
            }
        }
        return cur;
    }

    // ------------------------------------------------------------------
    // Types
    // ------------------------------------------------------------------

    Type resolveType(TypeRef t, Scope scope) {
        if (t instanceof Ast.FuncType f) {
            List<Type> params = new ArrayList<>();
            for (TypeRef p : f.params()) {
                params.add(resolveType(p, scope));
            }
            return new FunctionType(params, resolveType(f.result(), scope), f.nullable());
        }
        NamedType nt = (NamedType) t;
        String name = nt.name();
        if (!name.contains(".")) {
            TypeVar tv = scope.lookupTypeVar(name);
            if (tv != null) {
                if (!nt.args().isEmpty()) {
                    error("E0207", "le paramètre de type '" + name + "' n'accepte pas d'arguments", nt.pos());
                }
                return nt.nullable() ? tv.withNullable(true) : tv;
            }
            if (name.equals("unit")) {
                if (nt.nullable()) {
                    error("E0207", "'unit' ne peut pas être nullable", nt.pos());
                }
                return Type.UNIT;
            }
        }
        Symbol s = resolveQualified(name, scope);
        if (!(s instanceof ClassSymbol cs)) {
            error("E0101", "type inconnu '" + name + "'." + Suggest.hint(name, scope.allNames()), nt.pos());
            return Type.ERROR;
        }
        List<Type> args = new ArrayList<>();
        for (TypeRef a : nt.args()) {
            args.add(resolveType(a, scope));
        }
        if (args.size() != cs.typeParams.size()) {
            error("E0207", "le type '" + cs.name + "' attend " + cs.typeParams.size()
                    + " argument(s) de type, " + args.size() + " fourni(s)", nt.pos());
            args = new ArrayList<>();
            for (int i = 0; i < cs.typeParams.size(); i++) {
                args.add(Type.ERROR);
            }
        }
        return new ClassType(cs, args, nt.nullable());
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    static String mismatch(Type expected, Type found) {
        if (found instanceof Type.NullType && !expected.nullable()) {
            return "'null' ne peut pas être utilisé comme '" + expected + "' (utilisez '" + expected + "?')";
        }
        if (found.nullable() && !expected.nullable()
                && Types.isAssignable(found.withNullable(false), expected)) {
            return "type '" + found + "' incompatible avec '" + expected
                    + "' : la valeur peut être null (vérifiez-la ou utilisez '?:')";
        }
        return "type incompatible : attendu '" + expected + "', trouvé '" + found + "'";
    }

    static String codeFor(Type expected, Type found) {
        return (found instanceof Type.NullType || found.nullable()) && !expected.nullable() ? "E0201" : "E0202";
    }
}
