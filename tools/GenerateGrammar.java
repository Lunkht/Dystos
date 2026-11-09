import com.vulsoft.dystos.lexer.Highlighter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Génère les grammaires de coloration de Dystos à partir des listes de {@link Highlighter}
 * (source unique : mots-clés, types, fonctions prédéfinies, opérateurs).
 *
 * <pre>
 * java -cp build/classes tools/GenerateGrammar.java editors
 * </pre>
 *
 * Produit : textmate/dystos.tmLanguage.json, prism/prism-dystos.js,
 * vscode/syntaxes/dystos.tmLanguage.json, vscode/package.json, vscode/language-configuration.json.
 */
public class GenerateGrammar {

    // Partition des mots-clés par rôle (vérifiée contre Highlighter.KEYWORDS : aucun oubli possible)
    static final List<String> CONTROL = List.of("if", "elif", "else", "for", "while", "return", "break", "continue",
            "pass", "try", "except", "finally", "raise", "assert", "match", "case", "with");
    static final List<String> DECLARATION = List.of("abstract", "final", "import", "package", "val", "var", "as");
    static final List<String> WORD_OPERATORS = List.of("and", "or", "not", "in", "is");
    // def / class / interface / enum sont traités à part (ils nomment ce qui suit)

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "editors");
        checkPartition();
        String tm = Json.pretty(textMate());
        write(out.resolve("textmate/dystos.tmLanguage.json"), tm);
        write(out.resolve("vscode/syntaxes/dystos.tmLanguage.json"), tm);
        write(out.resolve("prism/prism-dystos.js"), prism());
        write(out.resolve("vscode/package.json"), Json.pretty(vscodePackage()));
        write(out.resolve("vscode/language-configuration.json"), Json.pretty(languageConfiguration()));
        System.out.println("Grammaires générées dans " + out.toAbsolutePath());
    }

    static void checkPartition() {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(CONTROL);
        all.addAll(DECLARATION);
        all.addAll(WORD_OPERATORS);
        all.addAll(List.of("def", "class", "interface", "enum"));
        if (!all.equals(Highlighter.KEYWORDS)) {
            Set<String> missing = new LinkedHashSet<>(Highlighter.KEYWORDS);
            missing.removeAll(all);
            Set<String> extra = new LinkedHashSet<>(all);
            extra.removeAll(Highlighter.KEYWORDS);
            throw new IllegalStateException("mots-clés non classés : " + missing + " ; inconnus : " + extra
                    + " — mettre à jour GenerateGrammar");
        }
    }

    static String words(Iterable<String> ws) {
        List<String> l = new ArrayList<>();
        ws.forEach(l::add);
        return String.join("|", l);
    }

    // ------------------------------------------------------------------
    // TextMate
    // ------------------------------------------------------------------

    static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    static Map<String, Object> include(String name) {
        return m("include", "#" + name);
    }

    static Map<String, Object> textMate() {
        List<Object> expression = new ArrayList<>();
        for (String r : List.of("comment", "decorator", "fstring", "string", "number", "declaration", "keyword",
                "constant", "self", "type", "builtin", "call", "constant-name", "class-name", "operator", "punctuation")) {
            expression.add(include(r));
        }
        Map<String, Object> repo = new LinkedHashMap<>();
        repo.put("comment", m("name", "comment.line.number-sign.dystos", "match", "#.*$"));
        repo.put("decorator", m("name", "entity.name.function.decorator.dystos", "match", "@[A-Za-z_][A-Za-z0-9_.]*"));
        repo.put("escape", m("name", "constant.character.escape.dystos",
                "match", "\\\\(?:[nrt0\\\\'\"\\n]|u[0-9a-fA-F]{4})"));
        repo.put("string", m("patterns", List.of(
                m("name", "string.quoted.triple.dystos", "begin", "\"\"\"", "end", "\"\"\"", "patterns", List.of(include("escape"))),
                m("name", "string.quoted.triple.dystos", "begin", "'''", "end", "'''", "patterns", List.of(include("escape"))),
                m("name", "string.quoted.double.dystos", "begin", "\"", "end", "\"|$", "patterns", List.of(include("escape"))),
                m("name", "string.quoted.single.dystos", "begin", "'", "end", "'|$", "patterns", List.of(include("escape"))))));
        repo.put("fstring", m("patterns", List.of(
                fstring("[fF](\"\"\")", "\"\"\"", false), fstring("[fF](''')", "'''", false),
                fstring("[fF](\")", "\"|$", true), fstring("[fF](')", "'|$", true))));
        repo.put("interpolation", m("name", "meta.interpolation.dystos", "begin", "\\{(?!\\{)", "end", "\\}",
                "beginCaptures", m("0", m("name", "punctuation.section.interpolation.begin.dystos")),
                "endCaptures", m("0", m("name", "punctuation.section.interpolation.end.dystos")),
                "patterns", List.of(
                        m("name", "constant.other.format-spec.dystos", "match", "(?<!\\?):[^{}'\"\\[\\]()\\n]*(?=\\})"),
                        m("begin", "\\(", "end", "\\)", "patterns", List.of(include("expression"))),
                        m("begin", "\\[", "end", "\\]", "patterns", List.of(include("expression"))),
                        include("expression"))));
        repo.put("expression", m("patterns", expression));
        repo.put("number", m("name", "constant.numeric.dystos",
                "match", "\\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|[0-9][0-9_]*(?:\\.[0-9][0-9_]*)?(?:[eE][+-]?[0-9]+)?)\\b"));
        repo.put("declaration", m("patterns", List.of(
                m("match", "\\b(def)\\s+([A-Za-z_][A-Za-z0-9_]*)",
                        "captures", m("1", m("name", "storage.type.function.dystos"), "2", m("name", "entity.name.function.dystos"))),
                m("match", "\\b(class|interface|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)",
                        "captures", m("1", m("name", "storage.type.class.dystos"), "2", m("name", "entity.name.type.class.dystos"))))));
        repo.put("keyword", m("patterns", List.of(
                m("name", "keyword.control.dystos", "match", "\\b(?:" + words(CONTROL) + ")\\b"),
                m("name", "storage.modifier.dystos", "match", "\\b(?:" + words(DECLARATION) + ")\\b"),
                m("name", "keyword.operator.word.dystos", "match", "\\b(?:" + words(WORD_OPERATORS) + ")\\b"))));
        repo.put("constant", m("name", "constant.language.dystos", "match", "\\b(?:" + words(Highlighter.CONSTANTS) + ")\\b"));
        repo.put("self", m("name", "variable.language.self.dystos", "match", "\\bself\\b"));
        repo.put("type", m("name", "support.type.dystos", "match", "\\b(?:" + words(Highlighter.TYPES) + ")\\b"));
        repo.put("builtin", m("name", "support.function.builtin.dystos", "match", "\\b(?:" + words(Highlighter.BUILTINS) + ")(?=\\()"));
        repo.put("call", m("name", "entity.name.function.call.dystos", "match", "\\b[A-Za-z_][A-Za-z0-9_]*(?=\\()"));
        repo.put("constant-name", m("name", "constant.other.dystos", "match", "\\b[A-Z][A-Z0-9_]+\\b"));
        repo.put("class-name", m("name", "entity.name.type.dystos", "match", "\\b[A-Z](?:[A-Za-z0-9_]*[a-z][A-Za-z0-9_]*)?\\b"));
        repo.put("operator", m("name", "keyword.operator.dystos", "match", operatorRegex()));
        repo.put("punctuation", m("name", "punctuation.separator.dystos", "match", "[()\\[\\]{},:.]"));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("$schema", "https://raw.githubusercontent.com/martinring/tmlanguage/master/tmlanguage.json");
        root.put("name", "Dystos");
        root.put("scopeName", "source.dystos");
        root.put("fileTypes", List.of("dys"));
        root.put("patterns", List.of(include("expression")));
        root.put("repository", repo);
        return root;
    }

    static Map<String, Object> fstring(String begin, String end, boolean singleLine) {
        return m("name", "string.quoted.other.fstring.dystos", "begin", begin, "end", end,
                "beginCaptures", m("0", m("name", "string.quoted.other.fstring.dystos")),
                "patterns", List.of(include("escape"),
                        m("match", "\\{\\{|\\}\\}"),
                        include("interpolation")));
    }

    static String operatorRegex() {
        List<String> parts = new ArrayList<>();
        for (String op : Highlighter.OPERATORS) {
            parts.add(op.replaceAll("([*+?.|\\-])", "\\\\$1"));
        }
        return String.join("|", parts);
    }

    // ------------------------------------------------------------------
    // Prism
    // ------------------------------------------------------------------

    static String prism() {
        String kw = words(Highlighter.KEYWORDS);
        String types = words(Highlighter.TYPES);
        String builtins = words(Highlighter.BUILTINS);
        String consts = words(Highlighter.CONSTANTS);
        String ops = operatorRegex().replace("/", "\\/");   // « / » doit être échappé dans un littéral /.../ JavaScript
        return "// Généré par tools/GenerateGrammar.java — ne pas modifier à la main.\n"
                + "// Usage : charger Prism, puis ce fichier ; le langage s'appelle « dystos » (alias « dys »).\n"
                + "(function (Prism) {\n"
                + "  Prism.languages.dystos = {\n"
                + "    'comment': { pattern: /#.*/, greedy: true },\n"
                + "    'fstring': {\n"
                + "      pattern: /[fF](?:\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')/,\n"
                + "      greedy: true,\n"
                + "      alias: 'string',\n"
                + "      inside: {\n"
                + "        'interpolation': {\n"
                + "          pattern: /\\{(?!\\{)(?:[^{}'\"\\n]|\"[^\"\\n]*\"|'[^'\\n]*')+\\}/,\n"
                + "          inside: {\n"
                + "            'format-spec': { pattern: /(?<!\\?):[^{}'\"\\[\\]()\\n]*(?=\\}$)/, alias: 'number' },\n"
                + "            'punctuation': /^\\{|\\}$/,\n"
                + "            'expression': { pattern: /[\\s\\S]+/, inside: null }\n"
                + "          }\n"
                + "        },\n"
                + "        'escape': { pattern: /\\\\(?:[nrt0\\\\'\"\\n]|u[0-9a-fA-F]{4})/, alias: 'string' },\n"
                + "        'fstring-text': { pattern: /[\\s\\S]+/, alias: 'string' }\n"
                + "      }\n"
                + "    },\n"
                + "    'string': {\n"
                + "      pattern: /\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'/,\n"
                + "      greedy: true,\n"
                + "      inside: { 'escape': { pattern: /\\\\(?:[nrt0\\\\'\"\\n]|u[0-9a-fA-F]{4})/, alias: 'entity' } }\n"
                + "    },\n"
                + "    'decorator': { pattern: /@[A-Za-z_][A-Za-z0-9_.]*/, alias: 'annotation' },\n"
                + "    'function-definition': {\n"
                + "      pattern: /(\\bdef\\s+)[A-Za-z_][A-Za-z0-9_]*/, lookbehind: true, alias: 'function'\n"
                + "    },\n"
                + "    'class-definition': {\n"
                + "      pattern: /(\\b(?:class|interface|enum)\\s+)[A-Za-z_][A-Za-z0-9_]*/, lookbehind: true, alias: 'class-name'\n"
                + "    },\n"
                + "    'keyword': /\\b(?:" + kw + ")\\b/,\n"
                + "    'boolean': /\\b(?:true|false)\\b/,\n"
                + "    'constant': /\\bnull\\b/,\n"
                + "    'variable': { pattern: /\\bself\\b/, alias: 'keyword' },\n"
                + "    'builtin': /\\b(?:" + types + ")\\b|\\b(?:" + builtins + ")(?=\\()/,\n"
                + "    'function': /\\b[A-Za-z_][A-Za-z0-9_]*(?=\\()/,\n"
                + "    'constant-name': { pattern: /\\b[A-Z][A-Z0-9_]+\\b/, alias: 'constant' },\n"
                + "    'class-name': /\\b[A-Z](?:[A-Za-z0-9_]*[a-z][A-Za-z0-9_]*)?\\b/,\n"
                + "    'number': /\\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|[0-9][0-9_]*(?:\\.[0-9][0-9_]*)?(?:[eE][+-]?[0-9]+)?)\\b/,\n"
                + "    'operator': /" + ops + "/,\n"
                + "    'punctuation': /[()\\[\\]{},:.]/\n"
                + "  };\n"
                + "  // L'expression d'une interpolation se colore avec le langage lui-même.\n"
                + "  Prism.languages.dystos.fstring.inside.interpolation.inside.expression.inside = Prism.languages.dystos;\n"
                + "  Prism.languages.dys = Prism.languages.dystos;\n"
                + "}(Prism));\n";
    }

    // ------------------------------------------------------------------
    // Extension VS Code
    // ------------------------------------------------------------------

    static Map<String, Object> vscodePackage() {
        return m("name", "dystos", "displayName", "Dystos", "description", "Coloration syntaxique pour le langage Dystos",
                "version", "0.1.0", "publisher", "vulsoft", "engines", m("vscode", "^1.70.0"),
                "categories", List.of("Programming Languages"),
                "contributes", m(
                        "languages", List.of(m("id", "dystos", "aliases", List.of("Dystos", "dystos"),
                                "extensions", List.of(".dys"), "configuration", "./language-configuration.json")),
                        "grammars", List.of(m("language", "dystos", "scopeName", "source.dystos",
                                "path", "./syntaxes/dystos.tmLanguage.json"))));
    }

    static Map<String, Object> languageConfiguration() {
        String opener = "^\\s*(?:def|class|interface|enum|if|elif|else|for|while|try|except|finally|with|match|case)\\b.*:\\s*(?:#.*)?$";
        return m("comments", m("lineComment", "#"),
                "brackets", List.of(List.of("{", "}"), List.of("[", "]"), List.of("(", ")")),
                "autoClosingPairs", List.of(m("open", "{", "close", "}"), m("open", "[", "close", "]"),
                        m("open", "(", "close", ")"),
                        m("open", "\"", "close", "\"", "notIn", List.of("string")),
                        m("open", "'", "close", "'", "notIn", List.of("string", "comment"))),
                "surroundingPairs", List.of(List.of("{", "}"), List.of("[", "]"), List.of("(", ")"),
                        List.of("\"", "\""), List.of("'", "'")),
                "indentationRules", m("increaseIndentPattern", opener,
                        "decreaseIndentPattern", "^\\s*(?:elif|else|except|finally|case)\\b.*$"),
                "onEnterRules", List.of(m("beforeText", opener, "action", m("indent", "indent"))));
    }

    // ------------------------------------------------------------------

    static void write(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, content.endsWith("\n") ? content : content + "\n", StandardCharsets.UTF_8);
    }

    /** Écriture JSON minimale (Map, List, String). */
    static final class Json {
        static String pretty(Object o) {
            StringBuilder sb = new StringBuilder();
            write(sb, o, 0);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        static void write(StringBuilder sb, Object o, int ind) {
            String pad = "  ".repeat(ind + 1);
            if (o instanceof Map<?, ?> map) {
                if (map.isEmpty()) {
                    sb.append("{}");
                    return;
                }
                sb.append("{\n");
                int i = 0;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) map).entrySet()) {
                    sb.append(pad).append(quote(e.getKey())).append(": ");
                    write(sb, e.getValue(), ind + 1);
                    sb.append(++i < map.size() ? ",\n" : "\n");
                }
                sb.append("  ".repeat(ind)).append('}');
            } else if (o instanceof List<?> list) {
                boolean flat = list.stream().allMatch(x -> x instanceof String);
                if (flat) {
                    sb.append('[');
                    for (int i = 0; i < list.size(); i++) {
                        sb.append(i > 0 ? ", " : "").append(quote((String) list.get(i)));
                    }
                    sb.append(']');
                    return;
                }
                sb.append("[\n");
                for (int i = 0; i < list.size(); i++) {
                    sb.append(pad);
                    write(sb, list.get(i), ind + 1);
                    sb.append(i + 1 < list.size() ? ",\n" : "\n");
                }
                sb.append("  ".repeat(ind)).append(']');
            } else {
                sb.append(quote(String.valueOf(o)));
            }
        }

        static String quote(String s) {
            StringBuilder sb = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\t': sb.append("\\t"); break;
                    default: sb.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
                }
            }
            return sb.append('"').toString();
        }
    }
}
