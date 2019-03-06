/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.internal.time.Timer;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Optional;

public class TransformerExecutionContext {
    private final TransformationWorkspaceProvider.TransformationWorkspace workspace;
    private final ExecutionStateChanges executionStateChanges;
    private final Timer executionTimer;
    private final ImmutableSortedMap<String, ValueSnapshot> inputSnapshots;

    private final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileFingerprints;
    private final ImplementationSnapshot implementationSnapshot;
    private final TransformerExecutionBuildCacheKey buildCacheKey;

    public TransformerExecutionContext(
        TransformationWorkspaceProvider.TransformationWorkspace workspace,
        @Nullable ExecutionStateChanges executionStateChanges,
        Timer executionTimer,
        ImmutableSortedMap<String, ValueSnapshot> inputSnapshots,
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileFingerprints,
        ImplementationSnapshot implementationSnapshot,
        TransformerExecutionBuildCacheKey buildCacheKey) {
        this.workspace = workspace;
        this.executionStateChanges = executionStateChanges;
        this.executionTimer = executionTimer;
        this.inputSnapshots = inputSnapshots;
        this.inputFileFingerprints = inputFileFingerprints;
        this.implementationSnapshot = implementationSnapshot;
        this.buildCacheKey = buildCacheKey;
    }

    public Optional<ExecutionStateChanges> getExecutionStateChanges() {
        return Optional.ofNullable(executionStateChanges);
    }

    public TransformationWorkspaceProvider.TransformationWorkspace getWorkspace() {
        return workspace;
    }

    public ImplementationSnapshot getImplementationSnapshot() {
        return implementationSnapshot;
    }

    public ImmutableSortedMap<String, ValueSnapshot> getInputSnapshots() {
        return inputSnapshots;
    }

    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileFingerprints() {
        return inputFileFingerprints;
    }

    public long markExecutionTime() {
        return executionTimer.getElapsedMillis();
    }

    public BuildCacheKey getBuildCacheKey() {
        return buildCacheKey;
    }

    public static class TransformerExecutionBuildCacheKey implements BuildCacheKey {
        private final Transformer transformer;
        private final File inputArtifact;
        private final HashCode hashCode;

        public TransformerExecutionBuildCacheKey(Transformer transformer, File inputArtifact, HashCode hashCode) {
            this.transformer = transformer;
            this.inputArtifact = inputArtifact;
            this.hashCode = hashCode;
        }

        @Override
        public String getHashCode() {
            return hashCode.toString();
        }

        @Override
        public String getDisplayName() {
            return getHashCode() + " for transformer " + transformer.getDisplayName() + ": " + inputArtifact;
        }
    }
}
