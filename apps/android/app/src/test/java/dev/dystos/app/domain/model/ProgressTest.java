package dev.dystos.app.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProgressTest {

    @Test
    public void mapVideNeSignaleRien() {
        Progress progress = new Progress(null, null);

        assertFalse(progress.isLessonRead("hello-world"));
        assertFalse(progress.isExerciseSolved("hello-world"));
        assertEquals(0, progress.readCount(3));
        assertEquals(0f, progress.ratio(3), 0.0001f);
    }

    @Test
    public void compteLesLeconsLues() {
        Map<String, Boolean> read = new LinkedHashMap<>();
        read.put("hello-world", true);
        read.put("variables", false);
        Progress progress = new Progress(read, null);

        assertTrue(progress.isLessonRead("hello-world"));
        assertFalse(progress.isLessonRead("variables"));
        assertEquals(1, progress.readCount(3));
        assertEquals(1f / 3f, progress.ratio(3), 0.0001f);
    }

    @Test
    public void progressionPlafonneAuTotal() {
        Map<String, Boolean> read = new LinkedHashMap<>();
        read.put("a", true);
        read.put("b", true);

        Progress progress = new Progress(read, null);

        assertEquals(1, progress.readCount(1));
        assertEquals(1f, progress.ratio(1), 0.0001f);
    }

    @Test
    public void ratioVautZeroSansLecon() {
        assertEquals(0f, new Progress(null, null).ratio(0), 0.0001f);
    }

    @Test
    public void exercicesEtLeconsSontIndependants() {
        Map<String, Boolean> read = new LinkedHashMap<>();
        read.put("a", true);
        Map<String, Boolean> solved = new LinkedHashMap<>();
        solved.put("a", true);
        solved.put("b", false);

        Progress progress = new Progress(read, solved);

        assertTrue(progress.isLessonRead("a"));
        assertTrue(progress.isExerciseSolved("a"));
        assertFalse(progress.isExerciseSolved("b"));
    }
}