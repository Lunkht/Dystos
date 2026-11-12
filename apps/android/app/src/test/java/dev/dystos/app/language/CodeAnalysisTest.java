package dev.dystos.app.language;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CodeAnalysisTest {

    /** Le texte couvert par un span, pour vérifier la position des couleurs. */
    private static String covered(String source, List<CodeAnalysis.Span> spans,
                                  CodeAnalysis.Highlight highlight) {
        StringBuilder builder = new StringBuilder();
        for (CodeAnalysis.Span span : spans) {
            if (span.highlight() == highlight && span.end() <= source.length()) {
                builder.append(source, span.start(), span.end());
                builder.append('|');
            }
        }
        return builder.toString();
    }

    private static Set<CodeAnalysis.Highlight> highlights(
            List<CodeAnalysis.Span> spans) {
        Set<CodeAnalysis.Highlight> found = new HashSet<>();
        for (CodeAnalysis.Span span : spans) {
            found.add(span.highlight());
        }
        return found;
    }

    @Test
    public void colorationDesMotsCles() {
        String code = "val n: int = 1";

        CodeAnalysis.Result result = CodeAnalysis.analyse(code);

        assertTrue(covered(code, result.spans(),
                CodeAnalysis.Highlight.KEYWORD).startsWith("val|"));
        assertTrue(highlights(result.spans()).contains(CodeAnalysis.Highlight.NUMBER));
    }

    @Test
    public void colorationDesChaines() {
        String code = "print(\"Bonjour\")";

        CodeAnalysis.Result result = CodeAnalysis.analyse(code);

        assertTrue(covered(code, result.spans(),
                CodeAnalysis.Highlight.STRING).contains("\"Bonjour\""));
    }

    @Test
    public void colorationDesChainesFormatees() {
        String code = "val a: int = 2\nprint(f\"{a}\")";

        CodeAnalysis.Result result = CodeAnalysis.analyse(code);

        assertTrue(covered(code, result.spans(),
                CodeAnalysis.Highlight.STRING).contains("f\"{a}\""));
    }

    @Test
    public void colorationDesIntegres() {
        String code = "print(\"x\")\nprint(42)";

        CodeAnalysis.Result result = CodeAnalysis.analyse(code);

        assertTrue(covered(code, result.spans(),
                CodeAnalysis.Highlight.NUMBER).contains("42"));
    }

    @Test
    public void colorationDesIntegresAvecSeparateur() {
        String code = "print(1_000)";

        CodeAnalysis.Result result = CodeAnalysis.analyse(code);

        assertTrue(covered(code, result.spans(),
                CodeAnalysis.Highlight.NUMBER).contains("1_000"));
    }

    @Test
    public void colorationDesOperateurs() {
        String code = "print(1 + 2)";

        CodeAnalysis.Result result = CodeAnalysis.analyse(code);

        assertTrue(covered(code, result.spans(),
                CodeAnalysis.Highlight.OPERATOR).contains("+"));
    }

    @Test
    public void colorationDesIntegresAIntegres() {
        String code = "print(len(\"abc\"))";

        CodeAnalysis.Result result = CodeAnalysis.analyse(code);

        assertTrue(covered(code, result.spans(),
                CodeAnalysis.Highlight.BUILTIN).contains("len"));
    }

    @Test
    public void colorationDesCommentaires() {
        String code = "# une note\nprint(1)";

        CodeAnalysis.Result result = CodeAnalysis.analyse(code);

        assertTrue(covered(code, result.spans(),
                CodeAnalysis.Highlight.COMMENT).contains("# une note"));
    }

    @Test
    public void commentaireDansUneChainePasColore() {
        String code = "print(\"# pas un commentaire\")";

        CodeAnalysis.Result result = CodeAnalysis.analyse(code);

        assertFalse("le # d'une chaîne ne doit pas être un commentaire",
                highlights(result.spans()).contains(CodeAnalysis.Highlight.COMMENT));
    }

    @Test
    public void couleurSurChaqueLigne() {
        String code = "val a: int = 1\nval b: int = 2\nval c: int = 3";

        CodeAnalysis.Result result = CodeAnalysis.analyse(code);

        assertEquals("val|val|val|", covered(code, result.spans(),
                CodeAnalysis.Highlight.KEYWORD));
    }

    @Test
    public void programmeValideSansErreur() {
        CodeAnalysis.Result result = CodeAnalysis.analyse("print(\"ok\")");

        assertFalse(result.hasProblem());
        assertNull(result.problem());
    }

    @Test
    public void programmeVideSansErreur() {
        CodeAnalysis.Result result = CodeAnalysis.analyse("");

        assertFalse(result.hasProblem());
        assertTrue(result.spans().isEmpty());
    }

    @Test
    public void texteIncompletNeProvoquePasDeCrash() {
        // Une saisie en cours n'est jamais un programme valide : l'analyse doit
        // survivre et rester utilisable pour la coloration.
        CodeAnalysis.Result result = CodeAnalysis.analyse("val a: int = ");

        assertNotNull(result.spans());
    }

    @Test
    public void guillemetNonFermeSignaleLigne() {
        CodeAnalysis.Result result = CodeAnalysis.analyse("print(1)\nprint(\"a");

        assertTrue(result.hasProblem());
        assertEquals(2, result.problem().line());
        assertTrue(result.problem().message().contains("chaîne"));
    }

    @Test
    public void caractereInattenduSignaleLigne() {
        CodeAnalysis.Result result = CodeAnalysis.analyse("print(1)\nval a: int = @");

        assertTrue(result.hasProblem());
        assertEquals(2, result.problem().line());
    }

    @Test
    public void erreurDeSyntaxeDistincteDeLexique() {
        CodeAnalysis.Result result = CodeAnalysis.analyse("if true\n    print(1)");

        assertTrue(result.hasProblem());
        assertTrue("un bloc sans deux-points est une erreur de parsing",
                result.isSyntaxError());
    }

    @Test
    public void indentationIncoherenteSignalee() {
        CodeAnalysis.Result result = CodeAnalysis.analyse(
                "if true:\n    print(1)\n        print(2)\nprint(3)");

        assertTrue(result.hasProblem());
    }

    @Test
    public void colorationPresenteMalgreLErreur() {
        // Le lexer s'arrête au caractère fautif, mais ce qui précède est
        // correctement colorié : l'apprenant voit où l'analyse s'est arrêtée.
        CodeAnalysis.Result result = CodeAnalysis.analyse("val a: int = 1\nval b: int = @");

        assertTrue(result.hasProblem());
        assertEquals(2, result.problem().line());
        assertTrue("la coloration doit rester active",
                covered("val a: int = 1\nval b: int = @", result.spans(),
                        CodeAnalysis.Highlight.KEYWORD).startsWith("val|val|"));
    }

    @Test
    public void spansNonChevauchantsEtOrdonnes() {
        String code = "val a: int = 1\n# note\nprint(f\"{a}\")";

        CodeAnalysis.Result result = CodeAnalysis.analyse(code);

        int previous = 0;
        for (CodeAnalysis.Span span : result.spans()) {
            assertTrue("span hors texte", span.start() >= 0);
            assertTrue("span hors texte", span.end() <= code.length());
            assertTrue("spans ordonnés", span.start() >= previous);
            previous = span.start();
        }
    }

    @Test
    public void listeDesMotsCles() {
        assertTrue(CodeAnalysis.keywords().contains("val"));
        assertTrue(CodeAnalysis.keywords().contains("while"));
        assertFalse(CodeAnalysis.keywords().contains("print"));
    }

    @Test
    public void listeDesIntegres() {
        assertTrue(CodeAnalysis.builtins().contains("print"));
        assertTrue(CodeAnalysis.builtins().contains("round"));
        assertFalse(CodeAnalysis.builtins().contains("val"));
    }

    @Test
    public void sourceNulTraiteCommeVide() {
        CodeAnalysis.Result result = CodeAnalysis.analyse(null);

        assertFalse(result.hasProblem());
    }
}