---
id: mots-cles
title: Mots-clés et opérateurs
sidebar_label: Mots-clés
sidebar_position: 2
description: Liste complète des mots-clés, opérateurs et littéraux de Dystos.
---

# Mots-clés et opérateurs

## Mots-clés

| Mot-clé | Rôle |
| --- | --- |
| `def` | Déclare une fonction |
| `val` | Lie une valeur immuable |
| `var` | Lie une variable modifiable |
| `class` | Déclare une classe |
| `interface` | Déclare un contrat |
| `enum` | Déclare une énumération |
| `abstract` | Classe qui ne peut pas être instanciée directement |
| `final` | Empêche l'héritage ou la surcharge |
| `import` `package` | Modules |
| `if` `elif` `else` | Condition |
| `for` `while` | Boucle |
| `match` `case` | Motif |
| `return` | Rend une valeur |
| `break` `continue` | Contrôle de boucle |
| `pass` | Corps vide (espace réservé) |
| `try` `except` `finally` `raise` | Exceptions |
| `assert` | Assertion |
| `as` | Alias d'import / cast de type |
| `is` | Test de type |
| `in` | Appartenance |
| `and` `or` `not` | Logique |
| `with` | Bloc avec gestion de ressource |

Il n'y a pas de `new`, `throw`, `catch`, `this` ni `__init__`.

## Types

| Type | Rôle |
| --- | --- |
| `int` | Entier |
| `float` | Nombre à virgule |
| `bool` | `true` ou `false` |
| `str` | Chaîne de caractères |
| `list[T]` | Liste |
| `map[K, V]` | Dictionnaire |
| `set[T]` | Ensemble |
| `unit` | Aucune valeur de retour |
| `any` | Tout type |

Il n'y a pas de `string`, `char`, `double` ni `void` : utilisez `str` et
`unit`. Un type nullable s'écrit `T?`.

## Opérateurs

| Catégorie | Opérateurs |
| --- | --- |
| Arithmétique | `+` `-` `*` `/` `%` `**` |
| Comparaison | `==` `!=` `<` `<=` `>` `>=` |
| Appartenance / type | `in` `not in` `is` `is not` |
| Null-sûreté | `?.` `?:` |
| Affectation | `=` `+=` `-=` `*=` `/=` `%=` |
| Chaîne | `+` concatène |

`/` est une vraie division : `7 / 2` vaut `3.5`.

## Littéraux

- Entiers : `42`, `-7`, `1_000`, `0xFF`, `0b1010`
- Flottants : `3.14`, `1.0e-3`
- Chaînes : `"texte"`, `'texte'`, `f"valeur {x}"`
- Booléens : `true`, `false`
- Listes : `[1, 2, 3]`
- Dictionnaires : `{"clé": 1}`
- Ensembles : `{1, 2, 3}`
