/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.impl;

import org.apache.maven.api.ArtifactCoordinates;
import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.services.ArtifactCoordinatesFactory;
import org.apache.maven.api.services.ArtifactCoordinatesFactoryRequest;
import org.eclipse.aether.artifact.ArtifactType;

import static java.util.Objects.requireNonNull;

@Named
@Singleton
public class DefaultArtifactCoordinatesFactory implements ArtifactCoordinatesFactory {
    @Override
    public ArtifactCoordinates create(@Nonnull ArtifactCoordinatesFactoryRequest request) {
        requireNonNull(request, "request");
        InternalSession session = InternalSession.from(request.getSession());
        if (request.getCoordinatesString() != null) {
            return new DefaultArtifactCoordinates(
                    session, new org.eclipse.aether.artifact.DefaultArtifact(request.getCoordinatesString()));
        } else {
            ArtifactType type = null;
            if (request.getType() != null) {
                type = session.getSession().getArtifactTypeRegistry().get(request.getType());
            }
            String str1 = request.getClassifier();
            String classifier = str1 != null && !str1.isEmpty()
                    ? request.getClassifier()
                    : type != null ? type.getClassifier() : "";
            String str = request.getExtension();
            String extension =
                    str != null && !str.isEmpty() ? request.getExtension() : type != null ? type.getExtension() : "";
            return new DefaultArtifactCoordinates(
                    session,
                    new org.eclipse.aether.artifact.DefaultArtifact(
                            request.getGroupId(),
                            request.getArtifactId(),
                            classifier,
                            extension,
                            request.getVersion(),
                            type));
        }
    }
}
