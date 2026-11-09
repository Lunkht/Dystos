package com.vulsoft.dystos.playground;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesObjectsArraysAndScalars() {
        Object o = Json.parse(" {\"a\": 1, \"b\": [true, false, null, 2.5, \"x\"], \"c\": {\"d\": -3}} ");
        Map<?, ?> m = (Map<?, ?>) o;
        assertEquals(1L, m.get("a"));
        assertEquals(List.of(true, false, 2.5, "x").size() + 1, ((List<?>) m.get("b")).size());
        assertEquals(-3L, ((Map<?, ?>) m.get("c")).get("d"));
    }

    @Test
    void parsesEscapesAndUnicode() {
        assertEquals("a\"b\\c/\n\t\u00e9\uD83D\uDE00", Json.parse("\"a\\\"b\\\\c\\/\\n\\t\\u00e9\\uD83D\\uDE00\""));
        assertEquals("é😀", Json.parse("\"é😀\""));
    }

    @Test
    void rejectsInvalidDocuments() {
        for (String bad : List.of("", "{", "[1,", "{\"a\"}", "{\"a\":}", "[1 2]", "tru", "\"abc", "{\"a\":1} x",
                "'a'", "{a:1}", "\"\\q\"", "\"\\u12\"", "-", "[\"a\nb\"]")) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(bad), bad);
        }
    }

    @Test
    void rejectsExcessiveNesting() {
        String deep = "[".repeat(200) + "]".repeat(200);
        assertThrows(IllegalArgumentException.class, () -> Json.parse(deep));
    }

    @Test
    void quoteRoundTrips() {
        String s = "ligne 1\nligne \"2\"\t\\ é \u0001";
        assertEquals(s, Json.parse(Json.quote(s)));
        assertTrue(Json.quote("a\u0001").contains("\\u0001"));
    }
}
