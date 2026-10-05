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

```bash
# Linux et macOS
curl -fsSL https://dystos.dev/install.sh | sh

# Windows (PowerShell)
irm https://dystos.dev/install.ps1 | iex
```

Vérifie l'installation :

```bash
dystos --version
dystos run hello.dys
```

## Depuis les sources

Le dépôt contient le compilateur et son système de build.

```bash
git clone https://github.com/Lunkht/Dystos.git
cd Dystos/compiler
./gradlew build
```

## Alternatives

| Besoin | Outil |
| --- | --- |
| Éditeur local | Éditeur avec l'extension Dystos (coloration par le lexer) |
| Sans installation | Application Android, contenu embarqué, mode avion |
| CI | `dystos check --json` renvoie les diagnostics en JSON |

## Prochaine étape

Écris ton [premier programme](/docs/demarrer/premier-programme).