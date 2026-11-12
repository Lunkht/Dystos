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
  "code": "E0201",
  "message": "'null' ne peut pas être utilisé comme 'str' (utilisez 'str?')",
  "line": 6,
  "column": 14
}
```

| Champ | Type | Rôle |
| --- | --- | --- |
| `severity` | `error` | Gravité (toujours `error` aujourd'hui) |
| `code` | `E####` | Code stable, utilisé par la CI |
| `message` | `string` | Texte lisible, sans la position |
| `line`, `column` | `int` | Position du début, base 1 |

Le `message` ne contient pas la position : celle-ci est dans `line` et
`column`, ce qui permet à l'éditeur de souligner la zone exacte.

## Familles de codes

| Préfixe | Famille | Exemples |
| --- | --- | --- |
| `E00xx` | Lexique et syntaxe | `E0001` lexer, `E0002` parser |
| `E01xx` | Noms et modules | `E0101` nom inconnu, `E0102` déjà défini, `E0104` membre privé |
| `E02xx` | Types et appels | `E0201` null-safety, `E0202` type incompatible, `E0212` réaffectation `val` |
| `E03xx` | Classes et entrées | `E0307` point d'entrée ambigu, `E0399` fonction locale non supportée |

Il n'y a pas d'avertissements (`W###`) pour l'instant : seul `error` est émis.

## Vérifier en ligne de commande

```bash
dystos run fichier.dys --json
```

Sortie standard : un objet JSON contenant `stdout`, `stderr`, `exitCode`,
`durationMs` et `diagnostics` (un tableau). Le code de sortie est non nul dès
qu'une erreur de compilation est présente. C'est ce que la CI consomme, et
c'est exactement ce que le playground affiche.

```bash
dystos test fichier.dys --json
```

idem, pour les blocs `@test`.

## Affichage

- Le site souligne la zone concernée dans l'éditeur du playground.
- L'application place une pastille dans la marge, à la bonne ligne.
- La CLI affiche le message avec un excerpt du code et un pointeur `^`.

## Règles

- Le compilateur signale plusieurs erreurs quand c'est utile, mais s'arrête à
  la première erreur qui empêche l'analyse de continuer.
- Les positions sont toujours en base 1, ligne et colonne, comme dans les
  éditeurs.
