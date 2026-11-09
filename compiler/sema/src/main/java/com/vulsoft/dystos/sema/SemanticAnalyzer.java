package com.vulsoft.dystos.sema;

import com.vulsoft.dystos.ast.Ast.SourceFile;
import com.vulsoft.dystos.lexer.LexerException;
import com.vulsoft.dystos.parser.ParseException;
import com.vulsoft.dystos.parser.Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analyse sémantique de Dystos : résolution des noms, typage, null-safety, vérification des
 * classes et interfaces.
 *
 * <pre>
 * Result r = SemanticAnalyzer.check(source);
 * for (Diagnostic d : r.diagnostics()) System.out.println(d);
 * String json = r.toJson();
 * </pre>
 */
public final class SemanticAnalyzer {

    private SemanticAnalyzer() {
    }

    /** Un fichier à analyser, identifié par son nom de module (ex. {@code app.outils}). */
    public record SourceUnit(String module, SourceFile file) {
    }

    /** @param typed programme typé (types, symboles résolus, appels) ; null si l'analyse n'a pas pu avoir lieu */
    public record Result(List<Diagnostic> diagnostics, TypedProgram typed) {

        public Result(List<Diagnostic> diagnostics) {
            this(diagnostics, null);
        }

        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(Diagnostic::isError);
        }

        public List<Diagnostic> errors() {
            return diagnostics.stream().filter(Diagnostic::isError).toList();
        }

        public String toJson() {
            return Diagnostic.toJson(diagnostics);
        }
    }

    /** Analyse des modules déjà parsés. */
    public static Result analyze(List<SourceUnit> units) {
        return analyze(units, List.of("java.", "javax."));
    }

    /**
     * @param javaPrefixes préfixes de classes Java accessibles (liste vide : interopérabilité désactivée)
     */
    public static Result analyze(List<SourceUnit> units, List<String> javaPrefixes) {
        return analyze(units, javaPrefixes, List.of());
    }

    /**
     * @param javaPrefixes préfixes de classes Java autorisés
     * @param javaDenied   préfixes interdits, prioritaires sur les préfixes autorisés
     */
    public static Result analyze(List<SourceUnit> units, List<String> javaPrefixes, List<String> javaDenied) {
        Ctx ctx = new Ctx(new JavaInterop(javaPrefixes, javaDenied));
        new Analyzer(ctx).run(units);
        ctx.typed.units = List.copyOf(units);
        ctx.typed.modules.putAll(ctx.modules);
        return new Result(List.copyOf(ctx.diagnostics), ctx.typed);
    }

    /** Analyse un source unique (module « main »). Les erreurs lexicales et syntaxiques deviennent des diagnostics. */
    public static Result check(String source) {
        SourceFile file;
        try {
            file = Parser.parse(source);
        } catch (LexerException e) {
            return single("E0001", e.getMessage(), e.line(), e.column());
        } catch (ParseException e) {
            return single("E0002", e.getMessage(), e.line(), e.column());
        }
        return analyze(List.of(new SourceUnit("main", file)));
    }

    /** Analyse plusieurs sources (module -> texte), dans l'ordre fourni. */
    public static Result checkAll(Map<String, String> sources) {
        List<SourceUnit> units = new ArrayList<>();
        List<Diagnostic> diags = new ArrayList<>();
        for (Map.Entry<String, String> en : sources.entrySet()) {
            try {
                units.add(new SourceUnit(en.getKey(), Parser.parse(en.getValue())));
            } catch (LexerException e) {
                diags.add(parseDiag("E0001", e.getMessage(), e.line(), e.column(), en.getKey()));
            } catch (ParseException e) {
                diags.add(parseDiag("E0002", e.getMessage(), e.line(), e.column(), en.getKey()));
            }
        }
        if (!diags.isEmpty()) {
            return new Result(diags);
        }
        return analyze(units);
    }

    private static Result single(String code, String message, int line, int column) {
        return new Result(List.of(parseDiag(code, message, line, column, null)));
    }

    private static Diagnostic parseDiag(String code, String message, int line, int column, String file) {
        int i = message.indexOf(" : ");
        String text = i >= 0 ? message.substring(i + 3) : message;
        return new Diagnostic(Diagnostic.Severity.ERROR, code, text, line, column, file);
    }
}
