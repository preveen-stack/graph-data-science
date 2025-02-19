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
package org.neo4j.gds.compat._56;

import org.neo4j.collection.RawIterator;
import org.neo4j.gds.annotation.SuppressForbidden;
import org.neo4j.gds.compat.CompatCallableProcedure;
import org.neo4j.internal.kernel.api.exceptions.ProcedureException;
import org.neo4j.internal.kernel.api.procs.ProcedureSignature;
import org.neo4j.kernel.api.ResourceMonitor;
import org.neo4j.kernel.api.procedure.CallableProcedure;
import org.neo4j.kernel.api.procedure.Context;
import org.neo4j.values.AnyValue;

@SuppressForbidden(reason = "This is the compat API")
public final class CallableProcedureImpl implements CallableProcedure {
    private final CompatCallableProcedure procedure;

    CallableProcedureImpl(CompatCallableProcedure procedure) {
        this.procedure = procedure;
    }

    @Override
    public ProcedureSignature signature() {
        return this.procedure.signature();
    }

    @Override
    public RawIterator<AnyValue[], ProcedureException> apply(
        Context ctx,
        AnyValue[] input,
        ResourceMonitor resourceMonitor
    ) throws ProcedureException {
        return this.procedure.apply(ctx, input);
    }
}
