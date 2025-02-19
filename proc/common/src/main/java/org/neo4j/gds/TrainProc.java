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
package org.neo4j.gds;

import org.neo4j.gds.core.model.Model;
import org.neo4j.gds.core.model.ModelCatalog;
import org.neo4j.gds.executor.ComputationResult;
import org.neo4j.gds.executor.ComputationResultConsumer;
import org.neo4j.gds.executor.ExecutionContext;
import org.neo4j.gds.executor.validation.BeforeLoadValidation;
import org.neo4j.gds.executor.validation.ValidationConfiguration;
import org.neo4j.gds.ml.training.TrainBaseConfig;
import org.neo4j.procedure.Context;

import java.util.List;
import java.util.stream.Stream;

public abstract class TrainProc<
    ALGO extends Algorithm<ALGO_RESULT>,
    ALGO_RESULT,
    TRAIN_CONFIG extends TrainBaseConfig,
    PROC_RESULT
    > extends AlgoBaseProc<ALGO, ALGO_RESULT, TRAIN_CONFIG, PROC_RESULT> {

    @Context
    public ModelCatalog modelCatalog;

    protected abstract String modelType();

    protected abstract PROC_RESULT constructProcResult(ComputationResult<ALGO, ALGO_RESULT, TRAIN_CONFIG> computationResult);

    protected abstract Model<?, ?, ?> extractModel(ALGO_RESULT algo_result);

    @Override
    public ComputationResultConsumer<ALGO, ALGO_RESULT, TRAIN_CONFIG, Stream<PROC_RESULT>> computationResultConsumer() {
        return (computationResult, executionContext) -> {
            if (computationResult.result().isPresent()) {
                var model = extractModel(computationResult.result().get());
                var modelCatalog = executionContext.modelCatalog();
                modelCatalog.set(model);

                if (computationResult.config().storeModelToDisk()) {
                    try {
                        modelCatalog.checkLicenseBeforeStoreModel(databaseService, "Store a model");
                        var modelDir = modelCatalog.getModelDirectory(databaseService);
                        modelCatalog.store(model.creator(), model.name(), modelDir);
                    } catch (Exception e) {
                        log.error("Failed to store model to disk after training.", e.getMessage());
                        throw e;
                    }
                }
                return Stream.of(constructProcResult(computationResult));
            }

            return Stream.empty();
        };
    }

    protected Stream<PROC_RESULT> trainAndSetModelWithResult(ComputationResult<ALGO, ALGO_RESULT, TRAIN_CONFIG> computationResult) {
        return computationResultConsumer().consume(computationResult, executionContext());
    }

    @Override
    public ValidationConfiguration<TRAIN_CONFIG> validationConfig(ExecutionContext executionContext) {
        return new ValidationConfiguration<>() {
            @Override
            public List<BeforeLoadValidation<TRAIN_CONFIG>> beforeLoadValidations() {
                return List.of(
                   new VerifyThatModelCanBeStored<>(executionContext.modelCatalog(), username(), modelType())
                );
            }
        };
    }

    @Override
    public ExecutionContext executionContext() {
        return super.executionContext().withModelCatalog(modelCatalog);
    }

}
