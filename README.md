# dexfile-cli

A command-line tool for working with Android dex files: inspect them, pull dexes out of an
APK, convert between dex versions, apply small targeted patches, assemble a class from
smali, split a big dex under the 64K-reference limit. All the format-heavy lifting is done
by [DexFile](https://github.com/vova7878/DexFile) and [Smali](https://github.com/vova7878/DexFile/tree/main/smali)
(Vladimir Kozelkov's `com.v7878` libraries); this repo wraps them in a single self-contained
jar with six subcommands.

Every invocation prints exactly one JSON object on stdout; human-readable logs go to stderr.
That contract is deliberate — the tool is meant to be driven by scripts and other programs,
not poked at interactively.

```
$ java -jar dexfile-cli.jar info hello.dex
{"version":"DEX035","target_api":34,"compact":false,"container":false,"detected":"flat",
 "counts":{"classes":1,"methods":2,"fields":1,"strings":13,"types":7,"map_items":12,
 "method_handles":0,"call_sites":0},"file":"hello.dex"}
```

## How this differs from the DexFile repo

If you were looking for the library itself, that's [vova7878/DexFile](https://github.com/vova7878/DexFile) —
this is not it, and it's not a fork either. None of the dex-format code lives here: Gradle
pulls `io.github.vova7878:DexFile` and `Smali` (v1.7.0) from Maven Central, and this project
adds the command-line shell around them.

| | DexFile (upstream) | dexfile-cli (this repo) |
| --- | --- | --- |
| Shape | Java library (`com.v7878.dex.*`) — you write Java/Kotlin against its API | Runnable CLI — six subcommands, no code to write |
| Output | In-process objects you consume however you like | One JSON object per run on stdout, logs on stderr, fixed exit codes |
| Reading | Reads whatever you point it at: flat dex, containers, cdex | Same, plus a magic-scan fallback that hunts for dex/cdex magics anywhere in the file (vdex-era cdex, packer-shuffled dex) and an API ladder (34 → 28 → 21 → 19 → min) that retries the read under different target-API interpretations |
| APKs | — | `extract` walks a zip and pulls out every `classes*.dex` (041 containers get split into parts) |
| Patching | Full rewriter API (`DexRewriter`) | `patch` drives that rewriter for you from a JSON ops file — no rewriter boilerplate |
| Smali | `SmaliIO` API | `assemble`: one smali text file → dex |
| Splitting | `DexIO.balanceAll` | `split`: same balancer, wired to write the parts out |
| Build | Pins a Java 17 Gradle *toolchain*, which breaks on machines without a provisioned JDK 17 | Compiles with `options.release = 17` from whatever JDK runs Gradle — no toolchain provisioning |
| Distribution | Gradle/Maven dependency | One fat jar via Shadow — `java -jar`, no install step |

What deliberately does **not** differ: format support. Every read/write capability
(035–041, odex instructions, cdex, containers) comes from DexFile unchanged. If the
library can't do it, neither can the CLI.

## Build

```
sh gradlew shadowJar        # → build/libs/dexfile-cli.jar
```

The wrapper downloads Gradle 9.8.1 on first run, so all you need is a JDK it can run under
(this repo is built with JDK 21). The bytecode targets Java 17, so at runtime any JRE 17+
is fine.

## Usage

```
java -jar build/libs/dexfile-cli.jar <subcommand> [options]
```

| Subcommand | What it does |
| --- | --- |
| `info <dex\|container\|vdex>` | version, target API, class/method/field/string counts; `--classes` adds a sorted class list (first 100, or `--limit N`) |
| `extract <apk\|dex\|vdex>` | pull every `classes*.dex` into a directory (default `<input>_dex_extract`) |
| `convert <in> --to 035…041` | read any supported version → write the target version (the old-ART vdex/cdex → plain dex bridge; for cdex/vdex-era content, 037 is the closest target — see limits) |
| `patch <dex> --ops <ops.json>` | apply the ops described below |
| `assemble <smali_file>` | one smali class → dex |
| `split <dex>` | shard the class list across dex files under the 64K-reference limit; the balancer picks the part count |

Options:

* `--out PATH` — where to write. Defaults are sensible-ish (`app.vdex` → `app.035.dex`,
  directories `<input>_dex_extract` and `<input>_split`), but note that **`patch` overwrites
  its input file when you omit `--out`** — pass `--out` or keep a copy.
* `--api N` — pin the target-API interpretation. The reader normally climbs an API ladder
  (34, 28, 21, 19, then the version's minimum) and keeps the first reading that works, which
  matters because odex/hidden-api handling changes with API level. You'll see it in output:
  a plain 035 file can report `target_api: 34` because that interpretation read it first.
  Pin it with `--api` if you want a specific one.

Exit codes: `0` ok, `1` unexpected error, `2` usage, `3` not-a-dex (no readable dex magic),
`4` invalid-dex (magic found, file broken). Errors also land as JSON on stdout, e.g.
`{"error":"no readable dex magic found (flat, container, or wrapped)","kind":"not_a_dex"}`.

### Patch ops

`ops.json` is a JSON array (a `{"ops": [...]}` wrapper works too), applied in a single
rewrite pass:

```json
[
  {"op": "replace-const-string", "old": "https://old.example.com/api",
                                 "new": "https://10.0.2.2:8080/api"},
  {"op": "rename", "target": "class", "class": "com.example.Foo",
                   "new_name": "com.example.Bar"},
  {"op": "retarget-invoke", "class": "com.example.Foo", "name": "greet",
                            "to_class": "com.example.Helper"},
  {"op": "flip-flags", "class": "com.example.Foo", "method": "checkLicense",
                       "set": [], "clear": ["NATIVE"]},
  {"op": "replace-method-body", "class": "com.example.Foo", "method": "checkLicense",
   "smali": "<method smali text>"}
]
```

Worth knowing:

* Class/method/field names are **Java-style dotted** (`com.example.Foo`), like the rest
  of the dex toolchain — not the `Lcom/example/Foo;` form smali prints. A dalvik-style
  name simply matches nothing (`matched: 0`).
* `rename` on a class rewrites the class def and every reference to it inside that dex;
  references in *other* dexes of the same app are your problem (convert/patch each, or ship
  it as one dex).
* `flip-flags` takes standard flag names (`PUBLIC`, `STATIC`, `NATIVE`, …) or `0x…` hex
  values, with `set` and `clear` lists; at class level when you give neither `method` nor
  `field`. Making a method non-`NATIVE` and replacing its body is a common pair.
* `replace-method-body` parses method-level smali (`SmaliIO.parseMethod`) — hidden-API flags
  survive the swap because the smali text can't express them.
* On a cdex input, `patch` re-writes to DEX037, not 035 — cdex 001 is Android 9 (API 28)
  era ART output, and 037 is the closest standard version to it. The JSON's
  `version_from`/`version_to` always tell you what actually happened.
* The result reports a `matched` count per op. `matched: 0` means the op found nothing —
  almost always a name typo.

## Honest limits

* This CLI is a thin shell; all format capability comes from DexFile. DexFile has no test
  suite upstream, and this repo doesn't have one yet either — if you convert or patch
  something you care about, sanity-check it (compare `info` counts before/after, then
  decompile the result before you trust it).
* Round-trips re-serialize: the semantic content survives, the byte layout does not. Don't
  rely on offsets or expect byte-identical output.
* vdex from modern ART (v27+ containers, Android 13+) carries **no embedded dex** — the dex
  lives in the APK. The magic-scan path honestly reports "no readable dex magic" for those.
  The vdex/cdex bridge applies to older ART output (embedded cdex, the Android 8–10 era)
  and standalone cdex from dalvik-cache.
* cdex can only be *written* as a standalone file, which isn't loadable via
  `PathClassLoader` on-device — treat it as a conversion/analysis artifact. When you do
  write it out, DEX037 is the right target: cdex 001 content is Android 9 (API 28) era, and
  037 (API 26+) is the closest standard dex version — dropping to 035 would mislabel it.
  `patch` already rewrites cdex to 037 by default; for `convert`, pass `--to 037` (the
  subcommand table's bridge note is the recommendation, not a default).
* `patch` and `split` want a single dex; hand them a 041 container and they'll tell you to
  run `extract` or `convert` first.

## Credits

* **FlyPhantomFly** ([github.com/FlyPhantomFly](https://github.com/FlyPhantomFly)) — this CLI.
* **Vladimir Kozelkov** ([vova7878](https://github.com/vova7878)) — [DexFile](https://github.com/vova7878/DexFile),
  [Smali](https://github.com/vova7878/DexFile/tree/main/smali) and AndroidMisc. They're
  bundled in the jar and this project wouldn't exist without them — the CLI adds maybe a
  thousand lines on top of their work.

License for this repo is MIT (© FlyPhantomFly) — see [LICENSE](LICENSE), which also carries
the third-party notice for the bundled libraries.
