---
id: fonctionnel
title: Fonctionnel
sidebar_label: Fonctionnel
sidebar_position: 6
description: Lambdas, fonctions d'ordre supérieur, compréhensions et tris.
---

# Fonctionnel

## Lambdas

Une lambda n'a pas de nom et s'écrit avec `->`. Si le type des paramètres est
évident, l'annotation est omise.

```dystos
val doubler = (x: int) -> x * 2
print(doubler(21))

val incrementer = (x: int) -> x + 1
print(incrementer(41))
```

```
42
42
```

## Fonctions d'ordre supérieur

Une fonction qui prend ou rend une autre fonction.

```dystos
def appliquer(x: int, f: (int) -> int) -> int:
    return f(x)

print(appliquer(10, doubler))

def composer(f, g):
    return (x: int) -> f(g(x))
```

## Compréhensions et filtres

Une compréhension transforme une collection avec un `for` et un filtre optionnel.

```dystos
val nombres = [1, 2, 3, 4, 5]

val doubles = [n * 2 for n in nombres]
val pairs = [n for n in nombres if n % 2 == 0]
val somme = 0
for n in nombres:
    somme += n
print(somme)
```

```
[2, 4, 6, 8, 10]
[2, 4]
15
```

`filter(f, xs)` garde les éléments pour lesquels `f` renvoie `true` :

```dystos
val pairs = filter(x -> x % 2 == 0, doubles)
print(pairs)   # [2, 4]
```

`sorted(xs, key = f)` trie selon une fonction de clé :

```dystos
val tries = sorted(["bb", "a", "ccc"], key=s -> s.length)
print(tries)   # ["a", "bb", "ccc"]
```

`abs`, `min` et `max` sont disponibles :

```dystos
print(abs(-5), min(3, 7), max(3, 7))
```

```
5 3 7
```

Il n'y a pas de méthode `.map` ni `.reduce` sur les listes : utilisez une
compréhension ou une boucle `for`.
