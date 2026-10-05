---
id: valeurs-et-types
title: Valeurs et types
sidebar_label: Valeurs et types
sidebar_position: 1
description: Types de base, littéraux, annotations et conversions.
---

# Valeurs et types

Dystos est à types statiques : chaque expression a un type connu à la
compilation. Les conversions explicites sont souhaitables, jamais implicites.

## Types élémentaires

| Type | Littéraux | Défaut |
| --- | --- | --- |
| `int` | `0`, `-42`, `1_000` | `0` |
| `float` | `3.14`, `1.0e-3`, `2f` | `0.0` |
| `bool` | `true`, `false` | `false` |
| `string` | `"texte"`, `"a" + "b"` | `""` |
| `char` | `'a'`, `'\n'` | `'\0'` |
| `void` | résultat de `print` | |

```dystos
val quantite: int = 12
val prix: float = 9.99
val disponible: bool = true
val nom: string = "clavier"
val initiale: char = 'c'

print(quantite, prix, disponible, nom, initiale)
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

## Conversions

Aucune conversion implicite : `int` et `float` ne se mélangent pas.

```dystos
val moyenne = 7 / 2        # 3 : division entière
val moyenne_reelle = 7.0 / 2  # 3.5

val entier: int = int(moyenne_reelle)
val texte: string = string(entier)
```

| Fonction | Effet |
| --- | --- |
| `int(x)` | Convertit vers `int` |
| `float(x)` | Convertit vers `float` |
| `string(x)` | Convertit vers `string` |
| `bool(x)` | Convertit vers `bool` |

## Opérateurs

| Catégorie | Opérateurs |
| --- | --- |
| Arithmétique | `+` `-` `*` `/` `%` |
| Comparaison | `==` `!=` `<` `<=` `>` `>=` |
| Logique | `and` `or` `not` |
| Affectation | `=` `+=` `-=` `*=` `/=` `%=` |
| Chaîne | `+` concatène, `*` répète |

## Erreurs de compilation

```dystos
val quantite: int = "douze"
```

```
E0201 : ligne 1, colonne 22 : string non assignable à int
```