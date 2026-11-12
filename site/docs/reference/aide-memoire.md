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
def main():
    print("bonjour")
```

`main` est appelée automatiquement : ne l'appelez pas vous-même.

## Variables

```dystos
val nom: str = "Awa"      # immuable
var compteur: int = 0     # modifiable
compteur += 1
val deduit = 42           # type inféré
val texte: str? = null    # nullable
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

while true:
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

val double = (x: int) -> x * 2
val pairs = filter(x -> x % 2 == 0, [1, 2, 3, 4])
```

## Collections

```dystos
val liste = [1, 2, 3]
liste.add(4)
print(liste[0], len(liste))

val stock = {"clavier": 5}
stock["ecran"] = 3
for nom, qte in stock.items():
    print(nom, qte)

val uniques = {1, 2, 2, 3}
print(len(uniques))   # 2

val pairs = [n * 2 for n in range(5) if n % 2 == 0]
```

## Chaînes

```dystos
val s = "Dystos"
print(s.upper(), s.length, s[0:4])
print(f"Bonjour, {nom} !")
print("-".join(["a", "b", "c"]))
```

## Null-sûreté

```dystos
val alias: str? = null
print(alias?.length ?: 0)   # 0
```

## Exceptions

```dystos
raise ValueError("montant invalide")
```

```dystos
try:
    retirer(10.0, 25.0)
except SoldeInsuffisant as e:
    print("Erreur :", e.message)
finally:
    print("Fin")
```

## Classes

```dystos
class Compte:
    titulaire: str
    _solde: float = 0.0

    def init(self, titulaire: str):
        self.titulaire = titulaire

    def deposer(self, montant: float):
        self._solde += montant

    def toString(self) -> str:
        return f"Compte({self.titulaire}, {self._solde})"

c = Compte("Awa")
c.deposer(150.0)
print(c)
```
