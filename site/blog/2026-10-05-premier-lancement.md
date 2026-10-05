---
slug: premier-lancement
title: Premier lancement de Dystos
authors: [dystos]
tags: [versions]
---

Le dépôt Dystos est ouvert. Il contient le compilateur, la source unique de
contenu, le site et le squelette de l'application Android.

<!-- truncate -->

## Ce qui est en place

- `compiler/` : le lexer et le parser.
- `content/` : les leçons et exercices, écrits une seule fois.
- `site/` : le site statique, en français, sans aucune ressource externe.
- `tools/build-content/` : génère les pages du site et les assets Android depuis
  `content/`.
- `services/playground/` : le contrat d'API pour l'analyse et l'exécution.
- `deploy/` : la configuration Docker Compose.

## Le principe qui structure tout

Une leçon est écrite une fois, en Markdown, dans `content/fr`. Le build en
faisant deux choses : une page de site et une entrée dans le paquet d'assets de
l'application Android. Si le contenu est invalide, le build échoue.

```yaml
id: variables
titre: Variables, val et var
chapitre: premiers-pas
ordre: 2
exercice:
  consigne: "Corrige la troisième ligne."
  sortie_attendue: "Dystos 1.0"
```

## Prochaines étapes

Le parcours guidé s'étend chapitre par chapitre. En parallèle, l'interpréteur
d'AST débloque l'exécution dans le playground et dans l'application sans
attendre la génération de bytecode.