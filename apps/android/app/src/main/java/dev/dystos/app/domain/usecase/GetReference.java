package dev.dystos.app.domain.usecase;

import java.util.ArrayList;
import java.util.List;

import dev.dystos.app.domain.model.ReferenceEntry;

/**
 * Référence rapide du langage.
 *
 * Mots-clés, opérateurs, bibliothèque standard et aide-mémoire sont écrits
 * ici plutôt que dans les assets : ce sont des règles du langage, pas du
 * contenu éditorial, et le compilateur doit pouvoir s'y référer sans fichier.
 * Rien n'est chargé depuis le disque, donc la référence est disponible même si
 * le contenu n'a pas encore été lu.
 */
public final class GetReference {

    /** Sections affichées dans cet ordre. */
    private static final String[] SECTION_IDS = {
            "mots-cles", "operateurs", "types", "bibliotheque", "controle",
    };

    public List<ReferenceEntry.Section> sections() {
        List<ReferenceEntry.Section> sections = new ArrayList<>();
        sections.add(keywords());
        sections.add(operators());
        sections.add(types());
        sections.add(standardLibrary());
        sections.add(control());
        return sections;
    }

    public ReferenceEntry.Section section(String id) {
        for (ReferenceEntry.Section section : sections()) {
            if (section.getId().equals(id)) {
                return section;
            }
        }
        return null;
    }

    private ReferenceEntry.Section keywords() {
        List<ReferenceEntry> entries = new ArrayList<>();
        entries.add(entry("val", "val nom: type = valeur",
                "Déclare une valeur qui ne change plus."));
        entries.add(entry("var", "var nom: type = valeur",
                "Déclare une variable modifiable."));
        entries.add(entry("def", "def nom(paramètres) -> type:",
                "Déclare une fonction. Le corps est indenté."));
        entries.add(entry("return", "return valeur",
                "Sort de la fonction et rend une valeur."));
        entries.add(entry("if", "if condition:", "Lance une branche si la condition est vraie."));
        entries.add(entry("elif", "elif condition:",
                "Branche supplémentaire, évaluée si les précédentes sont fausses."));
        entries.add(entry("else", "else:", "Branche exécutée quand toutes les autres sont fausses."));
        entries.add(entry("for", "for élément in itérable:",
                "Répète un bloc pour chaque élément."));
        entries.add(entry("while", "while condition:", "Répète un bloc tant que la condition est vraie."));
        entries.add(entry("in", "for élément in itérable:", "Introduit l'itérable d'une boucle."));
        entries.add(entry("and", "a and b", "Vrai si les deux opérandes sont vrais."));
        entries.add(entry("or", "a or b", "Vrai si au moins un opérande est vrai."));
        entries.add(entry("not", "not a", "Inverse la valeur logique."));
        entries.add(entry("true", "true", "Vérité littérale."));
        entries.add(entry("false", "false", "Falsité littérale."));
        entries.add(entry("break", "break", "Sort de la boucle la plus proche."));
        entries.add(entry("continue", "continue", "Passe à l'itération suivante."));
        return new ReferenceEntry.Section("mots-cles", "Mots-clés",
                "Dix-sept mots-clés, tous en minuscules.", entries);
    }

