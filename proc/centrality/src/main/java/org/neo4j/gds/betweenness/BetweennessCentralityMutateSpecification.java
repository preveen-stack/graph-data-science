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
package org.neo4j.gds.betweenness;

import org.neo4j.gds.MutatePropertyComputationResultConsumer;
import org.neo4j.gds.collections.haa.HugeAtomicDoubleArray;
import org.neo4j.gds.core.utils.paged.HugeArrayToNodeProperties;
import org.neo4j.gds.core.utils.paged.ParallelDoublePageCreator;
import org.neo4j.gds.core.write.ImmutableNodeProperty;
import org.neo4j.gds.executor.AlgorithmSpec;
import org.neo4j.gds.executor.ComputationResult;
import org.neo4j.gds.executor.ComputationResultConsumer;
import org.neo4j.gds.executor.ExecutionContext;
import org.neo4j.gds.executor.GdsCallable;
import org.neo4j.gds.executor.NewConfigFunction;
import org.neo4j.gds.executor.validation.ValidationConfiguration;
import org.neo4j.gds.result.AbstractResultBuilder;

import java.util.List;
import java.util.stream.Stream;

import static org.neo4j.gds.executor.ExecutionMode.MUTATE_NODE_PROPERTY;

@GdsCallable(name = "gds.betweenness.mutate", description = BetweennessCentrality.BETWEENNESS_DESCRIPTION, executionMode = MUTATE_NODE_PROPERTY)
public class BetweennessCentralityMutateSpecification implements AlgorithmSpec<BetweennessCentrality, HugeAtomicDoubleArray, BetweennessCentralityMutateConfig, Stream<MutateResult>, BetweennessCentralityFactory<BetweennessCentralityMutateConfig>> {
    @Override
    public String name() {
        return "BetweennessCentralityMutate";
    }

    @Override
    public BetweennessCentralityFactory<BetweennessCentralityMutateConfig> algorithmFactory(ExecutionContext executionContext) {
        return new BetweennessCentralityFactory<>();
    }

    @Override
    public NewConfigFunction<BetweennessCentralityMutateConfig> newConfigFunction() {
        return (__, userInput) -> BetweennessCentralityMutateConfig.of(userInput);
    }

    @Override
    public ComputationResultConsumer<BetweennessCentrality, HugeAtomicDoubleArray, BetweennessCentralityMutateConfig, Stream<MutateResult>> computationResultConsumer() {
        return new MutatePropertyComputationResultConsumer<>(
            computationResult -> List.of(ImmutableNodeProperty.of(
                computationResult.config().mutateProperty(),
                HugeArrayToNodeProperties.convert(computationResult.result()
                    .orElseGet(() -> HugeAtomicDoubleArray.of(0, ParallelDoublePageCreator.passThrough(1)))
                )
            )),
            this::resultBuilder
        );
    }

    @Override
    public ValidationConfiguration<BetweennessCentralityMutateConfig> validationConfig(ExecutionContext executionContext) {
        return new BetweennessCentralityConfigValidation<>();
    }

    private AbstractResultBuilder<MutateResult> resultBuilder(
        ComputationResult<BetweennessCentrality, HugeAtomicDoubleArray, BetweennessCentralityMutateConfig> computationResult,
        ExecutionContext executionContext
    ) {
        var builder = new MutateResult.Builder(
            executionContext.returnColumns(),
            computationResult.config().concurrency()
        );

        computationResult.result().ifPresent(result -> builder.withCentralityFunction(result::get));

        return builder;
    }

}
