# dystos-parser (Phase 1, suite)

Analyseur syntaxique (descente récursive) et AST de **Dystos**. Dépend de `dystos-lexer`.

## Utilisation

```java
SourceFile file = Parser.parse(Files.readString(Path.of("exemple.dys")));
Expr e = Parser.parseExpression("a ?: 0 + 1");
System.out.println(AstPrinter.print(file));
```

Les erreurs lèvent `ParseException` (ligne, colonne, message) ; les erreurs lexicales lèvent
`LexerException`.

Afficher l'AST d'un fichier :

```
java -cp <lexer.jar>:<parser.jar> com.vulsoft.dystos.parser.AstDump exemple.dys
```

## Build et tests

Depuis `compiler/` (le module `parser` a besoin de `lexer`) :

```
cd compiler
mvn test
```

## AST (`com.vulsoft.dystos.ast.Ast`)

Records immuables avec position (`Pos`). Interfaces scellées : `Expr`, `Stmt`, `TypeRef`,
`TopLevel`, `Member`, donc `switch` exhaustif possible dans les phases suivantes.

| Famille | Nœuds |
|---|---|
| Fichier | `SourceFile`, `ImportDecl` |
| Déclarations | `FuncDecl`, `ClassDecl`, `InterfaceDecl`, `EnumDecl`, `VarDecl`, `Param`, `Decorator` |
| Types | `NamedType` (génériques, `?` nullable), `FuncType` |
| Instructions | `ExprStmt`, `Assign`, `IfStmt`, `WhileStmt`, `ForStmt`, `ReturnStmt`, `BreakStmt`, `ContinueStmt`, `PassStmt`, `RaiseStmt`, `AssertStmt`, `TryStmt`, `WithStmt`, `MatchStmt` |
| Expressions | littéraux, `FStringLit`, `Name`, `Binary`, `Unary`, `Conditional`, `Call`, `Attribute` (`?.`), `Index`, `Slice`, `ListLit`, `SetLit`, `MapLit`, `Lambda`, compréhensions, `GeneratorExp` |

## Précédence des expressions (faible → forte)

`lambda` · `a if c else b` · `or` · `and` · `not` · comparaisons (`== != < > <= >= in, not in, is, is not`)
· `?:` · `+ -` · `* / %` · unaires `- +` · `**` (droite) · suffixes `.x` `?.x` `(...)` `[...]`

## Choix à connaître

- `elif` devient un `IfStmt` imbriqué dans `orElse`.
- `{}` vide est une **map** ; `{1, 2}` est un set.
- `x = 5` est un `Assign` ; `val x = 5`, `var x = 5` et `x: T = 5` sont des `VarDecl`. C'est l'analyse
  sémantique qui décide si `x = 5` déclare ou réaffecte.
- `Boite[int](42)` donne `Call(Index(Name Boite, [int]))` : l'indexation et l'instanciation générique
  ne se distinguent qu'à l'analyse sémantique.
- Une méthode sans `:` ni corps (interface, abstraite) a `body == null`.
- Dans une f-string, les nœuds de l'expression héritent de la position de la chaîne entière.
- **Scripts** : les instructions écrites au niveau du fichier vont dans `SourceFile.statements` (dans
  l'ordre), les déclarations dans `decls` (hissées, comme des membres). `isScript()` est vrai s'il y a
  au moins une instruction. Règles pour l'analyse sémantique (Phase 2) : les instructions de niveau
  fichier sont enveloppées dans un `main` implicite ; un fichier qui a **à la fois** des instructions
  de niveau fichier et un `def main()` explicite est une erreur (point d'entrée ambigu).
- Pas encore supporté : tuples, `async`, garde `case x if ...`, motifs de déstructuration.
