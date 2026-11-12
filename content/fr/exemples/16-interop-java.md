---
id: 16-interop-java
titre: Interop Java
chapitre: monde-reel
ordre: 16
categorie: Monde réel
note: >-
  Nécessite l’interopérabilité Java, volontairement absente de l’application mobile.
---

import java.util.ArrayList
import java.time.LocalDate

def main():
    liste = ArrayList[str]()
    liste.add("a")
    liste.add("b")
    print(liste.size(), LocalDate.now().year)
