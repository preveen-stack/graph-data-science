/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gds.core.compression.packed;

import org.apache.commons.lang3.mutable.MutableInt;
import org.neo4j.gds.api.compress.AdjacencyListBuilder;
import org.neo4j.gds.core.Aggregation;
import org.neo4j.gds.core.compression.common.AdjacencyCompression;
import org.neo4j.gds.mem.BitUtil;
import org.neo4j.internal.unsafe.UnsafeUtil;

import java.util.Arrays;

public final class AdjacencyPacker {

    private AdjacencyPacker() {}

    public static long align(long length) {
        return BitUtil.align(length, AdjacencyPacking.BLOCK_SIZE);
    }

    public static int align(int length) {
        return (int) BitUtil.align(length, AdjacencyPacking.BLOCK_SIZE);
    }

    static final int BYTE_ARRAY_BASE_OFFSET = UnsafeUtil.arrayBaseOffset(byte[].class);

    public static long compress(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        Aggregation aggregation,
        MutableInt degree
    ) {
        Arrays.sort(values, 0, length);
        return deltaCompress(allocator, slice, values, length, aggregation, degree);
    }

    static long compressWithProperties(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        long[][] properties,
        int length,
        Aggregation[] aggregations,
        boolean noAggregation,
        MutableInt degree
    ) {
        if (length > 0) {
            // sort, delta encode, reorder and aggregate properties
            length = AdjacencyCompression.applyDeltaEncoding(
                values,
                length,
                properties,
                aggregations,
                noAggregation
            );
        }

        degree.setValue(length);

        return preparePacking(allocator, slice, values, length);
    }

    private static long deltaCompress(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length,
        Aggregation aggregation,
        MutableInt degree
    ) {
        if (length > 0) {
            length = AdjacencyCompression.deltaEncodeSortedValues(values, 0, length, aggregation);
        }

        degree.setValue(length);

        return preparePacking(allocator, slice, values, length);
    }

    private static long preparePacking(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        int length
    ) {
        int blocks = BitUtil.ceilDiv(length, AdjacencyPacking.BLOCK_SIZE);
        var header = new byte[blocks];

        long bytes = 0L;
        int offset = 0;
        int blockIdx = 0;

        for (; blockIdx < blocks - 1; blockIdx++, offset += AdjacencyPacking.BLOCK_SIZE) {
            int bits = bitsNeeded(values, offset, AdjacencyPacking.BLOCK_SIZE);
            bytes += bytesNeeded(bits);
            header[blockIdx] = (byte) bits;
        }
        // "tail" block, may be smaller than BLOCK_SIZE
        {
            int bits = bitsNeeded(values, offset, length - offset);
            bytes += bytesNeeded(bits);
            header[blockIdx] = (byte) bits;
        }

        return runPacking(allocator, slice, values, header, bytes);
    }

    private static long runPacking(
        AdjacencyListBuilder.Allocator<Address> allocator,
        AdjacencyListBuilder.Slice<Address> slice,
        long[] values,
        byte[] header,
        long requiredBytes
    ) {
        assert values.length % AdjacencyPacking.BLOCK_SIZE == 0 : "values length must be a multiple of " + AdjacencyPacking.BLOCK_SIZE + ", but was " + values.length;

        long headerSize = header.length * Byte.BYTES;
        // we must add padding between the header and the data bytes
        // to avoid writing unaligned longs
        long alignedHeaderSize = BitUtil.align(headerSize, Long.BYTES);
        long fullSize = alignedHeaderSize + requiredBytes;
        // we must align to long because we write in terms of longs, not single bytes
        long alignedFullSize = BitUtil.align(fullSize, Long.BYTES);
        int allocationSize = Math.toIntExact(alignedFullSize);

        long adjacencyOffset = allocator.allocate(allocationSize, slice);

        Address address = slice.slice();
        long ptr = address.address() + slice.offset();

        // write header
        UnsafeUtil.copyMemory(header, BYTE_ARRAY_BASE_OFFSET, null, ptr, headerSize);
        ptr += alignedHeaderSize;

        // main packing loop
        int in = 0;
        for (byte bits : header) {
            ptr = AdjacencyPacking.pack(bits, values, in, ptr);
            in += AdjacencyPacking.BLOCK_SIZE;
        }

        return adjacencyOffset;
    }

    private static int bitsNeeded(long[] values, int offset, int length) {
        long bits = 0L;
        for (int i = offset; i < offset + length; i++) {
            bits |= values[i];
        }
        return Long.SIZE - Long.numberOfLeadingZeros(bits);
    }

    private static int bytesNeeded(int bits) {
        return BitUtil.ceilDiv(AdjacencyPacking.BLOCK_SIZE * bits, Byte.SIZE);
    }
}
