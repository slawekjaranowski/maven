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
package org.apache.maven.api.cli;

import org.apache.maven.api.annotations.Experimental;
import org.apache.maven.api.annotations.Immutable;

/**
 * Represents most common tools supported by CLIng.
 *
 * @since 4.0.0
 */
@Immutable
@Experimental
public final class Tools {
    private Tools() {}

    public static final String MVN_CMD = "mvn";
    public static final String MVN_NAME = "Maven";

    public static final String MVNENC_CMD = "mvnenc";
    public static final String MVNENC_NAME = "Maven Password Encrypting Tool";

    public static final String MVNSHELL_CMD = "mvnsh";
    public static final String MVNSHELL_NAME = "Maven Shell Tool";

    public static final String MVNUP_CMD = "mvnup";
    public static final String MVNUP_NAME = "Maven Upgrade Tool";
}
