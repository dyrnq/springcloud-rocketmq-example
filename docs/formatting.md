# Formatting

Code style is enforced by [Spotless 2.43.0](https://github.com/diffplug/spotless) using
[google-java-format 1.18.0](https://github.com/google/google-java-format). Both
versions are **pinned explicitly** in the root `pom.xml` so a future Spotless upgrade
cannot silently swap in google-java-format 2.x (which has subtly different behaviour in
lambda/switch/import-grouping cases).

The same config also runs `removeUnusedImports` on every Java source — unused imports
get stripped on `spotless:apply`.

## What's enforced

- **Indentation**: 2 spaces (continuation indent included). google-java-format's hard
  default — note that the project's older code used 4 spaces, so the very first
  `spotless:apply` reformats ~77 files.
- **Braces**: K&R (opening brace on same line).
- **Line length**: 100 columns, but google-java-format will wrap fluent chains and
  nested calls automatically.
- **Import order**: java.* / javax.* / blank / everything else, alphabetised.
- **Trailing whitespace**: stripped.
- **Final newline**: enforced at end of file.

The config is intentionally **not** an `.editorconfig` — the Spotless block in
`pom.xml` is the single source of truth, so build-time and editor-time agree.

## How to apply / check

```bash
# Reformat every Java file in the repo (in-place)
mvn -T 1C spotless:apply

# Verify everything is already formatted (CI uses this)
mvn spotless:check

# Reformat a single module
mvn -pl sbs spotless:apply
```

`spotless:apply` is idempotent — running it on an already-formatted tree is a no-op.

## Why every module redeclares Spotless

Spotless has a quirk: **the plugin declared in the parent pom's `<build><plugins>` is
NOT inherited by child modules** the way regular dependency declarations are. Each child
module must declare `<plugin>com.diffplug.spotless:spotless-maven-plugin</plugin>` (no
`<version>` — that's inherited from the root) for Spotless to run on its sources.

The root `pom.xml` declares:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>com.diffplug.spotless</groupId>
      <artifactId>spotless-maven-plugin</artifactId>
      <version>2.43.0</version>
      <configuration>
        <java>
          <googleJavaFormat>
            <version>1.18.0</version>
            <style>GOOGLE</style>
          </googleJavaFormat>
          <removeUnusedImports/>
        </java>
      </configuration>
    </plugin>
  </plugins>
</build>
```

Every child module's `pom.xml` declares:

```xml
<build>
  <plugins>
    <!-- ...other plugins... -->
    <plugin>
      <groupId>com.diffplug.spotless</groupId>
      <artifactId>spotless-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```

This is intentional duplication: each child picks up the version and configuration from
the root, but must explicitly opt in.

## Vendored files

Five files in this repo are vendored from Apache RocketMQ (carrying the ASF license
header). They are reformatted along with first-party code — there are **no excludes**.
google-java-format preserves the leading license comment block; only the body of the
file is reformatted.

When re-syncing from upstream Apache, run `mvn spotless:apply` once after the sync to
re-align style.

## CI gate

`.github/workflows/maven.yml` runs `mvn -B spotless:check` before the build step. A
format drift on any branch fails the PR. See [ci.md](ci.md).

## Bumping the formatter

To upgrade Spotless or google-java-format:

1. Bump the `<version>` in root `pom.xml`'s Spotless plugin block.
2. Run `mvn -T 1C spotless:apply` to bring every file up to the new style.
3. Run `mvn -T 1C test` to confirm no syntactic regressions.
4. Commit as a single PR; reviewers expect a "Reformat with google-java-format X.Y.Z"
   commit message that names the version bump explicitly.