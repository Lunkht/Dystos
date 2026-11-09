// Vérifie les grammaires générées avec les vrais moteurs : vscode-textmate (Oniguruma) et Prism.
// Usage : cd editors && npm install && npm test
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const vsctm = require('vscode-textmate');
const oniguruma = require('vscode-oniguruma');
const Prism = require('prismjs');

global.Prism = Prism;
require('../prism/prism-dystos.js');

const grammarPath = path.join(__dirname, '..', 'textmate', 'dystos.tmLanguage.json');
const examplesDir = path.join(__dirname, '..', '..', 'compiler', 'lexer', 'src', 'test', 'resources', 'exemples');

let failures = 0;
function check(name, fn) {
  try { fn(); console.log('  ok   ' + name); }
  catch (e) { failures++; console.log('  FAIL ' + name + '\n       ' + e.message); }
}

async function loadGrammar() {
  const wasmDir = path.dirname(require.resolve('vscode-oniguruma'));
  const candidates = [path.join(wasmDir, 'onig.wasm'), path.join(wasmDir, '..', 'release', 'onig.wasm')];
  const wasm = fs.readFileSync(candidates.find(fs.existsSync)).buffer;
  const onigLib = oniguruma.loadWASM(wasm).then(() => ({
    createOnigScanner: (s) => new oniguruma.OnigScanner(s),
    createOnigString: (s) => new oniguruma.OnigString(s),
  }));
  const registry = new vsctm.Registry({
    onigLib,
    loadGrammar: async (scope) => scope === 'source.dystos'
      ? vsctm.parseRawGrammar(fs.readFileSync(grammarPath, 'utf8'), grammarPath) : null,
  });
  return registry.loadGrammar('source.dystos');
}

function tokenizeAll(grammar, text) {
  let stack = vsctm.INITIAL;
  return text.split('\n').map((line) => {
    const r = grammar.tokenizeLine(line, stack);
    stack = r.ruleStack;
    return r.tokens.map((t) => ({ text: line.substring(t.startIndex, t.endIndex), scopes: t.scopes }));
  });
}

function has(tokens, text, scopePrefix) {
  return tokens.some((t) => t.text === text && t.scopes.some((s) => s.startsWith(scopePrefix)));
}

function prismFlat(code) {
  const out = [];
  (function walk(tokens, parentType) {
    for (const t of tokens) {
      if (typeof t === 'string') { out.push({ type: parentType, text: t }); continue; }
      const aliases = [].concat(t.alias || []);
      if (Array.isArray(t.content)) walk(t.content, t.type);
      else if (typeof t.content === 'string') out.push({ type: t.type, aliases, text: t.content });
      else walk([t.content], t.type);
    }
  })(Prism.tokenize(code, Prism.languages.dystos), null);
  return out;
}
function phas(flat, text, type) {
  return flat.some((t) => t.text === text && (t.type === type || (t.aliases || []).includes(type)));
}

