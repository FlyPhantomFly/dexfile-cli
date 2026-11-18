# Sorted primitive collections

`com.v7878.collections` holds four containers that keep a primitive key or element in a sorted
array: `IntMap<E>` (`int` key plus value), `IntSet`, `LongMap<E>` (`long` key plus value) and
`LongSet`. They replace `HashMap<Integer, ...>` for address, register and pool-index domains,
where boxing costs and order matters.

## Shared contract

| Aspect | Contract |
|---|---|
| Order | keys or elements ascend; index `0` is smallest, `size() - 1` largest; iterators, arrays, `stream()`, `toString()` and every `...At` follow |
| `~insertion_point` | `indexOfKey`/`indexOf` return the index or `~(insertion point)` as `Arrays.binarySearch`; `~result` is where the key would go (`size()` past the end), never `-1` |
| Bounds | every index accessor checks `0 <= index < size()` (`Objects.checkIndex`) and throws `IndexOutOfBoundsException`; ranges are half-open: `from == to` is a no-op, an inverted key/value range is a no-op returning `false`, an inverted index range throws `IndexOutOfBoundsException` |
| Changed | `removeRange`, `addRange`, `removeAtRange` and the set `addAll` overloads return `true` only when something was actually inserted or removed |
| Empty | `firstKey`, `lastKey`, `firstValue`, `lastValue`, `firstEntry`, `lastEntry`, `first`, `last` throw `NoSuchElementException` |
| Null | maps allow `null` values, so a stored `null` and an absent key both read `null`; `get(key, default)` separates them only for a non-null default; sets hold no `null`; a null `putAll`/`addAll` throws `NullPointerException` |
| Freeze | `freeze()` trims, seals and returns `this`; mutators then throw `UnsupportedOperationException`, readers still work; idempotent |
| Copy | `duplicate()` returns `this` when frozen and a writable copy when mutable; the copy constructor always yields a writable copy; no array sharing |
| Equality | positional and class-bound: same keys or elements in order with the same values; `hashCode()` is `contentHashCode()`; never equal to a `HashMap`/`HashSet` |
| Hash | map: `h = 31*h + key`, then `h = 31*h + Objects.hashCode(value)`; set: `h = 31*h + value`; `long` variants fold through `Long.hashCode` |
| Iterator | an index cursor, not fail-fast; `next()` past the end throws `NoSuchElementException`; on a frozen container `remove()` throws `UnsupportedOperationException` before any `lastRet` check, otherwise before `next()` or twice it throws `IllegalStateException`; entries are snapshots |

## com.v7878.collections.IntMap

`public final class IntMap<E> implements Iterable<IntMap.Entry<E>>`

Invariant: the live region `0 .. size - 1` strictly ascends with no duplicate keys;
`values.length == keys.length`; a removal leaves a marker until the next compacting call.

**Constructors**

| Signature | Note |
|---|---|
| `public IntMap()` | |
| `public IntMap(int capacity)` | |
| `public IntMap(IntMap<E> other)` | |

**Methods**

