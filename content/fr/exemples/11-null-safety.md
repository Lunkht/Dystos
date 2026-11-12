---
id: 11-null-safety
titre: Null-sûreté
chapitre: surete
ordre: 11
categorie: Sûreté
note: >-
  Nécessite les types nullables et les opérateurs ?., ?:, pas encore implémentés dans l’application.
---

def longueur(s: str?) -> int:
    return s?.length ?: 0

def main():
    print(longueur("abc"), longueur(null))
