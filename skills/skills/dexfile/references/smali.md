# Smali parsing and printing

`SmaliIO` turns smali text into the ordinary immutable model, and its `print*` methods turn a dex,
a class or a single method back into smali text governed by `SmaliOptions`. The parser is generated
from a grammar and runs with ANTLR's bail strategy: the first syntax error aborts the parse instead
of being collected. Every error, lexical or syntactic, is a `SmaliSyntaxException`; nothing is
written to the standard error stream.

## Directive reference

| Directive | Appears in | Closing | Produces |
|---|---|---|---|
| `.class` | start of a class block | none | descriptor and class access flags |
| `.super` | class body, at most once | — | superclass |
| `.implements` | class body, repeatable | — | one interface per distinct type, source order of first occurrences |
| `.source` | class body, at most once; also a method body | — | class source file, or a `SetFile` item |
| `.field` | class body | `.end field`, optional | `FieldDef` |
| `.end field` | after a `.field` | — | closes the field block |
| `.method` | class body | `.end method`, mandatory | `MethodDef` |
| `.end method` | end of a method block | — | closes the method |
| `.annotation` | class body, field block, method body, param block | `.end annotation`, mandatory | `Annotation` |
| `.end annotation` | end of an annotation | — | closes it |
| `.subannotation` | annotation element value | `.end subannotation`, mandatory | `EncodedAnnotation` |
| `.end subannotation` | end of a nested annotation | — | closes it |
| `.enum` | annotation element value | — | `EncodedEnum` from a field reference |
| `.registers` | first body element of a method | — | total register count |
| `.locals` | first body element of a method | — | locals; total is the value plus input registers |
| `.param` | method body, after the register directive | `.end param`, optional | `Parameter` name and annotations |
| `.end param` | after a `.param` | — | closes the parameter block |
| `.catch` | method body | — | typed handler for a label range |
| `.catchall` | method body | — | catch-all handler for a label range |
| `.line` | method body | — | `LineNumber` |
| `.local` | method body | — | `StartLocal` |
| `.end local` | method body | — | `EndLocal` |
| `.restart local` | method body | — | `RestartLocal` |
| `.prologue` | method body | — | `SetPrologueEnd` |
| `.epilogue` | method body | — | `SetEpilogueBegin` |
| `.array-data` | method body | `.end array-data`, mandatory | `ArrayPayload` |
| `.packed-switch` | method body | `.end packed-switch`, mandatory | `SwitchPayload` |
| `.sparse-switch` | method body | `.end sparse-switch`, mandatory | `SwitchPayload` |

Only `.end field` and `.end param` are optional, and they are exactly what attaches annotations to
the field or the parameter: an annotation written after an unclosed declaration parses outside it —
for a field, as an annotation of the class; for a parameter, as an annotation of the method. A class
block has no closing directive at all: it ends at the end of the input, or at the next class
directive for `parse`. A class body may be empty: the only directive-less class is
`Ljava/lang/Object;`, because the model requires a superclass of every other class - an
`IllegalArgumentException` from `ClassDef`, not a syntax error. The superclass and the source
file may each appear at most once.

Whitespace, including newlines, is skipped between tokens, so a whole class fits on one line;
comments start with `#` and run to the end of the line. The register directive must come before the
first instruction, label, debug directive, `.catch`, `.catchall` or `.param`, and only one is
allowed. `.locals N` adds the input registers (the parameters, plus this for an instance method), so
`.locals 2` in an instance method with one int parameter means four registers. A body without any
register directive has no implementation, which is the abstract and native form.

A `.param` register must be the first register of a parameter: this, the second half of a wide
parameter and anything past the parameter list are rejected. The `.local` type is mandatory:
`.local vN, "name"` is rejected, and `:V` is how a `null` type is written (with or without a
name). A `.catch` type is kept as written: a primitive, an array and a non-throwable reference are
accepted, because ART verifies the handler only when it executes `move-exception`. `.catch` and
`.catchall` name a range by two labels in either order plus a handler label, and may appear before
the labels exist; ranges sharing a boundary are cut into separate try blocks, and each switch or
array payload is laid out at the end of the instruction list, behind an alignment `nop` when the
address would be odd.

## Literals

