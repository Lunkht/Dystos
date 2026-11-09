# dystos-playground (service HTTP v0)

Service d'analyse et d'exécution de code Dystos pour le site (éditeur en ligne, blocs de code exécutables)
et pour tout client HTTP. **Aucune dépendance** : serveur `com.sun.net.httpserver` du JDK, lecteur JSON maison.

## Démarrage

```bash
scripts/playground                              # 127.0.0.1:8080, mode « inprocess »
scripts/playground --port 9000 --mode process   # une JVM fille jetable par exécution
scripts/playground --help
```

| Option | Défaut | Rôle |
|---|---|---|
| `--bind` | `127.0.0.1` | adresse d'écoute (n'exposez pas le service directement : mettez un proxy devant) |
| `--port` | `8080` | port (0 = au hasard) |
| `--mode` | `inprocess` | `inprocess` ou `process` (recommandé en production) |
| `--max-concurrent` | `4` | exécutions simultanées (au-delà : 429) |
| `--rate-run` / `--rate-analyze` | `20` / `120` | requêtes par minute et par client |
| `--cors-origin` | aucun | origine autorisée (ex. `https://dystos.exemple.org`) |
| `--trust-proxy` | non | lire l'IP du client dans `X-Forwarded-For` (seulement derrière **votre** proxy) |
| `--worker-mem` | `96` | Mo de mémoire du processus fils (mode `process`) |
| `--timeout-ms` / `--max-steps` | `5000` / `20000000` | limites d'une exécution |

## API

Tous les corps sont du JSON UTF-8 (`Content-Type: application/json`). Les erreurs de requête ont la forme
`{"error": "message"}`.

### `GET /api/v1/health`
`{"status":"ok","version":"0.1.0"}`

### `GET /api/v1/limits`
Limites actives (pour les afficher dans l'interface) : `maxBodyBytes`, `maxSteps`, `timeoutMs`, `maxOutputChars`,
`maxDepth`, `maxConcurrentRuns`, `rateRunPerMinute`, `rateAnalyzePerMinute`, `filesEnabled`, `javaPrefixes`, `mode`.

### `POST /api/v1/analyze`
Analyse sans exécuter.

```json
{ "code": "def main():\n    print(1)\n", "include": ["tokens", "ast", "highlight"] }
```

`include` est facultatif (valeurs : `tokens`, `ast`, `highlight`). Réponse :

```json
{
  "ok": true,
  "diagnostics": [ {"severity":"error","code":"E0201","message":"…","line":6,"column":14} ],
  "tokens":    [ {"type":"DEF","text":"def","line":1,"column":1}, … ],
  "ast":       "SourceFile @1:1\n  decls:\n    - FuncDecl @1:1 …",
  "highlight": [ {"start":0,"end":3,"kind":"keyword"}, … ]
}
```
- `ok` vaut `false` s'il y a au moins une erreur (les avertissements ne comptent pas).
- Les erreurs lexicales (`E0001`) et syntaxiques (`E0002`) sont des diagnostics comme les autres.
- `tokens` vaut `[]` si le lexer échoue ; `ast` vaut `null` si le parser échoue.
- `highlight` : indices de caractères UTF-16 (directement utilisables en JavaScript et avec `Spannable` Android) ;
  `kind` ∈ `keyword, constant, type, function, builtin, self, number, string, escape, comment, decorator, operator, punctuation`.
  Le colorateur tolère le code incomplet : il fonctionne même quand `ok` est `false`.

### `POST /api/v1/run`
Analyse puis exécute (mode sandbox).

```json
{ "code": "print(\"Bonjour\")" }
```
Réponse (format de `Runner.Result.toJson()`) :
```json
{"stdout":"Bonjour\n","stderr":"","exitCode":0,"durationMs":12,"diagnostics":[]}
```
Codes de sortie : `0` succès · `1` exception non rattrapée · `2` erreurs de compilation (rien n'est exécuté,
voir `diagnostics`) · `3` limite dépassée (instructions, durée, sortie, mémoire) · `4` erreur interne.
La requête elle-même répond `200` dans tous ces cas.

### Statuts HTTP

| Statut | Cas |
|---|---|
| 200 | analyse ou exécution effectuée |
| 400 | JSON invalide, `code` absent ou mal typé, `include` invalide |
| 404 / 405 / 415 | route inconnue / méthode non autorisée (en-tête `Allow`) / `Content-Type` ≠ JSON |
| 413 | corps de plus de 20 000 octets |
| 422 | code trop profondément imbriqué pour être analysé |
| 429 | trop de requêtes ou serveur occupé (en-tête `Retry-After`) |

### Exemples `curl`
```bash
curl -s localhost:8080/api/v1/health
curl -s -X POST localhost:8080/api/v1/analyze -H 'Content-Type: application/json' \
     -d '{"code":"x: str = null\n","include":["highlight"]}'
curl -s -X POST localhost:8080/api/v1/run -H 'Content-Type: application/json' \
     -d '{"code":"print(f\"{2 ** 10:,}\")"}'
```

## Sécurité : ce qui est fait, ce qui reste à votre charge

**Fait par le service**
- Exécution en mode `Runner.Options.sandbox()` : 20 M d'instructions, 5 s, 64 000 caractères de sortie, récursion
  bornée, fichiers désactivés, classes Java restreintes (`java.util`, `java.time`, `java.math`, `java.text`, quelques
  `java.lang`) ; `Runtime`, `System`, `Thread`, `java.io`, `java.net`, `java.nio`, `java.util.concurrent`… sont refusés
  **dès l'analyse** (`E0105`), y compris dans `/analyze`.
- Taille du corps bornée, `Content-Type` imposé, limitation de débit par client, plafond d'exécutions simultanées.
- Mode `process` : JVM fille jetable, **environnement vide**, mémoire plafonnée (`-Xmx`), arrêt forcé à l'expiration,
  sortie lue avec une taille maximale. Une boucle bloquée ou un manque de mémoire ne touche pas le serveur
  (vérifié par les tests).
- Écoute sur `127.0.0.1` par défaut ; journal sans contenu de programme.

**À votre charge pour un service public**
1. **Conteneur** : `deploy/docker-compose.yml` (lecture seule, `cap_drop ALL`, `no-new-privileges`, limites de mémoire,
   CPU et processus, réseau interne **sans accès sortant**). *Non testé dans l'environnement de développement.*
2. **Proxy inverse** avec HTTPS (le `Caddyfile` fourni est un point de départ) et limite de taille de corps.
3. **`--trust-proxy` uniquement derrière votre proxy** (sinon l'en-tête `X-Forwarded-For` peut être falsifié).
4. **Images Docker** : `eclipse-temurin` et `caddy` viennent de Docker Hub ; pour rester souverain, recopiez-les dans
   un registre à vous.
5. Aucune authentification n'est incluse : si le service n'est pas public, restreignez-le par le réseau.

> Le sandbox borne les ressources mais n'est pas une isolation complète. Considérez tout code soumis comme hostile.

## Intégration dans le site (Docusaurus / React)

```js
const r = await fetch(`${API}/api/v1/analyze`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ code, include: ['highlight'] }),
});
const { ok, diagnostics, highlight } = await r.json();
// diagnostics[i].line / .column (à partir de 1) → soulignement dans l'éditeur
```
Pour de l'analyse « à la frappe », attendez ~300 ms après la dernière touche (le débit est limité à 120/min par client).

## Tests
`mvn test` (module `services/playground`) : 26 tests avec de vraies requêtes HTTP, y compris le mode `process`.
