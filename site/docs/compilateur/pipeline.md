---
id: pipeline
title: Pipeline de compilation
sidebar_label: Pipeline
sidebar_position: 1
description: Les étapes du compilateur Dystos, de la source au bytecode JVM.
---

# Pipeline de compilation

```
source
  → lexing      jetons + diagnostics lexicaux
  → parsing     AST + diagnostics syntaxiques
  → resolution  table des symboles, résolution des noms
  → typage      null-safety, compatibilité des types
  → generation  bytecode JVM (.class)
```

Chaque étape consomme la précédente et produit des diagnostics au format
unique, partagé par la CLI, le site et l'application.

## Lexing

Le lexer transforme le texte en jetons. Il gère les commentaires (`#`), les
chaînes simples, brutes (`r"..."`) et formatées (`f"..."`), les littéraux
numériques dans toutes leurs bases, et l'indentation.

C'est la grammaire de coloration des trois cibles qui dérive de ses jetons :
le site utilise une grammaire Prism, l'application colore avec le lexer lui-même.

## Parsing

Le parser produit un AST. Indentation et délimiteurs ouvrants sont vérifiés à
ce stade, d'où les diagnostics du type `E0101`.

## Résolution et typage

La table des symboles associe noms et portée. Le système de types vérifie les
affectations, les arguments, les returns, et applique la null-safety : lire une
valeur nullable sans `?` est une erreur, pas une exception à l'exécution.

## Exécution

Deux chemins existent :

- l'**interpréteur d'AST**, pour le playground et l'application Android, où la
  Startup compte ;
- le **backend JVM**, pour la performance et les `.jar`.

## Compatibilité Android

Le lexer et le parser sont écrits pour Java 21. Pour les embarquer dans
l'application, la chaîne de build Android cible une version de bytecode
compatible avec le `minSdk` choisi, et le *core library desugaring* est activé
si des API Java récentes sont utilisées.