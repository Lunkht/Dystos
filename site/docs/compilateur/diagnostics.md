---
id: diagnostics
title: Diagnostics
sidebar_label: Diagnostics
sidebar_position: 2
description: Format des erreurs et avertissements, et codes de diagnostic.
---

# Diagnostics

Un diagnostic est un objet unique partagé par la CLI, le site et l'application.

```json
{
  "severity": "error",
  "code": "E0101",
  "message": "ligne 3, colonne 5 : '}' sans délimiteur ouvrant",
  "line": 3,
  "column": 5,
  "endLine": 3,
  "endColumn": 6
}
```

| Champ | Type | Rôle |
| --- | --- | --- |
| `severity` | `error` \| `warning` \| `info` | Gravité |
| `code` | `E####` \| `W####` | Code stable, utilisé par la CI |
| `message` | `string` | Texte avec position déjà incluse |
| `line`, `column` | `int` | Position du début, base 1 |
| `endLine`, `endColumn` | `int` | Fin de la zone, optionnel |

## Familles de codes

| Préfixe | Famille |
| --- | --- |
| `E01xx` | Lexique et syntaxe |
| `E02xx` | Types et affectations |
| `E03xx` | Portée, null-safety, réaffectation |
| `W01xx` | Avertissements de style et d'usage |

## Vérifier en ligne de commande

```bash
dystos check --json fichier.dys
```

Sortie standard : un tableau de diagnostics, code de sortie non nul dès qu'une
erreur est présente. C'est ce que la CI consomme, et c'est exactement ce que le
playground affiche.

## Affichage

- Le site souligne la zone concernée dans l'éditeur du playground.
- L'application place une pastille dans la marge, à la bonne ligne.
- La CLI affiche le message avec un excerpt du code et un pointeur `^`.

## Règles

- Le compilateur signale plusieurs erreurs quand c'est utile, mais s'arrête à la
  première erreur qui empêche l'analyse de continuer.
- Un avertissement ne fait jamais échouer le build par défaut.
- Les positions sont toujours en base 1, ligne et colonne, comme dans les
  éditeurs.