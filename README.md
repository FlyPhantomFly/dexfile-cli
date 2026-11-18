# dexfile-cli

A minimal Java 17 CLI over [DexFile](https://github.com/vova7878/DexFile) (`io.github.vova7878:DexFile`
+ `Smali`, MIT) — the operations android-re-mcp needs from the dex model, as one fat jar:

| Subcommand | What it does |
| --- | --- |
| `info <dex\|container\|vdex>` | version, target API, class/method/string counts, optional class list (magic-scanned: flat, 041 container, or wrapped cdex/dex inside vdex/packer blobs) |
| `extract <apk\|dex\|vdex>` | pull every `classes*.dex` (splitting 041 containers) into an output dir |
| `convert <in> --to 035…041` | read any version → write the target version (the old-ART vdex/cdex → jadx bridge) |
| `patch <dex> --ops <ops.json>` | targeted model edits: `replace-const-string`, `rename` (class/method/field), `retarget-invoke`, `flip-flags`, `replace-method-body` (smali text) |
| `assemble <smali_file>` | one smali class → dex (`SmaliIO`) |
| `split <dex>` | shard a class list across dex files under the 64K-reference limit (`DexIO.balanceAll`) |

One JSON object per invocation on stdout, logs on stderr. Exit codes: `0` ok, `1` unexpected,
`2` usage, `3` not-a-dex, `4` invalid-dex.

## Build

```
sh gradlew shadowJar          # -> build/libs/dexfile-cli.jar
```

Java 21+ runs the build (targets release-17 bytecode via `options.release`; DexFile itself
pins a Java 17 *toolchain*, which breaks on machines without a provisioned JDK 17 — see the
android-re-mcp integration review §2.2). Runtime needs any Java 17+ JRE.

## Honest limits

* The CLI is a thin shell; all format capability comes from DexFile. DexFile has **no
  upstream test suite** — the android-re-mcp round-trip tests are the safety net.
* Round-trips re-serialize: semantic content survives, layout does not.
* vdex: modern ART (v27+ containers, Android 13+) carries **no embedded dex** — the dex
  lives in the APK. The magic-scan path honestly reports "no readable dex magic" for those;
  the vdex/cdex bridge applies to older ART output (embedded cdex, Android 8–10 era) and
  standalone cdex from dalvik-cache.
* cdex *write* is DexFile-supported only as a standalone file (not `PathClassLoader`-loadable
  on device) — treated as a conversion/analysis artifact.

## License

MIT. This jar redistributes DexFile, Smali and AndroidMisc (all MIT, © Vladimir Kozelkov) —
their license text is reproduced in `LICENSE-THIRD-PARTY.md`.
