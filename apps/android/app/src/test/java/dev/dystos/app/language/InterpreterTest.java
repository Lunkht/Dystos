package dev.dystos.app.language;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class InterpreterTest {

    @Test
    public void afficheUneLigne() {
        assertEquals("Bonjour, Dystos !", Interpreter.run("print(\"Bonjour, Dystos !\")"));
    }

    @Test
    public void affichePlusieursArgumentsSepares() {
        assertEquals("Dystos 1.0", Interpreter.run("print(\"Dystos\", 1.0)"));
    }

    @Test
    public void plusieursArgumentsSeparesParUneEspace() {
        assertEquals("1 2", Interpreter.run("print(1, 2)"));
    }

    @Test
    public void declareUneVariableImmuable() {
        assertEquals("Dystos 1.0",
                Interpreter.run("version: str = \"1.0\"\nnom: str = \"Dystos\"\n"
                        + "print(nom, version)"));
    }

    @Test
    public void variableModifiableAvecVar() {
        assertEquals("1", Interpreter.run("var compteur: int = 0\ncompteur += 1\nprint(compteur)"));
    }

    @Test
    public void refuseDeModifierUneVal() {
        try {
            Interpreter.run("val x: int = 1\nx = 2");
            fail("une val ne doit pas être modifiable");
        } catch (DystosException e) {
            assertTrue(e.getMessage().contains("val"));
            assertEquals(2, e.getLine());
        }
    }

    @Test
    public void chaineFormeeInterpoleLesExpressions() {
        assertEquals("Bonjour, Awa !",
                Interpreter.run("nom: str = \"Awa\"\nprint(f\"Bonjour, {nom} !\")"));
    }

    @Test
    public void chaineFormeeAccepteUneAppel() {
        assertEquals("carré de 7 = 49",
                Interpreter.run("def carre(x: int) -> int:\n    return x * x\n\n"
                        + "print(f\"carré de 7 = {carre(7)}\")"));
    }

    @Test
    public void fonctionAvecRetour() {
        assertEquals("49", Interpreter.run("def carre(x: int) -> int:\n    return x * x\n\nprint(carre(7))"));
    }

    @Test
    public void fonctionSansRetourAfficheCeQuElleFait() {
        assertEquals("--- Stock ---",
                Interpreter.run("def afficher_titre(titre: str):\n"
                        + "    print(f\"--- {titre} ---\")\n\nafficher_titre(\"Stock\")"));
    }

    @Test
    public void parametreParDefaut() {
        assertEquals("120.0\n105.5",
                Interpreter.run("def prix_ht(ht: float, tva: float = 0.20) -> float:\n"
                        + "    return ht * (1.0 + tva)\n\n"
                        + "print(prix_ht(100.0))\nprint(prix_ht(100.0, tva: 0.055))"));
    }

    @Test
    public void argumentNommeEnSecondPosition() {
        assertEquals("105.5",
                Interpreter.run("def prix_ht(ht: float, tva: float = 0.20) -> float:\n"
                        + "    return ht * (1.0 + tva)\n\nprint(prix_ht(100.0, tva: 0.055))"));
    }

    @Test
    public void fonctionCommeValeur() {
        assertEquals("42", Interpreter.run("def doubler(x: int) -> int:\n    return x * 2\n\n"
                + "val operation = doubler\nprint(operation(21))"));
    }

    @Test
    public void fonctionRecursive() {
        assertEquals("0\n1\n1\n2\n3",
                Interpreter.run("def fibonacci(n: int) -> int:\n"
                        + "    if n < 2:\n        return n\n"
                        + "    return fibonacci(n - 1) + fibonacci(n - 2)\n\n"
                        + "for i in range(5):\n    print(fibonacci(i))"));
    }

    @Test
    public void conditionEtElif() {
        // n vaut 10 : la première branche est fausse, la seconde est vraie.
        assertEquals("grand",
                Interpreter.run("val n: int = 10\n"
                        + "if n < 5:\n    print(\"petit\")\n"
                        + "elif n < 20:\n    print(\"grand\")\n"
                        + "else:\n    print(\"énorme\")"));
    }

    @Test
    public void conditionEtElifEtElse() {
        assertEquals("énorme",
                Interpreter.run("val n: int = 30\n"
                        + "if n < 5:\n    print(\"petit\")\n"
                        + "elif n < 20:\n    print(\"grand\")\n"
                        + "else:\n    print(\"énorme\")"));
    }

    @Test
    public void premiereBrancheEstidue() {
        assertEquals("petit",
                Interpreter.run("val n: int = 2\nif n < 5:\n    print(\"petit\")"));
    }

    @Test
    public void boucleWhileAvecBreak() {
        assertEquals("1\n2\n3", Interpreter.run("var i: int = 0\n"
                + "while true:\n    i += 1\n    if i > 3:\n        break\n    print(i)"));
    }

    @Test
    public void continueSauteLEvaluation() {
        // 0 est pair : continue ne le saute pas.
        assertEquals("0\n2\n4", Interpreter.run("for i in range(5):\n"
                + "    if i % 2 != 0:\n        continue\n    print(i)"));
    }

    @Test
    public void continueSauteLesImpairs() {
        assertEquals("2\n4", Interpreter.run("for i in range(1, 5):\n"
                + "    if i % 2 != 0:\n        continue\n    print(i)"));
    }

    @Test
    public void divisionEntiereExacte() {
        assertEquals("3", Interpreter.run("print(6 / 2)"));
    }

    @Test
    public void divisionNonExacteDonneUnFlottant() {
        assertEquals("3.5", Interpreter.run("print(7 / 2)"));
    }

    @Test
    public void resteDeDivision() {
        assertEquals("1", Interpreter.run("print(7 % 2)"));
    }

    @Test
    public void notationScientifiqueEstAcceptee() {
        assertEquals("0.001", Interpreter.run("val petit: float = 1.0e-3\nprint(petit)"));
        assertEquals("1500.0", Interpreter.run("print(1.5e3)"));
        assertEquals("20000.0", Interpreter.run("print(2e+4)"));
        assertEquals("0.002", Interpreter.run("print(2E-3)"));
    }

    @Test
    public void identifiantCommencantParEEstResteUnIdentifiant() {
        assertEquals("5", Interpreter.run("val entier: int = 5\nprint(entier)"));
    }

    @Test
    public void separateurDeMilliers() {
        assertEquals("1000000\n1500.5", Interpreter.run("print(1_000_000)\nprint(1_500.5)"));
    }

    @Test
    public void operateursLogiques() {
        assertEquals("false\ntrue", Interpreter.run("print(1 > 2 and false)\nprint(not false)"));
    }

    @Test
    public void boucleSurUneListe() {
        assertEquals("1\n2\n3", Interpreter.run("for n in [1, 2, 3]:\n    print(n)"));
    }

    @Test
    public void longueurDUneChaine() {
        assertEquals("6", Interpreter.run("print(len(\"Dystos\"))"));
    }

    @Test
    public void conversionEnEntier() {
        assertEquals("43", Interpreter.run("print(int(\"42\") + 1)"));
    }

    @Test
    public void fonctionInconnueEstSignalee() {
        try {
            Interpreter.run("inexistant(1)");
            fail("une fonction inconnue doit être signalée");
        } catch (DystosException e) {
            assertTrue(e.getMessage().contains("inexistant"));
        }
    }

    @Test
    public void variableInconnueEstSignalee() {
        try {
            Interpreter.run("print(inconnue)");
            fail("une variable inconnue doit être signalée");
        } catch (DystosException e) {
            assertTrue(e.getMessage().contains("inconnue"));
        }
    }

    @Test
    public void divisionParZeroEstSignalee() {
        try {
            Interpreter.run("print(1 / 0)");
            fail("la division par zéro doit être signalée");
        } catch (DystosException e) {
            assertTrue(e.getMessage().contains("division"));
        }
    }

    @Test
    public void boucleInfinieEstArretee() {
        try {
            Interpreter.run("while true:\n    print(1)", 5_000L, 10);
            fail("une boucle infinie doit être arrêtée");
        } catch (DystosException e) {
            assertTrue(e.getMessage().contains("boucle infinie"));
        }
    }

    @Test
    public void indentationIncoherenceEstSignalee() {
        try {
            Interpreter.run("if true:\n        print(1)\n   print(2)");
            fail("une indentation incohérente doit être signalée");
        } catch (DystosException e) {
            assertTrue(e.getMessage().contains("indentation")
                    || e.getMessage().contains("attendu")
                    || e.getMessage().contains("inattendue"));
        }
    }

    @Test
    public void typesDeclaresIncompatiblesEstSignale() {
        try {
            Interpreter.run("val n: int = \"texte\"");
            fail("un type incompatible doit être signalé");
        } catch (DystosException e) {
            assertTrue(e.getMessage().contains("int"));
            assertTrue(e.getMessage().contains("str"));
        }
    }

    @Test
    public void argumentsManquantsEstSignales() {
        try {
            Interpreter.run("def f(a: int, b: int) -> int:\n    return a + b\n\nprint(f(1))");
            fail("un argument manquant doit être signalé");
        } catch (DystosException e) {
            assertTrue(e.getMessage().contains("argument manquant"));
        }
    }

    @Test
    public void sortieEstTronqueeAuDelai() {
        String output = Interpreter.run(
                "for i in range(50):\n    print(i)", 100_000L, 5);
        assertTrue(output.contains("tronquée"));
    }
}