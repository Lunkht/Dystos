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
val nom: str = "Awa"
val alias: str? = null
alias = "Awa"
```

Le compilateur refuse d'affecter `null` à un type non nullable, et d'accéder
directement à une valeur potentiellement nulle.

```dystos
val alias: str? = null
print(alias.length)     # E0201 : accès à '.length' sur le type nullable 'str?'
```

## Opérateur de nullabilité `?.`

`?.` court-circuite : si le récepteur est `null`, toute l'expression vaut
`null` au lieu de lever une exception.

```dystos
val alias: str? = "Awa"
print(alias?.length)    # 3

val absent: str? = null
print(absent?.length)   # null
```

## Valeur par défaut `?:`

`?:` rend une valeur par défaut quand l'expression de gauche est `null`.

```dystos
val alias: str? = null
print(alias?.length ?: 0)   # 0
print(alias ?: "inconnu")   # inconnu
```

## Exceptions

`raise` lève une exception, `try` / `except` / `finally` la gère.

```dystos
class SoldeInsuffisant(Exception):
    def init(self, manque: float):
        super().init(f"Il manque {manque}")

def retirer(solde: float, montant: float) -> float:
    if montant > solde:
        raise SoldeInsuffisant(montant - solde)
    return solde - montant

def main():
    try:
        retirer(10.0, 25.0)
    except SoldeInsuffisant as e:
        print("Erreur :", e.message)
    finally:
        print("Fin")
```

```
Erreur : Il manque 15.0
Fin
```

`except` attrape une exception spécifique ; `Exception` attrape toute exception.
Le bloc `finally` s'exécute toujours.
