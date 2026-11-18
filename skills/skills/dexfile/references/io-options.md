Up: [SKILL.md](../SKILL.md)

# Options

`DexOptions` is the sealed base of `ReadOptions` and `WriteOptions`: the six settings shared by
both directions. Option objects are immutable - a `with…` method returns a new instance, keeps the
other fields and never calls `validate()`, so an inconsistent combination is accepted when built
and rejected when used. The constructor clamps the target API to `37`, so every construction path
(explicit subclass constructors, `defaultOptions()` and every `with…` copy) tops out at `37`;
`DexVersion.forApi` is not clamped; version and API are independent components - an already-selected
lower `dex_version` stays.

`SmaliOptions`, the option class of the smali printer (`SmaliIO.print*`), is separate from this
hierarchy: seven independent switches in the same immutable `defaultOptions()`/`withXxx` style,
no base class and no `validate()` - `smali.md` documents it. Reading smali needs no options.

## com.v7878.dex.DexOptions

`public abstract sealed class DexOptions<D extends DexOptions<D>> permits ReadOptions, WriteOptions`

Invariant: `dup` must return the concrete type with the direction-specific fields copied; `D` is
what lets `with…` return `ReadOptions` or `WriteOptions`.

**Fields**

| Signature | Note |
|---|---|
| `protected final int api` | target API level |
| `protected final boolean art` | `true` = ART, `false` = Dalvik |
| `protected final boolean odex` | allow odex-only instructions |
| `protected final boolean expanded` | allow `dex\n036` jumbo, API 14-15 only |
| `protected final boolean hiddenapi` | hiddenapi flags, API 28+ |
| `protected final boolean debug_info` | keep debug information |

**Methods**

| Signature | Note |
|---|---|
| `public void validate()` | `IllegalArgumentException` on a bad combination |
| `protected abstract D dup(int api, boolean art, boolean odex, boolean expanded_opcodes, boolean hiddenapi, boolean debug_info)` | copy; subclass fields survive |
| `public int getTargetApi()` | |
| `public D withTargetApi(int api)` | |
| `public boolean isTargetForArt()` | |
| `public D withTargetForArt(boolean art)` | `true` = ART |
| `public boolean isTargetForDalvik()` | `!isTargetForArt()` |
| `public boolean hasOdexInstructions()` | |
| `public D withOdexInstructions(boolean odex)` | |
| `public boolean hasExpandedInstructions()` | |
| `public D withExpandedInstructions(boolean expanded)` | |
| `public boolean hasHiddenApiFlags()` | |
| `public D withHiddenApiFlags(boolean hiddenapi)` | |
| `public boolean hasDebugInfo()` | |
| `public D withDebugInfo(boolean debug_info)` | |

Defaults of `defaultOptions()`: `api` = 26 off-device, the device SDK on Android, capped at 37
before `art` is derived; `art` = `api > 20`; `odex` = `true` on Android, `false` elsewhere;
`expanded` = `14 <= api && api <= 15`; `hiddenapi` = `false`; `debug_info` = `true`. `odex` and
`expanded` are independent: odex is an instruction-set switch, not an odex container, and legacy
`m-*` opcodes follow the dex version, not either flag.

**Validation rules**, in this order:

| Rule | Exception |
|---|---|
| target API range `-1` … `37` | `IllegalArgumentException` |
| `art` requires `api >= 19`; Dalvik requires `api <= 20` | `IllegalArgumentException` |
| `hiddenapi` requires `api >= 28` | `IllegalArgumentException` |
| `expanded` requires `14 <= api <= 15` | `IllegalArgumentException` |
| `odex`, `debug_info` | no constraint |

API 19-20 accept both runtimes; below 19 only Dalvik; above 20 only ART.

**Interaction matrix** (`odex` and `debug_info` are free at every row):

| Target API | `art` | `hiddenapi` | `expanded` | Valid |
|---|---|---|---|---|
| `-1` … `13` | `false` | `false` | `false` | yes |
| `14` … `15` | `false` | `false` | `false` or `true` | yes |
| `16` … `18` | `false` | `false` | `false` | yes |
| `19` … `20` | `false` or `true` | `false` | `false` | yes |
| `21` … `27` | `true` | `false` | `false` | yes |
| `28` … `37` | `true` | `false` or `true` | `false` | yes |

`dup` contract: every `with…` delegates to it and both subclasses copy their own fields, so
`ReadOptions.withTargetApi(...)` keeps the read switches and `WriteOptions.withTargetForArt(...)`
keeps version, fixes, compact flags and sorting; there is no conversion between the branches.

## com.v7878.dex.ReadOptions

`public final class ReadOptions extends DexOptions<ReadOptions>`

Invariant: all three switches are `true` in `defaultOptions()`; they decide which optional parts of
the file are decoded.

**Methods**

| Signature | Note |
|---|---|
| `protected ReadOptions dup(int api, boolean art, boolean odex, boolean expanded_opcodes, boolean hiddenapi, boolean debug_info)` | switches preserved |
| `public static ReadOptions defaultOptions()` | shared defaults, all switches on |
| `public boolean hasAnnotations()` | |
| `public ReadOptions withAnnotations(boolean annotations)` | |
| `public boolean hasFieldValues()` | |
| `public ReadOptions withFieldValues(boolean field_values)` | |
| `public boolean hasMethodBodies()` | |
| `public ReadOptions withMethodBodies(boolean method_bodies)` | |

