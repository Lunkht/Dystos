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
| `if` `elif` `else` | Condition |
| `for` `while` | Boucle |
| `match` `case` | Motif |
| `return` | Rend une valeur |
| `break` `continue` | Contrôle de boucle |
| `import` `package` | Modules |
| `extends` `implements` | Héritage |
| `new` | Instancie un objet |
| `this` `self` `super` | Référence courante et parent |
| `try` `catch` `finally` `throw` | Exceptions |
| `as` `is` | Conversion et test de type |
| `in` | Appartenance |
| `and` `or` `not` | Logique |
| `typealias` | Alias de type |
| `static` `public` `private` `protected` `internal` | Visibilité |

## Types élémentaires

`int` `float` `double` `string` `bool` `char` `void` `any` `unit`

## Opérateurs

| Catégorie | Opérateurs |
| --- | --- |
| Arithmétique | `+` `-` `*` `/` `%` `**` |
| Comparaison | `==` `!=` `<` `<=` `>` `>=` |
| Logique | `and` `or` `not` |
| Affectation | `=` `+=` `-=` `*=` `/=` `%=` |
| Null-sûreté | `?` `?.` `??` `?:` |
| Divers | `|` `&` `^` `~` `<<` `>>` |

## Littéraux

| Catégorie | Exemple |
| --- | --- |
| Entier | `42` `1_000` `0xFF` `0b1010` `0o17` |
| Flottant | `3.14` `1e-3` `2.0f` |
| Booléen | `true` `false` |
| Chaîne | `"texte"` `'c'` `"""bloc"""` |
| Formatée | `f"{nom} a {age} ans"` |
| Brute | `r"\d+"` |
| Liste | `[1, 2, 3]` |
| Dictionnaire | `{"a": 1}` |
| Ensemble | `{1, 2, 3}` |

## Bibliothèque standard minimale

| Fonction | Effet |
| --- | --- |
| `print(...)` | Écrit sur la sortie standard |
| `len(c)` | Longueur d'une collection ou d'une chaîne |
| `range(...)` | Suite d'entiers |
| `int` `float` `string` `bool` | Conversions |
| `sorted` `reversed` `enumerate` `zip` | Manipulations de séquences |
| `abs` `min` `max` `sum` | Agrégats |
| `open` `read` `write` | Fichiers |