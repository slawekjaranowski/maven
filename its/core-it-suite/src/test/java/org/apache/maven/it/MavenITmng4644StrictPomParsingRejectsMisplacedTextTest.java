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
 * This is a test set for <a href="https://issues.apache.org/jira/browse/MNG-4644">MNG-4644</a>.
 *
 * @author Benjamin Bentmann
 */
public class MavenITmng4644StrictPomParsingRejectsMisplacedTextTest extends AbstractMavenIntegrationTestCase {

    public MavenITmng4644StrictPomParsingRejectsMisplacedTextTest() {
        super("[3.0-alpha-7,)");
    }

    /**
     * Verify that misplaced text inside the project element of a POM causes a parser error during reactor builds.
     *
     * @throws Exception in case of failure
     */
    @Test
    public void testit() throws Exception {
        File testDir = extractResources("/mng-4644");

        Verifier verifier = newVerifier(testDir.getAbsolutePath());
        verifier.setAutoclean(false);
        try {
            verifier.addCliArgument("validate");
            verifier.execute();
            verifier.verifyErrorFreeLog();

            fail("Should fail to validate the POM syntax due to misplaced text in project element.");
        } catch (VerificationException e) {
            // expected
        }
    }
}
