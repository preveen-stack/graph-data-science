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
package org.neo4j.gds.ml.pipeline.node.classification.predict;

import org.neo4j.gds.GraphStoreAlgorithmFactory;
import org.neo4j.gds.StreamProc;
import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;
import org.neo4j.gds.executor.ComputationResult;
import org.neo4j.gds.executor.ExecutionContext;
import org.neo4j.gds.executor.GdsCallable;
import org.neo4j.gds.executor.NewConfigFunction;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.neo4j.gds.executor.ExecutionMode.STREAM;
import static org.neo4j.gds.ml.pipeline.PipelineCompanion.preparePipelineConfig;
import static org.neo4j.gds.ml.pipeline.node.classification.NodeClassificationPipelineCompanion.ESTIMATE_PREDICT_DESCRIPTION;
import static org.neo4j.gds.ml.pipeline.node.classification.NodeClassificationPipelineCompanion.PREDICT_DESCRIPTION;
import static org.neo4j.gds.ml.pipeline.node.classification.predict.NodeClassificationPipelineStreamProc.NodeClassificationStreamResult;

@GdsCallable(name = "gds.beta.pipeline.nodeClassification.predict.stream", description = PREDICT_DESCRIPTION, executionMode = STREAM)
public class NodeClassificationPipelineStreamProc
    extends StreamProc<
    NodeClassificationPredictPipelineExecutor,
    NodeClassificationPredictPipelineExecutor.NodeClassificationPipelineResult,
    NodeClassificationStreamResult,
    NodeClassificationPredictPipelineStreamConfig> {

    @Context
    public ModelCatalog internalModelCatalog;

    @Procedure(name = "gds.beta.pipeline.nodeClassification.predict.stream", mode = Mode.READ)
    @Description(PREDICT_DESCRIPTION)
    public Stream<NodeClassificationStreamResult> stream(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        preparePipelineConfig(graphName, configuration);
        return stream(compute(graphName, configuration));
    }

    @Procedure(name = "gds.beta.pipeline.nodeClassification.predict.stream.estimate", mode = Mode.READ)
    @Description(ESTIMATE_PREDICT_DESCRIPTION)
    public Stream<MemoryEstimateResult> estimate(
        @Name(value = "graphNameOrConfiguration") Object graphNameOrConfiguration,
        @Name(value = "algoConfiguration") Map<String, Object> algoConfiguration
    ) {
        preparePipelineConfig(graphNameOrConfiguration, algoConfiguration);
        return computeEstimate(graphNameOrConfiguration, algoConfiguration);
    }

    @Override
    protected Stream<NodeClassificationStreamResult> stream(
        ComputationResult<
            NodeClassificationPredictPipelineExecutor,
            NodeClassificationPredictPipelineExecutor.NodeClassificationPipelineResult,
            NodeClassificationPredictPipelineStreamConfig
            > computationResult
    ) {
        return runWithExceptionLogging("Graph streaming failed", () -> {
            if (computationResult.result().isEmpty()) {
                return Stream.empty();
            }

            var pipelineGraphFilter = computationResult.algorithm().nodePropertyStepFilter();
            var graph = computationResult.graphStore().getGraph(pipelineGraphFilter.nodeLabels());

            var result = computationResult.result().get();
            var predictedClasses = result.predictedClasses();
            var predictedProbabilities = result.predictedProbabilities();
            return LongStream
                .range(0, graph.nodeCount())
                .boxed()
                .map(nodeId ->
                    new NodeClassificationStreamResult(
                        graph.toOriginalNodeId(nodeId),
                        predictedClasses.get(nodeId),
                        nodePropertiesAsList(predictedProbabilities, nodeId)
                    )
                );
        });
    }

    private static List<Double> nodePropertiesAsList(
        Optional<HugeObjectArray<double[]>> predictedProbabilities,
        long nodeId
    ) {
        return predictedProbabilities.map(p -> {
            var values = p.get(nodeId);
            return Arrays.stream(values).boxed().collect(Collectors.toList());
        }).orElse(null);
    }

    @Override
    protected NodeClassificationStreamResult streamResult(
        long originalNodeId, long internalNodeId, NodePropertyValues nodePropertyValues
    ) {
        throw new UnsupportedOperationException("NodeClassification handles result building individually.");
    }

    @Override
    protected NodeClassificationPredictPipelineStreamConfig newConfig(String username, CypherMapWrapper config) {
        return newConfigFunction().apply(username, config);
    }

    @Override
    public GraphStoreAlgorithmFactory<NodeClassificationPredictPipelineExecutor, NodeClassificationPredictPipelineStreamConfig> algorithmFactory(
        ExecutionContext executionContext
    ) {
        return new NodeClassificationPredictPipelineAlgorithmFactory<>(executionContext);
    }

    @Override
    public ExecutionContext executionContext() {
        return super.executionContext().withModelCatalog(internalModelCatalog);
    }

    @Override
    public NewConfigFunction<NodeClassificationPredictPipelineStreamConfig> newConfigFunction() {
        return new NodeClassificationPredictNewStreamConfigFn(executionContext().modelCatalog());
    }


    @SuppressWarnings("unused")
    public static final class NodeClassificationStreamResult {

        public long nodeId;
        public long predictedClass;
        public List<Double> predictedProbabilities;

        public NodeClassificationStreamResult(long nodeId, long predictedClass, List<Double> predictedProbabilities) {
            this.nodeId = nodeId;
            this.predictedClass = predictedClass;
            this.predictedProbabilities = predictedProbabilities;
        }
    }
}
