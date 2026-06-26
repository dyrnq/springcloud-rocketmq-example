# Docs

Project documentation that doesn't fit in the top-level `README.md` (which is mostly a
historical scratch-pad of `mqadmin` outputs and topology notes).

| File | Covers |
| --- | --- |
| [local-development.md](local-development.md) | Vagrant VM topology, broker scripts, how to bring up the environment used by every test. |
| [modules.md](modules.md) | The 9 Maven modules, the dependency matrix, what each module exercises. |
| [testing.md](testing.md) | The test matrix per module, key patterns (raw V4 push consumer, body-marker filter for delay, V5 SimpleConsumer long-poll), how to run them. |
| [test-support.md](test-support.md) | The `test-support` shared module — `Addresses`, `Topics`, `Groups`, `MessageFactory`, `ReceiptAwaiter`. |
| [formatting.md](formatting.md) | Spotless + google-java-format setup, why the version pins, vendored-file policy. |
| [ci.md](ci.md) | The `.github/workflows/maven.yml` pipeline — Spotless gate, JDK 21, Maven build. |

## Where to start

- **New contributor who wants to run the tests**: read [local-development.md](local-development.md) →
  [testing.md](testing.md) in that order.
- **Someone debugging a flaky test**: [testing.md § Known flaky tests](testing.md#known-flaky-tests).
- **Someone adding a new module**: copy the layout of the closest sibling module from
  [modules.md](modules.md), then add `<plugin>spotless</plugin>` to its `<build><plugins>` (see
  [formatting.md § Why every module redeclares Spotless](formatting.md#why-every-module-redeclares-spotless)).