/*
 * Copyright (c) 2017-2021 "Neo4j,"
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
package org.neo4j.graphalgo.core;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.gds.model.ModelPersistenceUtil;
import org.neo4j.graphalgo.BaseTest;
import org.neo4j.graphalgo.TestLog;
import org.neo4j.graphalgo.core.model.ModelCatalog;
import org.neo4j.test.TestDatabaseManagementServiceBuilder;
import org.neo4j.test.extension.ExtensionCallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PersistedModelsExtensionTest extends BaseTest {

    @TempDir
    Path tempDir;

    @Override
    @ExtensionCallback
    protected void configuration(TestDatabaseManagementServiceBuilder builder) {
        super.configuration(builder);
        builder.setConfig(ModelPersistenceSettings.model_persistence_location, tempDir);

        try {
            ModelPersistenceUtil.createAndPersistModel(tempDir, "modelAlice", "alice");
            ModelPersistenceUtil.createAndPersistModel(tempDir, "modelBob", "bob");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldLoadPersistedModels() {
        var aliceModels = ModelCatalog.list("alice");
        var bobModels = ModelCatalog.list("bob");

        assertThat(aliceModels).hasSize(1);
        assertThat(aliceModels.stream().findFirst().get().name()).isEqualTo("modelAlice");

        assertThat(bobModels).hasSize(1);
        assertThat(bobModels.stream().findFirst().get().name()).isEqualTo("modelBob");
    }

    @Nested
    class ValidationTest extends BaseTest {

        @Test
        void shouldLogIfPersistenceDirectoryDoesNotExists() {
            var testLog = new TestLog();
            assertThat(PersistedModelsExtension.validatePath(tempDir.resolve("DOES_NOT_EXIST"), testLog)).isFalse();

            testLog.containsMessage(TestLog.ERROR, "does not exist. Cannot load or persist models.");
        }

        @Test
        void shouldLogIfPersistenceDirectoryIsNotADirectory() throws IOException {
            var filePath = tempDir.resolve("THIS_IS_A_FILE");
            Files.createFile(filePath);

            var testLog = new TestLog();
            assertThat(PersistedModelsExtension.validatePath(filePath, testLog)).isFalse();

            testLog.containsMessage(TestLog.ERROR, "is not a directory. Cannot load or persist models.");
        }

    }

}
