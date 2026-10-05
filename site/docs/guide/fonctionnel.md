---
id: fonctionnel
title: Fonctionnel
sidebar_label: Fonctionnel
sidebar_position: 6
description: Lambdas, fonctions d'ordre supérieur, map, filter et reduce.
---

# Fonctionnel

## Lambdas

Une lambda n'a pas de nom et s'écrit entre chevrons. Si le type des paramètres
est évident, l'annotation est omise.

```dystos
val doubler = (x: int) => x * 2
print(doubler(21))

val incrementer = (x: int) => x + 1
print(incrementer(41))
```

## Fonctions d'ordre supérieur

Une fonction qui prend ou rend une autre fonction.

```dystos
def appliquer(x: int, f: (int) -> int) -> int:
    return f(x)

print(appliquer(10, doubler))

def composer(f, g):
    return (x) => f(g(x))
```

## `map`, `filter`, `reduce`

```dystos
val nombres = [1, 2, 3, 4, 5]

val doubles = nombres.map((n) => n * 2)
val pairs = nombres.filter((n) => n % 2 == 0)
val somme = nombres.reduce((acc, n) => acc + n, 0)

print(doubles)   # [2, 4, 6, 8, 10]
print(pairs)     # [2, 4]
print(somme)     # 15
```

| Fonction | Signature |
| --- | --- |
| `map` | `(T) -> U` transforme chaque élément |
| `filter` | `(T) -> bool` garde certains éléments |
| `reduce` | `(A, T) -> A` réduit à une valeur |
| `sorted` | trie selon une clé |
| `any`, `all` | quantificateur logique |

## Tri et regroupement

```dystos
val produits = ["pain", "fromage", "poire", "figue"]

produits.sort()
produits.sort((a, b) => len(a) - len(b))
```

## Portée et Capture

Une lambda capture les variables de la portée englobante. attention à ne pas
muter une variable capturée depuis une lambda.

```dystos
val prefixe = "M."
def nom_complet(nom: string) -> string:
    return f"{prefixe} {nom}"

print([nom_complet(n) for n in ["Awa", "Ibrahim"]])
```