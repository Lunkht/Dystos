package com.vulsoft.dystos.sema;

import com.vulsoft.dystos.ast.Ast.Attribute;
import com.vulsoft.dystos.ast.Ast.Call;
import com.vulsoft.dystos.ast.Ast.ExceptClause;
import com.vulsoft.dystos.ast.Ast.Expr;
import com.vulsoft.dystos.ast.Ast.Lambda;
import com.vulsoft.dystos.ast.Ast.FuncDecl;
import com.vulsoft.dystos.ast.Ast.Name;
import com.vulsoft.dystos.ast.Ast.Node;
import com.vulsoft.dystos.sema.Symbols.ClassSymbol;
import com.vulsoft.dystos.sema.Symbols.FuncSymbol;
import com.vulsoft.dystos.sema.Symbols.ModuleSymbol;
import com.vulsoft.dystos.sema.Symbols.Symbol;
import com.vulsoft.dystos.sema.Symbols.VarSymbol;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Résultat détaillé de l'analyse, destiné aux générateurs de code : type de chaque expression, symbole
 * résolu pour chaque nom, surcharge choisie pour chaque appel, variables déclarées par chaque instruction.
 * Toutes les tables sont indexées par identité de nœud d'AST.
 */
public final class TypedProgram {

    /** Nature d'un appel. */
    public enum CallKind {
        /** Fonction de module ou prédéfinie. */
        FUNCTION,
        /** Méthode d'instance. */
        METHOD,
        /** Méthode statique ({@code @static}). */
        STATIC_METHOD,
        /** Création d'objet ({@code Classe(...)}). */
        CONSTRUCT,
        /** {@code super().init(...)}. */
        SUPER_INIT,
        /** {@code super().methode(...)}. */
        SUPER_METHOD,
        /** Appel d'une valeur fonction (variable, paramètre). */
        FUNC_VALUE,
        /** {@code int(x)}, {@code str(x)}, {@code float(x)}, {@code bool(x)}. */
        CONVERSION
    }

    /**
     * Cible d'un appel.
     *
     * @param func fonction ou méthode choisie ; {@code null} pour un constructeur par défaut, une conversion
     *             ou un appel de valeur fonction
     * @param cls  classe instanciée (CONSTRUCT, CONVERSION) ; sinon null
     * @param bindings paramètres de type inférés ou explicites pour cet appel (clés : {@code TypeVar.key()})
     */
    public record CallInfo(CallKind kind, FuncSymbol func, ClassSymbol cls, Map<Type.TypeVar, Type> bindings) {
    }

    /** Champ, propriété ou constante d'énumération désigné par un accès {@code x.nom}. */
    public record MemberInfo(VarSymbol field) {
    }

    /** Variables d'une fonction : {@code self} (null pour une fonction ou une méthode statique) et paramètres. */
    public record FuncInfo(VarSymbol self, List<VarSymbol> params) {
    }

    /**
     * Lambda : paramètres et variables capturées de portées englobantes (dans l'ordre de première utilisation).
     */
    public record LambdaInfo(List<VarSymbol> params, List<VarSymbol> captured) {
    }

    public final Map<Expr, Type> types = new IdentityHashMap<>();
    public final Map<Name, Symbol> names = new IdentityHashMap<>();
    public final Map<Call, CallInfo> calls = new IdentityHashMap<>();
    public final Map<Attribute, MemberInfo> members = new IdentityHashMap<>();
    public final Map<Node, List<VarSymbol>> declared = new IdentityHashMap<>();
    public final Map<FuncDecl, FuncInfo> funcs = new IdentityHashMap<>();
    /** Type attrapé par chaque clause {@code except} (Exception pour une clause sans type). */
    public final Map<ExceptClause, Type> exceptTypes = new IdentityHashMap<>();
    public final Map<Lambda, LambdaInfo> lambdas = new IdentityHashMap<>();
    /** Variables capturées par au moins une lambda. */
    public final java.util.Set<VarSymbol> capturedVars = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    /** Variables réaffectées après leur déclaration ({@code x = ...}, {@code x += ...}). */
    public final java.util.Set<VarSymbol> reassigned = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Une variable capturée par une lambda <em>et</em> réaffectée doit vivre dans une boîte partagée
     * ({@code dystos.rt.Ref}) pour que la lambda et la fonction englobante voient les mêmes valeurs ;
     * les autres sont simplement copiées.
     */
    public boolean isBoxed(VarSymbol v) {
        return capturedVars.contains(v) && reassigned.contains(v);
    }
    public List<SemanticAnalyzer.SourceUnit> units = List.of();
    /** Modules analysés (nom → symbole), pour retrouver les fonctions et classes d'un module. */
    public final Map<String, ModuleSymbol> modules = new LinkedHashMap<>();

    public Type typeOf(Expr e) {
        Type t = types.get(e);
        if (t == null) {
            throw new IllegalStateException("type inconnu pour l'expression à " + e.pos());
        }
        return t;
    }

    void declare(Node node, VarSymbol v) {
        declared.computeIfAbsent(node, k -> new ArrayList<>()).add(v);
    }
}