| Signature | Note |
|---|---|
| `public static <T> IntMap<T> empty()` | |
| `public IntMap<E> duplicate()` | |
| `public IntMap<E> freeze()` | |
| `public void ensureCapacity(int min_capacity)` | |
| `public boolean trimToSize()` | |
| `public void clear()` | |
| `public int size()` | compacts tombstones |
| `public boolean isEmpty()` | |
| `public int indexOfKey(int key)` | `~(insertion point)` when absent |
| `public boolean contains(int key)` | a stored `null` value counts as present |
| `public E get(int key)` | |
| `public E get(int key, E valueIfKeyNotFound)` | |
| `public E put(int key, E value)` | |
| `public E append(int key, E value)` | a key above the last (or an empty map) appends without a search; otherwise `put` |
| `public void putAll(IntMap<? extends E> other)` | |
| `public E replace(int key, E value)` | writes only when the key is mapped; a removed key is unmapped |
| `public E putIfAbsent(int key, E value)` | inserts only when absent; a stored `null` counts as present and is returned |
| `public E computeIfAbsent(int key, IntFunction<? extends E> mapper)` | mapper at most once; a `null` result is stored, unlike `java.util.Map` |
| `public E remove(int key)` | |
| `public E removeAt(int index)` | the removed value; `null` when it was `null` |
| `public boolean removeRange(int from_key, int to_key)` | half-open on keys; `true` only when a live pair was in range |
| `public boolean removeAtRange(int from_index, int to_index)` | `true` for any non-empty range |
| `public int keyAt(int index)` | |
| `public int firstKey()` | |
| `public int lastKey()` | |
| `public int[] keysArray()` | |
| `public IntSet keysSet()` | independent mutable copy |
| `public E valueAt(int index)` | |
| `public E firstValue()` | |
| `public E lastValue()` | |
| `public E setValueAt(int index, E value)` | |
| `public <T> T[] valuesArray(IntFunction<T[]> factory)` | short array fails in the copy |
| `public Object[] valuesArray()` | |
| `public IntMap.Entry<E> entryAt(int index)` | |
| `public IntMap.Entry<E> firstEntry()` | |
| `public IntMap.Entry<E> lastEntry()` | |
| `public int indexOfValue(E value)` | identity `==`, else `-1` |
| `public int indexOfValueByValue(E value)` | `Objects.equals`, else `-1` |
| `public String toString()` | |
| `public boolean contentEquals(IntMap<?> other)` | |
| `public boolean equals(Object)` | |
| `public int contentHashCode()` | |
| `public int hashCode()` | |
| `public Iterator<IntMap.Entry<E>> iterator()` | |

### com.v7878.collections.IntMap.Entry

`public record Entry<T>(int key, T value)`

Invariant: an immutable snapshot; equality ignores the producing map; `value` may be `null`.

**Constructors**

| Signature | Note |
|---|---|
| `public Entry(int key, T value)` | |

**Methods**

| Signature | Note |
|---|---|
| `public int key()` | |
| `public T value()` | |
| `public final boolean equals(Object)` | |
| `public final int hashCode()` | |
| `public final String toString()` | `Entry[key=..., value=...]` |

## com.v7878.collections.IntSet

`public final class IntSet implements Iterable<Integer>`

Invariant: the live region strictly ascends with no gaps or markers; every removal shifts the tail,
so slots at or beyond `size` are unreachable stale numbers.

**Constructors**

| Signature | Note |
|---|---|
| `public IntSet()` | |
| `public IntSet(int initialCapacity)` | |
| `public IntSet(IntSet other)` | |

**Methods**

| Signature | Note |
|---|---|
| `public static IntSet empty()` | |
| `public IntSet duplicate()` | |
| `public IntSet freeze()` | |
| `public void ensureCapacity(int minCapacity)` | |
| `public boolean trimToSize()` | |
| `public void clear()` | |
| `public int size()` | |
| `public boolean isEmpty()` | |
| `public int indexOf(int value)` | `~(insertion point)` when absent |
| `public boolean contains(int value)` | |
| `public boolean add(int value)` | `true` when absent |
| `public boolean addRange(int from_value, int to_value)` | half-open on values; a range no array could hold throws `OutOfMemoryError` before any mutation |
| `public boolean addAll(IntSet other)` | `true` when at least one value was inserted |
| `public boolean addAll(int[] other)` | `true` when at least one value was inserted |
| `public boolean remove(int value)` | shifts the tail at once |
| `public void removeAt(int index)` | |
| `public boolean removeRange(int from_value, int to_value)` | half-open on values; shifts the tail at once |
| `public boolean removeAtRange(int from_index, int to_index)` | `true` for any non-empty range |
| `public int at(int index)` | |
| `public int first()` | |
| `public int last()` | |
| `public int[] toArray()` | |
| `public IntStream stream()` | bound to the array at call time |
| `public String toString()` | |
| `public boolean contentEquals(IntSet other)` | |
| `public boolean equals(Object)` | |
| `public int contentHashCode()` | |
| `public int hashCode()` | |
| `public Iterator<Integer> iterator()` | |

## com.v7878.collections.LongMap

`public final class LongMap<E> implements Iterable<LongMap.Entry<E>>`

