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
    nom: string = "Dystos"
    print(f"Bonjour, {nom} !")

main()
```

Puis :

```bash
dystos run hello.dys
```

```
Bonjour, Dystos !
```

## Anatomie d'un programme

- `def main():` déclare une fonction. Le `:` ouvre le bloc, l'indentation le
  délimite, il n'y a pas de accolades.
- `nom: string = "Dystos"` déclare une variable **immuable** avec son type.
- `f"..."` est une chaîne formatée : `{nom}` est remplacé par la valeur.
- `main()` appelle la fonction. Les programmes Dystos s'exécutent à partir du
  haut niveau.

## Vérifier avant d'exécuter

```bash
dystos check hello.dys
```

Le vérificateur rapporte les erreurs lexicales, syntaxiques et de types avant
toute exécution. Le format JSON est le même que celui consommé par le site et
l'application :

```json
{
  "severity": "error",
  "code": "E0101",
  "message": "ligne 1, colonne 1 : '}' sans délimiteur ouvrant",
  "line": 1,
  "column": 1
}
```

## Erreurs fréquentes

| Message | Cause |
| --- | --- |
| indentation inattendue | Mélange d'espaces et de tabulations |
| identifiant inconnu | Typo, ou variable déclarée dans un autre bloc |
| type incompatible | Affectation d'un `int` à une variable `string` |

## Prochaine étape

Les [variables, `val` et `var`](/docs/tour/premiers-pas/variables).