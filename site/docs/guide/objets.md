---
id: objets
title: Objets
sidebar_label: Objets
sidebar_position: 4
description: Classes, constructeurs, héritage, interfaces et génériques.
---

# Objets

## Déclarer une classe

`class` introduit un type. Les champs sont publics par défaut ; un `_` en tête
de nom signale un usage interne (accès restreint depuis l'extérieur de la
classe).

```dystos
class Compte:
    titulaire: str
    _solde: float = 0.0

    def init(self, titulaire: str):
        self.titulaire = titulaire

    def deposer(self, montant: float):
        self._solde += montant

    def solde(self) -> float:
        return self._solde

    def toString(self) -> str:
        return f"Compte({self.titulaire}, {self._solde})"

def main():
    c = Compte("Awa")
    c.deposer(150.0)
    print(c)
```

```
Compte(Awa, 150.0)
```

`init` est le constructeur. On invoque la classe directement, sans `new` :
`Compte("Awa")`. `self` fait référence à l'instance courante. `toString`
définit l'affichage de l'objet.

## Héritage

Une classe hérite d'une autre ou implémente une interface.

```dystos
interface Forme:
    def aire(self) -> float

abstract class Base(Forme):
    nom: str
    def init(self, nom: str):
        self.nom = nom

class Carre(Base):
    cote: float
    def init(self, cote: float):
        super().init("carré")
        self.cote = cote
    def aire(self) -> float:
        return self.cote * self.cote

def main():
    formes: list[Forme] = [Carre(2.0), Carre(3.0)]
    total = sum(f.aire() for f in formes)
    print(total)
```

```
13.0
```

`super().init(...)` appelle le constructeur de la classe parente.

## Génériques

```dystos
class Boite[T]:
    contenu: T
    def init(self, contenu: T):
        self.contenu = contenu
    def get(self) -> T:
        return self.contenu

def premier[T](items: list[T]) -> T:
    return items[0]

def main():
    b = Boite[int](42)
    print(b.get(), premier(["x", "y"]))
```

```
42 x
```
