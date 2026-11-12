---
id: 09-heritage
titre: Héritage et interfaces
chapitre: objets
ordre: 9
categorie: Objets
note: >-
  Nécessite l’héritage de classes, les interfaces et super, pas encore implémentés dans l’application.
---

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
