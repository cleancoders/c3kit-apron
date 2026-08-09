# Hand-rolled `c3kit.apron.log` (remove Timbre / SLF4J)

**Status:** Implemented (apron 3.1.0).

**Goal:** Reimplement `c3kit.apron.log` with the same public behavior, without depending on Timbre (or any Java logging framework). Drop `com.taoensso/timbre` and `org.slf4j/slf4j-nop` from apron’s main deps.

**Why:** Apron is a foundation library. Logging should not force Timbre, encore, truss, pretty, or SLF4J onto every consumer. Today the only code that needs those artifacts is `src/cljc/c3kit/apron/log.cljc` (plus `log_spec`).

**Non-goals:**

- Do **not** add Log4j, Logback, `tools.logging`, or another logging facade.
- Do **not** implement Timbre-compatible appenders, middleware, or SLF4J bridging.
- Do **not** change `table-spec` / `color-pr` behavior (unrelated helpers that live in the same ns).
- Do **not** require consumers to configure a backend; default output is stdout / JS console.

**Tech stack:** Clojure / ClojureScript / Babashka, Speclj, existing `deps.edn` / `bb.edn` runners.

**Baseline:** Work from current `master` of `cleancoders/c3kit-apron` (post-3.0.1). Confirm `VERSION` file and update `CHANGES.md` as part of the release task.

---

## Conventions for the implementing agent

- **TDD:** failing spec → minimal code → green → refactor.
- **Test runners** (from repo root):
  - JVM: `clj -M:test:spec`
  - CLJS: `clj -M:test:cljs once`
  - Babashka: `bb spec` (bb already requires `c3kit.apron.log` in `bb.edn` tasks)
- Prefer small commits with imperative subjects.
- Linked issue required before PR (see `CONTRIBUTING.md`).
- Do not commit unless the user asks; if implementing under direction to commit, exclude unrelated files.

---

## Current state (read this first)

### Dependencies tied to logging

From `deps.edn` (main `:deps`):

| Declared | Transitive today | Role |
|----------|------------------|------|
| `com.taoensso/timbre` 6.8.0 | encore, truss, pretty; upgrades tools.reader to 1.5.2 | Log implementation |
| `org.slf4j/slf4j-nop` 2.0.17 | slf4j-api | Silences SLF4J noise (comment in deps.edn) |

**There is no Log4j dependency today.** Removing Timbre + slf4j-nop is enough.

Jars that leave the main classpath when both are removed (approx.):

- `timbre`, `encore`, `truss`, `pretty`, `slf4j-nop`, `slf4j-api`
- tools.reader falls back to 1.4.0 via `tools.namespace` (still present)

### Public surface to preserve

Namespace: `c3kit.apron.log` (`src/cljc/c3kit/apron/log.cljc`)

| Symbol | Kind | Contract |
|--------|------|----------|
| `trace` `debug` `info` `warn` `error` `fatal` `report` | macros | Log at that level; args concatenated like Timbre (stringified and joined / printed as a message). Eligible only if call level ≥ configured min-level. **When the level check fails, the macro expansion must not evaluate args** (see **Lazy args**). The form’s value is `nil` when suppressed. |
| `off!` `fatal!` `error!` `warn!` `info!` `debug!` `all!` | fns | Set min-level (`off!` → `:report`, `all!` → `:trace`). |
| `level` | fn | Current min-level keyword. |
| `set-level!` | fn | Set min-level; when it **changes**, emit a `report` log `"Setting log level: …"`. |
| `with-level` | macro | Body runs with temporary min-level (dynamic). |
| `capture-logs` | macro | Clears capture buffer, forces min-level to `:trace` for the body, restores previous min-level after; body log calls are captured **without** printing to stdout. |
| `captured-logs` | atom | Buffer of captured entries (see **Capture format** below). |
| `parse-captured-logs` | fn | `({:level … :message …} …)` from buffer. |
| `captured-logs-str` | fn | Newline-joined message strings from buffer. |
| `time` | macro | Like `clojure.core/time` but `info`s `"Elapsed time: <n> msecs"` instead of printing. |
| `test-levels` | fn | Emits one message at each level (used for manual checks). |
| `table-spec` `color-pr` | fns | Keep as-is. |
| `-nanos` `-platform-time` | fns | Keep (used by `time`). |

CLJS: macros must remain available via `#?(:cljs (:require-macros …))` as today.

Babashka: `bb.edn` tasks call `(log/info …)` in `clean`. After the rewrite, **remove** Timbre-specific `#?(:bb …)` workarounds (`fatal`→`error`, `report`→`info`, `with-level` binding Timbre config) and implement one path that works on JVM, CLJS, and bb.

