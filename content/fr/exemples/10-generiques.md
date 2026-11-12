---
id: 10-generiques
titre: Génériques
chapitre: objets
ordre: 10
categorie: Objets
note: >-
  Nécessite les types génériques, pas encore implémentés dans l’application.
---

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
