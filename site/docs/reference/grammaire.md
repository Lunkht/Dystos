---
id: grammaire
title: Grammaire
sidebar_label: Grammaire
sidebar_position: 1
description: Forme syntaxique de Dystos, du jeton à l'expression.
---

# Grammaire

Cette page décrit la forme syntaxique de Dystos. Elle suit l'implémentation du
lexer et du parser : en cas d'écart, le code fait foi.

## Mots-clés

| Mot-clé | Rôle |
| --- | --- |
| `def` | Déclare une fonction |
| `val` | Lie une valeur immuable |
| `var` | Lie une variable modifiable |
| `class` | Déclare une classe |
| `interface` | Déclare un contrat |
| `enum` | Déclare une énumération |
| `abstract` | Classe abstrait (ne peut pas être instanciée directement) |
| `final` | Classe/méthode non extensible |
| `import` `package` | Modules et importations |
| `if` `elif` `else` | Condition |
| `for` `while` | Boucle |
| `match` `case` | Motif |
| `return` | Rend une valeur |
| `break` `continue` | Contrôle de boucle |
| `pass` | Corps vide |
| `try` `except` `finally` `raise` | Exceptions |
| `assert` | Assertion |
| `with` | Bloc avec gestion de ressource |
| `as` | Alias d'import / cast |
| `is` | Test de type |
| `in` | Appartenance |
| `and` `or` `not` | Logique |

Il n'y a pas de `new`, `throw`, `catch`, `this` ni `__init__`. Le constructeur
s'appelle `init`, et on instancie une classe directement : `Compte("Awa")`.

## Types

| Type | Rôle |
| --- | --- |
| `int` | Entier |
| `float` | Nombre à virgule |
| `bool` | `true` ou `false` |
| `str` | Chaîne de caractères |
| `list[T]` | Liste ordonnée |
| `map[K, V]` | Dictionnaire |
| `set[T]` | Ensemble |
| `unit` | Absence de valeur (retour d'une procédure) |
| `any` | Tout type |

Les types sont paramétrables avec des crochets : `list[str]`, `map[str, int]`.
Un type nullable s'écrit `str?`.

## Littéraux

```
entier      := 0 | -42 | 1_000 | 0xFF | 0b1010
flottant    := 3.14 | 1.0e-3
chaîne      := "texte" | 'texte' | f"expr {x}"
booléen     := true | false
liste       := [expr, ...]
dictionnaire:= {clé: valeur, ...}
ensemble    := {expr, ...}
```

Les guillemets simples et doubles produisent tous deux une `str`. Il n'y a pas
de type `char` séparé.

## Expressions

Precedence croissante :

| Niveau | Opérateurs |
| --- | --- |
| 1 | `or` |
| 2 | `and` |
| 3 | `not` |
| 4 | `==` `!=` `<` `<=` `>` `>=` `in` `not in` `is` `is not` |
| 5 | `?.` `?:` |
| 6 | `+` `-` |
| 7 | `*` `/` `%` |
| 8 | `**` (puissance, associative à droite) |
| 9 | `->` (lambda) |

`/` est une vraie division : `7 / 2` vaut `3.5`.

## Instructions

```
programme    := declaration*
declaration  := instruction | fonctionClasse
instruction  := expression | affectation | retour | boucle | si | match | lever | assertion
affectation  := identifiant ("=" | "+=" | ...) expression
retour       := "return" expression?
lever        := "raise" expression
assertion    := "assert" expression ["," expression]
```

## Déclarations

```
fonction     := "def" nom "(" parametres ")" ["->" type] ":" bloc
constructeur := "def" "init" "(" "self" "," parametres ")" ":" bloc
classe       := "class" nom ["(" parents ")"] ":" bloc
interface    := "interface" nom ":" bloc
enum         := "enum" nom ":" constante ("," constante)*
```

Un fichier exécute ses instructions de haut en bas. S'il définit une fonction
`main`, elle est appelée automatiquement ; il ne faut pas l'appeler soi-même
(sinon `E0307`, point d'entrée ambigu).
