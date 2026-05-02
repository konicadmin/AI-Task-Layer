# Local Skills — Project Plan

A private, offline Android app where users create, run, and share local AI skills — small declarative workflows that take input, run a local model, return structured output, and trigger reminders.

**Core principle:** No skill is built into the app. The app ships the *engine*; skills are user-authored data.

## Architecture

```
Local Skills App
├── Skill Builder          // create/edit skill manifests
├── Skill Library          // installed skills, enable/disable
├── Skill Runner           // input → model → schema → output
├── Local Model Runtime    // LiteRT-LM / MediaPipe (Gemma 3 1B default)
├── Local Database         // Room: skills, runs, results, rules
├── Rule / Reminder Engine // declarative conditions + WorkManager
└── Share / Import System  // .skill.json + share sheet + lint sandbox
```

## Skill manifest (canonical)

```json
{
  "manifest_version": "skill/1",
  "id": "coupon-extractor.v1",
  "name": "Coupon Extractor",
  "description": "Extracts coupon code, offer, brand, expiry",
  "inputs": ["image", "text"],
  "instruction": "Extract coupon code, offer, brand, expiry date",
  "output_schema": {
    "brand": "text",
    "coupon_code": "text",
    "offer": "text",
    "expiry_date": "date"
  },
  "rules": [
    { "condition": "expiry_date is in 3 days", "action": "notify" }
  ],
  "limits": { "max_input_chars": 12000, "max_runtime_ms": 4000 }
}
```

The same engine runs Coupon, Expense, Bill Reminder, Notes Summarizer, Recipe Extractor, Invoice Parser, Meeting Notes, etc. — without code changes.

## MVP scope

1. **Skill Builder** — name, description, input type (text/image/file), instruction, output fields (typed), rules.
2. **Skill Runner** — pick skill → provide input → run local model → show structured result + edit before save.
3. **Local Skill Library** — list, enable/disable, version, delete.
4. **Share / Import** — export `.skill.json` or plain text via Android Sharesheet; import with lint, dry-run, review-before-install.
5. **Rule Engine** — declarative conditions (`expiry_date in 3 days`, `amount > 20000`, `due_date is tomorrow`, weekly cadence) → local notifications via WorkManager.
6. **Sample skills bundled as data** (not code): Coupon Extractor, Expense Parser, Bill Reminder, Notes Summarizer — shipped as seed manifests in assets, deletable.

## Out of v1

- Auto-reading SMS / WhatsApp / notifications
- Full OS automation, accessibility-driven control
- Background app/screen monitoring
- Arbitrary code in skills (Kotlin/JS/eval)
- Cloud sync, accounts, skill marketplace
- Telemetry of user content

## Phased delivery

| Phase | Duration | Deliverables |
|---|---|---|
| **P0 Foundation** | 2 wk | Kotlin/Compose scaffold, Room schema (`Skill`, `Run`, `Result`, `Rule`, `Correction`), DataStore registry, model runtime wrapper, OCR wrapper |
| **P1 Runner** | 2 wk | Skill Runner, input intake (paste/share/image), output schema validator, edit-before-save UI |
| **P2 Builder** | 2 wk | Skill Builder UI (form-driven), schema field types, sample-input test harness, JSON preview |
| **P3 Share** | 1 wk | `.skill.json` export/import, sharesheet flow, importer lint + sandbox, install-as-disabled |
| **P4 Rules** | 2 wk | Rule DSL parser, WorkManager scheduler, notification dedupe + rate limits |
| **P5 Polish** | 2 wk | Seed skills, model-pack downloader (Play Feature Delivery), Baseline Profiles, privacy policy, Play submission |

**Total MVP: ~11 weeks.**

## Stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin + Jetpack Compose |
| DB | Room |
| Settings / registry | DataStore |
| Background | WorkManager |
| OCR (image input) | ML Kit Text Recognition v2 |
| Local LLM runtime | LiteRT-LM (default) / MediaPipe LLM Inference |
| Default model | Gemma 3 1B `dynamic_int4` (on-demand pack) |
| Storage | App-specific internal + Keystore |
| Distribution | App Bundle + Play Feature Delivery |

## Top risks & mitigations

1. **User-authored skills as security surface** → declarative manifests only (no code), resource limits, install-as-disabled, lint on import.
2. **Model size on mid-tier devices** → base APK ships no LLM; model pack downloads on demand; rules-only skills work without a model.
3. **Skill quality varies wildly between authors** → in-builder test harness with sample input + visible validation errors; confidence score shown on every run.

## Naming shortlist

SkillPad · Pocket Skills · Local Skills · Private Skills · TaskSkills · SkillBox

## First three tickets

1. Scaffold project: Kotlin + Compose + Hilt + Room + DataStore + WorkManager.
2. Define `Skill`, `Run`, `Result`, `Rule` entities + DAOs and `SkillManifest` Kotlin model with JSON (de)serialization.
3. Build minimal Skill Runner end-to-end with one hardcoded test manifest (text input → echo output) to prove the pipeline before adding the model.