| Written as | Value | Notes |
|---|---|---|
| `0x2a`, `42`, `0755`, `-1` | `EncodedInt` | decimal, octal, hex; `-2^31 … 0xffffffff` |
| `0x100000000L` | `EncodedLong` | suffix `L` |
| `0x1234s` | `EncodedShort` | suffix `s`; `-32768 … 0xffff` |
| `0x7ft` | `EncodedByte` | suffix `t`; `-128 … 0xff` |
| `1.5f`, `0x1.8p1f`, `1f`, `NaNf`, `-Infinityf` | `EncodedFloat` | suffix `f` |
| `2.5`, `2.5d`, `0x1.8p1`, `NaN`, `-Infinity` | `EncodedDouble` | suffix `d` optional |
| `'a'`, `'\n'`, `'\u0041'` | `EncodedChar` | one character or one escape |
| `"text"`, `"a\tb"` | `EncodedString` | escapes `\b \t \n \f \r \" \' \\ \uXXXX` |
| `true`, `false` | `EncodedBoolean` | |
| `null` | `EncodedNull.INSTANCE` | |
| `{1, 2}` | `EncodedArray` | any literals, nested arrays included |
| `.subannotation T … .end subannotation` | `EncodedAnnotation` | |
| `Ltype;`, `[I`, `[[Ljava/lang/String;` | `EncodedType` | |
| `Ltype;->name:Type`, `name:Type` | `EncodedField` | short form needs a class |
| `.enum Ltype;->NAME:Type` | `EncodedEnum` | field reference |
| `Ltype;->m()V`, `m()V` | `EncodedMethod` | short form needs a class |
| `kind@field-or-method` | `EncodedMethodHandle` | `static-get`, `invoke-static`, … |
| `(IJ)V` | `EncodedMethodType` | |

The value class follows the literal, not the declared type: `= 3` on a `J` field is an
`EncodedInt` unless it is written `3L`. A value outside the range of its literal throws
`NumberFormatException`; a malformed token such as `+1`, `1234e` or `'ab'` is a syntax error.

## Link resolution

| Form | Example |
|---|---|
| long | `Ldemo/Box;->size:I`, `Ldemo/Box;->grow(I)V` |
| short | `size:I`, `grow(I)V` |

The short form takes its declaring class from the class block being parsed. That is the whole rule:
`parse` and `parseClass` always have a class and accept both forms, while `parseMethod`,
`parseField` and `parseAnnotation` have none and throw
`IllegalArgumentException` (a short-form member reference needs an enclosing class). It covers
every place a member reference appears: annotation values, method-handle literals, field and invoke
instructions, call-site bootstrap methods. Type references, strings, prototypes and `.param`
registers are unaffected.

## Error model

All five methods throw `SmaliSyntaxException` (a subclass of `IllegalArgumentException`) for
anything that is not grammatical; the rest of the failures come from the model. The position is
1-based (`getLine()`/`getColumn()`, `0` when unknown) and the message is `line L:C: detail`.
The lexer runs to the end before the parser, so an invalid escape (`'\uu0041'`, `'\q'`) is fatal
instead of producing a literal. Any dot that does not begin a known directive spelling (an
unknown directive, a dotted member name such as `.field public a.b:I` or `Ltest/A;->a.b:I`,
or a lone `.`) is a fatal lexer error at that dot's 1-based position; `.end  field` with two
spaces fails the same way at its dot. The detail is the generic `token recognition error`, and
the quoted fragment it carries is not part of the contract. A directive spelled slightly too
long (`.classx`) lexes as the known directive plus a simple name and fails in the parser at the
stray suffix. Numerics (`.5`) and the `..` range separator are unaffected, nothing is silently
truncated, and nothing is printed to stderr.

| Thrown | Meaning | Typical trigger |
|---|---|---|
| `SmaliSyntaxException` | the text is not grammatical, or the lexer rejects a token | empty input, missing `.end method`, a second register directive, trailing text, invalid escape, unknown directive |
| `IllegalArgumentException` | grammatical but invalid | short form without a class, bad `.param` register, initial value on an instance field, duplicate member or annotation, failed superclass or flag check, payload of the wrong kind, second `.catchall` for one range |
| `IllegalStateException` | the code cannot be assembled | unknown mnemonic, undefined label (apostrophe written as a backtick), zero branch offset |
| `IndexOutOfBoundsException` | the register file is inconsistent | a register past the file, a count above 65535 or below the input registers |
| `NumberFormatException` | a literal does not fit its type | `4294967296` as an int, `256t` as a byte |
| `UnsupportedOperationException` | an unimplemented legacy form | the `m-packed-switch` or `m-sparse-switch` instruction |

The exception keeps the ANTLR `RecognitionException` as its cause when there is one. Model-side
failures worth recognising: a concrete method without a register directive and an abstract or native
method with one, a missing superclass, an initial value on an instance field (the apostrophe may also
be written as a backtick), a duplicate annotation type, an out-of-range `.param` register, a payload
element wider than its kind, a duplicate sparse-switch key and a label with no packed-switch payload.

## What the parser does not keep

