# ClassPath: static resolution and dispatch

`ClassPath` answers what a linker answers before code runs: which definition an id denotes, whether the
access is legal, and which method an invoke reaches. The class set is a `Function<TypeId, ClassDef>`;
`null` means "outside my class path" and is the source of every undeterminable answer. Results are
cached per query key, so treat the mapper as fixed.

## com.v7878.dex.analysis.ClassPath

`public class ClassPath`

Invariant: every entry point normalises an array `TypeId` to `java.lang.Object`.

**Constructors** — `public ClassPath(Function<TypeId, ClassDef> mapper)`

**Methods**

| Signature | Note |
|---|---|
| `public TypeResolver getTypeResolver()` | |
| `public ClassDef resolveClass(TypeId type)` | raw mapper call, no normalisation or caching |
| `public Result<ResolvedField> resolveField(FieldId fid)` | JLS lookup, no access checks |
| `public Result<ResolvedField> resolveField(FieldId fid, TypeId referrer, FieldAccess access)` | access and final-write checks |
| `public Result<ResolvedMethod> resolveMethod(MethodId mid)` | no access checks |
| `public Result<ResolvedMethod> resolveMethod(MethodId mid, TypeId referrer, InvokeType type)` | full checks |
| `public Result<CallTarget> findMethodToCall(MethodId mid, TypeId referrer, InvokeType type, TypeId receiver)` | virtual and interface dispatch |
| `public Result<CallTarget> findSuperMethodToCall(MethodId mid, TypeId referrer, TypeId receiver)` | super dispatch |
| `public Result<CallTarget> findNonVirtualMethodToCall(MethodId mid, TypeId referrer, InvokeType type)` | static and direct target |

## resolveField(FieldId)

The JLS lookup for class `C`: `C` itself (instance and static fields), then its direct interfaces in
declaration order (static fields only, recursively), then its superclass and its interfaces, up the
chain; visited types are remembered, so a cycle terminates. Outcomes: a link failure is
`fail(true, NO_CLASS_DEF, message)`, an unknown declaring class is `unknown()`, a hit is
`ok(exact, ResolvedField)`, a miss is `fail(exact, NO_SUCH_FIELD, null)`.

A hit *before* an unknown class is exact, because the unknown part lies further down the order and
cannot shadow it; an unknown class met after the start class makes every later answer, including
the miss, inexact. A class fails
to link when its hierarchy is cyclic, its superclass is an interface, it implements a non-interface, it
overrides a final method, it is both `abstract` and `final`, it is a `final` interface (ART treats an
interface as abstract even without `ACC_ABSTRACT`, so the bit does not matter) or it defines too many
methods; the failure propagates to subclasses and implementors.

## resolveField(FieldId, TypeId, FieldAccess)

| Step | Check | Failure |
|---|---|---|
| 1 | the referrer class links | `NO_CLASS_DEF`, exact |
| 2 | JLS resolution | the inner exactness, error and message, or the undeterminable state (inexact, no value, error NONE) |
| 3 | field kind matches the access (static for SGET/SPUT) | `INCOMPATIBLE_CLASS_CHANGE` (a static access to an instance field) |
| 4 | access to the declaring class, `FieldId` class as escape hatch | `ILLEGAL_ACCESS` when both are denied, else inexact |
| 5 | access to the member by its flags | `ILLEGAL_ACCESS`, or inexact for protected |
| 6 | a write does not touch a foreign final field | `ILLEGAL_ACCESS` |
| 7 | otherwise | `ok(exact, ResolvedField)` |

The kind check (step 3) comes *before* the access checks, the reverse of the method order. Step 4 asks
two questions — access to the class that really declares the field, and to the class named by the
`FieldId` — and either being true is enough, because the field may be reachable through a public
subclass. An unknown target class makes access fail-open: the value is kept, `exact` becomes false.

Step 5 is `CanAccessMember`: same class or public is allowed, private is denied, protected is allowed
in the same package and otherwise needs the one undeterminable assignability check, package-private
needs the same package. Step 6 is `CanBeChangedBy`: a write is legal unless the field is final in
another declaring class.

## resolveMethod(MethodId)

