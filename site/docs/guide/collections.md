---
id: collections
title: Collections
sidebar_label: Collections
sidebar_position: 3
description: Listes, dictionnaires, ensembles, tranches et compréhensions.
---

# Collections

## Listes

```dystos
val fruits = ["pomme", "banane", "kiwi"]
fruits.append("orange")

print(fruits[0])
print(len(fruits))
print(fruits[1:])
```

| Méthode | Effet |
| --- | --- |
| `append(x)` | Ajoute à la fin |
| `pop()` | Retire et rend le dernier élément |
| `insert(i, x)` | Insère à la position `i` |
| `remove(x)` | Retire la première occurrence |
| `contains(x)` | Teste la présence |
| `len(c)` | Nombre d'éléments |

## Dictionnaires

```dystos
val stock = {"clavier": 5, "souris": 12}
stock["ecran"] = 3

for nom, quantite in stock.items():
    print(f"{nom} : {quantite}")
```

## Ensembles et tuples

```dystos
val uniques = {1, 2, 2, 3}       # {1, 2, 3}
val paire = (10, "Awa")           # tuple heterogene
val premier, second = paire       # destructuring
```

## Tranches

```dystos
val lettres = ["a", "b", "c", "d", "e"]
print(lettres[1:3])     # ["b", "c"]
print(lettres[:2])      # ["a", "b"]
print(lettres[3:])      # ["d", "e"]
print(lettres[::-1])    # à l'envers
```

## Compréhensions

Une compréhension construit une collection à partir d'un `for` et d'un filtre.

```dystos
val nombres = range(1, 11)

val pairs = [n for n in nombres if n % 2 == 0]
val carres = [n * n for n in nombres]
val initiales = {nom: len(nom) for nom in ["a", "bb", "ccc"]}

print(pairs)
print(carres)
print(initiales)
```

## Itérer sur plusieurs séquences

```dystos
for nom, quantite in zip(["a", "b"], [1, 2]):
    print(nom, quantite)

for index, valeur in enumerate(["x", "y"]):
    print(index, valeur)
```