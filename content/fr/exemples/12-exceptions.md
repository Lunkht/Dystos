---
id: 12-exceptions
titre: Exceptions
chapitre: surete
ordre: 12
categorie: Sûreté
note: >-
  Nécessite les exceptions et le bloc try, pas encore implémentés dans l’application.
---

class SoldeInsuffisant(Exception):
    def init(self, manque: float):
        super().init(f"Il manque {manque}")

def retirer(solde: float, montant: float) -> float:
    if montant > solde:
        raise SoldeInsuffisant(montant - solde)
    return solde - montant

def main():
    try:
        retirer(10.0, 25.0)
    except SoldeInsuffisant as e:
        print("Erreur :", e.message)
    finally:
        print("Fin")
