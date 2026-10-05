---
id: null-safety
title: Null-sûreté et exceptions
sidebar_label: Null-sûreté
sidebar_position: 5
description: Opérateur ?, appel sûr ?., exceptions et types nullables.
---

# Null-sûreté et exceptions

## Types nullables

Un type simple n'accepte jamais `null`. Il faut l'écrire explicitement avec `?`.

```dystos
val nom: string = "Awa"
val alias: string? = null
alias = "A"
```

Le compilateur refuse l'affectation d'une valeur non nullable à un type
nullable inverse, et l'accès direct à une valeur potentiellement nulle.

```dystos
alias = null
print(alias.len())     # E0302 : alias est peut-être null
```

## Opérateur de nullabilité `?`

`?` « déballe » une valeur nullable juste après avoir testé qu'elle existe.

```dystos
val alias: string? = "Awa"

if alias?:
    print(alias?.len() ?? 0)
```

`?.` court-circuite : si le récepteur est `null`, toute l'expression vaut
`null` au lieu de lever une exception.

```dystos
val alias: string? = null
print(alias?.len() ?? 0)      # 0
```

| Opérateur | Effet |
| --- | --- |
| `x?` | `true` si `x` n'est pas `null` |
| `x?.champ` | Accès sûr, rend `null` si absent |
| `x ?? defaut` | Rend `defaut` si `x` est `null` |

## Exceptions

```dystos
def diviser(a: int, b: int) -> float:
    if b == 0:
        throw ArithmeticError("division par zéro")
    return float(a) / float(b)
```

Attraper avec `try` / `catch` / `finally` :

```dystos
try:
    print(diviser(1, 0))
except ArithmeticError as e:
    print("erreur :", e.message)
finally:
    print("terminé")
```

## Lever une exception personnalisée

```dystos
class SoldeInsuffisant(Exception):
    def __init__(self, manque: float):
        self.manque = manque

def retirer(compte: Compte, montant: float):
    if compte.solde() < montant:
        throw SoldeInsuffisant(montant - compte.solde())
    compte.deposer(-montant)
```

## Assertions

`assert` vérifie une condition et interrompt le programme si elle est fausse.
Utile en développement, à éviter pour valider une entrée utilisateur.

```dystos
def racine_carree(x: float) -> float:
    assert x >= 0.0
    return x ** 0.5
```