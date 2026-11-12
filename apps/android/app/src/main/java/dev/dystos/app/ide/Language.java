package dev.dystos.app.ide;

import java.util.List;
import java.util.function.Consumer;

import dev.dystos.app.domain.model.CheckResult;
import dev.dystos.app.language.CodeAnalysis;

/**
 * Un langage pris en charge par l'IDE.
 *
 * Chaque langage apporte sa propre expérience : code de départ, symboles de la
 * barre rapide, analyse syntaxique (coloration + erreur en direct) et
 * exécution du programme.
 */
public interface Language {

    /** Identifiant stable, unique. */
    String id();

    /** Nom affiché dans le sélecteur de langage. */
    String displayName();

    /** Extension de fichier sans point, utilisée à la sauvegarde. */
    String fileExtension();

    /** Code chargé à l'ouverture de l'IDE et au bouton « réinitialiser ». */
    String defaultCode();

    /** Symboles proposés par la barre rapide. */
    List<String> symbols();

    /**
     * Analyse le code en cours de saisie pour la coloration et la première
     * erreur. Ne doit jamais lever d'exception : du code à moitié écrit est
     * forcément invalide.
     */
    CodeAnalysis.Result analyse(String source);

    /** Exécute le programme et rend un résultat affichable par la console. */
    CheckResult run(String source);

    /**
     * Expansion intelligente d'un symbole de la barre rapide selon le texte
     * tapé jusqu'au curseur (espacement après un mot-clé, doublon de
     * guillemets…). Par défaut, insère le symbole tel quel.
     *
     * @param textBeforeCursor texte déjà tapé avant le curseur
     */
    default String expand(String symbol, String textBeforeCursor) {
        return symbol;
    }

    /**
     * Vrai si l'exécution passe par un moteur dont le thread UI ne peut pas
     * attendre (ex. WebView, exécution asynchrone obligatoire).
     */
    default boolean isAsync() {
        return false;
    }

    /**
     * Exécute le programme et appelle {@code onResult} quand c'est terminé.
     *
     * Peut être appelé depuis le thread UI : les langages asynchrones
     * exécutent le blocage hors de ce thread. Par défaut, exécution
     * synchrone via {@link #run(String)}.
     */
    default void runAsync(String source, Consumer<CheckResult> onResult) {
        onResult.accept(run(source));
    }
}