A class search is declared methods, then the superclass chain, then the copied methods; a method that
is not inherited (a private superclass method, or a package-private one from another package) is
returned only when nothing was found in the chain or among the copies. An interface search is the
interface's own declared methods, then the iftable entries, then `java.lang.Object`'s public
non-static methods. A link failure is `NO_CLASS_DEF` exact, an unknown class is `unknown()`, a hit is
`ok(exact, ResolvedMethod)`, a miss is `NO_SUCH_METHOD`.

A hit among the start class's own declarations is exact; copied, uninherited and miss answers inherit
the class exactness.

## resolveMethod(MethodId, TypeId, InvokeType)

| Step | Check | Failure |
|---|---|---|
| 1 | the reference class links | `NO_CLASS_DEF`, exact |
| 2 | it is known | `unknown()` |
| 3 | the referrer class links | `NO_CLASS_DEF`, exact, referrer text |
| 4 | `INTERFACE` needs an interface reference class | `INCOMPATIBLE_CLASS_CHANGE` (the reference class is a class, not an interface) |
| 5 | `VIRTUAL` needs a class reference class | `INCOMPATIBLE_CLASS_CHANGE` (the reference class is an interface, not a class) |
| 6 | search | see resolveMethod(MethodId) |
| 7 | access to the declaring class, `MethodId` class as escape hatch | `ILLEGAL_ACCESS`, or inexact |
| 8 | access to the member | `ILLEGAL_ACCESS`, or inexact for protected |
| 9 | `CheckIncompatibleClassChange` | `INCOMPATIBLE_CLASS_CHANGE` |
| 10 | otherwise | `ok(exact, ResolvedMethod)` |

Only `VIRTUAL` and `INTERFACE` get the reference-class mismatch check: `SUPER` and `STATIC` do not, and
`DIRECT` is legal on a modern-dex interface. When the search fails, an interface reference class gets
one retry through the class search; a hit there is `INCOMPATIBLE_CLASS_CHANGE` with the method in the
message (a private or static interface method through `invoke-interface`), otherwise
`NO_SUCH_METHOD` (the method is absent from the class).

Step 9, per `InvokeType`: `STATIC` is incompatible when the method is not static; `DIRECT` when it is
not direct or is static; `VIRTUAL` when it is direct or a declared interface method; `SUPER` when it is
a constructor or static; `INTERFACE` when it is direct, or its declarer is neither an interface nor
`java.lang.Object`.

An unknown referrer does not make the answer unknown: steps 7 and 8 decide the public, private and
package-private branches from descriptors and flags, so the result stays exact; only the protected
branch, and an access check against an unknown target class, degrades to `exact == false` with access
allowed.

## findMethodToCall

`type` must be `VIRTUAL` or `INTERFACE`, else `IllegalArgumentException` (a static or direct call
goes to `findNonVirtualMethodToCall`, a super call to `findSuperMethodToCall`). `referrer` is the
class containing the call, `receiver` the runtime class of the receiver.

| Step | What happens |
|---|---|
| 1 | resolve with the full checks; a failure is returned as is |
| 2 | link the receiver: a link error is `NO_CLASS_DEF` exact, an unknown receiver is `unknown()` |
| 3 | an interface receiver is `INCOMPATIBLE_CLASS_CHANGE` |
| 4 | `VIRTUAL` takes the receiver vtable at the resolved vtable index; `INTERFACE` takes the itable entry, except a `java.lang.Object` method, which takes the vtable |
| 5 | no dispatch entry for the method in the receiver is `INCOMPATIBLE_CLASS_CHANGE` |
| 6 | the target is wrapped with its execution error |

Exactness is `resolution.exact() && receiver.exact`, so an incomplete receiver hierarchy makes even a
successful dispatch inexact. `findNonVirtualMethodToCall` accepts `STATIC` and `DIRECT` only (else
`IllegalArgumentException`), and has no receiver, no dispatch and no miranda walk, so an abstract
target reports `ABSTRACT_METHOD`.

## findSuperMethodToCall

Resolution runs with `InvokeType.SUPER`; the dispatch depends on the reference class.

| Order | Check |
|---|---|
| 1 | resolve with the full checks; a failure is returned as is |
| 2 | referrer assignable to the reference class, else `NO_SUCH_METHOD`; an undeterminable relation is inexact and continues |
| 3 | interface reference class: the resolved method must be declared by an interface, else `INCOMPATIBLE_CLASS_CHANGE`; the target comes from the interface's own methods, its conflict copies, then a reverse iftable walk with JLS domination, else `NO_SUCH_METHOD` |
| 4 | class reference class with an unknown referrer: `unknown()` |
| 5 | class reference class: the superclass vtable at the resolved index, else `NO_SUCH_METHOD` |

