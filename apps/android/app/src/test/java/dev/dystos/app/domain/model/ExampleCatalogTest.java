package dev.dystos.app.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.dystos.app.data.code.DystosCodeRunner;
import dev.dystos.app.domain.gateway.CodeRunner;

/**
 * Le catalogue d'exemples doit correspondre au moteur embarqué.
 *
 * Un exemple de référence est une promesse : « ouvrez-le, exécutez-le, voici ce
 * que ça donne ». Si la sortie attendue est fausse, ou si le moteur ne sait pas
 * encore exécuter la construction illustrée, l'application ment à l'utilisateur
 * — et l'exemple devient décourageant plutôt qu'utile.
 *
 * Ce test lit directement la source du contenu, avant génération, pour que
 * l'écart soit visible dans la revue de code et non seulement à l'exécution.
 */
public class ExampleCatalogTest {

    @Test
    public void toutExempleVerifiableProduitSaSortieAttendue() throws IOException {
        Path catalogue = catalogue();
        CodeRunner runner = new DystosCodeRunner();
        List<String> failures = new ArrayList<>();
        int verified = 0;

        for (Path file : examples(catalogue)) {
            Example example = read(file);
            if (!example.isVerifiable()) {
                continue;
            }
            verified++;

            CodeRunner.RunResult run = runner.run(example.getCode());
            if (run.isFailed()) {
                failures.add(example.getId() + " : le moteur ne sait pas exécuter cet "
                        + "exemple (ligne " + run.errorLine() + ", " + run.errorMessage()
                        + "). Retire sa sortie attendue ou ajoute la fonctionnalité.");
                continue;
            }
            if (!OutputComparator.matches(run.output(), example.getExpectedOutput())) {
                failures.add(example.getId() + " : sortie attendue \"" + oneLine(
                        example.getExpectedOutput()) + "\" mais obtenue \""
                        + oneLine(run.output()) + "\".");
            }
        }

        assertEquals("Exemples non conformes :\n" + String.join("\n", failures),
                java.util.Collections.emptyList(), failures);
        assertTrue("Aucun exemple vérifiable : le catalogue ne sert à rien.", verified > 0);
    }

    @Test
    public void leCatalogueCouvreLesVingtExemplesPrevus() throws IOException {
        assertEquals("Le plan prévoit vingt exemples de programmes.",
                20, examples(catalogue()).size());
    }

    @Test
    public void chaqueExempleDitSIlEstExecutable() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path file : examples(catalogue())) {
            Example example = read(file);
            if (example.getId().isEmpty()) {
                problems.add(file.getFileName() + " : identifiant manquant");
            }
            if (example.getCode().trim().isEmpty()) {
                problems.add(example.getId() + " : code vide");
            }
            // L'utilisateur doit toujours comprendre pourquoi un exemple ne
            // s'exécute pas : sortie attendue, ou note expliquant la limite.
            if (!example.isVerifiable() && example.getNote().isEmpty()) {
                problems.add(example.getId() + " : ni sortie attendue ni note");
            }
        }

        assertEquals(String.join("\n", problems), java.util.Collections.emptyList(), problems);
    }

    /**
     * Localise le catalogue en remontant depuis le répertoire courant.
     *
     * Le répertoire de travail de Gradle dépend du module qui lance le test ;
     * on ne veut pas qu'un catalogue introuvable fasse passer le test sans rien
     * vérifier, ce qui laisserait silencieusement les exemples se dégrader.
     */
    private static Path catalogue() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(Path.of("content", "fr", "exemples"));
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "catalogue d'exemples introuvable depuis " + Path.of("").toAbsolutePath());
    }

    private List<Path> examples(Path catalogue) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(catalogue, "*.md")) {
            for (Path file : stream) {
                files.add(file);
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return files;
    }

    /**
     * Lit un exemple du catalogue, avec le même découpage que tools/build-content.
     *
     * Le programme est le corps Markdown. La sortie attendue est écrite après un
     * séparateur `---`, la note dans le frontmatter : les deux formes sont
     * acceptées ici parce que le catalogue a été écrit dans l'une puis l'autre,
     * et qu'un test de catalogue ne doit pas échouer sur cette différence.
     */
    private Example read(Path file) throws IOException {
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        int frontmatterStart = source.indexOf("---") + 3;
        int frontmatterEnd = source.indexOf("---", frontmatterStart);
        String frontmatter = source.substring(frontmatterStart, frontmatterEnd);

        int codeStart = frontmatterEnd + 3;
        int separator = source.indexOf("\n---", codeStart);
        String code = (separator < 0 ? source.substring(codeStart)
                : source.substring(codeStart, separator)).trim();

        String expected = blockScalar(source, "sortie_attendue:");
        String note = blockScalar(frontmatter, "note:");

        return new Example(
                field(frontmatter, "id:"),
                field(frontmatter, "titre:"),
                field(frontmatter, "chapitre:"),
                field(frontmatter, "categorie:"),
                0,
                code,
                expected,
                note);
    }

    /**
     * Lit une valeur YAML en bloc littéral, telle que `sortie_attendue: |`.
     *
     * Les lignes de contenu sont indentées ; la première ligne non indentée
     * après le bloc en marque la fin, ce qui évite de confondre le début d'une
     * autre clé avec la suite de la valeur.
     */
    private String blockScalar(String source, String key) {
        int keyIndex = source.indexOf(key);
        if (keyIndex < 0) {
            return null;
        }

        List<String> lines = new ArrayList<>();
        boolean started = false;
        int position = source.indexOf('\n', keyIndex) + 1;

        while (position > 0 && position < source.length()) {
            int lineEnd = source.indexOf('\n', position);
            if (lineEnd < 0) {
                lineEnd = source.length();
            }
            String line = source.substring(position, lineEnd);

            if (line.trim().isEmpty()) {
                // Une ligne vide termine le bloc s'il a déjà commencé.
                if (started) {
                    break;
                }
                position = lineEnd + 1;
                continue;
            }
            if (!Character.isWhitespace(line.charAt(0))) {
                break;
            }

            started = true;
            lines.add(line);
            position = lineEnd + 1;
        }

        if (lines.isEmpty()) {
            return null;
        }

        // On retire l'indentation commune, sinon la sortie comparée aurait des
        // espaces de début de ligne que le programme n'écrit jamais.
        int indent = Integer.MAX_VALUE;
        for (String line : lines) {
            indent = Math.min(indent, line.length() - line.stripLeading().length());
        }
        StringBuilder value = new StringBuilder();
        for (String line : lines) {
            value.append(line.substring(indent));
            if (value.length() > 0 && value.charAt(value.length() - 1) != '\n') {
                value.append('\n');
            }
        }
        return value.toString().stripTrailing();
    }

    /** Valeur d'une clé de premier niveau du frontmatter, sur la même ligne. */
    private String field(String source, String key) {
        int keyIndex = source.indexOf(key);
        if (keyIndex < 0) {
            return "";
        }
        int start = keyIndex + key.length();
        int end = source.indexOf('\n', start);
        return source.substring(start, end < 0 ? source.length() : end).trim();
    }

    private String oneLine(String text) {
        return text.replace("\n", " · ");
    }
}