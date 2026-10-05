---
id: hello-world
titre: Hello world
chapitre: premiers-pas
ordre: 1
resume: Écrire et exécuter un premier programme Dystos.
exemple: 01-hello-world
exercice:
  consigne: |-
    Affiche le texte `Bonjour, Dystos !` en utilisant `print`.
  code_initial: |
    # à compléter
  sortie_attendue: "Bonjour, Dystos !"
  indices:
    - "`print(...)` écrit une ligne sur la sortie standard."
    - "Le texte se met entre guillemets doubles."
  solution: |
    print("Bonjour, Dystos !")
---

`print` écrit une ligne sur la sortie standard. C'est la fonction la plus
utile pour vérifier ce que fait un programme.

```dystos
print("Bonjour, Dystos !")
```

La sortie est :

```
Bonjour, Dystos !
```

## Les chaînes de caractères

Une chaîne se déclare avec des guillemets doubles. Les guillemets simples
délimitent les caractères individuels.

```dystos
langue: string = "Dystos"
initiale: char = 'D'
print(langue, initiale)
```

## Les fonctions

On regroupe le code dans des fonctions avec `def`. L'indentation délimite le
bloc : pas d'accolades, pas de mot-clé `end`.

```dystos
def bonjour(nom: string):
    print(f"Bonjour, {nom} !")

bonjour("Awa")
bonjour("Moussa")
```

Le `f` devant la chaîne indique qu'elle est formatée : chaque `{...}` est
remplacé par la valeur de l'expression qu'il contient.

## Exécuter

Un fichier `.dys` s'exécute de haut en bas. La fonction `main` n'est pas
obligatoire, mais elle rend le code plus lisible quand le programme grossit.

```dystos
def main():
    print("Bonjour, Dystos !")

main()
```

## Ce qu'il faut retenir

- `print(...)` affiche une ligne.
- `def nom(...):` déclare une fonction, le bloc est indenté.
- `f"..."` interpole des expressions dans une chaîne.