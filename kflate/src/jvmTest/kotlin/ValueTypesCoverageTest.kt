package com.rafambn.kflate

import com.rafambn.kflate.compression.Gzip as CompressionGzip
import com.rafambn.kflate.compression.Raw as CompressionRaw
import com.rafambn.kflate.compression.Zlib as CompressionZlib
import com.rafambn.kflate.decompression.Gzip as DecompressionGzip
import com.rafambn.kflate.decompression.Raw as DecompressionRaw
import com.rafambn.kflate.decompression.Zlib as DecompressionZlib
import com.rafambn.kflate.format.GzipMemberResult
import com.rafambn.kflate.huffman.HuffmanNode
import com.rafambn.kflate.huffman.HuffmanTable
import com.rafambn.kflate.huffman.HuffmanTreeResult
import com.rafambn.kflate.streaming.DeflateState
import com.rafambn.kflate.streaming.InflateState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class ValueTypesCoverageTest {

    private val nullValue: Any?
        get() = listOf<Any?>().firstOrNull()

    @Test
    fun compressionRawValueContract() {
        val dictionary = byteArrayOf(1, 2)
        val value = CompressionRaw(1, 2, dictionary)

        assertSame(value, value)
        assertEqualsAcceptsSame(value)
        assertFalse(value.equals(null))
        assertFalse(value.equals(nullValue))
        assertEqualsRejectsNull(value)
        assertFalse(value.equals(CompressionZlib(1, 2, dictionary)))
        assertEqualsRejects(value, CompressionZlib(1, 2, dictionary))
        assertFalse(valuesEqual(value, Any()))
        assertNotEquals(value, CompressionRaw(2, 2, dictionary))
        assertNotEquals(value, CompressionRaw(1, 3, dictionary))
        assertNotEquals(value, CompressionRaw(1, 2, byteArrayOf(2, 1)))
        assertEquals(value, CompressionRaw(1, 2, dictionary.copyOf()))
        assertTrue(valuesEqual(value, CompressionRaw(1, 2, dictionary.copyOf())))
        assertEquals(value.hashCode(), CompressionRaw(1, 2, dictionary.copyOf()).hashCode())
        assertEquals(CompressionRaw().hashCode(), CompressionRaw().copy().hashCode())
        assertEquals(1, value.component1())
        assertEquals(2, value.component2())
        assertTrue(value.component3()!!.contentEquals(dictionary))
        assertEquals(CompressionRaw(3, 2, dictionary), value.copy(level = 3))
        assertTrue(value.toString().contains("level=1"))

        assertFailsWith<IllegalArgumentException> { CompressionRaw(level = -1) }
        assertFailsWith<IllegalArgumentException> { CompressionRaw(level = 10) }
        assertFailsWith<IllegalArgumentException> { CompressionRaw(mem = -1) }
        assertFailsWith<IllegalArgumentException> { CompressionRaw(mem = 13) }
        assertFailsWith<IllegalArgumentException> { CompressionRaw(dictionary = ByteArray(32_769)) }
    }

    @Test
    fun compressionZlibValueContract() {
        val dictionary = byteArrayOf(1, 2)
        val value = CompressionZlib(1, 2, dictionary)

        assertSame(value, value)
        assertEqualsAcceptsSame(value)
        assertFalse(value.equals(null))
        assertFalse(value.equals(nullValue))
        assertEqualsRejectsNull(value)
        assertFalse(value.equals(CompressionRaw(1, 2, dictionary)))
        assertEqualsRejects(value, CompressionRaw(1, 2, dictionary))
        assertFalse(valuesEqual(value, Any()))
        assertNotEquals(value, CompressionZlib(2, 2, dictionary))
        assertNotEquals(value, CompressionZlib(1, 3, dictionary))
        assertNotEquals(value, CompressionZlib(1, 2, byteArrayOf(2, 1)))
        assertEquals(value, CompressionZlib(1, 2, dictionary.copyOf()))
        assertTrue(valuesEqual(value, CompressionZlib(1, 2, dictionary.copyOf())))
        assertEquals(value.hashCode(), CompressionZlib(1, 2, dictionary.copyOf()).hashCode())
        assertEquals(CompressionZlib().hashCode(), CompressionZlib().copy().hashCode())
        assertEquals(1, value.component1())
        assertEquals(2, value.component2())
        assertTrue(value.component3()!!.contentEquals(dictionary))
        assertEquals(CompressionZlib(3, 2, dictionary), value.copy(level = 3))
        assertTrue(value.toString().contains("level=1"))

        assertFailsWith<IllegalArgumentException> { CompressionZlib(level = -1) }
        assertFailsWith<IllegalArgumentException> { CompressionZlib(level = 10) }
        assertFailsWith<IllegalArgumentException> { CompressionZlib(mem = -1) }
        assertFailsWith<IllegalArgumentException> { CompressionZlib(mem = 13) }
        assertFailsWith<IllegalArgumentException> { CompressionZlib(dictionary = ByteArray(32_769)) }
    }

    @Test
    fun compressionGzipValueContract() {
        val mtime = Instant.fromEpochSeconds(123)
        val fields = mapOf("AB" to byteArrayOf(1))
        val value = CompressionGzip(1, 2, "file", mtime, "comment", fields, true)

        assertSame(value, value)
        assertEqualsAcceptsSame(value)
        assertFalse(value.equals(null))
        assertFalse(value.equals(nullValue))
        assertEqualsRejectsNull(value)
        assertFalse(value.equals(CompressionRaw()))
        assertEqualsRejects(value, CompressionRaw())
        assertFalse(valuesEqual(value, Any()))
        assertNotEquals(value, value.copy(level = 2))
        assertNotEquals(value, value.copy(mem = 3))
        assertNotEquals(value, value.copy(filename = "other"))
        assertNotEquals(value, value.copy(mtime = Instant.fromEpochSeconds(124)))
        assertNotEquals(value, value.copy(comment = "other"))
        assertNotEquals(value, value.copy(extraFields = null))
        assertNotEquals(value, value.copy(includeHeaderCrc = false))
        assertEquals(value, value.copy())
        assertTrue(valuesEqual(value, value.copy()))
        assertEquals(value.hashCode(), value.copy().hashCode())
        assertEquals(CompressionGzip().hashCode(), CompressionGzip().copy().hashCode())
        assertEquals(1, value.component1())
        assertEquals(2, value.component2())
        assertEquals("file", value.component3())
        assertEquals(mtime, value.component4())
        assertEquals("comment", value.component5())
        assertEquals(fields, value.component6())
        assertTrue(value.component7())
        assertTrue(value.toString().contains("filename=file"))

        assertFailsWith<IllegalArgumentException> { CompressionGzip(level = -1) }
        assertFailsWith<IllegalArgumentException> { CompressionGzip(level = 10) }
        assertFailsWith<IllegalArgumentException> { CompressionGzip(mem = -1) }
        assertFailsWith<IllegalArgumentException> { CompressionGzip(mem = 13) }
        assertFailsWith<IllegalArgumentException> { CompressionGzip(filename = "a".repeat(65_536)) }
        assertFailsWith<IllegalArgumentException> { CompressionGzip(comment = "a".repeat(65_536)) }
        assertFailsWith<IllegalArgumentException> { CompressionGzip(extraFields = mapOf("A" to byteArrayOf())) }
        assertFailsWith<IllegalArgumentException> { CompressionGzip(extraFields = mapOf("ABC" to byteArrayOf())) }
        assertFailsWith<IllegalArgumentException> { CompressionGzip(extraFields = mapOf("AB" to ByteArray(65_536))) }
    }

    @Test
    fun decompressionValueContracts() {
        val dictionary = byteArrayOf(1, 2)
        val raw = DecompressionRaw(dictionary, 3)
        assertSame(raw, raw)
        assertEqualsAcceptsSame(raw)
        assertFalse(raw.equals(null))
        assertFalse(raw.equals(nullValue))
        assertEqualsRejectsNull(raw)
        assertFalse(raw.equals(DecompressionZlib(dictionary, 3)))
        assertEqualsRejects(raw, DecompressionZlib(dictionary, 3))
        assertFalse(valuesEqual(raw, Any()))
        assertNotEquals(raw, DecompressionRaw(byteArrayOf(2, 1), 3))
        assertNotEquals(raw, DecompressionRaw(dictionary, 4))
        assertEquals(raw, DecompressionRaw(dictionary.copyOf(), 3))
        assertTrue(valuesEqual(raw, DecompressionRaw(dictionary.copyOf(), 3)))
        assertEquals(raw.hashCode(), DecompressionRaw(dictionary.copyOf(), 3).hashCode())
        assertEquals(0, DecompressionRaw().hashCode())
        assertTrue(raw.component1()!!.contentEquals(dictionary))
        assertEquals(3, raw.component2())
        assertEquals(DecompressionRaw(dictionary, 4), raw.copy(maxOutputSize = 4))
        assertTrue(raw.toString().contains("maxOutputSize=3"))

        val zlib = DecompressionZlib(dictionary, 3)
        assertSame(zlib, zlib)
        assertEqualsAcceptsSame(zlib)
        assertFalse(zlib.equals(null))
        assertFalse(zlib.equals(nullValue))
        assertEqualsRejectsNull(zlib)
        assertFalse(zlib.equals(raw))
        assertEqualsRejects(zlib, raw)
        assertFalse(valuesEqual(zlib, Any()))
        assertNotEquals(zlib, DecompressionZlib(byteArrayOf(2, 1), 3))
        assertNotEquals(zlib, DecompressionZlib(dictionary, 4))
        assertEquals(zlib, DecompressionZlib(dictionary.copyOf(), 3))
        assertTrue(valuesEqual(zlib, DecompressionZlib(dictionary.copyOf(), 3)))
        assertEquals(zlib.hashCode(), DecompressionZlib(dictionary.copyOf(), 3).hashCode())
        assertEquals(0, DecompressionZlib().hashCode())
        assertTrue(zlib.component1()!!.contentEquals(dictionary))
        assertEquals(3, zlib.component2())
        assertEquals(DecompressionZlib(dictionary, 4), zlib.copy(maxOutputSize = 4))
        assertTrue(zlib.toString().contains("maxOutputSize=3"))

        val gzip = DecompressionGzip(3)
        assertEquals(3, gzip.component1())
        assertEquals(DecompressionGzip(4), gzip.copy(maxOutputSize = 4))
        assertTrue(gzip.toString().contains("maxOutputSize=3"))

        assertFailsWith<IllegalArgumentException> { DecompressionRaw(dictionary = ByteArray(32_769)) }
        assertFailsWith<IllegalArgumentException> { DecompressionRaw(maxOutputSize = -1) }
        assertFailsWith<IllegalArgumentException> { DecompressionZlib(dictionary = ByteArray(32_769)) }
        assertFailsWith<IllegalArgumentException> { DecompressionZlib(maxOutputSize = -1) }
        assertFailsWith<IllegalArgumentException> { DecompressionGzip(maxOutputSize = -1) }
    }

    @Test
    fun deflateStateValueContract() {
        val head = shortArrayOf(1, 2)
        val prev = shortArrayOf(3, 4)
        val value = DeflateState(head, prev, 1, 2, 3, 4, true)

        assertSame(value, value)
        assertEqualsAcceptsSame(value)
        assertFalse(value.equals(null))
        assertFalse(value.equals(nullValue))
        assertEqualsRejectsNull(value)
        assertFalse(value.equals(InflateState()))
        assertEqualsRejects(value, InflateState())
        assertFalse(valuesEqual(value, Any()))
        assertNotEquals(value, value.copy(inputOffset = 2))
        assertNotEquals(value, value.copy(inputEndIndex = 3))
        assertNotEquals(value, value.copy(waitIndex = 4))
        assertNotEquals(value, value.copy(bitBuffer = 5))
        assertNotEquals(value, value.copy(isLastChunk = false))
        assertNotEquals(value, value.copy(head = shortArrayOf(2, 1)))
        assertNotEquals(value, value.copy(prev = shortArrayOf(4, 3)))
        assertEquals(value, value.copy(head = head.copyOf(), prev = prev.copyOf()))
        assertTrue(valuesEqual(value, value.copy(head = head.copyOf(), prev = prev.copyOf())))
        assertEquals(value.hashCode(), value.copy(head = head.copyOf(), prev = prev.copyOf()).hashCode())
        assertEquals(DeflateState().hashCode(), DeflateState().copy().hashCode())

        val copy = value.copy()
        copy.head = head
        copy.prev = prev
        copy.inputOffset = 1
        copy.inputEndIndex = 2
        copy.waitIndex = 3
        copy.bitBuffer = 4
        copy.isLastChunk = true
        assertEquals(value, copy)
        assertTrue(copy.toString().contains("inputOffset=1"))
    }

    @Test
    fun inflateStateValueContract() {
        val literals = shortArrayOf(1, 2)
        val distances = shortArrayOf(3, 4)
        val value = InflateState(literals, distances, 1, 2, true, 3, 4, 5)

        assertSame(value, value)
        assertEqualsAcceptsSame(value)
        assertFalse(value.equals(null))
        assertFalse(value.equals(nullValue))
        assertEqualsRejectsNull(value)
        assertFalse(value.equals(DeflateState()))
        assertEqualsRejects(value, DeflateState())
        assertFalse(valuesEqual(value, Any()))
        assertNotEquals(value, value.copy(literalMaxBits = 2))
        assertNotEquals(value, value.copy(distanceMaxBits = 3))
        assertNotEquals(value, value.copy(isFinalBlock = false))
        assertNotEquals(value, value.copy(inputBitPosition = 4))
        assertNotEquals(value, value.copy(outputOffset = 5))
        assertNotEquals(value, value.copy(validationMode = 6))
        assertNotEquals(value, value.copy(literalMap = shortArrayOf(2, 1)))
        assertNotEquals(value, value.copy(distanceMap = shortArrayOf(4, 3)))
        assertEquals(value, value.copy(literalMap = literals.copyOf(), distanceMap = distances.copyOf()))
        assertTrue(valuesEqual(value, value.copy(literalMap = literals.copyOf(), distanceMap = distances.copyOf())))
        assertEquals(value.hashCode(), value.copy(literalMap = literals.copyOf(), distanceMap = distances.copyOf()).hashCode())
        assertEquals(InflateState().hashCode(), InflateState().copy().hashCode())

        val copy = value.copy()
        copy.literalMap = literals
        copy.distanceMap = distances
        copy.literalMaxBits = 1
        copy.distanceMaxBits = 2
        copy.isFinalBlock = true
        copy.inputBitPosition = 3
        copy.outputOffset = 4
        copy.validationMode = 5
        assertEquals(value, copy)
        assertTrue(copy.toString().contains("literalMaxBits=1"))
    }

    @Test
    fun internalHolderValueContracts() {
        val member = GzipMemberResult(byteArrayOf(1, 2), 3)
        assertSame(member, member)
        assertEqualsAcceptsSame(member)
        assertFalse(member.equals(null))
        assertFalse(member.equals(nullValue))
        assertEqualsRejectsNull(member)
        assertFalse(member.equals(HuffmanTreeResult(byteArrayOf(1, 2), 3)))
        assertEqualsRejects(member, HuffmanTreeResult(byteArrayOf(1, 2), 3))
        assertFalse(valuesEqual(member, Any()))
        assertNotEquals(member, GzipMemberResult(byteArrayOf(2, 1), 3))
        assertNotEquals(member, GzipMemberResult(byteArrayOf(1, 2), 4))
        assertEquals(member, GzipMemberResult(byteArrayOf(1, 2), 3))
        assertTrue(valuesEqual(member, GzipMemberResult(byteArrayOf(1, 2), 3)))
        assertEquals(member.hashCode(), GzipMemberResult(byteArrayOf(1, 2), 3).hashCode())
        assertTrue(member.decompressed.contentEquals(byteArrayOf(1, 2)))
        assertEquals(3, member.bytesConsumed)
        assertTrue(member.toString().contains("bytesConsumed=3"))

        val tree = HuffmanTreeResult(byteArrayOf(1, 2), 3)
        assertSame(tree, tree)
        assertEqualsAcceptsSame(tree)
        assertFalse(tree.equals(null))
        assertFalse(tree.equals(nullValue))
        assertEqualsRejectsNull(tree)
        assertFalse(tree.equals(member))
        assertEqualsRejects(tree, member)
        assertFalse(valuesEqual(tree, Any()))
        assertNotEquals(tree, HuffmanTreeResult(byteArrayOf(1, 2), 4))
        assertNotEquals(tree, HuffmanTreeResult(byteArrayOf(2, 1), 3))
        assertEquals(tree, HuffmanTreeResult(byteArrayOf(1, 2), 3))
        assertTrue(valuesEqual(tree, HuffmanTreeResult(byteArrayOf(1, 2), 3)))
        assertEquals(tree.hashCode(), HuffmanTreeResult(byteArrayOf(1, 2), 3).hashCode())
        assertTrue(tree.tree.contentEquals(byteArrayOf(1, 2)))
        assertEquals(3, tree.maxBits)
        assertTrue(tree.toString().contains("maxBits=3"))

        val table = HuffmanTable(shortArrayOf(1, 2), intArrayOf(3, 4))
        assertSame(table, table)
        assertEqualsAcceptsSame(table)
        assertFalse(table.equals(null))
        assertFalse(table.equals(nullValue))
        assertEqualsRejectsNull(table)
        assertFalse(table.equals(tree))
        assertEqualsRejects(table, tree)
        assertFalse(valuesEqual(table, Any()))
        assertNotEquals(table, HuffmanTable(shortArrayOf(2, 1), intArrayOf(3, 4)))
        assertNotEquals(table, HuffmanTable(shortArrayOf(1, 2), intArrayOf(4, 3)))
        assertEquals(table, HuffmanTable(shortArrayOf(1, 2), intArrayOf(3, 4)))
        assertTrue(valuesEqual(table, HuffmanTable(shortArrayOf(1, 2), intArrayOf(3, 4))))
        assertEquals(table.hashCode(), HuffmanTable(shortArrayOf(1, 2), intArrayOf(3, 4)).hashCode())
        assertTrue(table.toString().contains("baseLengths="))

        val left = HuffmanNode(1, 2)
        val right = HuffmanNode(3, 4)
        val node = HuffmanNode(5, 6, left, right)
        assertEquals(5, node.component1())
        assertEquals(6, node.component2())
        assertEquals(left, node.component3())
        assertEquals(right, node.component4())
        node.leftChild = right
        node.rightChild = left
        assertEquals(HuffmanNode(5, 6, right, left), node)
        assertFalse(node == left)
        assertTrue(node.toString().contains("symbol=5"))
        assertEquals(node.hashCode(), node.copy().hashCode())
    }

    private fun assertEqualsRejectsNull(value: Any) {
        assertEqualsRejects(value, nullValue)
    }

    private fun assertEqualsAcceptsSame(value: Any) {
        val equals = value.javaClass.getMethod("equals", Any::class.java)
        assertEquals(true, equals.invoke(value, value))
    }

    private fun assertEqualsRejects(value: Any, other: Any?) {
        val equals = value.javaClass.getMethod("equals", Any::class.java)
        assertEquals(false, equals.invoke(value, other))
    }

    private fun valuesEqual(first: Any?, second: Any?): Boolean = first == second
}
