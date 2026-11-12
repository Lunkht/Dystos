---
id: 19-tests
titre: Tests intégrés
chapitre: qualite
ordre: 19
categorie: Qualité
note: >-
  Nécessite le décorateur @test et la commande dystos test, absents de l’application mobile.
---

def additionner(a: int, b: int) -> int:
    return a + b

@test
def test_addition():
    assert additionner(2, 3) == 5
    assert additionner(-1, 1) == 0
