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
package org.apache.maven.api.services;

import org.apache.maven.api.ProtoSession;
import org.apache.maven.api.annotations.Experimental;
import org.apache.maven.api.annotations.Nonnull;

import static java.util.Objects.requireNonNull;

/**
 * Base class for requests.
 *
 * @since 4.0.0
 */
@Experimental
abstract class BaseRequest<S extends ProtoSession> implements Request<S> {

    private final S session;
    private final RequestTrace trace;

    protected BaseRequest(@Nonnull S session) {
        this(session, null);
    }

    protected BaseRequest(@Nonnull S session, RequestTrace trace) {
        this.session = requireNonNull(session, "session cannot be null");
        this.trace = trace;
    }

    @Nonnull
    @Override
    public S getSession() {
        return session;
    }

    @Override
    public RequestTrace getTrace() {
        return trace;
    }
}