| Switch off | Effect |
|---|---|
| annotations | `DEX035`+: class, field, method and parameter sets are empty, no annotation directory is parsed; `DEX009`/`DEX013` synthesise a `Throws` annotation from the throws list only when this switch is on - with it off such a method has no annotations at all (the throws list is not exposed elsewhere) |
| field values | `FieldDef.getInitialValue()` is `null`, also for `static final` fields |
| method bodies | a method with a `code_item` gets an empty `MethodImplementation` (255 registers, no instructions, no try blocks, no debug items); `abstract`/`native` keep `null` |
| debug info | debug items and parameter names are dropped |

Skeleton mode (`withMethodBodies(false)`) is deliberate: the presence of a `code_item` is a
structural fact that separates a real body from an `abstract`/`native` method, so it must survive a
round trip - hence an empty implementation rather than `null`, and `getImplementation() == null`
always means "no code item". 255 registers are valid for any signature, so every skipped method
gets the same tiny body and writing a skeleton back stays compact. Treat such a body as a
placeholder: it is not executable and not a correct implementation. Read with bodies on before
inspecting, rewriting or running code.

The reader calls `DexVersion.checkApi(options.getTargetApi())`: the default API 26 cannot read
`DEX036`, `DEX039`, `DEX040`, `DEX041`, `CDEX001`, `DEX009` or `DEX013`. Set `-1` for `dex\n009` and
`0` for `dex\n013` (both with `withTargetForArt(false)`), `>= 35` for `dex\n041`, `28`-`35` for
`cdex001`. Raising the API does not lower `art`, so `withTargetApi(0)` alone still fails
`validate()`; use `withTargetApi(0).withTargetForArt(false)`.

## com.v7878.dex.WriteOptions

`public final class WriteOptions extends DexOptions<WriteOptions>`

Invariant: `dex_version` is computed once at construction (`DexVersion.forApi(api)`);
`withTargetApi` does not re-derive it.

**Methods**

| Signature | Note |
|---|---|
| `public void validate()` | version, then compact flags, then shared rules |
| `protected WriteOptions dup(int api, boolean art, boolean odex, boolean expanded_opcodes, boolean hiddenapi, boolean debug_info)` | write settings preserved |
| `public DexVersion getDexVersion()` | |
| `public WriteOptions withDexVersion(DexVersion version)` | |
| `public Integer getCDEXFlags()` | `null` = unset |
| `public WriteOptions withCDEXFlags(Integer flags)` | |
| `public boolean isClassSorting()` | |
| `public WriteOptions withClassSorting(boolean sort)` | |
| `public StringFix getStringFix()` | |
| `public WriteOptions withStringFix(StringFix string_fix)` | |
| `public RawFix getRawFix()` | |
| `public WriteOptions withRawFix(RawFix raw_fix)` | |
| `public static WriteOptions defaultOptions()` | defaults below |

Defaults: `dex_version` = `DexVersion.forApi(api)`, `string_fix` = `FIX_JUMBO`, `raw_fix` =
`DO_NOT_TOUCH`, `compact_flags` = `null`, `sort_defs` = `false`. Off-device (`api = 26`) that is
`DEX038`; on an Android 35 device the default object already has `DEX041`, so `write` produces a
one-component container - call `withDexVersion(DexVersion.DEX038)` for a plain file. A container
needs at least one component: `writeDexContainer` with none throws `IllegalArgumentException`.
A pool section above `DexConstants.MAX_POOL_SIZE` (`65534`; `65535` is reserved) fails at write time
with `IllegalStateException`; `65534` is accepted.

Validation, in this order:

1. `dex_version.checkApi(api)` - `IllegalArgumentException`, reported as a version problem before
   the shared range check.
2. `compact_flags != null` only for a compact version, else `IllegalArgumentException` (compact
   flags on a non-compact version).
3. `super.validate()`.

`withDexVersion(null)`, `withStringFix(null)` and `withRawFix(null)` throw `NullPointerException`
immediately; `withDexVersion` and `withCDEXFlags` check nothing else, and the writer calls
`validate()`, so an invalid object fails at the first `write` call. The valid bit values of
`compact_flags` are not defined by the library: pass `null` or a value from a source that defines
them.

`withClassSorting(true)` reorders `class_defs` so every superclass and interface precedes its
referrer and raises `IllegalStateException` for a duplicate class type or a hierarchy cycle; without
sorting the model order is written as-is.

## com.v7878.dex.WriteOptions.StringFix

`public enum WriteOptions.StringFix`

**Fields** — `public static final StringFix`: `NONE` `FIX_JUMBO` `FIX_ALL`

**Methods**

| Signature | Note |
|---|---|
| `public static StringFix[] values()` | |
| `public static StringFix valueOf(String name)` | |

`NONE` leaves instructions untouched (an index that does not fit then fails the write); `FIX_JUMBO`
turns only an overflowing `const-string` into `const-string/jumbo`; `FIX_ALL` picks the narrowest
form for every string constant, so the output is not byte-identical to the input.

## com.v7878.dex.WriteOptions.RawFix

`public enum WriteOptions.RawFix`

**Fields** — `public static final RawFix`: `DO_NOT_TOUCH` `USE_WRAPPER` `USE_RAW`

**Methods**

| Signature | Note |
|---|---|
| `public static RawFix[] values()` | |
| `public static RawFix valueOf(String name)` | |

`DO_NOT_TOUCH` (default) keeps every instruction exactly as modelled; `USE_WRAPPER` replaces
`RAW`/`RAW_ALIGNED`/`RAW_REF`/`RAW_REF_JUMBO` with the corresponding wrapper opcode; `USE_RAW`
replaces `WRAPPER_RAW*` with the plain raw instruction.
