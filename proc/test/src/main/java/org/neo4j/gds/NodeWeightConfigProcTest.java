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

import org.junit.jupiter.api.DynamicTest;
import org.neo4j.graphalgo.AlgoBaseProc;
import org.neo4j.graphalgo.config.NodeWeightConfig;
import org.neo4j.graphalgo.core.CypherMapWrapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.neo4j.gds.ConfigProcTestHelpers.GRAPH_NAME;

public final class NodeWeightConfigProcTest {

    public static List<DynamicTest> defaultTest(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return List.of(
            defaultNodeWeightProperty(proc, config),
            whitespaceNodeWeightProperty(proc, config),
            validNodeWeightProperty(proc, config)
        );
    }

    public static List<DynamicTest> mandatoryParameterTest(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return List.of(
            unspecifiedNodeWeightProperty(proc, config),
            validNodeWeightProperty(proc, config)
        );
    }

    private NodeWeightConfigProcTest() {}

    private static DynamicTest defaultNodeWeightProperty(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return DynamicTest.dynamicTest("defaultNodeWeightProperty", () -> {
            var algoConfig = ((NodeWeightConfig) proc.newConfig(GRAPH_NAME, config));
            assertThat(algoConfig.nodeWeightProperty()).isNull();
        });
    }

    private static DynamicTest whitespaceNodeWeightProperty(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return DynamicTest.dynamicTest("whitespaceNodeWeightProperty", () -> {
            var nodeWeightConfig = config.withString("nodeWeightProperty", "  ");
            var algoConfig = ((NodeWeightConfig) proc.newConfig(GRAPH_NAME, nodeWeightConfig));
            assertThat(algoConfig.nodeWeightProperty()).isNull();
        });
    }

    private static DynamicTest unspecifiedNodeWeightProperty(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return DynamicTest.dynamicTest("unspecifiedNodeWeightProperty", () -> {
            assertThatThrownBy(() -> proc.newConfig(GRAPH_NAME, config.withoutEntry("nodeWeightProperty")))
                .hasMessageContaining("nodeWeightProperty")
                .hasMessageContaining("mandatory");
        });
    }

    private static DynamicTest validNodeWeightProperty(
        AlgoBaseProc<?, ?, ?> proc,
        CypherMapWrapper config
    ) {
        return DynamicTest.dynamicTest("validNodeWeightProperty", () -> {
            var nodeWeightConfig = config.withString("nodeWeightProperty", "nw");
            var algoConfig = ((NodeWeightConfig) proc.newConfig(GRAPH_NAME, nodeWeightConfig));
            assertThat(algoConfig.nodeWeightProperty()).isEqualTo("nw");
        });
    }
}