- Comments, blank lines, indentation and line breaks: the result is a model, not a document.
- Backticks: a quoted name is stored unquoted, so a member written with spaces keeps the spaces and
  loses the quotes; a backtick-quoted segment inside a descriptor is unquoted the same way.
- Labels and parameter registers: branches carry code-unit offsets and every register is absolute.
- The spelling of a short reference: it becomes the full `FieldId` or `MethodId` of the class.
- Text order for members: fields and methods land in the model's sorted sets, annotation elements in
  name order, while interfaces, parameters, instructions and debug items keep the order of the text
  (a repeated `.implements` of one type collapses to its first occurrence).
- Annotations of a field or parameter exist only when the `.end field` or `.end param` form was used.

## Printing

`print`, `printClass` and `printMethod` return the text of a whole dex, one class block or one
method block, each with an optional `SmaliOptions`. `print` separates the class blocks with a blank
line; `printMethod` always writes long member references, because a lone method has no enclosing
class, so `implicitReferences` has no effect there. A class block carries the section comments
`# interfaces`, `# annotations`, `# static fields`, `# instance fields`, `# direct methods`,
`# virtual methods`; a body separates instruction groups with a blank line and prints
`.param pN, "name"    # type` (p0 is `this` of an instance method, the first parameter is `p1`).
A name that is not a bare simple name prints backtick-quoted; the quoted form admits only simple
characters and a space, so a name with a space round-trips and a name with any other non-simple
character does not re-parse.

| Option | Default | Effect when on |
|---|---|---|
| `parameterRegisters` | `true` | registers of the incoming zone print as `pN`; off prints plain `vN` |
| `useLocals` | `false` | bodies open with `.locals` (count without the incoming parameters) instead of `.registers` (total) |
| `sequentialLabels` | `false` | labels are numbered per prefix in address order instead of named after their address |
| `debugInfo` | `true` | prints `.line`, `.local`, `.param` names, `.prologue`, `.epilogue`, in-method `.source`; off drops them |
| `codeOffsets` | `false` | a `#@0xN` comment with the code-unit address precedes every instruction |
| `relativeOffsets` | `false` | branch, payload and switch-element targets print as `+0xN`/`-0xN` relative to the referring instruction, try ranges relative to the code start; any code prints live, but the grammar has no relative target form, so the result does not re-parse |
| `implicitReferences` | `false` | field and method references whose declaring class is the printed class take the short `name:Type` form |

Round trip: the printed text re-parses to an **equal** model when the bodies are in the canonical
layout this library emits - payloads at the end of the instruction list behind an alignment `nop`
(that `nop` is dropped; re-parse regenerates it), debug items and try ranges on instruction
boundaries. Debug items print in model list order, and a try block with several handlers prints one
`.catch`/`.catchall` line per handler over the same range, after the last covered instruction.
Content the label grammar cannot express prints commented out with a `nop` in place of the
instruction, so the output still re-parses: `m-packed-switch`/`m-sparse-switch`,
branch or payload targets that hit no instruction boundary, try ranges that cannot be placed on
boundaries (their `.catch` line prints commented), orphan and non-reassemblable payloads (packed
keys not the sequence `first, first+1, ...` or a key range overflowing an `int`), and the `.param`
block of a bodiless method. Such input, a genuinely shared payload (the second switch gets a
duplicated copy) and degenerate switch tables (empty, single key `0`, sparse with consecutive
keys) print valid text that re-parses to a different, canonicalized model. Raw instructions
print live and re-parse: the eight mnemonics `raw`, `raw-aligned`, `raw-ref`, `raw-ref/jumbo`,
`wrapper-raw`, `wrapper-raw-aligned`, `wrapper-raw-ref`, `wrapper-raw-ref/jumbo`; the `0x`
forms take one 16-bit literal in any integral spelling, range-checked (`IllegalArgumentException`
on overflow), the `0c` forms take one reference of any kind, including a class def as
`@Ltest/T;` and the `inline@0xN`/`vtable@0xN`/`field@0xN` index forms, and a printed raw-ref
renders its raw index as `field@0xN`.

The odex verification-failure instructions print live and re-parse as
`throw-verification-error <error>, <reference>` and `throw-verification-error/jumbo <error>,
<reference>` - the error is a `VerificationError` name (`generic-error` … `instantiation-error`),
the reference a type, field or method reference in long or short form; `no-error` is not a name
and fails as a syntax error. Odex raw-index references print as `inline@0x2a`, `vtable@0xf9` or
`field@0x8` - hex, the prefix chosen by opcode family (`execute-inline*`, quick invokes, quick
fields); every raw-index opcode accepts all three spellings on parse. Labels are named by prefix and lowercase hex address:
`goto_`, `cond_`, `pswitch_data_`, `sswitch_data_`, `pswitch_`, `sswitch_`, `array_`,
`try_start_`, `try_end_`, `catch_`, `catchall_` (`:cond_9`, `:pswitch_data_12`);
`sequentialLabels` replaces the address with the per-prefix counter.

