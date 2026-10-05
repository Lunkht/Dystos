---
id: objets
title: Objets
sidebar_label: Objets
sidebar_position: 4
description: Classes, constructeurs, héritage, interfaces et génériques.
---

# Objets

## Déclarer une classe

`class` introduit un type. Les champs non privés sont publics, un `_` en
tête de nom signale l'usage interne.

```dystos
class Compte:
    _solde: float = 0.0

    def __init__(self, nom: string):
        self.nom = nom

    def deposer(self, montant: float):
        if montant <= 0.0:
            throw ValueError("montant invalide")
        self._solde += montant
        return self._solde

    def __str__(self) -> string:
        return f"{self.nom} ({self._solde:.2f})"
```

`__init__` est le constructeur, appelé avec `new` ou directement :

```dystos
val compte = Compte("Awa")
print(compte.deposer(150.0))
```

## Héritage

```dystos
class CompteEpargne(Compte):
    def __init__(self, nom: string, taux: float):
        super.__init__(nom)
        self.taux = taux

    def interets(self) -> float:
        return self._solde * self.taux
```

## Interfaces

```dystos
interface Exportable:
    def exporter() -> string

class Facture(Exportable):
    def exporter(self) -> string:
        return "facture"

def sauvegarder(item: Exportable):
    print(item.exporter())
```

## Génériques

```dystos
class Pile<T>:
    _elements: list<T> = []

    def ajouter(self, element: T):
        self._elements.append(element)

    def dépiler(self) -> T:
        return self._elements.pop()

var pile = Pile<string>()
pile.ajouter("a")
pile.ajouter("b")
print(pile.dépiler())
```

## `enum` et `match`

```dystos
enum Role:
    ADMIN
    CLIENT

val roles = [Role.ADMIN, Role.CLIENT]

for role in roles:
    match role:
        case Role.ADMIN:
            print("accès total")
        case Role.CLIENT:
            print("accès limité")
        case _:
            print("rôle inconnu")
```