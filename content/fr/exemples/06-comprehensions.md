---
id: 06-comprehensions
titre: Compréhensions
chapitre: donnees
ordre: 6
categorie: Données
note: >-
  Nécessite les compréhensions de liste, de dictionnaire et d’ensemble, pas encore implémentées dans l’application.
---

def main():
    carres = [x * x for x in range(10) if x % 2 == 0]
    longueurs = {mot: len(mot) for mot in ["a", "bcd", "ef"]}
    pairs = {x for x in range(6) if x % 2 == 0}
    print(carres, longueurs, pairs)
