package net.ccbluex.liquidbounce.features.module.modules.render
/**
 * Opal‑style ArrayList implementation tailored for **LiquidBounce‑Nextgen 0.39** modules.
 *
 * The original `java.util.ArrayList` brings a lot of byte‑code bloat which is
 * undesirable for a heavily obfuscated client.  This lightweight version offers
 * the most commonly used operations while staying fully compatible with Kotlin.
 *
 * Features:
 *  - Adjustable initial capacity (default 10)
 *  - `trimToSize()` to shrink the backing array after heavy removals
 *  - Implements `Iterable<T>` so it can be used in `for‑each` loops
 *  - Fluent‑style helpers (`addAll`, `removeIf`) reminiscent of the OpalV2 code
 *    base
 *  - No external dependencies – only plain Kotlin/JVM classes
 */
class OpalArrayList<T>(initialCapacity: Int = DEFAULT_CAPACITY) : Iterable<T> {
    /** Backing array storing elements. */
    private var data: Array<Any?> = arrayOfNulls(initialCapacity.coerceAtLeast(1))
    /** Number of valid items in [data]. */
    var size: Int = 0
        private set

    // ---------------------------------------------------------------------
    // Basic accessors
    // ---------------------------------------------------------------------
    /** Returns true if the list contains no elements. */
    fun isEmpty(): Boolean = size == 0

    /** Returns true if the list contains at least one element. */
    fun isNotEmpty(): Boolean = size > 0

    /** Retrieves the element at the given [index]. */
    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T {
        require(index in 0 until size) { "Index out of bounds: $index (size=$size)" }
        return data[index] as T
    }

    /** Replaces the element at [index] with [value] and returns the previous element. */
    @Suppress("UNCHECKED_CAST")
    fun set(index: Int, value: T): T {
        require(index in 0 until size) { "Index out of bounds: $index (size=$size)" }
        val old = data[index] as T
        data[index] = value
        return old
    }

    // ---------------------------------------------------------------------
    // Modification operations
    // ---------------------------------------------------------------------
    /** Appends [value] to the end of the list. */
    fun add(value: T) {
        ensureCapacity(size + 1)
        data[size++] = value
    }

    /** Inserts [value] at position [index] – shifting subsequent elements right. */
    fun add(index: Int, value: T) {
        require(index in 0..size) { "Index out of bounds: $index (size=$size)" }
        ensureCapacity(size + 1)
        if (index < size) {
            System.arraycopy(data, index, data, index + 1, size - index)
        }
        data[index] = value
        size++
    }

    /** Adds all elements from another collection. Returns this for fluent usage. */
    fun addAll(values: Collection<T>): OpalArrayList<T> {
        ensureCapacity(size + values.size)
        for (v in values) {
            data[size++] = v
        }
        return this
    }

    /** Removes the element at [index] and returns it. */
    @Suppress("UNCHECKED_CAST")
    fun removeAt(index: Int): T {
        require(index in 0 until size) { "Index out of bounds: $index (size=$size)" }
        val old = data[index] as T
        val moved = size - index - 1
        if (moved > 0) {
            System.arraycopy(data, index + 1, data, index, moved)
        }
        data[--size] = null
        return old
    }

    /** Removes the first occurrence of [value]. Returns true if something was removed. */
    fun remove(value: T): Boolean {
        for (i in 0 until size) {
            if (data[i] == value) {
                removeAt(i)
                return true
            }
        }
        return false
    }

    /** Removes every element that matches the given [predicate]. Returns the amount removed. */
    fun removeIf(predicate: (T) -> Boolean): Int {
        var removed = 0
        var i = 0
        while (i < size) {
            @Suppress("UNCHECKED_CAST")
            val elem = data[i] as T
            if (predicate(elem)) {
                removeAt(i)
                removed++
                // Do not increment i – the next element shifted into this slot
            } else {
                i++
            }
        }
        return removed
    }

    /** Clears the list – all references become null. */
    fun clear() {
        for (i in 0 until size) {
            data[i] = null
        }
        size = 0
    }

    // ---------------------------------------------------------------------
    // Capacity handling
    // ---------------------------------------------------------------------
    /** Ensures the backing array can hold at least [minCapacity] elements. */
    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > data.size) {
            var newCap = data.size shl 1 // double
            if (newCap < minCapacity) newCap = minCapacity
            data = data.copyOf(newCap)
        }
    }

    /** Shrinks the internal array to exactly fit the current [size]. */
    fun trimToSize() {
        if (size < data.size) {
            data = data.copyOf(size)
        }
    }

    // ---------------------------------------------------------------------
    // Utility / Kotlin interop
    // ---------------------------------------------------------------------
    /** Returns a snapshot as an immutable Kotlin [List]. */
    @Suppress("UNCHECKED_CAST")
    fun toList(): List<T> = (0 until size).map { data[it] as T }

    /** Returns the element at [index] or null if the index is out of range. */
    @Suppress("UNCHECKED_CAST")
    fun getOrNull(index: Int): T? = if (index in 0 until size) data[index] as T else null

    /** Returns true if the list contains [value]. */
    fun contains(value: T): Boolean {
        for (i in 0 until size) if (data[i] == value) return true
        return false
    }

    /** Returns the first element matching [predicate] or null. */
    @Suppress("UNCHECKED_CAST")
    fun find(predicate: (T) -> Boolean): T? {
        for (i in 0 until size) {
            val e = data[i] as T
            if (predicate(e)) return e
        }
        return null
    }

    /** Provides an iterator so the list can be used in `for (x in list)` blocks. */
    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var cursor = 0
        override fun hasNext(): Boolean = cursor < size
        @Suppress("UNCHECKED_CAST")
        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            return data[cursor++] as T
        }
    }

    override fun toString(): String = toList().toString()

    companion object {
        private const val DEFAULT_CAPACITY = 10
    }
}

/**
 * Example usage inside a **LiquidBounce‑Nextgen 0.39** module (pseudo code):
 * ```kotlin
 * class TickTracker : Module("TickTracker", "Tracks tick‑related data") {
 *     private val recentTicks = OpalArrayList<Long>(capacity = 20)
 *
 *     override fun onTick() {
 *         recentTicks.add(mc.theWorld?.totalWorldTime ?: 0L)
 *         // keep only the last 20 entries
 *         if (recentTicks.size > 20) recentTicks.removeAt(0)
 *     }
 *
 *     fun avgTickDelta(): Double {
 *         if (recentTicks.size < 2) return 0.0
 *         var sum = 0L
 *         for (i in 1 until recentTicks.size) {
 *             sum += recentTicks[i] - recentTicks[i - 1]
 *         }
 *         return sum.toDouble() / (recentTicks.size - 1)
 *     }
 * }
 * ```
 */
 
