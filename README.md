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
com.cleancoders.c3kit/apron {:mvn/version "2.1.5"}
```

Or to your `project.clj`:

```clojure
[com.cleancoders.c3kit/apron "2.1.5"]
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

## Releasing (maintainers)

In order to deploy to c3kit you must be a member of the Clojars group `com.cleancoders.c3kit`.

1. Go to https://clojars.org/tokens and configure a token with the appropriate scope
2. Set the following environment variables

```
CLOJARS_USERNAME=<your username>
CLOJARS_PASSWORD=<your deploy key>
```

3. Update `resources/c3kit/apron/VERSION`
4. `clj -T:build deploy`
