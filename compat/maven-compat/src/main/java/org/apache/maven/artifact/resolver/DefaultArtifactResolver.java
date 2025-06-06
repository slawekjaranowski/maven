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
package org.apache.maven.artifact.resolver;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.metadata.ResolutionGroup;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.LegacyLocalRepositoryManager;
import org.apache.maven.artifact.repository.RepositoryRequest;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotArtifactRepositoryMetadata;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.apache.maven.repository.legacy.metadata.DefaultMetadataResolutionRequest;
import org.apache.maven.repository.legacy.metadata.MetadataResolutionRequest;
import org.apache.maven.repository.legacy.resolver.conflict.ConflictResolver;
import org.apache.maven.wagon.events.TransferListener;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 */
@Named
@Singleton
@Deprecated
public class DefaultArtifactResolver implements ArtifactResolver, Disposable {
    @Inject
    private Logger logger;

    @Inject
    protected ArtifactFactory artifactFactory;

    @Inject
    private ArtifactCollector artifactCollector;

    @Inject
    private ResolutionErrorHandler resolutionErrorHandler;

    @Inject
    private ArtifactMetadataSource source;

    @Inject
    private PlexusContainer container;

    @Inject
    private LegacySupport legacySupport;

    private final Executor executor;

    public DefaultArtifactResolver() {
        int threads = Integer.getInteger("maven.artifact.threads", 5);
        if (threads <= 1) {
            executor = Runnable::run;
        } else {
            executor = new ThreadPoolExecutor(
                    threads, threads, 3, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new DaemonThreadCreator());
        }
    }

    private RepositorySystemSession getSession(ArtifactRepository localRepository) {
        return LegacyLocalRepositoryManager.overlay(localRepository, legacySupport.getRepositorySession(), null);
    }

    private void injectSession1(RepositoryRequest request, MavenSession session) {
        if (session != null) {
            request.setOffline(session.isOffline());
            request.setForceUpdate(session.getRequest().isUpdateSnapshots());
        }
    }

    private void injectSession2(ArtifactResolutionRequest request, MavenSession session) {
        injectSession1(request, session);

        if (session != null) {
            request.setServers(session.getRequest().getServers());
            request.setMirrors(session.getRequest().getMirrors());
            request.setProxies(session.getRequest().getProxies());
        }
    }

