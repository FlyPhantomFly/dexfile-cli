# dexfile-cli

A command-line tool for working with Android dex files: inspect them, pull dexes out of an
APK, convert between dex versions, apply small targeted patches, assemble a class from
smali, validate a dex's linkage and method bodies, merge dexes back into one, split a big
dex under the 64K-reference limit. All the format-heavy lifting is done
by [DexFile](https://github.com/vova7878/DexFile) and [Smali](https://github.com/vova7878/DexFile/tree/main/smali)
(Vladimir Kozelkov's `com.v7878` libraries); this repo wraps them in a single self-contained
jar with nine subcommands.

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
| Shape | Java library (`com.v7878.dex.*`) — you write Java/Kotlin against its API | Runnable CLI — nine subcommands, no code to write |
| Output | In-process objects you consume however you like | One JSON object per run on stdout, logs on stderr, fixed exit codes |
| Reading | Reads whatever you point it at: flat dex, containers, cdex | Same, plus a magic-scan fallback that hunts for dex/cdex magics anywhere in the file (vdex-era cdex, packer-shuffled dex, dey-embedded dex) and an API ladder (34 → 28 → 21 → 19 → min; pre-release 009/013 pinned to their era) that retries the read under different target-API interpretations, odex instructions always enabled |
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

**Dependencies:** `DexFile` and `Smali` are pinned at **v1.7.1** from Maven Central
(upstream tagged v1.7.1 at the commit this repo briefly vendored — the vendored jars were
content-identical to the release and have been retired; AndroidMisc v1.0.1 and the ANTLR
runtime 4.13.2 come transitively via the published POMs).

## Usage

```
java -jar build/libs/dexfile-cli.jar <subcommand> [options]
```

| Subcommand | What it does |
| --- | --- |
| `info <dex\|container\|vdex>` | version, target API, class/method/field/string counts; `--classes` adds a sorted class list (first 100, or `--limit N`) — and switches to a full-model read, see below |
| `extract <apk\|dex\|vdex>` | pull every `classes*.dex` into a directory (default `<input>_dex_extract`) |
| `convert <in> --to 035…041\|cdex001` | read any supported version → write the target version (for cdex/vdex-era content, 037 is the closest standard target — see limits). `cdex001` writes a standalone artifact, not a loadable dex |
| `patch <dex> --ops <ops.json>` | apply the ops described below |
| `assemble <smali_file>` | one smali class → dex |
| `split <dex>` | shard the class list across dex files under the 64K-reference limit; the balancer picks the part count |
| `validate <dex>` | linkage + method-body diagnostics over a self-contained classpath: duplicate classes, super-is-interface, member-reference resolution errors, per-method analysis failures. Framework classes are outside the classpath — those references are counted `references_unknown`, never flagged. `--max-issues N` caps the shown list (default 200); `issues_total` and `truncated` keep the count honest |
| `merge <dex...>` | the reverse of `split`: concatenate class lists into one dex, refusing class collisions across inputs. `--allow-cdex` writes standalone cdex; `--api 35` writes a single-component DEX041 container |
| `outline <dex> --class <name>` | per-class outline without decompiling — descriptor, super, interfaces, formatted access flags, fields (name/type/flags), methods (name/descriptor/flags/registers), annotation count, dex version; class names are Java-style dotted, like the patch ops |

Options:

* `--out PATH` — where to write. Defaults are sensible-ish (`app.vdex` → `app.035.dex`,
  directories `<input>_dex_extract` and `<input>_split`), but note that **`patch` overwrites
  its input file when you omit `--out`** — pass `--out` or keep a copy.
* `--max-issues N` — `validate`'s shown-issue cap (default 200). The result always carries
  `issues_total`, `issues_shown` and `truncated`.
* `--allow-cdex` — `merge` output as standalone cdex (a conversion/analysis artifact, not
  PathClassLoader-loadable on-device).
* `--api N` — pin the target-API interpretation. Without it, the reader climbs an API
  ladder (34, 28, 21, 19, then the version's minimum; the pre-release dexes 009/013 pin
  exactly their own era api). Odex instructions are always enabled on reads and writes:
  the runtime accepts every opcode it supports regardless of the dex version carrying it.
  (APIs 19 and 20 shipped both Dalvik and ART — the library accepts either `art` setting
  there; this CLI reads them as dalvik-era.) You'll see which interpretation won in the
  output: `target_api` plus a `detected` value like `wrapped@30`. A plain 035 file reports
  `target_api: 34` because that interpretation read it first; a real-ART cdex with legacy
  hidden-API bits packed into field flags falls through to api 28, where they decrypt.
  Pin it with `--api` if you want a specific one.
* Plain `info` walks only the section tables — fast, but it can't catch content that only
  fails at full materialization (those quickened opcodes, packed field flags).
  `info --classes`, `convert`, `patch` and `split` force the full model, so their ladder
  answer is the one that actually survives touching every class.

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
* On a cdex input, `patch` re-writes to DEX037, not 035 — cdex doesn't record the dex
  version it was converted from (just a one-bit flags field: 0 = the source was a 035,
  1 = later), and dex037 (introduced in API 24) covers the cdex era with room to spare.
  The JSON's `version_from`/`version_to` always tell you what actually happened.
* The result reports a `matched` count per op. `matched: 0` means the op found nothing —
  almost always a name typo.

## Honest limits

* This CLI is a thin shell; all format capability comes from DexFile. DexFile has no test
  suite upstream, and this repo doesn't have one yet either — if you convert or patch
  something you care about, sanity-check it (compare `info` counts before/after, then
  decompile the result before you trust it).
* Round-trips re-serialize: the semantic content survives, the byte layout does not. Don't
  rely on offsets or expect byte-identical output.
* **Quickened odex opcodes are written verbatim, and that's fine on-device.** The runtime
  accepts every instruction it supports regardless of the dex version carrying it, so
  `convert`/`patch`/`split` write odex opcodes (odex instructions are enabled on every
  write) instead of refusing. Verified against a 19 MB Android-12 boot vdex (vdex 019,
  three embedded cdex parts, 13,754 framework classes, ~330k odex instructions): reading
  lands on the api-28 interpretation where legacy hidden-API bits packed into field flags
  decrypt, and `convert --to 037` produces a dex that reads back with the same class
  count. The results report an `odex_instructions` count and a note — quickened references
  (`invoke-virtual-quick`, `iget/iput-quick`) carry raw vtable/field offsets, so analysis
  tools can only show them as unresolved (baksmali 3 annotates them as comments; the
  DexFile dev branch ships a smali printer that handles odex/legacy/expanded opcodes
  natively).
* **Legacy ≠ odex.** The m-prefixed instructions in dex 009/013 are the standard
  *legacy* set of their era, not odex — the library's `LegacyRewriter` converts them to
  the modern format, and the CLI applies it automatically before any write. Verified
  against real HTC Sooner framework files: flat dex009 (2,412 classes) and dex013 (2,910)
  convert to 037 and baksmali decompiles every class; the dey odex wrappers carry genuine
  dalvik-era odex opcodes on top (e.g. `invoke-direct-empty`, encodable only at api ≤ 13),
  so those want a dalvik-era target — `--to 035` converts them (82,959 odex instructions
  written verbatim), while `--to 037` refuses honestly, naming the opcode and suggesting
  the right target range.
* vdex from the newest ART (v27+ containers, Android 13+) carries **no embedded dex** —
  the dex lives in the APK. The magic scan honestly reports "no readable dex magic" for
  those. Everything older that embeds cdex or dex parts reads (see above).
* cdex can only be *written* as a standalone file, which isn't loadable via
  `PathClassLoader` on-device — treat it as a conversion/analysis artifact. `convert --to
  cdex001` and `merge --allow-cdex` are the opt-ins for writing it. For standard
  targets, DEX037 (introduced in API 24) is the right landing for cdex-era content: cdex
  doesn't record the dex version it was converted from — just a one-bit flags field
  (0 = the source was a DEX035, 1 = later) — and the difference is verification
  semantics, not opcodes: DEX035 forbids default methods in interfaces and doesn't
  require classes in topological order (super before sub — cycles are even representable,
  though unloadable), while 037+ enforces the order. `patch` rewrites cdex to 037 by
  default; for `convert`, pass `--to 037`.
* `patch`, `split`, `validate` and `merge` want a single dex; hand them a 041 container and
  they'll tell you to run `extract` or `convert` first.
* **`validate` is not the ART verifier.** It runs DexFile's `ClassPath`/`analysis` package
  over only the classes in the file: a reference whose hierarchy leaves the dex (framework
  classes) is non-determinable and counted in `references_unknown`, never reported as
  broken. Quickened odex instructions are not analyzable (`analysis_failed` names the
  limitation). Treat findings as diagnostics, not a runtime verdict.
* **`merge` does not rebalance.** It refuses duplicate classes across inputs and writes one
  dex; if any id section exceeds the 64K limit the write fails with the section named.
  Split the inputs first and merge each shard. `--api 35` produces a single-component DEX041
  container, which is still subject to the per-component limits.

## Credits

* **FlyPhantomFly** ([github.com/FlyPhantomFly](https://github.com/FlyPhantomFly)) — this CLI.
* **Vladimir Kozelkov** ([vova7878](https://github.com/vova7878)) — [DexFile](https://github.com/vova7878/DexFile),
  [Smali](https://github.com/vova7878/DexFile/tree/main/smali) and AndroidMisc. They're
  bundled in the jar and this project wouldn't exist without them — the CLI adds maybe a
  thousand lines on top of their work.

License for this repo is MIT (© FlyPhantomFly) — see [LICENSE](LICENSE), which also carries
the third-party notice for the bundled libraries.