## com.v7878.dex.smali.SmaliIO

`public class SmaliIO`

Invariant: stateless; every `parse*` method consumes the whole string, since trailing text that
cannot continue the element is an error, and every `print*` method returns the complete text. Calls
are independent, so the same text may be parsed concurrently. `parse` keeps the text order of the
classes and performs no duplicate check, so two blocks with one descriptor both appear; members
inside a class are collected into the model's sorted sets. Repeated `.implements` directives of one
type produce one interface: the first occurrence wins and the order of first occurrences is kept.

**Constructors**

| Signature |
|---|
| `public SmaliIO()` |

**Methods**

| Signature | Note |
|---|---|
| `public static Dex parse(String smali)` | one or more class blocks; classes in text order |
| `public static ClassDef parseClass(String smali)` | exactly one class block |
| `public static MethodDef parseMethod(String smali)` | exactly one method block |
| `public static FieldDef parseField(String smali)` | exactly one field declaration |
| `public static Annotation parseAnnotation(String smali)` | exactly one annotation block |
| `public static String print(Dex dex)` | default options; every class in model order |
| `public static String print(Dex dex, SmaliOptions options)` | class blocks separated by a blank line |
| `public static String printClass(ClassDef cls)` | default options; one class block |
| `public static String printClass(ClassDef cls, SmaliOptions options)` | one class block; the round-trip contract of `Printing` |
| `public static String printMethod(MethodDef method)` | default options; one method block, long references only |
| `public static String printMethod(MethodDef method, SmaliOptions options)` | `implicitReferences` ignored |

## com.v7878.dex.smali.SmaliOptions

`public final class SmaliOptions`

Invariant: immutable; the two constructors are private, so instances come from `defaultOptions()`
and `withXxx` copies. The seven switches are independent and no combination is invalid.

**Methods**

| Signature | Note |
|---|---|
| `public static SmaliOptions defaultOptions()` | the defaults of the `Printing` table |
| `public boolean hasParameterRegisters()` | |
| `public SmaliOptions withParameterRegisters(boolean parameterRegisters)` | |
| `public boolean hasUseLocals()` | |
| `public SmaliOptions withUseLocals(boolean useLocals)` | |
| `public boolean hasSequentialLabels()` | |
| `public SmaliOptions withSequentialLabels(boolean sequentialLabels)` | |
| `public boolean hasDebugInfo()` | |
| `public SmaliOptions withDebugInfo(boolean debugInfo)` | |
| `public boolean hasCodeOffsets()` | |
| `public SmaliOptions withCodeOffsets(boolean codeOffsets)` | |
| `public boolean hasRelativeOffsets()` | |
| `public SmaliOptions withRelativeOffsets(boolean relativeOffsets)` | |
| `public boolean hasImplicitReferences()` | |
| `public SmaliOptions withImplicitReferences(boolean implicitReferences)` | |

## com.v7878.dex.smali.SmaliSyntaxException

`public class SmaliSyntaxException extends IllegalArgumentException`

Line and column are 1-based; `0` means the position is unknown. The message is `line L:C: detail`,
or `detail` alone when the position is unknown.

**Constructors**

| Signature | Note |
|---|---|
| `public SmaliSyntaxException(String detail)` | no position; both getters return `0` |
| `public SmaliSyntaxException(int line, int column, String detail)` | 1-based position |
| `public SmaliSyntaxException(int line, int column, String detail, Throwable cause)` | keeps the ANTLR cause |

**Methods**

| Signature | Note |
|---|---|
| `public int getLine()` | 1-based line, `0` when unknown |
| `public int getColumn()` | 1-based column, `0` when unknown |

## Example

```java
ClassDef ping = SmaliIO.parseClass("""
        .class public final Ldemo/Ping;
        .super Ljava/lang/Object;
        .source "Ping.java"

        .field public static final TAG:Ljava/lang/String; = "ping"

        .method public static tag()Ljava/lang/String;
            .registers 1
            sget-object v0, TAG:Ljava/lang/String;
            return-object v0
        .end method
        """);
System.out.println(ping.getSourceFile());   // Ping.java
System.out.println(ping.findStaticField("TAG", TypeId.of("Ljava/lang/String;"))
        .getInitialValue().getValueType()); // STRING
String text = SmaliIO.printClass(ping);
System.out.println(SmaliIO.parseClass(text).equals(ping)); // true - canonical model round trip
```

Up: [SKILL.md](../SKILL.md)
