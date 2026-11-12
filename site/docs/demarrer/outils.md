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
| `dystos test fichier.dys` | Analyse et exécute les blocs `@test` |
| `dystos run fichier.dys --json` | Diagnostics et résultat au format JSON |

Options utiles : `--sandbox` (limites strictes du playground), `--max-steps N`,
`--timeout-ms N`, `--max-output N`. Le fichier `-` lit le programme sur
l'entrée standard.

Il n'y a pas de sous-commandes `check`, `tokens` ni `ast` : le site et le
playground appellent le service d'analyse directement.

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

`dystos run fichier.dys --json` renvoie un objet JSON contenant les diagnostics
sur la sortie standard. Une intégration continue peut s'appuyer sur `exitCode`
(0 = succès, 2 = erreur de compilation) pour rejeter un changement qui casse
le langage.
