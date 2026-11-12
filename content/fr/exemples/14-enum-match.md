---
id: 14-enum-match
titre: Enum et match
chapitre: structurer
ordre: 14
categorie: Structurer
note: >-
  Nécessite les énumérations et le motif match, pas encore implémentés dans l’application.
---

enum Jour:
    LUNDI, MARDI, SAMEDI, DIMANCHE

def type_jour(j: Jour) -> str:
    match j:
        case Jour.SAMEDI | Jour.DIMANCHE:
            return "week-end"
        case _:
            return "semaine"

def main():
    print(type_jour(Jour.SAMEDI))