`receiver` never influences the dispatch; it only feeds the execution error, because the miranda walk
runs along the receiver's superclass chain. An array receiver is normalised to `Object`.

## Target error and accuracy

A default-conflict entry becomes `CallTarget.DefaultConflict`; any other non-invokable target gets its
error from the receiver (unknown, unlinked or incomplete sets inexact and skips the miranda walk) and
is wrapped as `CallTarget.Method`, so "would throw `AbstractMethodError`" is representable. The miranda
walk takes the same name and proto along the receiver's superclass chain: non-public is
`ILLEGAL_ACCESS`, public abstract is `ABSTRACT_METHOD`, public concrete is looked past.

`exact() == false` means "the runtime could answer differently because the hierarchy is incomplete",
never "wrong".

| Query | inexact when |
|---|---|
| `resolveField` | an unknown class after the start class was visited before the hit or miss, or an access check was undeterminable |
| `resolveMethod` | the search or an access check was inexact |
| `findMethodToCall` | the resolution or the receiver hierarchy is inexact |
| `findSuperMethodToCall` | the assignability check, referrer superclass or interface walk was undeterminable |
| any dispatch, target not invokable | the receiver is unknown, unlinked or incomplete |

`NO_CLASS_DEF` is always exact: a bigger class set cannot rescue a class that fails to link. An
`INCOMPATIBLE_CLASS_CHANGE` answer keeps the exactness it was produced with. Hiddenapi flags are
ignored, IMTs are not modelled, and `invoke-polymorphic`/`invoke-custom` are absent from `InvokeType`.

## com.v7878.dex.analysis.ClassPath.ResolutionError

`public enum ClassPath.ResolutionError`

A resolution-stage error: no value is returned. Execution-stage errors are `InvocationError`.

**Fields** — `public static final ResolutionError`: `NONE` `NO_SUCH_METHOD` `NO_SUCH_FIELD` `NO_CLASS_DEF` `ILLEGAL_ACCESS` `INCOMPATIBLE_CLASS_CHANGE`

**Methods**

| Signature | Note |
|---|---|
| `public static ResolutionError[] values()` | declaration order |
| `public static ResolutionError valueOf(String name)` | |

`NO_SUCH_METHOD` is `NoSuchMethodError`, `NO_SUCH_FIELD` `NoSuchFieldError`, `NO_CLASS_DEF`
`NoClassDefFoundError`, `ILLEGAL_ACCESS` `IllegalAccessError`, `INCOMPATIBLE_CLASS_CHANGE`
`IncompatibleClassChangeError`.

## com.v7878.dex.analysis.ClassPath.InvocationError

`public enum ClassPath.InvocationError`

An execution-stage error, returned with a found target. `NONE` means the call proceeds.

**Fields** — `public static final InvocationError`: `NONE` `ABSTRACT_METHOD` `ILLEGAL_ACCESS` `INCOMPATIBLE_CLASS_CHANGE`

**Methods**

| Signature | Note |
|---|---|
| `public static InvocationError[] values()` | declaration order |
| `public static InvocationError valueOf(String name)` | |

The three non-`NONE` values map to `AbstractMethodError`, `IllegalAccessError` and
`IncompatibleClassChangeError` (a default-method conflict).

## com.v7878.dex.analysis.ClassPath.Result

`public record ClassPath.Result<T>(boolean exact, T value, ClassPath.ResolutionError error, String message)`

| State | exact | value | error |
|---|---|---|---|
| success | true | the value | NONE |
| success on an incomplete hierarchy | false | the value | NONE |
| failure | true or false | null | not NONE |
| undeterminable | false | null | NONE |

**Constructors** — `public Result(boolean exact, T value, ClassPath.ResolutionError error, String message)`

**Constructors** — `public Result(boolean exact, T value)`

**Methods**

| Signature | Note |
|---|---|
| `public static <T> Result<T> ok(boolean exact, T value)` | success |
| `public static <T> Result<T> fail(boolean exact, ClassPath.ResolutionError error, String message)` | no value |
| `public static <T> Result<T> unknown()` | inexact; `value() == null`, error NONE |
| `public boolean exact()` | |
| `public T value()` | null on a failure or an undeterminable answer |
| `public ClassPath.ResolutionError error()` | |
| `public String message()` | details or null |
| `public boolean equals(Object)` | all four components |
| `public int hashCode()` | |
| `public String toString()` | |

