# Dystos

Langage de programmation qui mêle la lisibilité de Python et la rigueur de Java, exécuté sur la JVM
(interpréteur ou **compilation en bytecode** vers un `.jar` autonome).
Aucune dépendance à un service tiers ; fonctionne hors ligne.

```
class Compte:
    titulaire: str
    _solde: float = 0.0

    def init(self, titulaire: str):
        self.titulaire = titulaire

    def deposer(self, montant: float):
        self._solde += montant

    def toString(self) -> str:
        return f"Compte({self.titulaire}, {self._solde})"

def main():
    c = Compte("Awa")
    c.deposer(150.0)
    print(c)          # Compte(Awa, 150.0)
```

## Structure

- `compiler/` — compilateur Dystos : lexer, parser, analyse sémantique, interpréteur, runtime, backend JVM
- `services/playground/` — service HTTP du playground (`analyze`, `run`)
- `editors/` — grammaires TextMate / Prism, extension VS Code
- `content/` — contenu d'apprentissage (Markdown + exercices)
- `site/` — site web d'apprentissage (React/Docusaurus + TypeScript)
- `apps/android/` — application Android
- `tools/` — outillage : génération des grammaires, build du contenu
- `benchmarks/` — mesures interpréteur / bytecode
- `scripts/` — build, lanceur, exemples, CI locale
- `deploy/` — configuration de déploiement
- `docs/` — documentation

## Démarrage rapide (Linux / WSL2, JDK 21)

```bash
scripts/build.sh                                  # compile → build/dystos.jar
scripts/dystos run  compiler/interp/src/test/resources/exemples/08-classes-et-constructeurs.dys
scripts/dystos test compiler/interp/src/test/resources/exemples/19-tests-integres.dys
scripts/dystos build compiler/interp/src/test/resources/exemples/08-classes-et-constructeurs.dys -o compte.jar
java -jar compte.jar                              # bytecode JVM autonome
scripts/run-examples.sh                           # les 20 exemples
mvn test                                          # 281 tests (racine)
scripts/playground                                # service HTTP : curl localhost:8080/api/v1/health
scripts/test-editors.sh                           # grammaires de coloration (Node.js)
```

## Démarrage rapide (Windows natif, PowerShell, JDK 21)

```powershell
scripts\build.ps1                                 # compile → build\dystos.jar
scripts\dystos.ps1 run  compiler\interp\src\test\resources\exemples\08-classes-et-constructeurs.dys
scripts\dystos.ps1 build compiler\interp\src\test\resources\exemples\08-classes-et-constructeurs.dys -o compte.jar
scripts\run-examples.ps1                          # les 20 exemples et leurs codes de sortie
mvn test                                          # 281 tests (si Maven est installé)
```

Les scripts PowerShell trouvent seuls le JDK 21 (`JAVA_HOME`, Eclipse Adoptium, puis `PATH`) : un `java` 8 sur le `PATH` n'est pas utilisé.

## Documentation

- **[docs/GUIDE-PROJET.md](docs/GUIDE-PROJET.md)** : état d'avancement, architecture, décisions, feuille de route pas à pas.
- `docs/02-phase0-exemples-et-syntaxe.md` : les 20 exemples et la syntaxe.
- `docs/03-plan-site-et-application-android.md` : plan du site d'apprentissage et de l'application Android.
- `compiler/*/README.md` : un guide par module (lexer, parser, sema, interp, runtime, jvm).
- `services/playground/README.md` : API HTTP `analyze` / `run`, sécurité, déploiement.
- `editors/README.md` : coloration (TextMate, Prism, VS Code, Android).

Dépôt : https://github.com/Lunkht/Dystos.git
