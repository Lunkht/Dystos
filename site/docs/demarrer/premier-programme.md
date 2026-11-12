---
id: premier-programme
title: Premier programme
sidebar_label: Premier programme
sidebar_position: 2
description: Écrire, analyser et exécuter un premier programme Dystos.
---

# Premier programme

Crée un fichier `hello.dys` :

```dystos
def main():
    nom: str = "Dystos"
    print(f"Bonjour, {nom} !")
```

Puis :

```bash
dystos run hello.dys
```

```
Bonjour, Dystos !
```

## Anatomie d'un programme

- `def main():` déclare la fonction d'entrée. Le `:` ouvre le bloc,
  l'indentation le délimite, il n'y a pas d'accolades.
- `nom: str = "Dystos"` déclare une variable **immuable** avec son type.
- `f"..."` est une chaîne formatée : `{nom}` est remplacé par la valeur.
- `main` est appelée automatiquement à la fin du fichier : n'ajoute pas
  `main()` à la fin, cela provoquerait une erreur `E0307` (point d'entrée
  ambigu).

Un fichier sans `main` exécute simplement ses instructions de haut en bas.

## Vérifier avant d'exécuter

```bash
dystos run hello.dys --json
```

Le résultat est un objet JSON contenant `stdout`, `stderr`, `exitCode`,
`durationMs` et `diagnostics` :

```json
{"stdout":"Bonjour, Dystos !\n","stderr":"","exitCode":0,"durationMs":120,"diagnostics":[]}
```

En cas d'erreur de compilation, `exitCode` vaut `2` et `diagnostics`
contient le code, la ligne et la colonne du problème.
