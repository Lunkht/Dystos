// Génère demo/index.html : quelques exemples colorés avec Prism, sans aucune ressource externe.
// Usage : cd editors && npm install && node demo/build-demo.js
const fs = require('fs');
const path = require('path');
const Prism = require('prismjs');
global.Prism = Prism;
require('../prism/prism-dystos.js');

const dir = path.join(__dirname, '..', '..', 'compiler', 'lexer', 'src', 'test', 'resources', 'exemples');
const picks = ['07-', '08-', '11-', '13-', '20-'];
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
const blocks = picks.map((p) => {
  const f = fs.readdirSync(dir).filter((x) => x.startsWith(p) && x.endsWith('.dys')).sort()[0];
  const code = fs.readFileSync(path.join(dir, f), 'utf8');
  return `<h2>${esc(f)}</h2>\n<pre><code>${Prism.highlight(code, Prism.languages.dystos, 'dystos')}</code></pre>`;
}).join('\n');

const css = `
:root{color-scheme:dark}
body{background:#0f1117;color:#d7dae0;font:16px/1.5 system-ui,sans-serif;max-width:860px;margin:2rem auto;padding:0 1rem}
h1{font-size:1.4rem} h2{font-size:1rem;color:#8b93a7;font-weight:500;margin-top:2rem}
pre{background:#161922;border:1px solid #262b38;border-radius:8px;padding:1rem;overflow:auto;font:14px/1.55 ui-monospace,Consolas,monospace}
.token.comment{color:#6b7280;font-style:italic}
.token.string,.token.fstring-text,.token.escape{color:#a5d6a7}
.token.escape{color:#f9a8d4}
.token.number,.token.format-spec{color:#fbbf77}
.token.keyword,.token.boolean{color:#c792ea}
.token.constant{color:#f78c6c}
.token.builtin{color:#82aaff}
.token.function,.token.function-definition{color:#7fdbca}
.token.class-name,.token.class-definition{color:#ffcb6b}
.token.annotation{color:#f9a8d4}
.token.operator{color:#89ddff}
.token.punctuation{color:#9aa3b8}
.token.interpolation>.token.punctuation{color:#f78c6c}`;
fs.writeFileSync(path.join(__dirname, 'index.html'),
  `<!doctype html><html lang="fr"><meta charset="utf-8"><title>Dystos — coloration</title><style>${css}</style>\n<h1>Dystos — aperçu de la coloration (Prism)</h1>\n${blocks}\n</html>\n`);
console.log('demo/index.html généré');