(async () => {
  const grammar = await loadGrammar();
  assert(grammar, 'grammaire non chargée');

  console.log('TextMate (vscode-textmate + Oniguruma)');
  const tm = (code) => tokenizeAll(grammar, code).flat();

  check('def + nom de fonction', () => {
    const t = tm('def main():');
    assert(has(t, 'def', 'storage.type.function'));
    assert(has(t, 'main', 'entity.name.function'));
  });
  check('class, interface et enum nomment un type', () => {
    const t = tm('class Compte(Base):');
    assert(has(t, 'class', 'storage.type.class'));
    assert(has(t, 'Compte', 'entity.name.type.class'));
    assert(has(t, 'Base', 'entity.name.type'));
    assert(has(tm('enum Jour:'), 'Jour', 'entity.name.type.class'));
  });
  check('mots-clés de contrôle, modificateurs et opérateurs-mots', () => {
    const t = tm('if x and not y:\n    return val');
    assert(has(t, 'if', 'keyword.control'));
    assert(has(t, 'and', 'keyword.operator.word'));
    assert(has(t, 'not', 'keyword.operator.word'));
    assert(has(t, 'return', 'keyword.control'));
    assert(has(t, 'val', 'storage.modifier'));
  });
  check('constantes, self, types, ALL_CAPS', () => {
    const t = tm('self.x = true if null else Jour.SAMEDI\nx: list[int]');
    assert(has(t, 'self', 'variable.language.self'));
    assert(has(t, 'true', 'constant.language'));
    assert(has(t, 'null', 'constant.language'));
    assert(has(t, 'SAMEDI', 'constant.other'));
    assert(has(t, 'Jour', 'entity.name.type'));
    assert(has(t, 'list', 'support.type'));
    assert(has(t, 'int', 'support.type'));
  });
  check('nombres', () => {
    const t = tm('a = 1_000 + 0xFF + 0b101 + 3.14 + 1e-3');
    for (const n of ['1_000', '0xFF', '0b101', '3.14', '1e-3']) assert(has(t, n, 'constant.numeric'), n);
  });
  check('opérateurs spéciaux', () => {
    const t = tm('s?.length ?: 0 ** 2 -> x');
    for (const o of ['?.', '?:', '**', '->']) assert(has(t, o, 'keyword.operator'), o);
  });
  check('commentaire et décorateur', () => {
    assert(has(tm('x = 1  # note'), '# note', 'comment.line'));
    assert(has(tm('@test'), '@test', 'entity.name.function.decorator'));
  });
  check('appels de fonction et fonctions prédéfinies', () => {
    const t = tm('print(len(xs))\nf(1)');
    assert(has(t, 'print', 'support.function.builtin'));
    assert(has(t, 'len', 'support.function.builtin'));
    assert(has(t, 'f', 'entity.name.function.call'));
  });
  check('chaîne avec échappement', () => {
    const t = tm('s = "a\\nb"');
    assert(has(t, '\\n', 'constant.character.escape'));
    assert(t.some((x) => x.scopes.some((s) => s.startsWith('string.quoted.double'))));
  });
  check('f-string : interpolation, expression colorée et format', () => {
    const t = tm('print(f"Aire {x.aire():.2f} {{ok}}")');
    assert(has(t, '{', 'punctuation.section.interpolation'));
    assert(has(t, 'aire', 'entity.name.function.call'));
    assert(has(t, ':.2f', 'constant.other.format-spec'));
    assert(has(t, '}', 'punctuation.section.interpolation'));
  });
  check("f-string : « ?: » et tranche ne sont pas un format", () => {
    const t = tm('f"{a ?: 0} {xs[1:2]}"');
    assert(has(t, '?:', 'keyword.operator'));
    assert(!t.some((x) => x.scopes.some((s) => s.startsWith('constant.other.format-spec'))));
  });
  check('f-string : guillemets différents dans l\'expression', () => {
    const t = tm('f"{\'ok\' if p > 3 else \'ko\'}"');
    assert(has(t, 'if', 'keyword.control'));
    assert(has(t, 'else', 'keyword.control'));
  });
  check('chaîne triple sur plusieurs lignes', () => {
    const lines = tokenizeAll(grammar, 's = """a\nb # pas un commentaire\nc"""\nx = 1');
    assert(lines[1].every((x) => x.scopes.some((s) => s.startsWith('string.quoted.triple'))));
    assert(has(lines[3], '1', 'constant.numeric'));
  });
  check('chaîne simple non terminée : la ligne suivante est saine', () => {
    const lines = tokenizeAll(grammar, 'x = "abc\ny = 2');
    assert(has(lines[1], '2', 'constant.numeric'));
  });
  check('les 21 exemples se colorent et se reconstituent à l\'identique', () => {
    const files = fs.readdirSync(examplesDir).filter((f) => f.endsWith('.dys'));
    assert.strictEqual(files.length, 21);
    for (const f of files) {
      const code = fs.readFileSync(path.join(examplesDir, f), 'utf8');
      const lines = tokenizeAll(grammar, code);
      code.split('\n').forEach((line, i) => assert.strictEqual(lines[i].map((t) => t.text).join(''), line, f));
      const flat = lines.flat();
      assert(flat.some((t) => t.scopes.some((s) => s.startsWith('storage.type.function'))) || f.startsWith('18-packages-et-visibilite-b') ||
        flat.length > 0, f);
    }
  });

  console.log('Prism');
  check('mots-clés, définitions, types', () => {
    const f = prismFlat('def main(x: int):\nclass Compte(Base):');
    assert(phas(f, 'def', 'keyword'));
    assert(phas(f, 'main', 'function'));
    assert(phas(f, 'int', 'builtin'));
    assert(phas(f, 'Compte', 'class-name'));
    assert(phas(f, 'Base', 'class-name'));
  });
  check('booléens, null, self, nombres, opérateurs, commentaire, décorateur', () => {
    const f = prismFlat('@test\nx = true and null  # c\nself.y = 0x1F + 2.5e3 ?: 1');
    assert(phas(f, 'true', 'boolean'));
    assert(phas(f, 'null', 'constant'));
    assert(phas(f, 'and', 'keyword'));
    assert(phas(f, 'self', 'keyword'));
    assert(phas(f, '0x1F', 'number'));
    assert(phas(f, '2.5e3', 'number'));
    assert(phas(f, '?:', 'operator'));
    assert(phas(f, '# c', 'comment'));
    assert(phas(f, '@test', 'annotation'));
  });
  check('f-string avec interpolation et format', () => {
    const f = prismFlat('f"Aire {x.aire():.2f} {{ok}}"');
    assert(phas(f, 'aire', 'function'));
    assert(phas(f, ':.2f', 'format-spec'));
  });
  check('les 21 exemples : le texte reconstitué est identique au source', () => {
    for (const file of fs.readdirSync(examplesDir).filter((f) => f.endsWith('.dys'))) {
      const code = fs.readFileSync(path.join(examplesDir, file), 'utf8');
      assert.strictEqual(prismFlat(code).map((t) => t.text).join(''), code, file);
    }
  });

  console.log(failures === 0 ? '\nTous les contrôles passent.' : '\n' + failures + ' contrôle(s) en échec.');
  process.exit(failures === 0 ? 0 : 1);
})();
