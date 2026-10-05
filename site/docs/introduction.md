---
id: introduction
title: Introduction à Dystos
sidebar_label: Introduction
sidebar_position: 1
description: Dystos est un langage lisible comme Python, sûr comme Java, qui tourne sur la JVM.
---

# Introduction

Dystos est un langage de programmation conçu pour être appris vite et bien.
Il combine la lisibilité d'un langage dynamique, la sécurité d'un langage statique
et l'écosystème de la JVM.

```dystos
def fibonacci(n: int) -> int:
    if n < 2:
        return n
    return fibonacci(n - 1) + fibonacci(n - 2)

print(fibonacci(10))
```

## Les trois propriétés

**Lisible.** L'indentation marque les blocs, il n'y a pas de point-virgule, et
les mots-clés sont en français comme en anglais quand c'est utile. Un programme
Dystos se lit de haut en bas sans explication.

**Sûr.** Les types sont vérifiés avant l'exécution. Le null-safety est intégré
avec `?` et `?.` : pas de `NullPointerException` surprise. Les erreurs
lexicales, syntaxiques et de types sont rapportées avec une ligne, une colonne
et un code de diagnostic.

**Autohébergé.** Aucun service tiers. Pas de CDN, pas de police distante, pas de
télémétrie. Le compilateur, le site et le playground se déploient avec Docker
Compose sur l'infrastructure de ton choix.

## Sur quoi tourne Dystos

Le compilateur produit du bytecode JVM. Un programme Dystos s'exécute donc
n'importe où une JVM est disponible, et appelle directement le code Java
existant.

```dystos
import java.util.ArrayList

def est_premier(n: int) -> bool:
    if n < 2:
        return False
    for d in range(2, n):
        if n % d == 0:
            return False
    return True

var liste = ArrayList<String>()
liste.add("Dystos")
print(liste)
print([i for i in range(2, 20) if est_premier(i)])
```

## Où aller ensuite

- [Démarrer](/docs/demarrer/installation) : installer la CLI et écrire un premier programme.
- [Parcours guidé](/docs/tour) : dix chapitres de leçons courtes avec exercices.
- [Playground](/playground) : écrire, analyser et exécuter du Dystos dans le navigateur.
- [Guide du langage](/docs/guide/valeurs-et-types) : la référence pédagogique.