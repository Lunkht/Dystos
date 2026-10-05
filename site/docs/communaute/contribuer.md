---
id: contribuer
title: Contribuer
sidebar_label: Contribuer
sidebar_position: 1
description: Cloner, compiler, tester et proposer une modification.
---

# Contribuer

Dystos est un projet souverain : pas de service tiers, pas de CDN, pas de
dépendance à une plateforme. Toute contribution qui introduit une dépendance
externe pour le site ou l'application doit être justifiée.

## Préparer l'environnement

```bash
git clone https://github.com/Lunkht/Dystos.git
cd Dystos

# Compilateur
cd compiler && ./gradlew build

# Site
cd ../site && npm install

# Contenu
cd ../tools/build-content && npm install
```

## Travailler sur le contenu

Les leçons vivent dans `content/fr`. Une leçon est un fichier Markdown avec un
en-tête et, éventuellement, un exercice.

```bash
cd tools/build-content
npm run check      # valide le schéma
npm run build      # regénère le site et les assets Android
```

Le build échoue si le contenu est invalide. Les pages du site sous
`site/docs/tour/` sont générées : ne les édite pas à la main.

## Écrire un exercice

Chaque exercice a une consigne, un code initial, une sortie attendue, des
indices et une solution validée par le compilateur.

```yaml
exercice:
  consigne: "Complète la méthode `deposer`."
  code_initial: |
    class Compte:
        _solde: float = 0.0
  sortie_attendue: "150.0"
  indices: ["Utilise self._solde"]
  solution: |
    self._solde += montant
```

## Conventions

- Le contenu est en français ; l'anglais arrive plus tard.
- Les exemples de code utilisent des noms lisibles, pas `a`, `b`, `tmp`.
- Les messages de diagnostic commencent par une minuscule et finissent sans
  point.
- Aucun appel réseau depuis le site ou l'application.

## Signaler un bug

Ouvre une issue avec un exemple minimal, la sortie attendue et la sortie
obtenue. Ajoute `dystos check --json` quand c'est une erreur de compilation :
le format JSON suffit à reproduire.