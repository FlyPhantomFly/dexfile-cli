# Vendored DexFile/Smali — upstream `main` @ `b3fd141`

These two jars are **not** Maven Central artifacts. They were built locally from
[vova7878/DexFile](https://github.com/vova7878/DexFile) at commit `b3fd141`
(merge of PR #24 — the `dev` branch: smali printer, `InstructionN0ci` reading of the
20bc/40sc verification-error formats, always-throw analysis), with:

```
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
git clone --recursive https://github.com/vova7878/DexFile && cd DexFile
git checkout b3fd141
sh gradlew jar -x javadoc          # → build/libs/DexFile.jar, smali/build/libs/smali.jar
```

Why vendored instead of a Maven pin: upstream has **no release containing these changes**
(tags stop at v1.7.0; Maven Central stops at v1.7.0). Vendoring keeps the repo buildable
anywhere while carrying the unreleased capabilities.

**Revert plan:** when `vova7878` tags the next release (v1.8.0+), delete this directory,
restore the two `implementation("io.github.vova7878:...")` lines in `build.gradle.kts`
and bump the pins. The git history of `vendor/README.md` records exactly what was gained.

`io.github.vova7878:AndroidMisc` stays a Maven dependency (DexFile's runtime dependency);
the vendored jar's POM is not read by Gradle for `files()` dependencies, so the explicit
line in `build.gradle.kts` matters.
