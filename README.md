# Local Skills

A private, offline Android app where users create, run, and share local AI skills — small declarative workflows that take input, run an on-device model, return structured output, and trigger reminders.

**Core principle:** No skill is built into the app. The app ships the *engine*; skills are user-authored data.

See [PROJECT_PLAN.md](./PROJECT_PLAN.md) for the full design rationale.

## What it does

- **Skill Builder** — author a new skill from a form (metadata, inputs, instruction, output fields, regex patterns, rules, limits) and test it on a sample input before saving.
- **Skill Runner** — pick an installed skill, give it text or an image, get a schema-validated structured result with a deterministic confidence score.
- **Skill Library** — list, enable, share, and delete installed skills. Seed manifests ship in `assets/seeds/` and install on first launch.
- **Share / Import** — export any skill as `AOSL-SKILL/1` text or a `.skill.json` file via the Android share sheet. Imported skills are linted, sandbox-dry-run, and installed disabled until you review them.
- **Rules + Reminders** — declarative expressions (`expiry_date is in 3 days`, `amount > 5000`, `sum(... where ...) > 20000`) sweep daily via WorkManager and post deduped, rate-limited notifications.

## What it doesn't do (deliberately)

- Read SMS, call logs, accessibility events, or notifications.
- Run arbitrary code from manifests — skills are pure declarative data.
- Send anything off-device. No accounts, no cloud, no telemetry of user content.
- Auto-extract on shared content. The user always picks which skill consumes a given input.

## Architecture

```
LocalSkillsApp ─┬─ NotificationChannels.ensureRegistered()
                └─ RuleScheduler.scheduleAll()  → WorkManager periodic RuleSweepWorker

MainActivity → AppNavHost
                ├─ library     → LibraryScreen + SkillSeeder
                ├─ runner/{id} → RunSkillScreen → SkillRunner → ReviewScreen
                ├─ builder     → BuilderScreen + SkillTestHarness + Templates
                └─ export/{id} → ExportSkillScreen → SkillExporter

ShareReceiverActivity → PendingShareHolder → MainActivity (library picker)
ImportReceiverActivity → SkillImporter ─┬─ SkillLinter (allowlist + safety)
                                         ├─ SkillSandbox (dry-run)
                                         └─ SkillInstaller → SkillRepository (IMPORTED, disabled)

Rules: RuleEntity → RuleSweepWorker → RuleEvaluator → DefaultRuleEngine
                                                    → RuleNotifier (DataStore dedupe + 3/day rate-limit)
```

## Stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin + Jetpack Compose |
| DB | Room |
| Settings / registry | DataStore |
| Background | WorkManager |
| OCR | ML Kit Text Recognition v2 (Latin + Devanagari) |
| Local LLM runtime | Pluggable `ExtractorRuntime`; `EchoRuntime` ships as default placeholder |
| DI | Hilt |
| Distribution | App Bundle + Play Feature Delivery (model packs land later) |

## Building

Requires Android Studio Ladybug (or newer) with the Android SDK + JDK 17.

The wrapper jar (`gradle/wrapper/gradle-wrapper.jar`) is intentionally not committed. Generate it once before the first build:

```bash
# Either let Android Studio do it on first project open, or run:
gradle wrapper --gradle-version 8.9
```

Then:

```bash
./gradlew :app:assembleDebug
./gradlew :app:test
```

Min SDK is 26, target SDK is 34. Models are excluded from VCS — see `.gitignore`.

## Project layout

```
app/
├── src/main/
│   ├── AndroidManifest.xml
│   ├── assets/seeds/                  # bundled starter skills
│   ├── kotlin/com/localskills/app/
│   │   ├── LocalSkillsApp.kt          # Application; channel + scheduler bootstrap
│   │   ├── data/
│   │   │   ├── db/                    # Room: AppDatabase, entities, DAOs
│   │   │   └── repo/                  # SkillRepository, RunRepository
│   │   ├── di/                        # Hilt modules
│   │   ├── engine/
│   │   │   ├── builder/               # ManifestDraft, DraftToManifest, SkillTestHarness
│   │   │   ├── confidence/            # ConfidenceEngine
│   │   │   ├── ocr/                   # OcrEngine + MlKitOcrEngine
│   │   │   ├── rules/                 # Lexer, Parser, AST, Interpreter, Scheduler, Evaluator
│   │   │   ├── runner/                # SkillRunner, SkillSeeder
│   │   │   ├── runtime/               # ExtractorRuntime contract + EchoRuntime
│   │   │   └── validation/            # SchemaValidator
│   │   ├── notifications/             # RuleNotifier, NotificationChannels
│   │   ├── skill/manifest/            # SkillManifest + ManifestCodec (AOSL-SKILL/1)
│   │   ├── skill/share/               # Exporter, Importer, Linter, Sandbox, Installer
│   │   ├── ui/
│   │   │   ├── AppNavHost.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── builder/               # form sections, ViewModel, templates
│   │   │   ├── imports/               # ImportReceiverActivity + screens
│   │   │   ├── library/
│   │   │   ├── runner/
│   │   │   └── share/
│   │   └── work/                      # RuleSweepWorker
│   └── res/
└── src/test/                          # JVM unit tests (manifest, parser, validator, etc.)
```

## Privacy

All processing happens on-device. Raw text, images, and OCR output never leave the phone. See [PRIVACY.md](./PRIVACY.md).

## Status

MVP is feature-complete on `claude/create-project-plan-vuC3j`. Remaining work before public release:

- Swap `EchoRuntime` for a real on-device LLM (LiteRT-LM or MediaPipe LLM Inference) delivered as an optional Play Feature module.
- Run `assembleDebug` on a developer machine; the codebase was authored without the Android SDK available, so first compile may need minor fixes.
- Generate Baseline Profiles.
- Write the Play Store privacy policy + Data Safety form.
