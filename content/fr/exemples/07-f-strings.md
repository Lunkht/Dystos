---
id: 07-f-strings
titre: Chaînes formatées
chapitre: donnees
ordre: 7
categorie: Données
note: >-
  Nécessite les méthodes de chaîne (.upper, .length, .split, .join) et les tranches, pas encore implémentées dans l’application.
---

def main():
    s = "Développeur"
    print(s.upper(), s.length, s[0:4])
    pi = 3.14159265
    print(f"{pi:.2f} | {42:05d} | {'ok' if pi > 3 else 'ko'}")
    lignes = "a,b,c".split(",")
    print("-".join(lignes))
