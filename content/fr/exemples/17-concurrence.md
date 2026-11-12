---
id: 17-concurrence
titre: Concurrence
chapitre: monde-reel
ordre: 17
categorie: Monde réel
note: >-
  Nécessite la concurrence (threads, verrous), volontairement absente de l’application mobile.
---

import concurrent

def calcul(n: int) -> int:
    return n * n

def main():
    taches = [concurrent.run(() -> calcul(i)) for i in range(4)]
    resultats = [t.await() for t in taches]
    print(resultats)
