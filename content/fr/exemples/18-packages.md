---
id: 18-packages
titre: Packages et visibilité
chapitre: structurer
ordre: 18
categorie: Structurer
note: >-
  Nécessite les packages et la visibilité, volontairement absents de l’application mobile.
---

package app

class Depot:
    _interne: int = 0

    def incrementer(self, n: int = 1) -> int:
        self._interne += n
        return self._interne

def main():
    print(Depot().incrementer(5))