## com.v7878.dex.analysis.ClassPath.ResolvedField

`public record ClassPath.ResolvedField(ClassDef declaringClass, FieldDef field)`

Both components are non-null (`NullPointerException`).

**Constructors** — `public ResolvedField(ClassDef declaringClass, FieldDef field)`

**Methods**

| Signature | Note |
|---|---|
| `public ClassDef declaringClass()` | |
| `public FieldDef field()` | |
| `public String toString()` | `Ldemo/Box;->size:I` |
| `public boolean equals(Object)` | both components |
| `public int hashCode()` | |

## com.v7878.dex.analysis.ClassPath.ResolvedMethod

`public record ClassPath.ResolvedMethod(ClassDef declaringClass, MethodDef method)`

Both components are non-null (`NullPointerException`). An interface copy keeps the *interface* as
declarer, not the class that received the copy.

**Constructors** — `public ResolvedMethod(ClassDef declaringClass, MethodDef method)`

**Methods**

| Signature | Note |
|---|---|
| `public ClassDef declaringClass()` | |
| `public MethodDef method()` | |
| `public boolean isDefault()` | interface declarer with a body |
| `public String toString()` | `Ldemo/Base;->size()I` |
| `public boolean equals(Object)` | both components |
| `public int hashCode()` | |

## com.v7878.dex.analysis.ClassPath.InvokeType

`public enum ClassPath.InvokeType`

**Fields** — `public static final InvokeType`: `STATIC` `DIRECT` `VIRTUAL` `SUPER` `INTERFACE`

**Methods**

| Signature | Note |
|---|---|
| `public static InvokeType[] values()` | declaration order |
| `public static InvokeType valueOf(String name)` | |

`STATIC`/`DIRECT` go to `findNonVirtualMethodToCall`, `VIRTUAL`/`INTERFACE` to `findMethodToCall`,
`SUPER` to `findSuperMethodToCall`. There is no polymorphic or custom constant.

## com.v7878.dex.analysis.ClassPath.FieldAccess

`public enum ClassPath.FieldAccess`

**Fields** — `public static final FieldAccess`: `IGET` `IPUT` `SGET` `SPUT`

**Methods**

| Signature | Note |
|---|---|
| `public static FieldAccess[] values()` | declaration order |
| `public static FieldAccess valueOf(String name)` | |

## com.v7878.dex.analysis.ClassPath.CallTarget

`public sealed interface ClassPath.CallTarget permits ClassPath.CallTarget.Method, ClassPath.CallTarget.DefaultConflict`

The outcome of a dispatch; the execution error belongs to the target.

**Methods**

| Signature | Note |
|---|---|
| `public abstract InvocationError invocationError()` | NONE when the call proceeds |

## com.v7878.dex.analysis.ClassPath.CallTarget.Method

`public record ClassPath.CallTarget.Method(ResolvedMethod method, InvocationError invocationError) implements ClassPath.CallTarget`

**Constructors** — `public Method(ResolvedMethod method, InvocationError invocationError)`

**Methods**

| Signature | Note |
|---|---|
| `public ResolvedMethod method()` | |
| `public InvocationError invocationError()` | |
| `public boolean isInvokable()` | error is NONE |
| `public boolean equals(Object)` | both components |
| `public int hashCode()` | |
| `public String toString()` | |

## com.v7878.dex.analysis.ClassPath.CallTarget.DefaultConflict

`public record ClassPath.CallTarget.DefaultConflict(List<ResolvedMethod> candidates) implements ClassPath.CallTarget`

A conflict between default methods; the list is copied with `List.copyOf`, so it is immutable and never
null.

**Constructors** — `public DefaultConflict(List<ResolvedMethod> candidates)`

**Methods**

| Signature | Note |
|---|---|
| `public List<ResolvedMethod> candidates()` | all unmasked defaults |
| `public InvocationError invocationError()` | always `INCOMPATIBLE_CLASS_CHANGE` |
| `public boolean equals(Object)` | candidates |
| `public int hashCode()` | |
| `public String toString()` | |

Up: [SKILL.md](../SKILL.md)
