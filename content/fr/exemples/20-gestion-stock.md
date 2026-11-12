---
id: 20-gestion-stock
titre: Gestion de stock
chapitre: projet
ordre: 20
categorie: Projet
note: >-
  Nécessite les classes, les collections et la null-sûreté, pas encore implémentées dans l’application.
---

class Produit:
    nom: str
    prix: float
    quantite: int

    def init(self, nom: str, prix: float, quantite: int = 0):
        self.nom = nom
        self.prix = prix
        self.quantite = quantite

    def valeur(self) -> float:
        return self.prix * self.quantite

class Stock:
    _produits: map[str, Produit] = {}

    def ajouter(self, p: Produit):
        self._produits[p.nom] = p

    def trouver(self, nom: str) -> Produit?:
        return self._produits.get(nom)

    def total(self) -> float:
        return sum(p.valeur() for p in self._produits.values())

def main():
    stock = Stock()
    stock.ajouter(Produit("riz", 1.5, 100))
    stock.ajouter(Produit("huile", 3.2, 40))

    p = stock.trouver("riz")
    print(p?.nom ?: "introuvable")
    print(stock.trouver("sucre")?.nom ?: "introuvable")
    print(f"Valeur totale : {stock.total():.2f}")
