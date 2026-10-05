---
id: aide-memoire
title: Aide-mémoire
sidebar_label: Aide-mémoire
sidebar_position: 3
description: L'essentiel de Dystos sur une page.
---

# Aide-mémoire

## Programme

```dystos
def main() -> void:
    print("bonjour")

main()
```

## Variables

```dystos
val nom: string = "Awa"      # immuable
var compteur: int = 0        # modifiable
compteur += 1
val deduit = 42              # type inféré
```

## Contrôle

```dystos
if n > 0:
    print("positif")
elif n == 0:
    print("zéro")
else:
    print("négatif")

for i in range(10):
    if i % 2 == 0:
        continue
    print(i)

while True:
    break

match statut:
    case Statut.ACTIF:
        print("actif")
    case _:
        print("autre")
```

## Fonctions

```dystos
def somme(a: int, b: int = 0) -> int:
    return a + b

val double = (x: int) => x * 2
```

## Collections

```dystos
val liste = [1, 2, 3]
liste.append(4)
print(liste[0], liste[1:], len(liste))

val dict = {"a": 1, "b": 2}
for cle, valeur in dict.items():
    print(cle, valeur)

val pairs = [n for n in range(20) if n % 2 == 0]
val init = {nom: len(nom) for nom in ["a", "bb"]}
```

## Objets

```dystos
class Compte:
    _solde: float = 0.0

    def __init__(self, nom: string):
        self.nom = nom

    def __str__(self) -> string:
        return f"{self.nom} ({self._solde:.2f})"
```

## Null-sûreté et exceptions

```dystos
val alias: string? = null
print(alias?.len() ?? 0)

if alias?:
    print(alias)

try:
    risky()
except ValueError as e:
    print(e.message)
finally:
    print("fini")
```

## Fonctionnel

```dystos
val xs = [1, 2, 3, 4]
print(xs.map((n) => n * 2))
print(xs.filter((n) => n > 2))
print(xs.reduce((a, n) => a + n, 0))
```

## Interop Java

```dystos
import java.util.ArrayList

var liste = ArrayList<String>()
liste.add("Dystos")
for element in liste:
    print(element)
```

## Diagnostics courants

| Code | Signification |
| --- | --- |
| `E0101` | Délimiteur ouvrant manquant |
| `E0104` | Identifiant inconnu |
| `E0201` | Type non assignable |
| `E0301` | Réaffectation d'une `val` |
| `E0302` | Accès à une valeur nullable sans `?` |