### Capture format (compatibility decision)

**Today (Timbre-shaped):** entries are vectors mimicking `timbre/-log!` args; `parse-captured-logs` / `captured-logs-str` read level at index 1 and deref `vargs_` at index 8.

**Specs that are Timbre-specific and must change:**

- `log_spec` requires `taoensso.timbre` and tests `timbre/-log!` arities 9–12 via `with-redefs`. **Delete that example** once Timbre is gone; replace with tests of the new capture path.

**Decision for this plan (implement this):**

1. **Public helpers stay stable:** `capture-logs`, `parse-captured-logs`, `captured-logs-str` keep their *documented* behavior (`:level` + `:message` maps; joined message strings).
2. **Internal buffer may change.** Store simple maps:

   ```clojure
   {:level :info :args ["hello"] :message "hello"}
   ```

   Or `{:level :info :message "hello"}` with args folded into `:message` at capture time.

3. Document in the ns docstring that **raw `@captured-logs` is not a stable Timbre tuple** — callers should use `parse-captured-logs` / `captured-logs-str`. This is an acceptable break for anyone who peeks at Timbre vectors; monorepo scan found no external peeks.

4. Update `parse-captured-logs` / `captured-logs-str` to read the new shape (do not keep index-8 Timbre layout).

### Level ordering (match Timbre)

Ascending severity / “more important”:

```text
:trace < :debug < :info < :warn < :error < :fatal < :report
```

A call at level `L` is emitted iff `L` is **≥** configured min-level in this order.

Defaults: match Timbre’s usual default of **`:info`** unless tests force otherwise. After `capture-logs` / level helpers, restore prior level.

`off!` → min-level `:report` (only `:report` logs).  
`all!` → min-level `:trace` (everything).

### Output behavior

- **JVM / bb:** write to `*out*` via `println` (or equivalent) when not capturing. One line per call; message = args stringified and joined with spaces (same idea as Timbre’s vargs).
- **CLJS:** prefer `js/console` methods by level (`debug`/`info`/`warn`/`error`) when available; otherwise `println`. Capturing must still suppress console noise (bind/redef the append path, not only `*out*`).
- Exceptions in args: stringifying with `str` is enough; no special stack-trace middleware required for v1.
- Do not depend on ANSI colors for normal logs (`color-pr` remains separate).

### Default min-level at load

Initialize config so that `(level)` returns `:info` unless the host previously changed it. Specs currently call `off!` in `after` and wrap with `capture-logs`; expand specs so default and filtering are explicit.

---

## Target design

### Config

Keep a single source of truth, platform-portable:

```clojure
(def ^:dynamic *config*
  {:min-level :info})
```

- `level` → `(:min-level *config*)`
- `set-level!` → swap/set config (atom + dynamic, or atom only). Prefer:
  - **atom** for global min-level (`set-level!`, `info!`, …)
  - **`with-level`** via `binding` of a dynamic that overrides the atom for the dynamic extent, **or** bind the whole config.

Recommended pattern:

```clojure
(defonce state (atom {:min-level :info}))
(def ^:dynamic *min-level-override* nil)

(defn level []
  (or *min-level-override* (:min-level @state)))
```

`with-level` binds `*min-level-override*`. `set-level!` resets the atom (and still `report`s on change comparing previous `level`).

### Emit path

Single internal function (name free; e.g. `-log` or `log*`):

```clojure
(defn- may-log? [lvl]
  (>= (level-rank lvl) (level-rank (level))))

(defn- format-args [args]
  (str/join " " (map str args)))

(defn- append! [lvl message]
  ;; if capturing → conj to captured-logs, no print
  ;; else → platform print
  )
```

### Lazy args (required)

**Requirement:** if the current min-level would suppress a call, **none of the macro’s arguments may be evaluated**, and the expansion should amount to a cheap level check that yields `nil`.

This is possible because the log forms are **macros**, not functions. A function always evaluates its args before entry; a macro can put args **inside** a runtime `when`:

```clojure
(defmacro info [& args]
  `(when (may-log? :info)
     (-emit! :info (list ~@args))))
