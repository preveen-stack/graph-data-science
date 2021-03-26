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
package org.neo4j.graphalgo.beta.pregel;

import java.util.function.LongConsumer;

public interface ComputeStep {

    int iteration();

    boolean isMultiGraph();

    long nodeCount();

    long relationshipCount();

    int degree(long nodeId);

    void voteToHalt(long nodeId);

    void sendTo(long targetNodeId, double message);

    void sendToNeighbors(long sourceNodeId, double message);

    void sendToNeighborsWeighted(long sourceNodeId, double message);

    void forEachNeighbor(long sourceNodeId, LongConsumer targetConsumer);

    void forEachDistinctNeighbor(long sourceNodeId, LongConsumer targetConsumer);

    double doubleNodeValue(String key, long nodeId);

    long longNodeValue(String key, long nodeId);

    long[] longArrayNodeValue(String key, long nodeId);

    double[] doubleArrayNodeValue(String key, long nodeId);

    void setNodeValue(String key, long nodeId, double value);

    void setNodeValue(String key, long nodeId, long value);

    void setNodeValue(String key, long nodeId, long[] value);

    void setNodeValue(String key, long nodeId, double[] value);

    boolean hasSendMessage();
}
