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
package org.apache.maven.it;

import java.io.File;

import org.junit.jupiter.api.Test;

/**
 * The usage of a <code>${revision}</code> for the version in the pom file and furthermore
 * defining the property in the pom file and overwrite it via command line and
 * try to build a partial reactor via <code>mvn -pl ..</code>
 * <a href="https://issues.apache.org/jira/browse/MNG-6090">MNG-6090</a>.
 * Note: this IT is 99% copy of MNG-6090 with one difference: it does not use flatten plugin.
 *
 * @author Karl Heinz Marbaise khmarbaise@apache.org
 */
public class MavenITmng8744CIFriendlyTest extends AbstractMavenIntegrationTestCase {

    public MavenITmng8744CIFriendlyTest() {
        super("[4.0.0-rc-4,)");
    }

    /**
     * Check that the resulting run will not fail in case
     * of defining the property via command line and
     * install the projects and afterwards just build
     * a part of the whole reactor.
     *
     * @throws Exception in case of failure
     */
    @Test
    public void testitShouldResolveTheInstalledDependencies() throws Exception {
        File testDir = extractResources("/mng-8744-ci-friendly");

        Verifier verifier = newVerifier(testDir.getAbsolutePath(), false);
        verifier.setAutoclean(false);

        verifier.addCliArgument("-Drevision=1.2");
        verifier.addCliArgument("-Dmaven.maven3Personality=true");
        verifier.setLogFileName("install-log.txt");
        verifier.addCliArguments("clean", "install");
        verifier.execute();
        verifier.verifyErrorFreeLog();

        verifier = newVerifier(testDir.getAbsolutePath(), false);
        verifier.setAutoclean(false);
        verifier.addCliArgument("clean");
        verifier.execute();
        verifier.verifyErrorFreeLog();

        verifier = newVerifier(testDir.getAbsolutePath(), false);
        verifier.setAutoclean(false);

        verifier.addCliArgument("-Drevision=1.2");
        verifier.addCliArgument("-Dmaven.maven3Personality=true");
        verifier.addCliArgument("-pl");
        verifier.addCliArgument("module-3");
        verifier.addCliArgument("package");
        verifier.execute();
        verifier.verifyErrorFreeLog();
    }
}
