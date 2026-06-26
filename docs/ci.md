# CI

CI is `.github/workflows/maven.yml` — a single job, `Java CI with Maven`, that runs on
`ubuntu-latest` for pushes to `main` and for every pull request.

```yaml
on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - name: Set up JDK 21
        uses: actions/setup-java@v5
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Check formatting (Spotless)
        run: mvn -B spotless:check
      - name: Build with Maven
        run: mvn -B package --file pom.xml -Dmaven.test.skip=true
```

## What each step does

| Step                          | Purpose                                                                  |
| ----------------------------- | ------------------------------------------------------------------------ |
| `actions/checkout@v6`         | Pull the branch.                                                         |
| `setup-java@v5` (Temurin 21)  | Install JDK 21. `cache: maven` reuses the `~/.m2` cache between runs.   |
| `Check formatting (Spotless)` | `mvn -B spotless:check`. Fails the build if any source file is unformatted. Runs in seconds. |
| `Build with Maven`            | `mvn -B package -Dmaven.test.skip=true`. Builds every module's jar / spring-boot fat-jar. **Skips tests.** |

## Why tests aren't run in CI

Every test in this repository talks to a **real RocketMQ broker running in a Vagrant VM**
(`192.168.88.123:9876` / `192.168.88.128:9876`). The CI runner has no access to that VM —
there is no `vagrant up` step, no Testcontainers, no broker-in-proc.

This means **CI cannot catch a test regression** — only a compile or format regression.
The pipeline is intentionally a fast gate (Spotless + build) rather than a full
end-to-end test run.

Local developers run the full test suite against the Vagrant VM before pushing. See
[testing.md](testing.md).

## Why `-Dmaven.test.skip=true`

Two reasons:

1. **Tests would all fail** on the runner (no broker reachable), producing a misleading
   red build.
2. **The runner has no `insecure_private_key`** for `vagrant ssh`, so we couldn't
   bootstrap a broker in time anyway.

The flag suppresses both compilation and execution of test classes — the build is
fast and predictable. If you want to compile tests without running them locally
(useful for IDE-driven dev), use `mvn -DskipTests test-compile` instead.

## Adding a new CI step

When extending the pipeline, remember:

- **Keep JDK 21.** Several modules (`spring-cloud-v2025-1` with Spring Boot 4.1.0)
  require JDK 17+; using JDK 21 gives headroom.
- **Use `-B` (batch mode)** to suppress interactive progress bars.
- **Cache Maven artifacts** — already done via `setup-java`'s `cache: maven`. If you
  add a step that downloads large artifacts manually, add your own cache block.