```

What that means:

| Situation | What runs |
|-----------|-----------|
| min-level filters out `:info` | Only `(may-log? :info)` → false → **`nil`**. No arg eval, no format, no I/O. |
| min-level allows `:info` | Args evaluate once inside the `when`, then `-emit!` formats / prints or captures. |

**Not required (and not practical with a runtime min-level):** compile-time total elision (as if the call were never in the bytecode). You still pay for one `may-log?` lookup. That is enough for the usual win — expensive message construction in `debug`/`trace` stays free when those levels are off.

**Do not** use a plain function for the public API (`(defn info [& args] …)`), or args would always evaluate.

**`-emit!` may still call `may-log?` defensively**, but the **macro-level `when` is the contract** that protects callers.

**Spec (required):** with min-level `:error`, evaluate something like:

```clojure
(let [called? (atom false)]
  (info (do (reset! called? true) "x"))
  (should= false @called?))
```

Same idea for other suppressed levels. When the level *is* enabled, the arg form must run (and the message must appear in capture/output).

### Capture

```clojure
(def captured-logs (atom []))
(def ^:dynamic *capturing?* false)

(defmacro capture-logs [& body]
  `(let [prev# (level)]
     (reset! captured-logs [])
     (try
       (set-min-level-silent! :trace)   ;; silent = no "Setting log level" report
       (binding [*capturing?* true]
         ~@body)
       (finally
         (set-min-level-silent! prev#)))))
```

Important: current `set-level!` **reports** on change. `capture-logs` must not pollute the buffer with `"Setting log level: :trace"`. Implement a silent setter for capture/restore (private), and keep public `set-level!` reporting behavior.

### Remove Timbre-only code

- Delete all `taoensso.timbre` requires.
- Delete `capture-log!` multi-arity Timbre `-log!` shims.
- Delete Chrome blackbox comments that reference Timbre paths (replace with a short note about CLJS console levels if useful).
- Specs must not require Timbre.

### deps.edn

Remove:

```clojure
com.taoensso/timbre {...}
org.slf4j/slf4j-nop {...}
```

Verify with `clj -Stree` that encore/truss/pretty/slf4j are gone.

---

## Test plan (rewrite / expand `log_spec.cljc`)

Keep the file: `spec/cljc/c3kit/apron/log_spec.cljc`.

**Remove:**

- `taoensso.timbre` require
- `-log! arity overrides` example / `test-log-arity` macro

**Keep / rework:**

1. **level controls** — `debug!` / `off!` / `fatal!` / `all!` update `(level)` as today.
2. **capture-logs** — `info` inside capture produces empty stdout; parsed log has `:level :info` and message `"hello"`.
3. **time** — body still prints; captured string starts with `"Elapsed time:"` and ends with `" msecs"`.

**Add:**

4. **level filtering** — with min-level `:warn`, `info` does not capture/print; `warn` and `error` do.
5. **with-level** — temporary override does not permanently change global level.
6. **set-level! report** — changing level captures/prints `"Setting log level: …"` once; setting the same level again does not.
7. **off! / all!** — `off!` allows `report`; blocks `info`. `all!` allows `trace`.
8. **multi-arg message** — `(info "a" :b 1)` → message `"a :b 1"` (or document exact `str` rules and test them).
9. **capture isolation** — nested or sequential `capture-logs` resets buffer each time (define expected behavior: sequential reset is enough).
10. **lazy args (required)** — when level is filtered out, args are **not** evaluated (atom/side-effect probe stays false). When level is enabled, args **are** evaluated.

Run JVM + CLJS + bb after green.

---

## Implementation tasks

### Task 0: Branch and baseline

**Files:** none

- [ ] Create branch from up-to-date `master`, e.g. `hand-rolled-log`.
- [ ] Confirm green baseline:
  - `clj -M:test:spec`
  - `clj -M:test:cljs once`
  - `bb spec` (if environment has babashka)
- [ ] Ensure work is linked to an issue per `CONTRIBUTING.md`.

---

### Task 1: Specs for the new capture model (fail first)

**Files:**

- Modify: `spec/cljc/c3kit/apron/log_spec.cljc`

- [ ] Remove Timbre require and arity-override example.
- [ ] Rewrite `capture-logs` example so it does **not** destructure Timbre `-log!` vectors; assert via `parse-captured-logs` / `captured-logs-str` only (and optionally that `@captured-logs` entries are maps if you choose maps).
- [ ] Add filtering / `with-level` / multi-arg examples listed above.
- [ ] Run `clj -M:test:spec` — expect **failures** (or compile errors) until implementation lands. If old implementation still passes some tests, keep new tests red for filtering/capture format.

---

### Task 2: Hand-rolled core (levels + emit + capture)

**Files:**

- Modify: `src/cljc/c3kit/apron/log.cljc`

- [ ] Remove `[taoensso.timbre :as timbre]`.
- [ ] Add level rank map, config atom / dynamic override, `may-log?`, `-emit!`, silent min-level setter.
- [ ] Reimplement macros `trace`…`report` as `(when (may-log? <lvl>) (-emit! <lvl> (list ~@args)))` so **disabled levels do not evaluate args** (single implementation for clj/bb; cljs via require-macros). Add the lazy-args spec from the test plan.
- [ ] Reimplement `capture-logs`, `with-level`, `level`, `set-level!`, bang helpers.
- [ ] Reimplement `parse-captured-logs` and `captured-logs-str` for the new buffer shape.
- [ ] Keep `time`, `table-spec`, `color-pr`, nanos helpers.
- [ ] Update ns docstring: facade is hand-rolled; no Timbre; capture helpers are the supported test API.
- [ ] Run `clj -M:test:spec` → all log specs green.
- [ ] Run full `clj -M:test:spec` → suite green.

---

### Task 3: Drop dependencies

**Files:**

- Modify: `deps.edn`

- [ ] Remove `com.taoensso/timbre` and `org.slf4j/slf4j-nop`.
- [ ] `clj -Stree` — confirm no `timbre`, `encore`, `truss`, `pretty`, `slf4j`.
- [ ] Re-run `clj -M:test:spec` and `clj -M:test:cljs once`.
- [ ] `bb spec` and `bb clean` (exercises `log/info` in bb.edn).

---

### Task 4: CLJS / bb polish

**Files:**

- Modify: `src/cljc/c3kit/apron/log.cljc` as needed

- [ ] Ensure `#?(:cljs (:require-macros …))` refer list still exports all macros used from CLJS.
- [ ] Remove obsolete `#?(:bb …)` Timbre shims if a unified impl works on bb.
- [ ] CLJS output goes to console when not capturing; capture still silent.
- [ ] `clj -M:test:cljs once` green.
- [ ] `bb spec` green.

---

### Task 5: Docs and version

**Files:**

- Modify: `CHANGES.md`
- Modify: `resources/c3kit/apron/VERSION` (if this ships as its own release)
- Modify: `README.md` only if it mentions Timbre (today it only says “platform independent logging”)

Suggested `CHANGES.md` bullets (adjust version per maintainer):

```markdown
### X.Y.Z

 * **Hand-rolled `c3kit.apron.log`.** Same macros and level controls; no Timbre.
 * **Removed dependencies:** `com.taoensso/timbre`, `org.slf4j/slf4j-nop`
   (and their transitive stack: encore, truss, pretty, slf4j-api).
 * **Capture buffer:** raw `@log/captured-logs` is no longer Timbre `-log!`
   vectors. Use `parse-captured-logs` / `captured-logs-str` in tests.
```

Versioning guidance:

- Prefer **minor** bump if you treat capture-vector change as supported API break for raw atom peeks; **patch** only if maintainers explicitly guarantee no one relies on Timbre tuples.
- Default recommendation: **minor** (e.g. `3.1.0` from `3.0.1`).

- [ ] Bump `VERSION` consistently with `CHANGES.md`.
- [ ] Do not publish from a laptop; release stays CI-gated (`CONTRIBUTING.md` / release workflow).

---

### Task 6: Final verification checklist

- [ ] `clj -M:test:spec`
- [ ] `clj -M:test:cljs once`
- [ ] `bb spec`
- [ ] `clj -Stree` has no Timbre/SLF4J
- [ ] `rg -n 'taoensso|timbre|slf4j' src spec deps.edn` → no matches (except maybe `CHANGES.md` history)
- [ ] `bb.edn` still loads and `clean` logs via new impl
- [ ] PR description notes consumer migration: replace any Timbre vector peeks with `parse-captured-logs`

---

## Out-of-repo notes (do not block merge)

Downstream c3kit modules (bucket / wire / scaffold) depend on published apron versions. They do not need code changes if they only use macros + `capture-logs` helpers. After release:

- Bump apron version in those modules in separate PRs when ready.
- Optional: search consumer apps for `@log/captured-logs` or `taoensso.timbre` redefs aimed at apron’s old capture shims.

---

## Success criteria

1. `c3kit.apron.log` public macros/fns behave as specified (levels, capture, time).
2. Main `deps.edn` no longer declares Timbre or slf4j-nop; tree is clean of that stack.
3. Specs cover filtering and capture without Timbre.
4. JVM, CLJS, and bb test paths pass.
5. `CHANGES.md` + `VERSION` updated for the chosen release number.
