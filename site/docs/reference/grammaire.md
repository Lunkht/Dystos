---
id: grammaire
title: Grammaire
sidebar_label: Grammaire
sidebar_position: 1
description: Forme syntaxique de Dystos, du jeton à l'expression.
---

# Grammaire

Cette page décrit la forme syntaxique. Elle suit l'implémentation du lexer et
du parser : en cas d'écart, le code fait foi.

## Programme

```
programme    := declaration*
declaration := instruction | fonctionClasse
instruction := expression | declarationVariable | retour | boucle | ...
```

## Instructions

```
instruction     := affectation
                 | expressionAppel
                 | retour
                 | boucleWhile
                 | boucleFor
                 | instructionSi
                 | instructionMatch
                 | lever

affectation     := identifiant ("=" | "+=" | "-=" | "*=" | "/=" | "%=") expression
retour          := "return" expression?
lever           := "throw" expression
```

## Expressions

Precedence croissante :

| Niveau | Opérateurs |
| --- | --- |
| 1 | `or` |
| 2 | `and` |
| 3 | `not` (unaire) |
| 4 | `==` `!=` `<` `<=` `>` `>=` `is` `in` |
| 5 | `+` `-` `\|` |
| 6 | `*` `/` `%` |
| 7 | appel, indexation, accès `.`, `?.` |
| 8 | unaire `-` `+` `!` |
| 9 | postfixe : `(...)` `[...]` `.` `?.` `?` |

```
expression     := lambda | "if" expression "else" expression | orExpr
orExpr         := andExpr ("or" andExpr)*
andExpr        := notExpr ("and" notExpr)*
notExpr        := "not" notExpr | comparaison
comparaison    := somme (("==" | "!=" | "<" | "<=" | ">" | ">=") somme)*
somme          := produit (("+" | "-") produit)*
produit        := unaire (("*" | "/" | "%" | "**") unaire)*
unaire         := ("-" | "+") unaire | postfixe
postfixe       := primaire ( "(" args ")" | "[" expression "]" | "." identifiant
                             | "?." identifiant | "?" | argumentsLambda )*
primaire       := litteral | identifiant | "(" expression ")" | liste | dict
                 | construction | lambda
```

## Types

```
type      := "int" | "float" | "string" | "bool" | "char" | "void" | "any"
            | nomType ("<" types ">")? ("?")?
types     := type ("," type)*
```

## Déclarations

```
variable    := ("val" | "var") identifiant (":" type)? ("=" expression)?
fonction    := "def" identifiant "(" parametres? ")" (":" type)? ":" bloc
parametres  := parametre ("," parametre)*
parametre   := identifiant ":" type ("=" expression)?
classe      := "class" identifiant (":" types)? ":" bloc
interface   := "interface" identifiant ":" bloc
enum        := "enum" identifiant ":" (membreCorps)+
lambda      := "(" parametres? ")" "=>" expression
             | identifiant "=>" expression
```

## Commentaires

```
ligne     := "#" texte
bloc      := "###" texte "###"
```

## Jetons réservés

`def` `val` `var` `class` `interface` `enum` `record` `object` `if` `elif`
`else` `for` `while` `match` `case` `return` `break` `continue` `import`
`package` `extends` `implements` `new` `this` `self` `super` `try` `catch`
`finally` `throw` `as` `is` `in` `and` `or` `not` `typealias` `static`
`public` `private` `protected` `internal` `fun` `where` `yield`

## Littéraux

| Type | Forme |
| --- | --- |
| Entier | `42`, `1_000`, `0xFF`, `0b1010`, `0o17` |
| Flottant | `3.14`, `1e-3`, `2.0f` |
| Booléen | `true`, `false` |
| Chaîne | `"texte"`, `'c'`, `"` multi-lignes `"""` |
| Formatée | `f"Bonjour, {nom}"` |
| Bruts | `r"mot\s"` |
| Collection | `[1, 2]`, `{"a": 1}`, `{1, 2}` |