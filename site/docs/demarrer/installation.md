---
id: installation
title: Installation
sidebar_label: Installation
sidebar_position: 1
description: Installer la CLI Dystos ou utiliser le playground dans le navigateur.
---

# Installation

## Dans le navigateur, sans rien installer

Ouvre le [playground](/playground), colle du code Dystos et appuie sur
**Analyser**. C'est le moyen le plus rapide pour essayer Dystos, et ça marche
même sans connexion une fois la page chargée.

## La CLI

Le compilateur s'installe avec le gestionnaire de paquets de ta plateforme.
Il ne dépend d'aucun service distant : les artefacts sont publiés sur le
dépôt GitHub du projet.

Vérifie l'installation :

```bash
dystos run hello.dys
```

## Depuis les sources

Le dépôt contient le compilateur et son système de build.

```bash
git clone https://github.com/Lunkht/Dystos.git
cd Dystos/compiler
./gradlew build
```

Le binaire résultant se trouve dans `compiler/interp/build/libs/`. Utilise-le
avec `java -jar dystos.jar run programme.dys [options]`.

## Options de la CLI

| Option | Effet |
| --- | --- |
| `run fichier.dys` | Analyse puis exécute |
| `test fichier.dys` | Analyse et exécute les blocs `@test` |
| `--sandbox` | Limites strictes (playground) |
| `--json` | Résultat JSON sur la sortie standard |
| `--max-steps N` | Nombre maximal d'étapes d'exécution |
| `--timeout-ms N` | Délai maximal d'exécution |
| `--max-output N` | Taille maximale de la sortie |

Le fichier `-` lit le programme sur l'entrée standard.
