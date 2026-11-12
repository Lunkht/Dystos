package dev.dystos.app.domain.usecase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import dev.dystos.app.domain.model.Chapter;
import dev.dystos.app.domain.model.Example;
import dev.dystos.app.domain.model.Lesson;
import dev.dystos.app.domain.repository.ContentRepository;

/**
 * Le catalogue d'exemples, sans toucher aux assets.
 *
 * Le regroupement est la seule chose qui ne soit pas triviale : un catalogue
 * mal groupé est illisible, et l'ordre du site doit se retrouver dans
 * l'application.
 */
public class GetExamplesTest {

    @Test
    public void groupeParCategorieEtConserveLOrdre() {
        GetExamples useCase = new GetExamples(new FakeContentRepository(List.of(
                new Example("a", "Premier", "base", "Bases", 1, "print(1)", "1"),
                new Example("b", "Second", "base", "Bases", 2, "print(2)", "2"),
                new Example("c", "Troisieme", "fonctions", "Fonctions", 3, "print(3)", "3"))));

        List<GetExamples.Group> groups = useCase.grouped();

        assertEquals(2, groups.size());
        assertEquals("Bases", groups.get(0).getCategory());
        assertEquals(2, groups.get(0).getExamples().size());
        assertEquals("Premier", groups.get(0).getExamples().get(0).getTitle());
        assertEquals("Fonctions", groups.get(1).getCategory());
    }

    @Test
    public void categorieVideReprendLeChapitre() {
        Example example = new Example("a", "Titre", "structures", "", 1, "", "");

        assertEquals("structures", example.getCategory());
    }

    @Test
    public void exempleSansSortieNestPasVerifiable() {
        assertFalse(new Example("a", "T", "c", "C", 1, "print(1)", null).isVerifiable());
        assertFalse(new Example("a", "T", "c", "C", 1, "print(1)", "   ").isVerifiable());
        assertTrue(new Example("a", "T", "c", "C", 1, "print(1)", "1").isVerifiable());
    }

    @Test
    public void exempleSeComporteCommeUnExercice() {
        Example example = new Example("a", "Titre", "c", "C", 1, "print(1)", "1");

        assertEquals("Titre", example.toExercise().getInstruction());
        assertEquals("print(1)", example.toExercise().getInitialCode());
        assertEquals("1", example.toExercise().getExpectedOutput());
    }

    @Test
    public void rechercheUnExempleParIdentifiant() {
        FakeContentRepository repository = new FakeContentRepository(List.of(
                new Example("a", "Premier", "base", "Bases", 1, "", "")));
        GetExamples useCase = new GetExamples(repository);

        assertEquals("Premier", useCase.one("a").getTitle());
        assertNull(useCase.one("inconnu"));
    }

    @Test
    public void catalogueVideNeProduitAucunGroupe() {
        assertTrue(new GetExamples(new FakeContentRepository(List.of()))
                .grouped().isEmpty());
    }

    /** Dépôt de contenu qui renvoie une liste fixe. */
    private static final class FakeContentRepository implements ContentRepository {

        private final List<Example> examples;

        FakeContentRepository(List<Example> examples) {
            this.examples = examples;
        }

        @Override
        public List<Example> getExamples() {
            return examples;
        }

        @Override
        public Example getExample(String exampleId) {
            for (Example example : examples) {
                if (example.getId().equals(exampleId)) {
                    return example;
                }
            }
            return null;
        }

        @Override
        public List<Chapter> getChapters() {
            return new ArrayList<>();
        }

        @Override
        public Lesson getLesson(String lessonId) {
            return null;
        }

        @Override
        public int countLessons() {
            return 0;
        }
    }
}