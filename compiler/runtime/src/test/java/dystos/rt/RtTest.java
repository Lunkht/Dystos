package dystos.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RtTest {

    @Test
    void floatsPrintLikePython() {
        assertEquals("13.0", Rt.strDouble(13.0));
        assertEquals("0.0001", Rt.strDouble(0.0001));
        assertEquals("1e-05", Rt.strDouble(0.00001));
        assertEquals("1e+20", Rt.strDouble(1e20));
        assertEquals("0.30000000000000004", Rt.strDouble(0.1 + 0.2));
        assertEquals("-0.0", Rt.strDouble(-0.0));
        assertEquals("nan", Rt.strDouble(Double.NaN));
    }

    @Test
    void valuesPrintWithDystosSyntax() {
        List<Object> l = new ArrayList<>(List.of(1L, "a", 2.5, true));
        assertEquals("[1, \"a\", 2.5, true]", Rt.str(l));
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put("k", 1L);
        assertEquals("{\"k\": 1}", Rt.str(m));
        assertEquals("set()", Rt.str(new LinkedHashSet<>()));
        assertEquals("null", Rt.str(null));
        assertEquals("Entry(\"a\", 1)", Rt.str(new Entry("a", 1L)));
        assertEquals("<DObject>", Rt.str(new DObject()));
    }

    @Test
    void integerArithmeticDetectsOverflow() {
        assertEquals(7, Rt.add(3, 4));
        assertThrows(DRuntimeError.class, () -> Rt.add(Long.MAX_VALUE, 1));
        assertThrows(DRuntimeError.class, () -> Rt.sub(Long.MIN_VALUE, 1));
        assertThrows(DRuntimeError.class, () -> Rt.mul(Long.MAX_VALUE, 2));
        assertThrows(DRuntimeError.class, () -> Rt.neg(Long.MIN_VALUE));
        assertThrows(DRuntimeError.class, () -> Rt.divInt(1, 0));
        assertThrows(DRuntimeError.class, () -> Rt.powInt(2, -1));
        assertEquals(2, Rt.modInt(-7, 3));
        assertEquals(1024, Rt.powInt(2, 10));
        assertEquals(0.5, Rt.powFloat(2.0, -1));
    }

    @Test
    void indexingAndSlicesFollowPython() {
        List<Object> l = new ArrayList<>(List.of(1L, 2L, 3L, 4L));
        assertEquals(4L, Rt.list_getItem(l, -1));
        assertEquals("[2, 3]", Rt.str(Rt.list_slice(l, 1L, 3L, null)));
        assertEquals("[4, 3, 2, 1]", Rt.str(Rt.list_slice(l, null, null, -1L)));
        assertEquals("bc", Rt.str_slice("abcd", 1L, 3L, null));
        assertEquals("dcba", Rt.str_slice("abcd", null, null, -1L));
        DRuntimeError e = assertThrows(DRuntimeError.class, () -> Rt.list_getItem(l, 9));
        assertTrue(e.message.contains("hors limites"));
        assertThrows(DRuntimeError.class, () -> Rt.str_slice("a", null, null, 0L));
    }

    @Test
    void formatsMatchTheInterpreter() {
        assertEquals("3.14", Rt.format(3.14159, ".2f"));
        assertEquals("00042", Rt.format(42L, "05d"));
        assertEquals("**a**", Rt.format("a", "*^5"));
        assertEquals("1,234,567", Rt.format(1234567L, ","));
        assertEquals("25.6%", Rt.format(0.256, ".1%"));
        assertEquals("+5", Rt.format(5L, "+d"));
        assertEquals("ff", Rt.format(255L, "x"));
        assertThrows(DRuntimeError.class, () -> Rt.format("x", "d"));
    }

    @Test
    void equalityAndComparisonMixNumbers() {
        assertTrue(Rt.eq(3L, 3.0));
        assertTrue(Rt.eq(new ArrayList<>(List.of(1L)), new ArrayList<>(List.of(1.0))));
        assertTrue(!Rt.eq(new DObject(), new DObject()));
        assertTrue(Rt.cmp(1L, 2.5) < 0);
        assertTrue(Rt.contains("abc", "b"));
        assertThrows(DRuntimeError.class, () -> Rt.cmp("a", 1L));
    }

    @Test
    void conversions() {
        assertEquals(12, Rt.toInt("12"));
        assertEquals(3, Rt.toInt(3.9));
        assertEquals(2.5, Rt.toFloat("2.5"));
        assertTrue(Rt.toBool("x") && !Rt.toBool("") && !Rt.toBool(0L));
        DValueError e = assertThrows(DValueError.class, () -> Rt.toInt("abc"));
        assertEquals("conversion impossible en int : \"abc\"", e.message);
    }

    @Test
    void functionsAsValues() {
        Fn len = a -> Rt.str_length((String) a[0]);
        Fn even = a -> ((Long) a[0]) % 2 == 0;
        List<Object> words = new ArrayList<>(List.of("bb", "a", "ccc"));
        assertEquals("[\"a\", \"bb\", \"ccc\"]", Rt.str(Rt.sorted2(words, len)));
        assertEquals("[\"bb\", \"a\", \"ccc\"]", Rt.str(words), "la liste d'origine est inchangée");
        assertEquals("[2, 4]", Rt.str(Rt.filter(even, new ArrayList<>(List.of(1L, 2L, 3L, 4L)))));
        Ref r = new Ref(1L);
        r.v = (Long) r.v + 1;
        assertEquals(2L, r.v);
    }

    @Test
    void fileFollowsTheInterpreterRules(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        java.nio.file.Path p = dir.resolve("n.txt");
        DFile w = new DFile(p, "w");
        w.write("a\nb\n");
        assertThrows(DRuntimeError.class, w::read, "un fichier ouvert en écriture ne se lit pas");
        w.close();
        w.close();
        assertThrows(DRuntimeError.class, () -> w.write("x"), "fermé");
        DFile r = new DFile(p, "r");
        assertEquals("a\nb\n", r.read());
        assertEquals("[\"a\", \"b\"]", Rt.str(r.lines()));
        assertThrows(DRuntimeError.class, () -> r.write("x"));
        DFile a = new DFile(p, "a");
        a.write("c\n");
        a.close();
        assertEquals("a\nb\nc\n", new DFile(p, "r").read());
        assertEquals("fichier introuvable : absent.txt", assertThrows(DRuntimeError.class, () -> Rt.io_open("absent.txt")).message);
        assertEquals("chemin hors du répertoire de travail : ../x", assertThrows(DRuntimeError.class, () -> Rt.io_open("../x", "w")).message);
        assertEquals("mode d'ouverture inconnu : 'z' (r, w ou a)", assertThrows(DRuntimeError.class, () -> Rt.io_open("a", "z")).message);
    }

    @Test
    void tasksReallyRunInParallelAndRethrowTheirErrors() throws Exception {
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(3);
        Fn meet = a -> {
            try {
                barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException("les tâches ne s'exécutent pas en parallèle", e);
            }
            return 7L;
        };
        DTask t1 = Rt.concurrent_run(meet);
        DTask t2 = Rt.concurrent_run(meet);
        DTask t3 = Rt.concurrent_run(meet);
        assertEquals(7L, Rt.Task_await(t1));
        assertEquals(7L, Rt.Task_await(t2));
        assertEquals(7L, Rt.Task_await(t3));
        DTask bad = Rt.concurrent_run(a -> {
            throw new DValueError("dans la tâche");
        });
        assertEquals("dans la tâche", assertThrows(DValueError.class, () -> Rt.Task_await(bad)).message);
        assertEquals("dans la tâche", assertThrows(DValueError.class, () -> Rt.Task_await(bad)).message, "attendre deux fois");
    }

    @Test
    void exceptionsCarryTheirMessageAndName() {
        assertEquals("RuntimeError", new DRuntimeError("x").typeName());
        assertEquals("ValueError", new DValueError("x").typeName());
        assertEquals("Exception", new DException("x").typeName());
        assertEquals("x", new DException("x").toString());
        assertEquals("", new DException().message);
    }
}
