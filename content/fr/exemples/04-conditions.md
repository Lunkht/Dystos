---
id: 04-conditions
titre: Conditions et boucles
chapitre: controle
ordre: 4
categorie: Contrôle
---

for i in range(1, 16):
    if i % 15 == 0:
        print("FizzBuzz")
    elif i % 3 == 0:
        print("Fizz")
    elif i % 5 == 0:
        print("Buzz")
    else:
        print(i)

var n = 0
while true:
    n += 1
    if n == 3:
        continue
    if n > 5:
        break
    print(n)

---

sortie_attendue: |
  1
  2
  Fizz
  4
  Buzz
  Fizz
  7
  8
  Fizz
  Buzz
  11
  Fizz
  13
  14
  FizzBuzz
  1
  2
  4
  5
