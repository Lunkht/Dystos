---
id: valeurs-et-types
title: Valeurs et types
sidebar_label: Valeurs et types
sidebar_position: 1
description: Types de base, littéraux, annotations et conversions.
---

# Valeurs et types

Dystos est à types statiques : chaque expression a un type connu à la
compilation. Les conversions sont explicites, jamais implicites.

## Types élémentaires

| Type | Littéraux | Exemple de valeur |
| --- | --- | --- |
| `int` | `0`, `-42`, `1_000`, `0xFF`, `0b1010` | `42` |
| `float` | `3.14`, `1.0e-3` | `3.14` |
| `bool` | `true`, `false` | `true` |
| `str` | `"texte"`, `'a'` | `"clavier"` |
| `list` | `[1, 2, 3]` | `["a", "b"]` |
| `map` | `{"clé": 1}` | `{"a": 1}` |
| `set` | `{1, 2, 3}` | `{1, 2, 3}` |
| `unit` | résultat d'une fonction sans valeur de retour | |

Les guillemets simples et doubles délimitent tous les deux une `str` : il n'y a
pas de type `char` séparé.

```dystos
val quantite: int = 12
val prix: float = 9.99
val disponible: bool = true
val nom: str = "clavier"
val initiale: str = 'c'

print(quantite, prix, disponible, nom, initiale)
```

```
12 9.99 true clavier c
```

## Annotations et inférence

L'annotation peut être omise quand le type est évident à la lecture. Elle reste
recommandée dans les signatures de fonction.

```dystos
val total = 42          # int
val total_annote: int = 42

def surface(largeur: int, hauteur: int) -> int:
    return largeur * hauteur
```

## Nombres

Les littéraux acceptent les séparateurs `_` et les bases `0x` (hexadécimal) et
`0b` (binaire). Le suffixe `f` des flottants n'existe pas : `2f` est refusé par
le lexer.

```dystos
print(1_000, 0xFF, 0b1010)
print(3.14, 1.0e-3)
```

```
1000 255 10
3.14 0.001
```

## Conversions

Aucune conversion implicite : `int` et `float` ne se mélangent pas dans une
annotation. Les fonctions de conversion sont explicites.

```dystos
print(int("12"), int(3.9), str(42), float(2), bool(0), bool(1))
```

```
12 3 42 2.0 false true
```

| Fonction | Effet |
| --- | --- |
| `int(x)` | Convertit vers `int` (tronque un `float`) |
| `float(x)` | Convertit vers `float` |
| `str(x)` | Convertit vers `str` |
| `bool(x)` | Convertit vers `bool` |

## Opérateurs

| Catégorie | Opérateurs |
| --- | --- |
| Arithmétique | `+` `-` `*` `/` `%` `**` |
| Comparaison | `==` `!=` `<` `<=` `>` `>=` |
| Logique | `and` `or` `not` |
| Affectation | `=` `+=` `-=` `*=` `/=` `%=` |
| Chaîne | `+` concatène deux `str` |

`/` est une vraie division : `7 / 2` vaut `3.5`, pas `3`. Pour un quotient
entier, convertis le résultat avec `int`.

```dystos
print(7 / 2)
print(7 % 2)
print(2 ** 10)
```

```
3.5
1
1024
```

## Erreurs de compilation

```dystos
val quantite: int = "douze"
```

```
E0202 : ligne 1, colonne 21 : type incompatible : attendu 'int', trouvé 'str'
```
