/*
 * Copyright (c) 2017-2020 "Neo4j,"
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
package org.neo4j.gds.embeddings.randomprojections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.AlgoTestBase;
import org.neo4j.graphalgo.Orientation;
import org.neo4j.graphalgo.StoreLoaderBuilder;
import org.neo4j.graphalgo.TestProgressLogger;
import org.neo4j.graphalgo.api.DefaultValue;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.beta.generator.RandomGraphGenerator;
import org.neo4j.graphalgo.beta.generator.RelationshipDistribution;
import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.ImmutableGraphDimensions;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.core.utils.paged.HugeObjectArray;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.gds.embeddings.randomprojections.FastRP.l2Normalize;

class FastRPTest extends AlgoTestBase {

    static final int DEFUALT_EMBEDDING_SIZE = 128;
    static final FastRPBaseConfig DEFAULT_CONFIG = ImmutableFastRPBaseConfig.builder()
        .embeddingSize(DEFUALT_EMBEDDING_SIZE)
        .addIterationWeight(1.0D)
        .build();

    private static final String DB_CYPHER =
        "CREATE" +
        "  (a:Node1)" +
        ", (b:Node1)" +
        ", (c:Node2)" +
        ", (d:Isolated)" +
        ", (e:Isolated)" +
        ", (a)-[:REL {weight: 2.0}]->(b)" +
        ", (b)-[:REL {weight: 1.0}]->(a)" +
        ", (a)-[:REL {weight: 1.0}]->(c)" +
        ", (c)-[:REL {weight: 1.0}]->(a)" +
        ", (b)-[:REL {weight: 1.0}]->(c)" +
        ", (c)-[:REL {weight: 1.0}]->(b)";

    @BeforeEach
    void setupGraphDb() {
        runQuery(DB_CYPHER);
    }

    @Test
    void shouldSwapInitialRandomVectors() {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Node1")
            .build();

        Graph graph = graphLoader.graph();

        FastRP fastRP = new FastRP(
            graph,
            DEFAULT_CONFIG,
            progressLogger,
            AllocationTracker.empty()
        );

        fastRP.initRandomVectors();
        HugeObjectArray<float[]> randomVectors = HugeObjectArray.newArray(float[].class, 2, AllocationTracker.empty());
        fastRP.currentEmbedding(-1).copyTo(randomVectors, 2);
        fastRP.propagateEmbeddings();
        HugeObjectArray<float[]> embeddings = fastRP.embeddings();

        float[] expected = randomVectors.get(1);
        l2Normalize(expected);
        assertArrayEquals(expected, embeddings.get(0));
    }

    @Test
    void shouldAverageNeighbors() {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Node1")
            .addNodeLabel("Node2")
            .build();

        Graph graph = graphLoader.graph();

        FastRP fastRP = new FastRP(
            graph,
            DEFAULT_CONFIG,
            progressLogger,
            AllocationTracker.empty()
        );

        fastRP.initRandomVectors();
        HugeObjectArray<float[]> randomVectors = HugeObjectArray.newArray(float[].class, 3, AllocationTracker.empty());
        fastRP.currentEmbedding(-1).copyTo(randomVectors, 3);
        fastRP.propagateEmbeddings();
        HugeObjectArray<float[]> embeddings = fastRP.embeddings();

        float[] expected = new float[DEFUALT_EMBEDDING_SIZE];
        for (int i = 0; i < DEFUALT_EMBEDDING_SIZE; i++) {
            expected[i] = (randomVectors.get(1)[i] + randomVectors.get(2)[i]) / 2.0f;
        }
        l2Normalize(expected);
        assertArrayEquals(expected, embeddings.get(0));
    }

    @Test
    void shouldAverageNeighborsWeighted() {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Node1")
            .addNodeLabel("Node2")
            .addRelationshipProperty("weight", "weight", DefaultValue.of(1.0), Aggregation.NONE)
            .build();

        Graph graph = graphLoader.graph();

        var weightedConfig = ImmutableFastRPBaseConfig
            .builder()
            .from(DEFAULT_CONFIG)
            .relationshipWeightProperty("weight")
            .embeddingSize(DEFUALT_EMBEDDING_SIZE)
            .build();

        FastRP fastRP = new FastRP(
            graph,
            weightedConfig,
            progressLogger,
            AllocationTracker.empty()
        );

        fastRP.initRandomVectors();
        HugeObjectArray<float[]> randomVectors = HugeObjectArray.newArray(float[].class, 3, AllocationTracker.empty());
        fastRP.currentEmbedding(-1).copyTo(randomVectors, 3);
        fastRP.propagateEmbeddings();
        HugeObjectArray<float[]> embeddings = fastRP.embeddings();

        float[] expected = new float[DEFUALT_EMBEDDING_SIZE];
        for (int i = 0; i < DEFUALT_EMBEDDING_SIZE; i++) {
            expected[i] = (2.0f * randomVectors.get(1)[i] + 1.0f * randomVectors.get(2)[i]) / 2.0f;
        }
        l2Normalize(expected);

        assertArrayEquals(expected, embeddings.get(0));
    }

    @Test
    void shouldDistributeValuesCorrectly() {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Node1")
            .addNodeLabel("Node2")
            .build();

        Graph graph = graphLoader.graph();

        FastRP fastRP = new FastRP(
            graph,
            ImmutableFastRPBaseConfig.builder()
                .embeddingSize(512)
                .addIterationWeight(1.0D)
                .build(),
            progressLogger,
            AllocationTracker.empty()
        );

        fastRP.initRandomVectors();
        double p = 1D / 6D;
        int maxNumPositive = (int) ((p + 5D * Math.sqrt((p * (1 - p)) / 512D)) * 512D); // 1:30.000.000 chance of failing :P
        int minNumPositive = (int) ((p - 5D * Math.sqrt((p * (1 - p)) / 512D)) * 512D);
        HugeObjectArray<float[]> randomVectors = fastRP.currentEmbedding(-1);
        for (int i = 0; i < graph.nodeCount(); i++) {
            float[] embedding = randomVectors.get(i);
            int numZeros = 0;
            int numPositive = 0;
            for (int j = 0; j < 512; j++) {
                double embeddingValue = embedding[j];
                if (embeddingValue == 0) {
                    numZeros++;
                } else if (embeddingValue > 0) {
                    numPositive++;
                }
            }

            int numNegative = 512 - numZeros - numPositive;
            assertTrue(numPositive >= minNumPositive && numPositive <= maxNumPositive);
            assertTrue(numNegative >= minNumPositive && numNegative <= maxNumPositive);
        }
    }

    @Test
    void shouldYieldEmptyEmbeddingForIsolatedNodes() {
        GraphLoader graphLoader = new StoreLoaderBuilder()
            .api(db)
            .addNodeLabel("Isolated")
            .build();

        Graph graph = graphLoader.graph();

        FastRP fastRP = new FastRP(
            graph,
            ImmutableFastRPBaseConfig.builder()
                .embeddingSize(64)
                .addIterationWeights(1.0D, 1.0D, 1.0D, 1.0D)
                .build(),
            progressLogger,
            AllocationTracker.empty()
        );

        FastRP computeResult = fastRP.compute();
        HugeObjectArray<float[]> embeddings = computeResult.embeddings();
        for (int i = 0; i < embeddings.size(); i++) {
            float[] embedding = embeddings.get(i);
            for (double embeddingValue : embedding) {
                assertEquals(0.0f, embeddingValue);
            }
        }
    }

    @Test
    void testMemoryEstimationWithoutIterationWeights() {
        var config = ImmutableFastRPBaseConfig
            .builder()
            .addIterationWeights(1.0D, 1.0D)
            .embeddingSize(128)
            .build();

        var dimensions = ImmutableGraphDimensions.builder().nodeCount(100).build();

        var estimate = FastRP.memoryEstimation(config).estimate(dimensions, 1).memoryUsage();
        assertEquals(estimate.min, estimate.max);
        assertEquals(159_784, estimate.min);
    }

    @Test
    void testMemoryEstimationWithIterationWeights() {
        var config = ImmutableFastRPBaseConfig
            .builder()
            .embeddingSize(128)
            .iterationWeights(List.of(1.0D, 2.0D))
            .build();

        var dimensions = ImmutableGraphDimensions.builder().nodeCount(100).build();

        var estimate = FastRP.memoryEstimation(config).estimate(dimensions, 1).memoryUsage();
        assertEquals(estimate.min, estimate.max);
        assertEquals(159_784, estimate.min);
    }

    @Test
    void testProgressLogging() {
        var graph = RandomGraphGenerator
            .builder()
            .nodeCount(100)
            .averageDegree(2)
            .orientation(Orientation.UNDIRECTED)
            .relationshipDistribution(RelationshipDistribution.RANDOM)
            .build()
            .generate();

        var config = ImmutableFastRPBaseConfig
            .builder()
            .embeddingSize(2)
            .iterationWeights(List.of(1.0D, 2.0D))
            .concurrency(4)
            .build();

        var logger = new TestProgressLogger(
            graph.nodeCount(),
            FastRP.class.getSimpleName(),
            config.concurrency()
        );

        new FastRP(graph, config, logger, AllocationTracker.empty()).compute();

        assertTrue(logger.containsMessage(TestProgressLogger.INFO, ":: Start"));
        assertTrue(logger.containsMessage(TestProgressLogger.INFO, "Iteration 1 :: Start"));
        assertTrue(logger.containsMessage(TestProgressLogger.INFO, "Iteration 1 :: Finished"));
        assertTrue(logger.containsMessage(TestProgressLogger.INFO, "Iteration 2 :: Start"));
        assertTrue(logger.containsMessage(TestProgressLogger.INFO, "Iteration 2 :: Finished"));
        assertTrue(logger.containsMessage(TestProgressLogger.INFO, ":: Finished"));
        assertEquals(
            3,
            logger.getMessages(TestProgressLogger.INFO).stream().filter(message -> message.contains("100%")).count()
        );
    }
}