    private ReferenceEntry.Section operators() {
        List<ReferenceEntry> entries = new ArrayList<>();
        entries.add(entry("+", "a + b", "Addition, ou concaténation de chaînes."));
        entries.add(entry("-", "a - b", "Soustraction."));
        entries.add(entry("*", "a * b", "Multiplication."));
        entries.add(entry("/", "a / b", "Division. Une division non exacte donne un flottant."));
        entries.add(entry("%", "a % b", "Reste de la division."));
        entries.add(entry("==", "a == b", "Égalité."));
        entries.add(entry("!=", "a != b", "Différence."));
        entries.add(entry("<", "a < b", "Strictement inférieur."));
        entries.add(entry("<=", "a <= b", "Inférieur ou égal."));
        entries.add(entry(">", "a > b", "Strictement supérieur."));
        entries.add(entry(">=", "a >= b", "Supérieur ou égal."));
        entries.add(entry("=", "nom = valeur", "Affectation."));
        entries.add(entry("+=", "n += 1", "Affectation composée. Idem pour les autres opérateurs."));
        entries.add(entry("!", "not a", "Négation logique."));
        entries.add(entry("->", "def f() -> type:", "Type de retour d'une fonction."));
        return new ReferenceEntry.Section("operateurs", "Opérateurs",
                "Les opérateurs sont les mêmes qu'en Python, sans surprise.", entries);
    }

    private ReferenceEntry.Section types() {
        List<ReferenceEntry> entries = new ArrayList<>();
        entries.add(entry("int", "42", "Entier de 64 bits. Les séparateurs sont permis : 1_000."));
        entries.add(entry("float", "3.14", "Décimal. Notation scientifique acceptée : 1.0e-3."));
        entries.add(entry("string", "\"texte\"", "Texte. Les guillemets simples désignent aussi un caractère."));
        entries.add(entry("bool", "true", "Vrai ou faux."));
        entries.add(entry("list", "[1, 2, 3]", "Liste de valeurs du même type."));
        return new ReferenceEntry.Section("types", "Types",
                "Le type s'écrit après le nom, avec deux-points.", entries);
    }

    private ReferenceEntry.Section standardLibrary() {
        List<ReferenceEntry> entries = new ArrayList<>();
        entries.add(entry("print", "print(valeur, ...)", "Affiche sur une ligne, séparés par une espace."));
        entries.add(entry("range", "range(debut, fin[, pas])", "Suite d'entiers. La borne finale est exclue."));
        entries.add(entry("len", "len(texte_ou_liste)", "Nombre de caractères ou d'éléments."));
        entries.add(entry("str", "str(valeur)", "Convertit en texte."));
        entries.add(entry("int", "int(texte)", "Convertit en entier. Lève une erreur si ce n'est pas un entier."));
        entries.add(entry("float", "float(texte)", "Convertit en flottant."));
        entries.add(entry("bool", "bool(valeur)", "Convertit en vrai ou faux."));
        entries.add(entry("abs", "abs(nombre)", "Valeur absolue."));
        entries.add(entry("min", "min(a, b, ...)", "Plus petit des arguments."));
        entries.add(entry("max", "max(a, b, ...)", "Plus grand des arguments."));
        entries.add(entry("round", "round(nombre)", "Arrondi à l'entier le plus proche."));
        return new ReferenceEntry.Section("bibliotheque", "Bibliothèque standard",
                "Onze fonctions, toutes disponibles sans import.", entries);
    }

    private ReferenceEntry.Section control() {
        List<ReferenceEntry> entries = new ArrayList<>();
        entries.add(entry("if / elif / else", "if n < 5: … elif n < 20: … else: …",
                "La première condition vraie gagne."));
        entries.add(entry("for", "for i in range(1, 4): …", "Un bloc indenté par niveau."));
        entries.add(entry("while", "while n > 0: …", "Attention aux boucles infinies."));
        entries.add(entry("break", "break", "Interrompt la boucle courante."));
        entries.add(entry("continue", "continue", "Saîne le reste de l'itération."));
        entries.add(entry("def", "def double(n: int) -> int:\n    return n * 2",
                "L'indentation délimite le corps, il n'y a pas d'accolade."));
        return new ReferenceEntry.Section("controle", "Aide-mémoire",
                "Les formes qu'on oublie le plus.", entries);
    }

    private ReferenceEntry entry(String name, String signature, String description) {
        return new ReferenceEntry(name, signature, description);
    }

    /** Les identifiants des sections, pour le menu. */
    public static List<String> sectionIds() {
        return List.of(SECTION_IDS);
    }
}