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
package org.apache.maven.impl.model;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.maven.api.Version;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.model.ModelValidator;
import org.apache.maven.impl.InternalSession;
import org.apache.maven.impl.model.profile.SimpleProblemCollector;
import org.apache.maven.model.v4.MavenStaxReader;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.scope.DependencyScope;
import org.eclipse.aether.scope.ScopeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultModelValidatorTest {

    private ModelValidator validator;

    private InternalSession session;

    private Model read(String pom) throws Exception {
        String resource = "/poms/validation/" + pom;
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            assertNotNull(is, "missing resource: " + resource);
            return new MavenStaxReader().read(is);
        }
    }

    private SimpleProblemCollector validate(String pom) throws Exception {
        return validateEffective(pom, ModelValidator.VALIDATION_LEVEL_STRICT);
    }

    private SimpleProblemCollector validateFile(String pom) throws Exception {
        return validateFile(pom, ModelValidator.VALIDATION_LEVEL_STRICT);
    }

    private SimpleProblemCollector validateRaw(String pom) throws Exception {
        return validateRaw(pom, ModelValidator.VALIDATION_LEVEL_STRICT);
    }

    @SuppressWarnings("SameParameterValue")
    private SimpleProblemCollector validateEffective(String pom, int level) throws Exception {
        Model model = read(pom);
        SimpleProblemCollector problems = new SimpleProblemCollector();
        validator.validateEffectiveModel(session, model, level, problems);
        return problems;
    }

    @SuppressWarnings("SameParameterValue")
    private SimpleProblemCollector validateFile(String pom, int level) throws Exception {
        Model model = read(pom);
        SimpleProblemCollector problems = new SimpleProblemCollector();
        validator.validateFileModel(session, model, level, problems);
        return problems;
    }

    @SuppressWarnings("SameParameterValue")
    private SimpleProblemCollector validateRaw(String pom, int level) throws Exception {
        Model model = read(pom);
        SimpleProblemCollector problems = new SimpleProblemCollector();
        validator.validateRawModel(session, model, level, problems);
        return problems;
    }

    private void assertContains(String msg, String substring) {
        assertTrue(msg.contains(substring), "\"" + substring + "\" was not found in: " + msg);
    }

    private static class DepScope implements DependencyScope {
        private final String id;

        private DepScope(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isTransitive() {
            return false;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        validator = new DefaultModelValidator();

        Collection<DependencyScope> scopes = new ArrayList<>();
        scopes.add(new DepScope("compile"));
        scopes.add(new DepScope("runtime"));
        scopes.add(new DepScope("provided"));
        scopes.add(new DepScope("test"));
        ScopeManager scopeManager = mock(ScopeManager.class);
        when(scopeManager.getDependencyScopeUniverse()).thenReturn(scopes);
        RepositorySystemSession repoSession = mock(RepositorySystemSession.class);
        when(repoSession.getScopeManager()).thenReturn(scopeManager);
        session = mock(InternalSession.class);
        when(session.getSession()).thenReturn(repoSession);

        // Mock Maven version for error message testing
        Version mavenVersion = mock(Version.class);
        when(mavenVersion.toString()).thenReturn("4.0.0-test");
        when(session.getMavenVersion()).thenReturn(mavenVersion);
    }

    @AfterEach
    void tearDown() throws Exception {
        this.validator = null;
    }

    private void assertViolations(SimpleProblemCollector result, int fatals, int errors, int warnings) {
        assertEquals(fatals, result.getFatals().size(), String.valueOf(result.getFatals()));
        assertEquals(errors, result.getErrors().size(), String.valueOf(result.getErrors()));
        assertEquals(warnings, result.getWarnings().size(), String.valueOf(result.getWarnings()));
    }

    @Test
    void testMissingModelVersion() throws Exception {
        SimpleProblemCollector result = validate("missing-modelVersion-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertEquals("'modelVersion' is missing.", result.getErrors().get(0));
    }

    @Test
    void testBadModelVersion() throws Exception {
        SimpleProblemCollector result = validateFile("bad-modelVersion.xml");

        assertViolations(result, 1, 0, 0);

        assertTrue(result.getFatals().get(0).contains("modelVersion"));
    }

    @Test
    void testModelVersionMessage() throws Exception {
        SimpleProblemCollector result = validateFile("modelVersion-4_0.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(result.getErrors().get(0).contains("'modelVersion' must be one of"));
    }

    @Test
    void testModelVersionMessageIncludesMavenVersion() throws Exception {
        SimpleProblemCollector result = validateFile("bad-modelVersion.xml");

        assertViolations(result, 1, 0, 0);

        String errorMessage = result.getFatals().get(0);
        assertTrue(errorMessage.contains("modelVersion"));
        // Should include Maven version (either "4.0.0-test" from mock or "unknown" as fallback)
        assertTrue(
                errorMessage.contains("4.0.0-test") || errorMessage.contains("unknown"),
                "Error message should include Maven version: " + errorMessage);
        assertTrue(errorMessage.contains("is not supported by this Maven version"));
    }

    @Test
    void testMissingArtifactId() throws Exception {
        SimpleProblemCollector result = validate("missing-artifactId-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertEquals("'artifactId' is missing.", result.getErrors().get(0));
    }

    @Test
    void testMissingGroupId() throws Exception {
        SimpleProblemCollector result = validate("missing-groupId-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertEquals("'groupId' is missing.", result.getErrors().get(0));
    }

    @Test
    void testInvalidCoordinateIds() throws Exception {
        SimpleProblemCollector result = validate("invalid-coordinate-ids-pom.xml");

        assertViolations(result, 0, 2, 0);

        assertEquals(
                "'groupId' with value 'o/a/m' does not match a valid coordinate id pattern.",
                result.getErrors().get(0));

        assertEquals(
                "'artifactId' with value 'm$-do$' does not match a valid coordinate id pattern.",
                result.getErrors().get(1));
    }

    @Test
    void testMissingType() throws Exception {
        SimpleProblemCollector result = validate("missing-type-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertEquals("'packaging' is missing.", result.getErrors().get(0));
    }

    @Test
    void testMissingVersion() throws Exception {
        SimpleProblemCollector result = validate("missing-version-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertEquals("'version' is missing.", result.getErrors().get(0));
    }

    @Test
    void testInvalidAggregatorPackaging() throws Exception {
        SimpleProblemCollector result = validate("invalid-aggregator-packaging-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(result.getErrors().get(0).contains("Aggregator projects require 'pom' as packaging."));
    }

    @Test
    void testMissingDependencyArtifactId() throws Exception {
        SimpleProblemCollector result = validate("missing-dependency-artifactId-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(
                result.getErrors()
                        .get(0)
                        .contains(
                                "'dependencies.dependency.artifactId' for groupId='groupId', artifactId=, type='jar' is missing"));
    }

    @Test
    void testMissingDependencyGroupId() throws Exception {
        SimpleProblemCollector result = validate("missing-dependency-groupId-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(
                result.getErrors()
                        .get(0)
                        .contains(
                                "'dependencies.dependency.groupId' for groupId=, artifactId='artifactId', type='jar' is missing"));
    }

    @Test
    void testMissingDependencyVersion() throws Exception {
        SimpleProblemCollector result = validate("missing-dependency-version-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(
                result.getErrors()
                        .get(0)
                        .contains(
                                "'dependencies.dependency.version' for groupId='groupId', artifactId='artifactId', type='jar' is missing"));
    }

    @Test
    void testMissingDependencyManagementArtifactId() throws Exception {
        SimpleProblemCollector result = validate("missing-dependency-mgmt-artifactId-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(
                result.getErrors()
                        .get(0)
                        .contains(
                                "'dependencyManagement.dependencies.dependency.artifactId' for groupId='groupId', artifactId=, type='jar' is missing"));
    }

    @Test
    void testMissingDependencyManagementGroupId() throws Exception {
        SimpleProblemCollector result = validate("missing-dependency-mgmt-groupId-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(
                result.getErrors()
                        .get(0)
                        .contains(
                                "'dependencyManagement.dependencies.dependency.groupId' for groupId=, artifactId='artifactId', type='jar' is missing"));
    }

    @Test
    void testMissingAll() throws Exception {
        SimpleProblemCollector result = validate("missing-1-pom.xml");

        assertViolations(result, 0, 4, 0);

        List<String> messages = result.getErrors();

        assertTrue(messages.contains("'modelVersion' is missing."));
        assertTrue(messages.contains("'groupId' is missing."));
        assertTrue(messages.contains("'artifactId' is missing."));
        assertTrue(messages.contains("'version' is missing."));
        // type is inherited from the super pom
    }

    @Test
    void testMissingPluginArtifactId() throws Exception {
        SimpleProblemCollector result = validate("missing-plugin-artifactId-pom.xml");

        assertViolations(result, 0, 1, 0);

        assertEquals(
                "'build.plugins.plugin.artifactId' is missing.",
                result.getErrors().get(0));
    }

    @Test
    void testEmptyPluginVersion() throws Exception {
        SimpleProblemCollector result = validate("empty-plugin-version.xml");

        assertViolations(result, 0, 1, 0);

        assertEquals(
                "'build.plugins.plugin.version' for org.apache.maven.plugins:maven-it-plugin"
                        + " must be a valid version but is ''.",
                result.getErrors().get(0));
    }

    @Test
    void testMissingRepositoryId() throws Exception {
        SimpleProblemCollector result =
                validateFile("missing-repository-id-pom.xml", ModelValidator.VALIDATION_LEVEL_STRICT);

        assertViolations(result, 0, 4, 0);

        assertEquals(
                "'repositories.repository.id' is missing.", result.getErrors().get(0));

        assertEquals(
                "'repositories.repository.[null].url' is missing.",
                result.getErrors().get(1));

        assertEquals(
                "'pluginRepositories.pluginRepository.id' is missing.",
                result.getErrors().get(2));

        assertEquals(
                "'pluginRepositories.pluginRepository.[null].url' is missing.",
                result.getErrors().get(3));
    }

    @Test
    void testMissingResourceDirectory() throws Exception {
        SimpleProblemCollector result = validate("missing-resource-directory-pom.xml");

        assertViolations(result, 0, 2, 0);

        assertEquals(
                "'build.resources.resource.directory' is missing.",
                result.getErrors().get(0));

        assertEquals(
                "'build.testResources.testResource.directory' is missing.",
                result.getErrors().get(1));
    }

    @Test
    void testBadPluginDependencyScope() throws Exception {
        SimpleProblemCollector result = validate("bad-plugin-dependency-scope.xml");

        assertViolations(result, 0, 3, 0);

        assertTrue(result.getErrors().get(0).contains("groupId='test', artifactId='d'"));

        assertTrue(result.getErrors().get(1).contains("groupId='test', artifactId='e'"));

        assertTrue(result.getErrors().get(2).contains("groupId='test', artifactId='f'"));
    }

    @Test
    void testBadDependencyScope() throws Exception {
        SimpleProblemCollector result = validate("bad-dependency-scope.xml");

        assertViolations(result, 0, 0, 2);

        assertTrue(result.getWarnings().get(0).contains("groupId='test', artifactId='f'"));

        assertTrue(result.getWarnings().get(1).contains("groupId='test', artifactId='g'"));
    }

    @Test
    void testBadDependencyManagementScope() throws Exception {
        SimpleProblemCollector result = validate("bad-dependency-management-scope.xml");

        assertViolations(result, 0, 0, 1);

        assertContains(result.getWarnings().get(0), "groupId='test', artifactId='g'");
    }

    @Test
    void testBadDependencyVersion() throws Exception {
        SimpleProblemCollector result = validate("bad-dependency-version.xml");

        assertViolations(result, 0, 2, 0);

        assertContains(
                result.getErrors().get(0),
                "'dependencies.dependency.version' for groupId='test', artifactId='b', type='jar' must be a valid version");
        assertContains(
                result.getErrors().get(1),
                "'dependencies.dependency.version' for groupId='test', artifactId='c', type='jar' must not contain any of these characters");
    }

    @Test
    void testDuplicateModule() throws Exception {
        SimpleProblemCollector result = validateFile("duplicate-module.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(result.getErrors().get(0).contains("child"));
    }

    @Test
    void testInvalidProfileId() throws Exception {
        SimpleProblemCollector result = validateFile("invalid-profile-ids.xml");

        assertViolations(result, 0, 4, 0);

        assertTrue(result.getErrors().get(0).contains("+invalid-id"));
        assertTrue(result.getErrors().get(1).contains("-invalid-id"));
        assertTrue(result.getErrors().get(2).contains("!invalid-id"));
        assertTrue(result.getErrors().get(3).contains("?invalid-id"));
    }

    @Test
    void testDuplicateProfileId() throws Exception {
        SimpleProblemCollector result = validateFile("duplicate-profile-id.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(result.getErrors().get(0).contains("non-unique-id"));
    }

    @Test
    void testBadPluginVersion() throws Exception {
        SimpleProblemCollector result = validate("bad-plugin-version.xml");

        assertViolations(result, 0, 4, 0);

        assertContains(
                result.getErrors().get(0), "'build.plugins.plugin.version' for test:mip must be a valid version");
        assertContains(
                result.getErrors().get(1), "'build.plugins.plugin.version' for test:rmv must be a valid version");
        assertContains(
                result.getErrors().get(2), "'build.plugins.plugin.version' for test:lmv must be a valid version");
        assertContains(
                result.getErrors().get(3),
                "'build.plugins.plugin.version' for test:ifsc must not contain any of these characters");
    }

    @Test
    void testDistributionManagementStatus() throws Exception {
        SimpleProblemCollector result = validate("distribution-management-status.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(result.getErrors().get(0).contains("distributionManagement.status"));
    }

    @Test
    void testIncompleteParent() throws Exception {
        SimpleProblemCollector result = validateRaw("incomplete-parent.xml");

        assertViolations(result, 3, 0, 0);
        assertTrue(result.getFatals().get(0).contains("parent.groupId"));
        assertTrue(result.getFatals().get(1).contains("parent.artifactId"));
        assertTrue(result.getFatals().get(2).contains("parent.version"));
    }

    @Test
    void testHardCodedSystemPath() throws Exception {
        SimpleProblemCollector result = validateFile("hard-coded-system-path.xml");

        assertViolations(result, 0, 0, 3);

        assertContains(
                result.getWarnings().get(0),
                "'dependencies.dependency.scope' for groupId='test', artifactId='a', type='jar' declares usage of deprecated 'system' scope");
        assertContains(
                result.getWarnings().get(1),
                "'dependencies.dependency.systemPath' for groupId='test', artifactId='a', type='jar' should use a variable instead of a hard-coded path");
        assertContains(
                result.getWarnings().get(2),
                "'dependencies.dependency.scope' for groupId='test', artifactId='b', type='jar' declares usage of deprecated 'system' scope");
    }

    @Test
    void testEmptyModule() throws Exception {
        SimpleProblemCollector result = validate("empty-module.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(result.getErrors().get(0).contains("'modules.module[0]' has been specified without a path"));
    }

    @Test
    void testDuplicatePlugin() throws Exception {
        SimpleProblemCollector result = validateFile("duplicate-plugin.xml");

        assertViolations(result, 0, 4, 0);

        assertTrue(result.getErrors().get(0).contains("duplicate declaration of plugin test:duplicate"));
        assertTrue(result.getErrors().get(1).contains("duplicate declaration of plugin test:managed-duplicate"));
        assertTrue(result.getErrors().get(2).contains("duplicate declaration of plugin profile:duplicate"));
        assertTrue(result.getErrors().get(3).contains("duplicate declaration of plugin profile:managed-duplicate"));
    }

    @Test
    void testDuplicatePluginExecution() throws Exception {
        SimpleProblemCollector result = validateFile("duplicate-plugin-execution.xml");

        assertViolations(result, 0, 4, 0);

        assertContains(result.getErrors().get(0), "duplicate execution with id a");
        assertContains(result.getErrors().get(1), "duplicate execution with id default");
        assertContains(result.getErrors().get(2), "duplicate execution with id c");
        assertContains(result.getErrors().get(3), "duplicate execution with id b");
    }

    @Test
    void testReservedRepositoryId() throws Exception {
        SimpleProblemCollector result = validate("reserved-repository-id.xml");

        assertViolations(result, 0, 4, 0);

        assertContains(result.getErrors().get(0), "'repositories.repository.id'" + " must not be 'local'");
        assertContains(result.getErrors().get(1), "'pluginRepositories.pluginRepository.id' must not be 'local'");
        assertContains(result.getErrors().get(2), "'distributionManagement.repository.id' must not be 'local'");
        assertContains(result.getErrors().get(3), "'distributionManagement.snapshotRepository.id' must not be 'local'");
    }

    @Test
    void testMissingPluginDependencyGroupId() throws Exception {
        SimpleProblemCollector result = validate("missing-plugin-dependency-groupId.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(result.getErrors().get(0).contains("groupId=, artifactId='a',"));
    }

    @Test
    void testMissingPluginDependencyArtifactId() throws Exception {
        SimpleProblemCollector result = validate("missing-plugin-dependency-artifactId.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(result.getErrors().get(0).contains("groupId='test', artifactId=,"));
    }

    @Test
    void testMissingPluginDependencyVersion() throws Exception {
        SimpleProblemCollector result = validate("missing-plugin-dependency-version.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(result.getErrors().get(0).contains("groupId='test', artifactId='a',"));
    }

    @Test
    void testBadPluginDependencyVersion() throws Exception {
        SimpleProblemCollector result = validate("bad-plugin-dependency-version.xml");

        assertViolations(result, 0, 1, 0);

        assertTrue(result.getErrors().get(0).contains("groupId='test', artifactId='b'"));
    }

    @Test
    void testBadVersion() throws Exception {
        SimpleProblemCollector result = validate("bad-version.xml");

        assertViolations(result, 0, 1, 0);

        assertContains(result.getErrors().get(0), "'version' must not contain any of these characters");
    }

    @Test
    void testBadSnapshotVersion() throws Exception {
        SimpleProblemCollector result = validate("bad-snapshot-version.xml");

        assertViolations(result, 0, 1, 0);

        assertContains(result.getErrors().get(0), "'version' uses an unsupported snapshot version format");
    }

    @Test
    void testBadRepositoryId() throws Exception {
        SimpleProblemCollector result = validate("bad-repository-id.xml");

        assertViolations(result, 0, 4, 0);

        assertContains(
                result.getErrors().get(0), "'repositories.repository.id' must not contain any of these characters");
        assertContains(
                result.getErrors().get(1),
                "'pluginRepositories.pluginRepository.id' must not contain any of these characters");
        assertContains(
                result.getErrors().get(2),
                "'distributionManagement.repository.id' must not contain any of these characters");
        assertContains(
                result.getErrors().get(3),
                "'distributionManagement.snapshotRepository.id' must not contain any of these characters");
    }

    @Test
    void testBadDependencyExclusionId() throws Exception {
        SimpleProblemCollector result =
                validateEffective("bad-dependency-exclusion-id.xml", ModelValidator.VALIDATION_LEVEL_MAVEN_2_0);

        assertViolations(result, 0, 0, 2);

        assertContains(
                result.getWarnings().get(0),
                "'dependencies.dependency.exclusions.exclusion.groupId' for groupId='gid', artifactId='aid', type='jar'");
        assertContains(
                result.getWarnings().get(1),
                "'dependencies.dependency.exclusions.exclusion.artifactId' for groupId='gid', artifactId='aid', type='jar'");

        // MNG-3832: Aether (part of M3+) supports wildcard expressions for exclusions

        SimpleProblemCollector result30 = validate("bad-dependency-exclusion-id.xml");

        assertViolations(result30, 0, 0, 0);
    }

    @Test
    void testMissingDependencyExclusionId() throws Exception {
        SimpleProblemCollector result = validate("missing-dependency-exclusion-id.xml");

        assertViolations(result, 0, 0, 2);

        assertContains(
                result.getWarnings().get(0),
                "'dependencies.dependency.exclusions.exclusion.groupId' for groupId='gid', artifactId='aid', type='jar' is missing");
        assertContains(
                result.getWarnings().get(1),
                "'dependencies.dependency.exclusions.exclusion.artifactId' for groupId='gid', artifactId='aid', type='jar' is missing");
    }

    @Test
    void testBadImportScopeType() throws Exception {
        SimpleProblemCollector result = validateFile("bad-import-scope-type.xml");

        assertViolations(result, 0, 0, 1);

        assertContains(
                result.getWarnings().get(0),
                "'dependencyManagement.dependencies.dependency.type' for groupId='test', artifactId='a', type='jar' must be 'pom'");
    }

    @Test
    void testBadImportScopeClassifier() throws Exception {
        SimpleProblemCollector result = validateFile("bad-import-scope-classifier.xml");

        assertViolations(result, 0, 1, 0);

        assertContains(
                result.getErrors().get(0),
                "'dependencyManagement.dependencies.dependency.classifier' for groupId='test', artifactId='a', classifier='cls', type='pom' must be empty");
    }

    @Test
    void testSystemPathRefersToProjectBasedir() throws Exception {
        SimpleProblemCollector result = validateFile("basedir-system-path.xml");

        assertViolations(result, 0, 0, 4);

        assertContains(
                result.getWarnings().get(0),
                "'dependencies.dependency.scope' for groupId='test', artifactId='a', type='jar' declares usage of deprecated 'system' scope");
        assertContains(
                result.getWarnings().get(1),
                "'dependencies.dependency.systemPath' for groupId='test', artifactId='a', type='jar' should not point at files within the project directory");
        assertContains(
                result.getWarnings().get(2),
                "'dependencies.dependency.scope' for groupId='test', artifactId='b', type='jar' declares usage of deprecated 'system' scope");
        assertContains(
                result.getWarnings().get(3),
                "'dependencies.dependency.systemPath' for groupId='test', artifactId='b', type='jar' should not point at files within the project directory");
    }

    @Test
    void testInvalidVersionInPluginManagement() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/missing-plugin-version-pluginManagement.xml");

        assertViolations(result, 1, 0, 0);

        assertEquals(
                "'build.pluginManagement.plugins.plugin.(groupId:artifactId)' version of a plugin must be defined. ",
                result.getFatals().get(0));
    }

    @Test
    void testInvalidGroupIdInPluginManagement() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/missing-groupId-pluginManagement.xml");

        assertViolations(result, 1, 0, 0);

        assertEquals(
                "'build.pluginManagement.plugins.plugin.(groupId:artifactId)' groupId of a plugin must be defined. ",
                result.getFatals().get(0));
    }

    @Test
    void testInvalidArtifactIdInPluginManagement() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/missing-artifactId-pluginManagement.xml");

        assertViolations(result, 1, 0, 0);

        assertEquals(
                "'build.pluginManagement.plugins.plugin.(groupId:artifactId)' artifactId of a plugin must be defined. ",
                result.getFatals().get(0));
    }

    @Test
    void testInvalidGroupAndArtifactIdInPluginManagement() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/missing-ga-pluginManagement.xml");

        assertViolations(result, 2, 0, 0);

        assertEquals(
                "'build.pluginManagement.plugins.plugin.(groupId:artifactId)' groupId of a plugin must be defined. ",
                result.getFatals().get(0));

        assertEquals(
                "'build.pluginManagement.plugins.plugin.(groupId:artifactId)' artifactId of a plugin must be defined. ",
                result.getFatals().get(1));
    }

    @Test
    void testMissingReportPluginVersion() throws Exception {
        SimpleProblemCollector result = validate("missing-report-version-pom.xml");

        assertViolations(result, 0, 0, 0);
    }

    @Test
    void testDeprecatedDependencyMetaversionsLatestAndRelease() throws Exception {
        SimpleProblemCollector result = validateFile("deprecated-dependency-metaversions-latest-and-release.xml");

        assertViolations(result, 0, 0, 2);

        assertContains(
                result.getWarnings().get(0),
                "'dependencies.dependency.version' for groupId='test', artifactId='a', type='jar' is either LATEST or RELEASE (both of them are being deprecated)");
        assertContains(
                result.getWarnings().get(1),
                "'dependencies.dependency.version' for groupId='test', artifactId='b', type='jar' is either LATEST or RELEASE (both of them are being deprecated)");
    }

    @Test
    void testSelfReferencingDependencyInRawModel() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/self-referencing.xml");

        assertViolations(result, 1, 0, 0);

        assertEquals(
                "'dependencies.dependency[com.example.group:testinvalidpom:0.0.1-SNAPSHOT]' for com.example.group:testinvalidpom:0.0.1-SNAPSHOT is referencing itself.",
                result.getFatals().get(0));
    }

    @Test
    void testSelfReferencingDependencyWithClassifierInRawModel() throws Exception {
        SimpleProblemCollector result = validateRaw("raw-model/self-referencing-classifier.xml");

        assertViolations(result, 0, 0, 0);
    }

    @Test
    void testCiFriendlySha1() throws Exception {
        SimpleProblemCollector result = validateRaw("raw-model/ok-ci-friendly-sha1.xml");
        assertViolations(result, 0, 0, 0);
    }

    @Test
    void testCiFriendlyRevision() throws Exception {
        SimpleProblemCollector result = validateRaw("raw-model/ok-ci-friendly-revision.xml");
        assertViolations(result, 0, 0, 0);
    }

    @Test
    void testCiFriendlyChangeList() throws Exception {
        SimpleProblemCollector result = validateRaw("raw-model/ok-ci-friendly-changelist.xml");
        assertViolations(result, 0, 0, 0);
    }

    @Test
    void testCiFriendlyAllExpressions() throws Exception {
        SimpleProblemCollector result = validateRaw("raw-model/ok-ci-friendly-all-expressions.xml");
        assertViolations(result, 0, 0, 0);
    }

    @Test
    void testCiFriendlyBad() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/bad-ci-friendly.xml");
        assertViolations(result, 0, 0, 1);
        assertEquals(
                "'version' contains an expression but should be a constant.",
                result.getWarnings().get(0));
    }

    @Test
    void testCiFriendlyBadSha1Plus() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/bad-ci-friendly-sha1plus.xml");
        assertViolations(result, 0, 0, 1);
        assertEquals(
                "'version' contains an expression but should be a constant.",
                result.getWarnings().get(0));
    }

    @Test
    void testCiFriendlyBadSha1Plus2() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/bad-ci-friendly-sha1plus2.xml");
        assertViolations(result, 0, 0, 1);
        assertEquals(
                "'version' contains an expression but should be a constant.",
                result.getWarnings().get(0));
    }

    @Test
    void testParentVersionLATEST() throws Exception {
        SimpleProblemCollector result = validateRaw("raw-model/bad-parent-version-latest.xml");
        assertViolations(result, 0, 0, 1);
        assertEquals(
                "'parent.version' is either LATEST or RELEASE (both of them are being deprecated)",
                result.getWarnings().get(0));
    }

    @Test
    void testParentVersionRELEASE() throws Exception {
        SimpleProblemCollector result = validateRaw("raw-model/bad-parent-version-release.xml");
        assertViolations(result, 0, 0, 1);
        assertEquals(
                "'parent.version' is either LATEST or RELEASE (both of them are being deprecated)",
                result.getWarnings().get(0));
    }

    @Test
    void repositoryWithExpression() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/repository-with-expression.xml");
        assertViolations(result, 0, 1, 0);
        assertEquals(
                "'repositories.repository.[repo].url' contains an unsupported expression (only expressions starting with 'project.basedir' or 'project.rootDirectory' are supported).",
                result.getErrors().get(0));
    }

    @Test
    void repositoryWithBasedirExpression() throws Exception {
        SimpleProblemCollector result = validateRaw("raw-model/repository-with-basedir-expression.xml");
        assertViolations(result, 0, 0, 0);
    }

    @Test
    void profileActivationWithAllowedExpression() throws Exception {
        SimpleProblemCollector result = validateRaw(
                "raw-model/profile-activation-file-with-allowed-expressions.xml",
                ModelValidator.VALIDATION_LEVEL_STRICT);
        //                mbr -> mbr.userProperties(
        //                        Map.of("foo", "foo", "bar", "foo")));
        assertViolations(result, 0, 0, 0);
    }

    @Test
    void profileActivationFileWithProjectExpression() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/profile-activation-file-with-project-expressions.xml");
        assertViolations(result, 0, 0, 2);

        assertEquals(
                "'profiles.profile[exists-project-version].activation.file.exists' "
                        + "Failed to interpolate profile activation property ${project.version}/test.txt: "
                        + "${project.version} expressions are not supported during profile activation.",
                result.getWarnings().get(0));

        assertEquals(
                "'profiles.profile[missing-project-version].activation.file.missing' "
                        + "Failed to interpolate profile activation property ${project.version}/test.txt: "
                        + "${project.version} expressions are not supported during profile activation.",
                result.getWarnings().get(1));
    }

    @Test
    void profileActivationPropertyWithProjectExpression() throws Exception {
        SimpleProblemCollector result =
                validateFile("raw-model/profile-activation-property-with-project-expressions.xml");
        assertViolations(result, 0, 0, 2);

        assertEquals(
                "'profiles.profile[property-name-project-version].activation.property.name' "
                        + "Failed to interpolate profile activation property ${project.version}: "
                        + "${project.version} expressions are not supported during profile activation.",
                result.getWarnings().get(0));

        assertEquals(
                "'profiles.profile[property-value-project-version].activation.property.value' "
                        + "Failed to interpolate profile activation property ${project.version}: "
                        + "${project.version} expressions are not supported during profile activation.",
                result.getWarnings().get(1));
    }

    @Test
    void selfCombineOk() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/self-combine-ok.xml");
        assertViolations(result, 0, 0, 0);
    }

    @Test
    void selfCombineBad() throws Exception {
        SimpleProblemCollector result = validateFile("raw-model/self-combine-bad.xml");
        assertViolations(result, 0, 1, 0);
    }
}
