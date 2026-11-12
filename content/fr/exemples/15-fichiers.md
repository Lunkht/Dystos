---
id: 15-fichiers
titre: Fichiers
chapitre: monde-reel
ordre: 15
categorie: Monde réel
note: >-
  Nécessite la lecture et l’écriture de fichiers, pas encore implémentées dans l’application.
---

import io

def main():
    with io.open("notes.txt", "w") as f:
        f.write("ligne 1\nligne 2\n")
    with io.open("notes.txt") as f:
        for ligne in f.lines():
            print(ligne.strip())
