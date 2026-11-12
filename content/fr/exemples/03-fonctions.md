---
id: 03-fonctions
titre: Fonctions
chapitre: premiers-pas
ordre: 3
categorie: Premiers pas
---

def saluer(nom: str, titre: str = "M.") -> str:
    return f"Bonjour {titre} {nom}"

print(saluer("Diallo"))
print(saluer("Martin", "Dr"))

---

sortie_attendue: |
  Bonjour M. Diallo
  Bonjour Dr Martin
