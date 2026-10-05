---
id: outils
title: Outils
sidebar_label: Outils
sidebar_position: 3
description: CLI, éditeur, extensions et intégration continue.
---

# Outils

## La CLI

| Commande | Effet |
| --- | --- |
| `dystos run fichier.dys` | Analyse puis exécute |
| `dystos check fichier.dys` | Analyse sans exécuter |
| `dystos check --json fichier.dys` | Diagnostics au format JSON |
| `dystos tokens fichier.dys` | Liste des jetons |
| `dystos ast fichier.dys` | Affiche l'arbre syntaxique |

## Éditeur

L'extension Dystos pour les éditeurs conçus pour les plugins fournit :

- la coloration syntaxique pilotée par le vrai lexer du compilateur ;
- le diagnostic sous la ligne fautive ;
- le formatage à l'indentation Dystos.

## Application Android

L'application embarque le contenu et le compilateur. Elle fonctionne en mode
avion : leçons, exercices, coloration et vérification de code restent
disponibles sans réseau. Aucune permission réseau n'est requise.

## Intégration continue

`dystos check --json` renvoie un tableau de diagnostics sur la sortie standard
et un code de sortie non nul dès qu'une erreur est présente. Les mêmes
diagnostics alimentent le [playground](/playground) et l'application, donc ce que
la CI valide est exactement ce que l'apprenant voit.

## Verifier les exercices

Chaque exercice de ce site est validé par le compilateur. Le build échoue si un
exemple ne passe plus après une évolution du langage.