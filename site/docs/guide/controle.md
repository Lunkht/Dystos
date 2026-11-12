---
id: controle
title: Contrôle de flux
sidebar_label: Contrôle de flux
sidebar_position: 2
description: Conditions, boucles et contrôle d'exécution.
---

# Contrôle de flux

## Conditions

`if`, `elif` et `else` forment une chaîne. La condition doit être un `bool` —
un `int` ne suffit pas.

```dystos
val age: int = 20

if age >= 18:
    print("majeur")
elif age >= 14:
    print("mineur")
else:
    print("enfant")
```

Une condition peut aussi être une expression inline :

```dystos
statut = "ok" if age >= 18 else "ko"
print(statut)
```

## Boucles

`for` parcourt une séquence, `while` répète tant qu'une condition est vraie.

```dystos
for i in range(5):
    print(i)   # 0, 1, 2, 3, 4

for i in range(0, 10, 3):
    print(i)   # 0, 3, 6, 9

for c in "Dystos":
    print(c)

var n: int = 0
while n < 3:
    print(n)
    n += 1
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

`break` et `continue` s'appliquent à la boucle la plus proche.

## `match`

`match` compare une valeur à des motifs, comme un `switch` enrichi. Il fonctionne
avec les énumérations et les chaînes.

```dystos
enum Statut:
    ACTIF
    INACTIF
    SUSPENDU

def etiquette(statut: Statut) -> str:
    match statut:
        case Statut.ACTIF | Statut.SUSPENDU:
            return "compte actif ou suspendu"
        case _:
            return "compte inactif"

print(etiquette(Statut.ACTIF))
```

## Portée

Une variable déclarée dans un bloc n'existe que dans ce bloc.

```dystos
if true:
    val message = "visible ici"
    print(message)

print(message)   # E0101 : nom inconnu 'message'
```
