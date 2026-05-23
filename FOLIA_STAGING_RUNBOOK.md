# Folia Staging Runbook (Phase 12)

Use this runbook to execute **real** Folia staging validation. Phase 12 is runtime validation — not
another migration sprint. The goal is to learn exactly what breaks under concurrent region load, not to
declare production readiness.

`folia-supported` is **enabled in plugin metadata for staging load only** — this is not a production
readiness claim. See REALFACTIONS_AUDIT.md Phase 12.

---

## 1. Environment record (fill before boot)

Copy this block into your staging notes and complete every field before starting the server.

```yaml
# --- Folia staging environment ---
date:
operator:
git_commit:                    # e.g. 477dffcb
jar_path:                      # factions-plugin/target/RealFactions.jar
jar_sha256:                    # shasum -a 256 RealFactions.jar

server:
  type: Folia
  jar_version:                 # e.g. folia-1.21.x
  java_version:                # java -version

plugins:
  required:
    - Vault
    - (economy provider name + version)
  optional:
    - PlaceholderAPI
    - dynmap
  excluded_for_baseline:       # RealCore, RealVoteBridge, website integrations

world:
  name:                          # fresh flat/overworld test world recommended
  pregenerated: true/false
  reset_between_runs: true/false

realfactions_config:
  validation-diagnostics: true          # required for staging metrics
  folia-strict-mode: true               # required for staging safety defaults
  allow-dynmap-on-folia: false          # baseline: dynmap off unless opt-in pass
  legacy-compatibility-mode: false

# plugin.yml declares folia-supported: true so Folia will load this jar for staging.
# That flag does NOT mean production-ready — see startup warnings in console.

economy:
  provider:
  vault_detected: true/false
  known_folia_safe: false

dynmap:
  installed: true/false
  Conf.dynmapUse_at_boot: true/false
  allow-dynmap-on-folia: false

test_accounts:
  - player1 (region A)
  - player2 (region B)
  - player3 (region C)
  - player4 (region D)
```

### Pre-flight gate

```bash
mvn -q test
mvn -q clean package
```

Audit must report all backlogs **0** and raw scheduler **5 / 3 files**.

---

## 2. Boot validation

1. Start Folia server with fresh world.
2. Confirm console:
   - `Scheduler mode: Folia` (or equivalent)
   - `[RealFactions] Running on Folia — production readiness is NOT guaranteed`
   - `[RealFactions] RealFactions is running on Folia with staging validation enabled...` (when diagnostics on)
   - `[RealFactions] validation-diagnostics enabled` and staging session banner
3. With strict mode and no dynmap opt-in: expect Dynmap disabled warning.
4. Run `/f debug` — counters at zero.

**Pass:** No thread assertion in first 60 seconds.

---

## 3. Runtime test matrix

| ID | Test | Pass criteria |
|----|------|---------------|
| T1 | `/f create` | Factions created in distant regions |
| T2 | `/f claim` | Board correct; peak pending settles ≤ 5 within 30s |
| T3 | `/f unclaim` | Refunds if economy enabled |
| T4 | `/f unclaimall` | All chunks freed |
| T5 | `/f disband` | Teardown; payout or strict-mode skip |
| T6 | join/leave/kick | Membership correct |
| T7 | promote/demote | Persists after restart |
| T8 | sethome/home | Teleport from distant region |
| T9 | warps/checkpoint | Works |
| T10 | flight | Cross-region; no thread errors |
| T11 | bank | Balances or strict-mode skips |
| T12 | concurrency burst | 4 players distant; queue → ~0; blocked wait max < 100ms routine |
| T13 | restart | Data identical after restart |
| T14 | soak 4–24h | No queue creep; memory stable |

Capture `/f debug` after T12.

---

## 4. Vault / provider matrix

| Run | foliaStrictMode | Expected |
|-----|-----------------|----------|
| A | true | Costs skipped; strict-mode skips increment; warnings; vault errors = 0 |
| B | false | Vault executes; watch for thread violations |

---

## 5. Dynmap evaluation

**Baseline:** `allow-dynmap-on-folia: false` — expect auto-disable at boot.

**Opt-in experiment:** `allow-dynmap-on-folia: true` — observe thread assertions and marker drift.

---

## 6. Scheduler backlog

| Site | Staging action |
|------|----------------|
| FactionsPlugin startup ×2 | Note if enable throws |
| Metrics ×1 | Ignore unless assertion |
| EngineDynmap ×2 | Only runs with opt-in |

Do not migrate unless staging proves they throw.

---

## 7. Failure signal log

| Timestamp | Signal | Test ID | Severity |
|-----------|--------|---------|----------|
| | | | |

---

## 8. Soak protocol

Every 30 min: `/f debug` snapshot. Every 2h: claim/unclaim/disband per player. Mid-soak restart.
Compare first vs last debug output.

---

## 9. Results template

```markdown
## Phase 12 staging results — [DATE]

### Environment
- Commit / Folia / Java / Provider / Dynmap config

### Runtime issues found
-

### Diagnostics observed
- Peak pendingModelWrites:
- Blocked wait max ms:
- Vault errors:

### Dynmap findings
-

### Scheduler backlog caused problems?
-

### New blockers
-

### Verdict
- Folia production: STILL BLOCKED because ...
```

---

## 10. Current status

**No Folia server execution recorded in-repo yet.** Execute this runbook on a real Folia host and fill §9.