Invariant: keys ascend in signed `long` order, so a key with the high bit set sorts first; storage
and index rules are `IntMap`'s.

**Constructors**

| Signature | Note |
|---|---|
| `public LongMap()` | |
| `public LongMap(int capacity)` | |
| `public LongMap(LongMap<E> other)` | |

**Methods**

| Signature | Note |
|---|---|
| `public static <T> LongMap<T> empty()` | |
| `public LongMap<E> duplicate()` | |
| `public LongMap<E> freeze()` | |
| `public void ensureCapacity(int min_capacity)` | |
| `public boolean trimToSize()` | |
| `public void clear()` | |
| `public int size()` | compacts tombstones |
| `public boolean isEmpty()` | |
| `public int indexOfKey(long key)` | `~(insertion point)` when absent |
| `public boolean contains(long key)` | a stored `null` value counts as present |
| `public E get(long key)` | |
| `public E get(long key, E valueIfKeyNotFound)` | |
| `public E put(long key, E value)` | |
| `public E append(long key, E value)` | a key above the last (or an empty map) appends without a search; otherwise `put` |
| `public void putAll(LongMap<? extends E> other)` | |
| `public E replace(long key, E value)` | writes only when the key is mapped; a removed key is unmapped |
| `public E putIfAbsent(long key, E value)` | inserts only when absent; a stored `null` counts as present and is returned |
| `public E computeIfAbsent(long key, LongFunction<? extends E> mapper)` | mapper at most once; a `null` result is stored, unlike `java.util.Map` |
| `public E remove(long key)` | |
| `public E removeAt(int index)` | the removed value; `null` when it was `null` |
| `public boolean removeRange(long from_key, long to_key)` | half-open on keys; `true` only when a live pair was in range |
| `public boolean removeAtRange(int from_index, int to_index)` | `true` for any non-empty range |
| `public long keyAt(int index)` | |
| `public long firstKey()` | |
| `public long lastKey()` | |
| `public long[] keysArray()` | |
| `public LongSet keysSet()` | independent mutable copy |
| `public E valueAt(int index)` | |
| `public E firstValue()` | |
| `public E lastValue()` | |
| `public E setValueAt(int index, E value)` | |
| `public <T> T[] valuesArray(IntFunction<T[]> factory)` | short array fails in the copy |
| `public Object[] valuesArray()` | |
| `public LongMap.Entry<E> entryAt(int index)` | |
| `public LongMap.Entry<E> firstEntry()` | |
| `public LongMap.Entry<E> lastEntry()` | |
| `public int indexOfValue(E value)` | identity `==`, else `-1` |
| `public int indexOfValueByValue(E value)` | `Objects.equals`, else `-1` |
| `public String toString()` | |
| `public boolean contentEquals(LongMap<?> other)` | |
| `public boolean equals(Object)` | |
| `public int contentHashCode()` | |
| `public int hashCode()` | |
| `public Iterator<LongMap.Entry<E>> iterator()` | |

### com.v7878.collections.LongMap.Entry

`public record Entry<T>(long key, T value)`

Invariant: an immutable snapshot; equality ignores the producing map; `value` may be `null`.

**Constructors**

| Signature | Note |
|---|---|
| `public Entry(long key, T value)` | |

**Methods**

| Signature | Note |
|---|---|
| `public long key()` | |
| `public T value()` | |
| `public final boolean equals(Object)` | |
| `public final int hashCode()` | |
| `public final String toString()` | `Entry[key=..., value=...]` |

## com.v7878.collections.LongSet

`public final class LongSet implements Iterable<Long>`

Invariant: values ascend in signed `long` order; deletion is physical, so an index is valid only
until the next mutation.

**Constructors**

| Signature | Note |
|---|---|
| `public LongSet()` | |
| `public LongSet(int initialCapacity)` | |
| `public LongSet(LongSet other)` | |

**Methods**

