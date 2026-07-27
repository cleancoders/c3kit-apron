# Apron

![Apron](https://github.com/cleancoders/c3kit/blob/master/img/apron_200.png?raw=true)

A library component of [c3kit - Clean Coders Clojure Kit](https://github.com/cleancoders/c3kit).

_"Where is thy leather apron and thy rule?"_ - Shakespeare

[![Apron Build](https://github.com/cleancoders/c3kit-apron/actions/workflows/test.yml/badge.svg)](https://github.com/cleancoders/c3kit-apron/actions/workflows/test.yml)
[![Clojars Project](https://img.shields.io/clojars/v/com.cleancoders.c3kit/apron.svg)](https://clojars.org/com.cleancoders.c3kit/apron)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Apron consists of necessities that almost any clojure app would find useful.

* __app.clj__ : application service and state management
* __util.clj__ : misc utilities used by other c3kit code
* __corec.cljc__ : useful fns, platform independent
* __cursor.cljc__ : atom cursor based on reagent's
* __legend.cljc__ : index application entities
* __log.cljc__ : platform independent logging
* __schema.cljc__ : validation, coercion, specification for entity structure
* __time.cljc__ : simple platform independent time manipulation
* __utilc.cljc__ : platform independent edn, transit, csv, etc..

## Installation

Add to your `deps.edn`:

```clojure
com.cleancoders.c3kit/apron {:mvn/version "2.7.0"}
```

Or to your `project.clj`:

```clojure
[com.cleancoders.c3kit/apron "2.7.0"]
```

Released artifacts: [Clojars](https://clojars.org/com.cleancoders.c3kit/apron). Changelog: [CHANGES.md](CHANGES.md).

**Requirements:** Clojure 1.11+, JDK 21+ (CI runs against JDK 21).

## Quickstart

Define a schema, then coerce/validate data against it:

```clojure
(require '[c3kit.apron.schema :as schema])

(def point {:kind {:type :keyword}
            :x    {:type :int}
            :y    {:type :int}})

(schema/coerce point {:kind :point :x "1" :y "2"})
;; => {:kind :point, :x 1, :y 2}

(schema/validate point {:kind :point :x 1 :y 2})
;; => {:kind :point, :x 1, :y 2}
```

See [SCHEMA.md](SCHEMA.md) for the full schema reference.

## Development

    # Delete the target directory
    bb clean

    # Run the JVM tests
    clj -M:test:spec
    clj -M:test:spec -a         # auto runner
    clj -M:test:spec-cst        # CST tests
    clj -M:test:spec-mst        # MST tests

    # Run the Babashka tests
    bb spec
    bb spec -a                  # auto runner
    bb spec-cst                 # CST tests
    bb spec-mst                 # MST tests

    # Compile and Run JS tests
    clj -M:test:cljs once
    clj -M:test:cljs            # auto runner
    
    # CST JS tests
    TZ=America/Chicago clj -M:test:cljs-cst

    # MST JS tests
    TZ=America/Phoenix clj -M:test:cljs-mst

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow, TDD
expectations, and pull-request requirements. This project follows the
[Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). Security issues
should be reported privately — see [SECURITY.md](SECURITY.md).

## Deployment

Releases run in CI. `clj -T:build deploy` refuses to run outside GitHub Actions,
so the sanctioned path always carries the CI check and leaves an audit trail.

1. Open a PR bumping `resources/c3kit/apron/VERSION` and `CHANGES.md`.
2. Merge to `master` and wait for **Apron Build** to go green. The version bump is
   part of the merged commit, so the commit CI validated is the commit that gets
   released.
3. Actions → **Release** → **Run workflow**.
4. Approve the `clojars` deployment when prompted.

The workflow verifies **Apron Build** succeeded for that exact commit, builds the
jar, publishes to Clojars, and only then pushes the version tag. A failed publish
therefore leaves no tag.

`clj -T:build jar` builds without publishing. `clj -T:build install` installs to
`~/.m2` for local testing.

### Break glass

Only when the release workflow itself cannot run. This **skips the CI check**, so
note its use in `CHANGES.md` for that release.

```
CLOJARS_USERNAME=<username> \
CLOJARS_PASSWORD=<deploy token> \
EMERGENCY_RELEASE=<the exact version in resources/c3kit/apron/VERSION> \
  clj -T:build emergency-publish
```

`CLOJARS_PASSWORD` is a Clojars deploy token generated at https://clojars.org/tokens, not your account password.
`EMERGENCY_RELEASE` must equal the version being released exactly; a
mismatched or unset value aborts. It still refuses a dirty working tree and an
already-tagged version.

## License

[MIT](LICENSE) © Clean Coders.
