---
id: controle
title: Contrôle de flux
sidebar_label: Contrôle de flux
sidebar_position: 2
description: Conditions, boucles et contrôle d'exécution.
---

# Contrôle de flux

## Conditions

`if`, `elif` et `else` forment une chaîne. Les conditions sont des `bool`, pas
des entiers.

```dystos
val age: int = 20

if age >= 18:
    print("majeur")
elif age >= 14:
    print("mineur")
else:
    print("enfant")
```

## Boucles

`for` parcourt une séquence, `while` répète tant qu'une condition est vraie.

```dystos
for i in range(5):
    print(i)

var n: int = 0
while n < 3:
    print(n)
    n += 1
```

`range(debut, fin, pas)` est flexible :

```dystos
for i in range(0, 10, 3):
    print(i)   # 0, 3, 6, 9

for c in "Dystos":
    print(c)
```

## `break` et `continue`

```dystos
for n in range(1, 100):
    if n % 7 == 0 and n % 5 == 0:
        print(n)
        break

for n in range(1, 6):
    if n % 2 == 0:
        continue
    print(n)   # 1, 3, 5
```

## `match`

`match` compare une valeur à des motifs, comme un `switch` enrichi.

```dystos
enum Statut:
    ACTIF
    INACTIF
    SUSPENDU

def etiquette(statut: Statut) -> string:
    match statut:
        case Statut.ACTIF:
            return "compte actif"
        case Statut.SUSPENDU:
            return "compte suspendu"
        case _:
            return "compte inactif"
```

## Portée

Une variable déclarée dans un bloc n'existe que dans ce bloc.

```dystos
if true:
    val message = "visible ici"
    print(message)

print(message)   # E0104 : message inconnu
```