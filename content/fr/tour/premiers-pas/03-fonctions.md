---
id: fonctions
titre: Fonctions
chapitre: premiers-pas
ordre: 3
resume: Déclarer des fonctions, paramètres, valeurs de retour et valeurs par défaut.
exemple: 03-fonctions
exercice:
  consigne: |-
    Complète `saluer` pour qu'elle affiche `Bonjour, <nom> !` puis `À bientôt`.
    Utilise deux appels à `print`.
  code_initial: |
    def saluer(nom: string):
        # à compléter

    saluer("Awa")
    saluer("Moussa")
  sortie_attendue: "Bonjour, Awa !\nÀ bientôt\nBonjour, Moussa !\nÀ bientôt"
  indices:
    - "Une fonction sans `->` ne renvoie rien."
    - "Les chaînes formatées s'écrivent `f\"...\"`."
  solution: |
    def saluer(nom: string):
        print(f"Bonjour, {nom} !")
        print("À bientôt")

    saluer("Awa")
    saluer("Moussa")
---

Une fonction regroupe un traitement sous un nom. `def` déclare le nom, les
paramètres entre parenthèses, et `->` le type de retour quand il y en a un.

## Déclaration

```dystos
def carre(x: int) -> int:
    return x * x

print(carre(7))
```

```
49
```

Le mot-clé `return` interrompt la fonction et rend une valeur. Sans `return`, une
fonction rend `void` : elle fait quelque chose mais ne produit rien.

```dystos
def afficher_titre(titre: string):
    print(f"--- {titre} ---")

afficher_titre("Stock")
```

## Paramètres et valeurs par défaut

Les paramètres peuvent avoir une valeur par défaut, et des paramètres nommés
rendent l'appel plus lisible.

```dystos
def prix_ht(ht: float, tva: float = 0.20) -> float:
    return ht * (1.0 + tva)

print(prix_ht(100.0))
print(prix_ht(100.0, tva: 0.055))
```

```
120.0
105.5
```

## Fonctions comme valeurs

Une fonction est une valeur : on la passe en argument, on la renvoie, on la
stocke.

```dystos
def doubler(x: int) -> int:
    return x * 2

val operation = doubler
print(operation(21))
```

## Fonctions récursives

Une fonction s'appelle elle-même. La condition d'arrêt est indispensable.

```dystos
def fibonacci(n: int) -> int:
    if n < 2:
        return n
    return fibonacci(n - 1) + fibonacci(n - 2)

for i in range(10):
    print(fibonacci(i))
```

## Ce qu'il faut retenir

- `def nom(param: type) -> type:` déclare une fonction.
- `return` rend une valeur, son absence signifie `void`.
- Une fonction est une valeur qu'on peut passer autour.