| Signature | Note |
|---|---|
| `public static LongSet empty()` | |
| `public LongSet duplicate()` | |
| `public LongSet freeze()` | |
| `public void ensureCapacity(int minCapacity)` | |
| `public boolean trimToSize()` | |
| `public void clear()` | |
| `public int size()` | |
| `public boolean isEmpty()` | |
| `public int indexOf(long value)` | `~(insertion point)` when absent |
| `public boolean contains(long value)` | |
| `public boolean add(long value)` | `true` when absent |
| `public boolean addRange(long from_value, long to_value)` | half-open on values; a range no array could hold throws `OutOfMemoryError` before any mutation |
| `public boolean addAll(LongSet other)` | `true` when at least one value was inserted |
| `public boolean addAll(long[] other)` | `true` when at least one value was inserted |
| `public boolean remove(long value)` | shifts the tail at once |
| `public void removeAt(int index)` | |
| `public boolean removeRange(long from_value, long to_value)` | half-open on values; shifts the tail at once |
| `public boolean removeAtRange(int from_index, int to_index)` | `true` for any non-empty range |
| `public long at(int index)` | |
| `public long first()` | |
| `public long last()` | |
| `public long[] toArray()` | |
| `public LongStream stream()` | bound to the array at call time |
| `public String toString()` | |
| `public boolean contentEquals(LongSet other)` | |
| `public boolean equals(Object)` | |
| `public int contentHashCode()` | |
| `public int hashCode()` | |
| `public Iterator<Long> iterator()` | |

## Tombstone versus physical deletion

| | maps | sets |
|---|---|---|
| a removal writes | a deleted marker in the value slot | a shift of the surviving tail |
| visible to readers | `get`/`contains` read a removed key as absent; the pair leaves the array on the next compacting call (`size()`, `indexOfKey`, an index accessor, iteration) | during the `remove` call |
| `size()` | compacts pending tombstones | plain field read |
| `trimToSize()`/`freeze()` | compacts, then shrinks | shrinks |

The marker is never returned and never survives a frozen map. A `put`, `putIfAbsent` or
`computeIfAbsent` whose key lands on a tombstoned slot revives it in place, keeping the keys
ascending. The trap is a repeated index:
`removeAt(1)` twice on `{0, 1, 2, 3}` leaves `{0, 3}`, because the second call compacts first and
removes the element that moved into index 1; remove a run from the highest index down, or call
`removeAtRange` once.

## Index invalidation

An index is valid only until the next call. A `put`/`add` shifts the tail right at the insertion
point, so later indexes grow by one; replacing an existing value keeps the index; a removal shifts
left or leaves a tombstone for the next compacting call. Re-derive with `indexOfKey`/`indexOf`.

## Which one to choose

| Need | Use |
|---|---|
| `int` key that carries a value | `IntMap<E>` |
| `int` values only, no payload | `IntSet` |
| key or value wider than 32 bits, packed pair, content hash | `LongMap<E>` / `LongSet` |
| ascending traversal plus positional access | any of the four (`iterator()`, `...At`) |
| a `java.util.Map`/`Set` for another API | export with `keysArray()`/`toArray()` and build one |

## Recipe: address index with floor and ceiling

```java
IntMap<String> labels = new IntMap<>();
labels.append(0x0000, "entry");
labels.append(0x0004, "loop");
labels.append(0x0010, "exit");

int probe = 0x0006;
int index = labels.indexOfKey(probe);
int floor = index >= 0 ? index : ~index - 1;
int ceiling = index >= 0 ? index : ~index;
System.out.println(labels.valueAt(floor));        // loop
System.out.println(ceiling < labels.size() ? labels.valueAt(ceiling) : "<none>"); // exit
System.out.println(labels.get(probe, "<branch target>")); // <branch target>
labels.freeze();
```

## Recipe: register set and removal while iterating

```java
IntSet registers = new IntSet();
registers.addAll(new int[]{3, 1, 2, 1});
registers.addAll(new int[]{0, 4});
System.out.println(registers);                    // {0, 1, 2, 3, 4}

IntSet wide = new IntSet(registers);
wide.removeAtRange(1, 3);
System.out.println(wide);                         // {0, 3, 4}

Iterator<Integer> it = registers.iterator();
while (it.hasNext()) {
    if (it.next() >= 3) {
        it.remove();
    }
}
registers.freeze();
System.out.println(registers);                    // {0, 1, 2}
```

Up: [SKILL.md](../SKILL.md)
