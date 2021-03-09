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
package org.neo4j.gds.ml.normalizing;

import org.neo4j.graphalgo.AlgorithmFactory;
import org.neo4j.graphalgo.AlphaAlgorithmFactory;
import org.neo4j.graphalgo.MutatePropertyProc;
import org.neo4j.graphalgo.api.NodeProperties;
import org.neo4j.graphalgo.api.nodeproperties.DoubleArrayNodeProperties;
import org.neo4j.graphalgo.config.GraphCreateConfig;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphalgo.result.AbstractResultBuilder;
import org.neo4j.graphalgo.results.StandardMutateResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class NormalizeFeatureMutateProc extends MutatePropertyProc<NormalizeFeatures, NormalizeFeatures.Result, NormalizeFeatureMutateProc.MutateResult, NormalizeFeaturesConfig> {

    @Procedure("gds.alpha.ml.normalizeFeatures.mutate")
    @Description("Normalize node properties")
    public Stream<MutateResult> mutate(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return mutate(compute(graphNameOrConfig, configuration));
    }

    @Override
    protected NodeProperties nodeProperties(ComputationResult<NormalizeFeatures, NormalizeFeatures.Result, NormalizeFeaturesConfig> computationResult) {
        return (DoubleArrayNodeProperties) computationResult.result().normalizedProperties()::get;
    }

    @Override
    protected NormalizeFeaturesConfig newConfig(
        String username,
        Optional<String> graphName,
        Optional<GraphCreateConfig> maybeImplicitCreate,
        CypherMapWrapper config
    ) {
        return NormalizeFeaturesConfig.of(username, graphName, maybeImplicitCreate, config);
    }

    @Override
    protected AlgorithmFactory<NormalizeFeatures, NormalizeFeaturesConfig> algorithmFactory() {
        return (AlphaAlgorithmFactory<NormalizeFeatures, NormalizeFeaturesConfig>) (graph, configuration, tracker, log, eventTracker) -> new NormalizeFeatures(
            graph,
            configuration,
            tracker
        );
    }

    @Override
    protected AbstractResultBuilder<MutateResult> resultBuilder(ComputationResult<NormalizeFeatures, NormalizeFeatures.Result, NormalizeFeaturesConfig> computeResult) {
        return new MutateResult.Builder();
    }

    public static final class MutateResult extends StandardMutateResult {

        public final long nodePropertiesWritten;

        MutateResult(
            long createMillis,
            long computeMillis,
            long mutateMillis,
            long nodePropertiesWritten,
            Map<String, Object> configuration
        ) {
            super(
                createMillis,
                computeMillis,
                0L,
                mutateMillis,
                configuration
            );
            this.nodePropertiesWritten = nodePropertiesWritten;
        }

        static class Builder extends AbstractResultBuilder<MutateResult> {

            @Override
            public MutateResult build() {
                return new MutateResult(
                    createMillis,
                    computeMillis,
                    mutateMillis,
                    nodePropertiesWritten,
                    config.toMap()
                );
            }
        }
    }
}
