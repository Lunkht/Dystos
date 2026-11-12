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
fruits.add("orange")

print(fruits[0])      # pomme
print(len(fruits))    # 4
print(fruits[1:])     # ["banane", "kiwi", "orange"]
print("kiwi" in fruits)  # true
```

| Opération | Effet |
| --- | --- |
| `fruits.add(x)` | Ajoute `x` à la fin |
| `fruits.pop()` | Retire et rend le dernier élément |
| `fruits[i]` | Accède à l'index `i` |
| `fruits[i:j]` | Tranche de `i` à `j` exclus |
| `len(fruits)` | Nombre d'éléments |
| `x in fruits` | Teste la présence |

## Dictionnaires

```dystos
val stock = {"clavier": 5, "souris": 12}
stock["ecran"] = 3

for nom, quantite in stock.items():
    print(f"{nom} : {quantite}")
```

| Opération | Effet |
| --- | --- |
| `stock["clé"]` | Accède à une valeur |
| `stock["clé"] = v` | Ajoute ou modifie |
| `stock.get("clé")` | Rend la valeur, ou `null` |
| `stock.keys()` | Liste des clés |
| `stock.values()` | Liste des valeurs |
| `stock.items()` | Paires (clé, valeur) |
| `len(stock)` | Nombre d'entrées |
| `"clé" in stock` | Teste la présence |

`stock.get("inconnu")` rend `null` quand la clé est absente.

## Ensembles

```dystos
val uniques = {1, 2, 2, 3}       # {1, 2, 3}
uniques.add(4)
print(len(uniques))              # 4
print(2 in uniques)              # true
```

## Tranches

```dystos
val lettres = ["a", "b", "c", "d", "e"]
print(lettres[1:3])     # ["b", "c"]
print(lettres[:2])      # ["a", "b"]
print(lettres[3:])      # ["d", "e"]
print(lettres[::-1])    # ["e", "d", "c", "b", "a"]
```

## Compréhensions

Une compréhension construit une collection à partir d'un `for` et d'un filtre.

```dystos
val nombres = range(1, 11)

val pairs = [n for n in nombres if n % 2 == 0]
val carres = [n * n for n in nombres]
val initiales = {nom: len(nom) for nom in ["a", "bb", "ccc"]}

print(pairs)    # [2, 4, 6, 8, 10]
print(carres)   # [1, 4, 9, 16, 25, 36, 49, 64, 81, 100]
print(initiales)  # {"a": 1, "bb": 2, "ccc": 3}
```

Il n'y a pas de tuple, ni de `zip`. Pour parcourir plusieurs séquences en même
temps, utilisez `enumerate` ou itérez sur les paires d'un dictionnaire :

```dystos
for index, valeur in enumerate(["x", "y"]):
    print(index, valeur)
```