    @Override
    public void resolve(
            Artifact artifact,
            List<ArtifactRepository> remoteRepositories,
            ArtifactRepository localRepository,
            TransferListener resolutionListener)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        resolve(artifact, remoteRepositories, getSession(localRepository));
    }

    @Override
    public void resolveAlways(
            Artifact artifact, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        resolve(artifact, remoteRepositories, getSession(localRepository));
    }

    private void resolve(
            Artifact artifact, List<ArtifactRepository> remoteRepositories, RepositorySystemSession session)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        if (artifact == null) {
            return;
        }

        if (Artifact.SCOPE_SYSTEM.equals(artifact.getScope())) {
            File systemFile = artifact.getFile();

            if (systemFile == null) {
                throw new ArtifactNotFoundException("System artifact: " + artifact + " has no file attached", artifact);
            }

            if (!systemFile.exists()) {
                throw new ArtifactNotFoundException(
                        "System artifact: " + artifact + " not found in path: " + systemFile, artifact);
            }

            if (!systemFile.isFile()) {
                throw new ArtifactNotFoundException(
                        "System artifact: " + artifact + " is not a file: " + systemFile, artifact);
            }

            artifact.setResolved(true);

            return;
        }

        if (!artifact.isResolved()) {
            ArtifactResult result;

            try {
                ArtifactRequest artifactRequest = new ArtifactRequest();
                artifactRequest.setArtifact(RepositoryUtils.toArtifact(artifact));
                artifactRequest.setRepositories(RepositoryUtils.toRepos(remoteRepositories));

                // Maven 2.x quirk: an artifact always points at the local repo, regardless whether resolved or not
                LocalRepositoryManager lrm = session.getLocalRepositoryManager();
                String path = lrm.getPathForLocalArtifact(artifactRequest.getArtifact());
                artifact.setFile(new File(lrm.getRepository().getBasedir(), path));

                RepositorySystem repoSystem = container.lookup(RepositorySystem.class);
                result = repoSystem.resolveArtifact(session, artifactRequest);
            } catch (ComponentLookupException e) {
                throw new IllegalStateException("Unable to lookup " + RepositorySystem.class.getName());
            } catch (org.eclipse.aether.resolution.ArtifactResolutionException e) {
                if (e.getCause() instanceof org.eclipse.aether.transfer.ArtifactNotFoundException) {
                    throw new ArtifactNotFoundException(e.getMessage(), artifact, remoteRepositories, e);
                } else {
                    throw new ArtifactResolutionException(e.getMessage(), artifact, remoteRepositories, e);
                }
            }

            artifact.selectVersion(result.getArtifact().getVersion());
            artifact.setFile(result.getArtifact().getFile());
            artifact.setResolved(true);

            if (artifact.isSnapshot()) {
                Matcher matcher = Artifact.VERSION_FILE_PATTERN.matcher(artifact.getVersion());
                if (matcher.matches()) {
                    Snapshot snapshot = new Snapshot();
                    snapshot.setTimestamp(matcher.group(2));
                    try {
                        snapshot.setBuildNumber(Integer.parseInt(matcher.group(3)));
                        artifact.addMetadata(new SnapshotArtifactRepositoryMetadata(artifact, snapshot));
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid artifact version " + artifact.getVersion() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public ArtifactResolutionResult resolveTransitively(
            Set<Artifact> artifacts,
            Artifact originatingArtifact,
            ArtifactRepository localRepository,
            List<ArtifactRepository> remoteRepositories,
            ArtifactMetadataSource source,
            ArtifactFilter filter)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        return resolveTransitively(
                artifacts,
                originatingArtifact,
                Collections.emptyMap(),
                localRepository,
                remoteRepositories,
                source,
                filter);
    }

    @Override
    public ArtifactResolutionResult resolveTransitively(
            Set<Artifact> artifacts,
            Artifact originatingArtifact,
            Map<String, Artifact> managedVersions,
            ArtifactRepository localRepository,
            List<ArtifactRepository> remoteRepositories,
            ArtifactMetadataSource source)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        return resolveTransitively(
                artifacts, originatingArtifact, managedVersions, localRepository, remoteRepositories, source, null);
    }

    @Override
    public ArtifactResolutionResult resolveTransitively(
            Set<Artifact> artifacts,
            Artifact originatingArtifact,
            Map<String, Artifact> managedVersions,
            ArtifactRepository localRepository,
            List<ArtifactRepository> remoteRepositories,
            ArtifactMetadataSource source,
            ArtifactFilter filter)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        return resolveTransitively(
                artifacts,
                originatingArtifact,
                managedVersions,
                localRepository,
                remoteRepositories,
                source,
                filter,
                null);
    }

    @Override
    public ArtifactResolutionResult resolveTransitively(
            Set<Artifact> artifacts,
            Artifact originatingArtifact,
            List<ArtifactRepository> remoteRepositories,
            ArtifactRepository localRepository,
            ArtifactMetadataSource source)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        return resolveTransitively(artifacts, originatingArtifact, localRepository, remoteRepositories, source, null);
    }

    @Override
    public ArtifactResolutionResult resolveTransitively(
            Set<Artifact> artifacts,
            Artifact originatingArtifact,
            List<ArtifactRepository> remoteRepositories,
            ArtifactRepository localRepository,
            ArtifactMetadataSource source,
            List<ResolutionListener> listeners)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        return resolveTransitively(
                artifacts,
                originatingArtifact,
                Collections.emptyMap(),
                localRepository,
                remoteRepositories,
                source,
                null,
                listeners);
    }

    @Override
    @SuppressWarnings("checkstyle:parameternumber")
    public ArtifactResolutionResult resolveTransitively(
            Set<Artifact> artifacts,
            Artifact originatingArtifact,
            Map<String, Artifact> managedVersions,
            ArtifactRepository localRepository,
            List<ArtifactRepository> remoteRepositories,
            ArtifactMetadataSource source,
            ArtifactFilter filter,
            List<ResolutionListener> listeners)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        return resolveTransitively(
                artifacts,
                originatingArtifact,
                managedVersions,
                localRepository,
                remoteRepositories,
                source,
                filter,
                listeners,
                null);
    }

    @SuppressWarnings("checkstyle:parameternumber")
    public ArtifactResolutionResult resolveTransitively(
            Set<Artifact> artifacts,
            Artifact originatingArtifact,
            Map<String, Artifact> managedVersions,
            ArtifactRepository localRepository,
            List<ArtifactRepository> remoteRepositories,
            ArtifactMetadataSource source,
            ArtifactFilter filter,
            List<ResolutionListener> listeners,
            List<ConflictResolver> conflictResolvers)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        ArtifactResolutionRequest request = new ArtifactResolutionRequest()
                .setArtifact(originatingArtifact)
                .setResolveRoot(false)
                .
                // This is required by the surefire plugin
                setArtifactDependencies(artifacts)
                .setManagedVersionMap(managedVersions)
                .setLocalRepository(localRepository)
                .setRemoteRepositories(remoteRepositories)
                .setCollectionFilter(filter)
                .setListeners(listeners);

        injectSession2(request, legacySupport.getSession());

        return resolveWithExceptions(request);
    }

    public ArtifactResolutionResult resolveWithExceptions(ArtifactResolutionRequest request)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        ArtifactResolutionResult result = resolve(request);

        // We have collected all the problems so let's mimic the way the old code worked and just blow up right here.
        // That's right lets just let it rip right here and send a big incomprehensible blob of text at unsuspecting
        // users. Bad dog!

        resolutionErrorHandler.throwErrors(request, result);

        return result;
    }

    // ------------------------------------------------------------------------
    //
    // ------------------------------------------------------------------------

    @Override
    @SuppressWarnings("checkstyle:methodlength")
    public ArtifactResolutionResult resolve(ArtifactResolutionRequest request) {
        Artifact rootArtifact = request.getArtifact();
        Set<Artifact> artifacts = request.getArtifactDependencies();
        Map<String, Artifact> managedVersions = request.getManagedVersionMap();
        List<ResolutionListener> listeners = request.getListeners();
        ArtifactFilter collectionFilter = request.getCollectionFilter();
        ArtifactFilter resolutionFilter = request.getResolutionFilter();
        RepositorySystemSession session = getSession(request.getLocalRepository());

        // TODO: hack because metadata isn't generated in m2e correctly and i want to run the maven i have in the
        // workspace
        if (source == null) {
            try {
                source = container.lookup(ArtifactMetadataSource.class);
            } catch (ComponentLookupException e) {
                // won't happen
            }
        }

        if (listeners == null) {
            listeners = new ArrayList<>();

            if (logger.isDebugEnabled()) {
                listeners.add(new DebugResolutionListener(logger));
            }

            listeners.add(new WarningResolutionListener(logger));
        }

        ArtifactResolutionResult result = new ArtifactResolutionResult();

        // The root artifact may, or may not be resolved so we need to check before we attempt to resolve.
        // This is often an artifact like a POM that is taken from disk and we already have hold of the
        // file reference. But this may be a Maven Plugin that we need to resolve from a remote repository
        // as well as its dependencies.

        if (request.isResolveRoot() /* && rootArtifact.getFile() == null */) {
            try {
                resolve(rootArtifact, request.getRemoteRepositories(), session);
            } catch (ArtifactResolutionException e) {
                result.addErrorArtifactException(e);
                return result;
            } catch (ArtifactNotFoundException e) {
                result.addMissingArtifact(request.getArtifact());
                return result;
            }
        }

        ArtifactResolutionRequest collectionRequest = request;

        if (request.isResolveTransitively()) {
            MetadataResolutionRequest metadataRequest = new DefaultMetadataResolutionRequest(request);

            metadataRequest.setArtifact(rootArtifact);
            metadataRequest.setResolveManagedVersions(managedVersions == null);

            try {
                ResolutionGroup resolutionGroup = source.retrieve(metadataRequest);

                if (managedVersions == null) {
                    managedVersions = resolutionGroup.getManagedVersions();
                }

                Set<Artifact> directArtifacts = resolutionGroup.getArtifacts();

                if (artifacts == null || artifacts.isEmpty()) {
                    artifacts = directArtifacts;
                } else {
                    List<Artifact> allArtifacts = new ArrayList<>();
                    allArtifacts.addAll(artifacts);
                    allArtifacts.addAll(directArtifacts);

                    Map<String, Artifact> mergedArtifacts = new LinkedHashMap<>();
                    for (Artifact artifact : allArtifacts) {
                        String conflictId = artifact.getDependencyConflictId();
                        if (!mergedArtifacts.containsKey(conflictId)) {
                            mergedArtifacts.put(conflictId, artifact);
                        }
                    }

                    artifacts = new LinkedHashSet<>(mergedArtifacts.values());
                }

                collectionRequest = new ArtifactResolutionRequest(request);
                collectionRequest.setServers(request.getServers());
                collectionRequest.setMirrors(request.getMirrors());
                collectionRequest.setProxies(request.getProxies());
                collectionRequest.setRemoteRepositories(resolutionGroup.getResolutionRepositories());
            } catch (ArtifactMetadataRetrievalException e) {
                ArtifactResolutionException are = new ArtifactResolutionException(
                        "Unable to get dependency information for " + rootArtifact.getId() + ": " + e.getMessage(),
                        rootArtifact,
                        metadataRequest.getRemoteRepositories(),
                        e);
                result.addMetadataResolutionException(are);
                return result;
            }
        }

        if (artifacts == null || artifacts.isEmpty()) {
            if (request.isResolveRoot()) {
                result.addArtifact(rootArtifact);
            }
            return result;
        }

        // After the collection we will have the artifact object in the result but they will not be resolved yet.
        result = artifactCollector.collect(
                artifacts, rootArtifact, managedVersions, collectionRequest, source, collectionFilter, listeners, null);

        // We have metadata retrieval problems, or there are cycles that have been detected
        // so we give this back to the calling code and let them deal with this information
        // appropriately.

        if (result.hasMetadataResolutionExceptions()
                || result.hasVersionRangeViolations()
                || result.hasCircularDependencyExceptions()) {
            return result;
        }

        if (result.getArtifactResolutionNodes() != null) {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            CountDownLatch latch =
                    new CountDownLatch(result.getArtifactResolutionNodes().size());

            for (ResolutionNode node : result.getArtifactResolutionNodes()) {
                Artifact artifact = node.getArtifact();

                if (resolutionFilter == null || resolutionFilter.include(artifact)) {
                    executor.execute(new ResolveTask(
                            classLoader, latch, artifact, session, node.getRemoteRepositories(), result));
                } else {
                    latch.countDown();
                }
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                result.addErrorArtifactException(
                        new ArtifactResolutionException("Resolution interrupted", rootArtifact, e));
            }
        }

        // We want to send the root artifact back in the result but we need to do this after the other dependencies
        // have been resolved.
        if (request.isResolveRoot()) {
            // Add the root artifact (as the first artifact to retain logical order of class path!)
            Set<Artifact> allArtifacts = new LinkedHashSet<>();
            allArtifacts.add(rootArtifact);
            allArtifacts.addAll(result.getArtifacts());
            result.setArtifacts(allArtifacts);
        }

        return result;
    }

    @Override
    public void resolve(
            Artifact artifact, List<ArtifactRepository> remoteRepositories, ArtifactRepository localRepository)
            throws ArtifactResolutionException, ArtifactNotFoundException {
        resolve(artifact, remoteRepositories, localRepository, null);
    }

    /**
     * ThreadCreator for creating daemon threads with fixed ThreadGroup-name.
     */
    @Deprecated
    static final class DaemonThreadCreator implements ThreadFactory {
        static final String THREADGROUP_NAME = "org.apache.maven.artifact.resolver.DefaultArtifactResolver";

        static final ThreadGroup GROUP = new ThreadGroup(THREADGROUP_NAME);

        static final AtomicInteger THREAD_NUMBER = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread newThread = new Thread(GROUP, r, "resolver-" + THREAD_NUMBER.getAndIncrement());
            newThread.setDaemon(true);
            newThread.setContextClassLoader(null);
            return newThread;
        }
    }

    private class ResolveTask implements Runnable {

        private final ClassLoader classLoader;

        private final CountDownLatch latch;

        private final Artifact artifact;

        private final RepositorySystemSession session;

        private final List<ArtifactRepository> remoteRepositories;

        private final ArtifactResolutionResult result;

        ResolveTask(
                ClassLoader classLoader,
                CountDownLatch latch,
                Artifact artifact,
                RepositorySystemSession session,
                List<ArtifactRepository> remoteRepositories,
                ArtifactResolutionResult result) {
            this.classLoader = classLoader;
            this.latch = latch;
            this.artifact = artifact;
            this.session = session;
            this.remoteRepositories = remoteRepositories;
            this.result = result;
        }

        @Override
        public void run() {
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(classLoader);
                resolve(artifact, remoteRepositories, session);
            } catch (ArtifactNotFoundException anfe) {
                // These are cases where the artifact just isn't present in any of the remote repositories
                // because it wasn't deployed, or it was deployed in the wrong place.

                synchronized (result) {
                    result.addMissingArtifact(artifact);
                }
            } catch (ArtifactResolutionException e) {
                // This is really a wagon TransferFailedException so something went wrong after we successfully
                // retrieved the metadata.

                synchronized (result) {
                    result.addErrorArtifactException(e);
                }
            } finally {
                latch.countDown();
                Thread.currentThread().setContextClassLoader(old);
            }
        }
    }

    @Override
    public void dispose() {
        if (executor instanceof ExecutorService executorService) {
            executorService.shutdownNow();
        }
    }
}
