# Éditeurs : coloration syntaxique de Dystos

Les fichiers de ce dossier sont **générés** par `scripts/generate-grammar.sh` à partir des listes de mots-clés du
colorateur (`compiler/lexer/.../Highlighter.java`) : ne les modifiez pas à la main.

| Fichier | Pour |
|---|---|
| `textmate/dystos.tmLanguage.json` | VS Code, Shiki, TextMate, Sublime, la plupart des éditeurs |
| `prism/prism-dystos.js` | Prism (Docusaurus, pages web) — langage `dystos` (alias `dys`) |
| `vscode/` | extension VS Code prête à l'emploi (coloration, commentaires `#`, parenthèses, indentation après `:`) |

## Vérification

```bash
scripts/test-editors.sh        # Node.js 18+ ; installe vscode-textmate, vscode-oniguruma et prismjs
```
Le script charge la grammaire dans le **vrai moteur TextMate de VS Code** (Oniguruma) et dans Prism, contrôle les scopes
de constructions précises (f-strings avec formats, `?:`, chaînes triples…), puis colore les 21 exemples en vérifiant que
le texte reconstitué est identique au source.

## Installer l'extension VS Code (manuellement)

```bash
# Linux / WSL2 (la fenêtre VS Code doit être rouverte ensuite)
cp -r editors/vscode ~/.vscode/extensions/vulsoft.dystos-0.1.0
```
> Sous WSL2 avec « VS Code Remote », installez-la plutôt dans `~/.vscode-server/extensions/`.
> Le moteur de coloration a été vérifié automatiquement ; l'installation de l'extension dans VS Code n'a pas été
> essayée dans l'environnement de développement.

## Utiliser Prism dans Docusaurus

D'après la documentation de Docusaurus, un langage personnalisé s'ajoute en « swizzlant » le fichier
`prism-include-languages` (vérifiez la procédure pour votre version) :

```js
// site/src/theme/prism-include-languages.js
import siteConfig from '@generated/docusaurus.config';
export default function prismIncludeLanguages(PrismObject) {
  const { themeConfig: { prism: { additionalLanguages = [] } = {} } } = siteConfig;
  globalThis.Prism = PrismObject;
  additionalLanguages.forEach((lang) => { require(`prismjs/components/prism-${lang}`); });
  require('./prism-dystos');          // copie de editors/prism/prism-dystos.js
  delete globalThis.Prism;
}
```
Les blocs de code s'écrivent ensuite avec le langage `dystos` : ```` ```dystos ````.

## Application Android : le colorateur Java

Le module `lexer` contient `Highlighter`, qui fonctionne sur du code incomplet et renvoie des indices UTF-16 :

```java
SpannableStringBuilder sb = new SpannableStringBuilder(text);
for (Highlighter.Span s : Highlighter.highlight(text)) {
    sb.setSpan(new ForegroundColorSpan(colorOf(s.kind())), s.start(), s.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
}
editText.setText(sb);   // recolorer après un court délai (debounce) pendant la frappe
```
`Highlighter.Kind` : `KEYWORD, CONSTANT, TYPE, FUNCTION, BUILTIN, SELF, NUMBER, STRING, ESCAPE, COMMENT, DECORATOR,
OPERATOR, PUNCTUATION` (les identifiants ordinaires n'ont pas de span).

## Aperçu

`demo/index.html` (généré par `node demo/build-demo.js`) montre quelques exemples colorés avec Prism, hors ligne.
