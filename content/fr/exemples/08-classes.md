---
id: 08-classes
titre: Classes et constructeurs
chapitre: objets
ordre: 8
categorie: Objets
note: >-
  Nécessite les classes et la méthode init, pas encore implémentées dans l’application.
---

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
