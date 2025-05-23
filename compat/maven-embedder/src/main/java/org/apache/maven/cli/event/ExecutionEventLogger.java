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
package org.apache.maven.cli.event;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.apache.maven.api.services.MessageBuilder;
import org.apache.maven.api.services.MessageBuilderFactory;
import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.BuildFailure;
import org.apache.maven.execution.BuildSuccess;
import org.apache.maven.execution.BuildSummary;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.maven.cli.CLIReportingUtils.formatDuration;
import static org.apache.maven.cli.CLIReportingUtils.formatTimestamp;

/**
 * Logs execution events to logger, eventually user-supplied.
 *
 */
@Deprecated
public class ExecutionEventLogger extends AbstractExecutionListener {
    private static final int MAX_LOG_PREFIX_SIZE = 8; // "[ERROR] "
    private static final int PROJECT_STATUS_SUFFIX_SIZE = 20; // "SUCCESS [  0.000 s]"
    private static final int MIN_TERMINAL_WIDTH = 60;
    private static final int DEFAULT_TERMINAL_WIDTH = 80;
    private static final int MAX_TERMINAL_WIDTH = 130;
    private static final int MAX_PADDED_BUILD_TIME_DURATION_LENGTH = 9;

    private final MessageBuilderFactory messageBuilderFactory;
    private final Logger logger;
    private int terminalWidth;
    private int lineLength;
    private int maxProjectNameLength;
    private int totalProjects;
    private volatile int currentVisitedProjectCount;

    public ExecutionEventLogger(MessageBuilderFactory messageBuilderFactory) {
        this(messageBuilderFactory, LoggerFactory.getLogger(ExecutionEventLogger.class));
    }

    public ExecutionEventLogger(MessageBuilderFactory messageBuilderFactory, Logger logger) {
        this(messageBuilderFactory, logger, -1);
    }

    public ExecutionEventLogger(MessageBuilderFactory messageBuilderFactory, Logger logger, int terminalWidth) {
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.messageBuilderFactory = messageBuilderFactory;
        this.terminalWidth = terminalWidth;
    }

    private static String chars(char c, int count) {
        return String.valueOf(c).repeat(Math.max(0, count));
    }

    private void infoLine(char c) {
        infoMain(chars(c, lineLength));
    }

    private void infoMain(String msg) {
        logger.info(builder().strong(msg).toString());
    }

    private void init() {
        if (maxProjectNameLength == 0) {
            if (terminalWidth < 0) {
                terminalWidth = messageBuilderFactory.getTerminalWidth();
            }
            terminalWidth = Math.min(
                    MAX_TERMINAL_WIDTH,
                    Math.max(terminalWidth <= 0 ? DEFAULT_TERMINAL_WIDTH : terminalWidth, MIN_TERMINAL_WIDTH));
            lineLength = terminalWidth - MAX_LOG_PREFIX_SIZE;
            maxProjectNameLength = lineLength - PROJECT_STATUS_SUFFIX_SIZE;
        }
    }

    @Override
    public void projectDiscoveryStarted(ExecutionEvent event) {
        if (logger.isInfoEnabled()) {
            init();
            logger.info("Scanning for projects...");
        }
    }

    @Override
    public void sessionStarted(ExecutionEvent event) {
        if (logger.isInfoEnabled() && event.getSession().getProjects().size() > 1) {
            init();
            infoLine('-');

            infoMain("Reactor Build Order:");

            logger.info("");

            final List<MavenProject> projects = event.getSession().getProjects();
            for (MavenProject project : projects) {
                int len = lineLength
                        - project.getName().length()
                        - project.getPackaging().length()
                        - 2;
                logger.info("{}{}[{}]", project.getName(), chars(' ', (len > 0) ? len : 1), project.getPackaging());
            }

            final List<MavenProject> allProjects = event.getSession().getAllProjects();

            currentVisitedProjectCount = allProjects.size() - projects.size();
            totalProjects = allProjects.size();
        }
    }

    @Override
    public void sessionEnded(ExecutionEvent event) {
        if (logger.isInfoEnabled()) {
            init();
            if (event.getSession().getProjects().size() > 1) {
                logReactorSummary(event.getSession());
            }

            ILoggerFactory iLoggerFactory = LoggerFactory.getILoggerFactory();

            if (iLoggerFactory instanceof org.apache.maven.logging.api.LogLevelRecorder recorder
                    && recorder.hasReachedMaxLevel()) {
                event.getSession()
                        .getResult()
                        .addException(
                                new Exception("Build failed due to log statements with a higher severity than allowed. "
                                        + "Fix the logged issues or remove flag --fail-on-severity (-fos)."));
            }

            logResult(event.getSession());

            logStats(event.getSession());

            infoLine('-');
        }
    }

    private boolean isSingleVersionedReactor(MavenSession session) {
        boolean result = true;

        MavenProject topProject = session.getTopLevelProject();
        List<MavenProject> sortedProjects = session.getProjectDependencyGraph().getSortedProjects();
        for (MavenProject mavenProject : sortedProjects) {
            if (!topProject.getVersion().equals(mavenProject.getVersion())) {
                result = false;
                break;
            }
        }

        return result;
    }

    private void logReactorSummary(MavenSession session) {
        boolean isSingleVersion = isSingleVersionedReactor(session);

        infoLine('-');

        StringBuilder summary = new StringBuilder("Reactor Summary");
        if (isSingleVersion) {
            summary.append(" for ");
            summary.append(session.getTopLevelProject().getName());
            summary.append(" ");
            summary.append(session.getTopLevelProject().getVersion());
        }
        summary.append(":");
        infoMain(summary.toString());

        logger.info("");

        MavenExecutionResult result = session.getResult();

        List<MavenProject> projects = session.getProjects();

        StringBuilder buffer = new StringBuilder(128);

        for (MavenProject project : projects) {
            buffer.append(project.getName());
            buffer.append(' ');

            if (!isSingleVersion) {
                buffer.append(project.getVersion());
                buffer.append(' ');
            }

            if (buffer.length() <= maxProjectNameLength) {
                while (buffer.length() < maxProjectNameLength) {
                    buffer.append('.');
                }
                buffer.append(' ');
            }

            BuildSummary buildSummary = result.getBuildSummary(project);

            if (buildSummary == null) {
                buffer.append(builder().warning("SKIPPED"));
            } else if (buildSummary instanceof BuildSuccess) {
                buffer.append(builder().success("SUCCESS"));
                buffer.append(" [");
                String buildTimeDuration = formatDuration(buildSummary.getTime());
                int padSize = MAX_PADDED_BUILD_TIME_DURATION_LENGTH - buildTimeDuration.length();
                if (padSize > 0) {
                    buffer.append(chars(' ', padSize));
                }
                buffer.append(buildTimeDuration);
                buffer.append(']');
            } else if (buildSummary instanceof BuildFailure) {
                buffer.append(builder().failure("FAILURE"));
                buffer.append(" [");
                String buildTimeDuration = formatDuration(buildSummary.getTime());
                int padSize = MAX_PADDED_BUILD_TIME_DURATION_LENGTH - buildTimeDuration.length();
                if (padSize > 0) {
                    buffer.append(chars(' ', padSize));
                }
                buffer.append(buildTimeDuration);
                buffer.append(']');
            }

            logger.info(buffer.toString());
            buffer.setLength(0);
        }
    }

    private void logResult(MavenSession session) {
        infoLine('-');
        MessageBuilder buffer = builder();

        if (session.getResult().hasExceptions()) {
            buffer.failure("BUILD FAILURE");
        } else {
            buffer.success("BUILD SUCCESS");
        }
        logger.info(buffer.toString());
    }

    private MessageBuilder builder() {
        return messageBuilderFactory.builder();
    }

    private void logStats(MavenSession session) {
        infoLine('-');

        long finish = System.currentTimeMillis();

        long time = finish - session.getRequest().getStartTime().getTime();

        String wallClock = session.getRequest().getDegreeOfConcurrency() > 1 ? " (Wall Clock)" : "";

        logger.info("Total time:  {}{}", formatDuration(time), wallClock);

        logger.info("Finished at: {}", formatTimestamp(finish));
    }

    @Override
    public void projectSkipped(ExecutionEvent event) {
        if (logger.isInfoEnabled()) {
            init();
            logger.info("");
            infoLine('-');
            String name = event.getProject().getName();
            infoMain("Skipping " + name);
            logger.info("{} was not built because a module it depends on failed to build.", name);

            infoLine('-');
        }
    }

    @Override
    public void projectStarted(ExecutionEvent event) {
        if (logger.isInfoEnabled()) {
            init();
            MavenProject project = event.getProject();

            logger.info("");

            // -------< groupId:artifactId >-------
            String projectKey = project.getGroupId() + ':' + project.getArtifactId();

            final String preHeader = "--< ";
            final String postHeader = " >--";

            final int headerLen = preHeader.length() + projectKey.length() + postHeader.length();

            String prefix = chars('-', Math.max(0, (lineLength - headerLen) / 2)) + preHeader;

            String suffix =
                    postHeader + chars('-', Math.max(0, lineLength - headerLen - prefix.length() + preHeader.length()));

            logger.info(
                    builder().strong(prefix).project(projectKey).strong(suffix).toString());

            // Building Project Name Version    [i/n]
            String building = "Building " + event.getProject().getName() + " "
                    + event.getProject().getVersion();

            if (totalProjects <= 1) {
                infoMain(building);
            } else {
                // display progress [i/n]
                int number;
                synchronized (this) {
                    number = ++currentVisitedProjectCount;
                }
                String progress = " [" + number + '/' + totalProjects + ']';

                int pad = lineLength - building.length() - progress.length();

                infoMain(building + ((pad > 0) ? chars(' ', pad) : "") + progress);
            }

            // path to pom.xml
            File currentPom = project.getFile();
            if (currentPom != null) {
                MavenSession session = event.getSession();
                Path current = currentPom.toPath().toAbsolutePath().normalize();
                Path topDirectory = session.getTopDirectory();
                if (topDirectory != null && current.startsWith(topDirectory)) {
                    current = topDirectory.relativize(current);
                }
                logger.info("  from " + current);
            }

            // ----------[ packaging ]----------
            prefix = chars('-', Math.max(0, (lineLength - project.getPackaging().length() - 4) / 2));
            suffix = chars('-', Math.max(0, lineLength - project.getPackaging().length() - 4 - prefix.length()));
            infoMain(prefix + "[ " + project.getPackaging() + " ]" + suffix);
        }
    }

    @Override
    public void mojoSkipped(ExecutionEvent event) {
        if (logger.isWarnEnabled()) {
            init();
            logger.warn(
                    "Goal '{}' requires online mode for execution but Maven is currently offline, skipping",
                    event.getMojoExecution().getGoal());
        }
    }

    /**
     * <pre>--- mojo-artifactId:version:goal (mojo-executionId) @ project-artifactId ---</pre>
     */
    @Override
    public void mojoStarted(ExecutionEvent event) {
        if (logger.isInfoEnabled()) {
            init();
            logger.info("");

            MessageBuilder buffer = builder().strong("--- ");
            append(buffer, event.getMojoExecution());
            append(buffer, event.getProject());
            buffer.strong(" ---");

            logger.info(buffer.toString());
        }
    }

    // CHECKSTYLE_OFF: LineLength
    /**
     * <pre>&gt;&gt;&gt; mojo-artifactId:version:goal (mojo-executionId) &gt; :forked-goal @ project-artifactId &gt;&gt;&gt;</pre>
     * <pre>&gt;&gt;&gt; mojo-artifactId:version:goal (mojo-executionId) &gt; [lifecycle]phase @ project-artifactId &gt;&gt;&gt;</pre>
     */
    // CHECKSTYLE_ON: LineLength
    @Override
    public void forkStarted(ExecutionEvent event) {
        if (logger.isInfoEnabled()) {
            init();
            logger.info("");

            MessageBuilder buffer = builder().strong(">>> ");
            append(buffer, event.getMojoExecution());
            buffer.strong(" > ");
            appendForkInfo(buffer, event.getMojoExecution().getMojoDescriptor());
            append(buffer, event.getProject());
            buffer.strong(" >>>");

            logger.info(buffer.toString());
        }
    }

    // CHECKSTYLE_OFF: LineLength
    /**
     * <pre>&lt;&lt;&lt; mojo-artifactId:version:goal (mojo-executionId) &lt; :forked-goal @ project-artifactId &lt;&lt;&lt;</pre>
     * <pre>&lt;&lt;&lt; mojo-artifactId:version:goal (mojo-executionId) &lt; [lifecycle]phase @ project-artifactId &lt;&lt;&lt;</pre>
     */
    // CHECKSTYLE_ON: LineLength
    @Override
    public void forkSucceeded(ExecutionEvent event) {
        if (logger.isInfoEnabled()) {
            init();
            logger.info("");

            MessageBuilder buffer = builder().strong("<<< ");
            append(buffer, event.getMojoExecution());
            buffer.strong(" < ");
            appendForkInfo(buffer, event.getMojoExecution().getMojoDescriptor());
            append(buffer, event.getProject());
            buffer.strong(" <<<");

            logger.info(buffer.toString());

            logger.info("");
        }
    }

    private void append(MessageBuilder buffer, MojoExecution me) {
        String prefix = me.getMojoDescriptor().getPluginDescriptor().getGoalPrefix();
        if (prefix == null || prefix.isEmpty()) {
            prefix = me.getGroupId() + ":" + me.getArtifactId();
        }
        buffer.mojo(prefix + ':' + me.getVersion() + ':' + me.getGoal());
        if (me.getExecutionId() != null) {
            buffer.a(' ').strong('(' + me.getExecutionId() + ')');
        }
    }

    private void appendForkInfo(MessageBuilder buffer, MojoDescriptor md) {
        StringBuilder buff = new StringBuilder();
        if (md.getExecutePhase() != null && !md.getExecutePhase().isEmpty()) {
            // forked phase
            if (md.getExecuteLifecycle() != null && !md.getExecuteLifecycle().isEmpty()) {
                buff.append('[');
                buff.append(md.getExecuteLifecycle());
                buff.append(']');
            }
            buff.append(md.getExecutePhase());
        } else {
            // forked goal
            buff.append(':');
            buff.append(md.getExecuteGoal());
        }
        buffer.strong(buff.toString());
    }

    private void append(MessageBuilder buffer, MavenProject project) {
        buffer.a(" @ ").project(project.getArtifactId());
    }

    @Override
    public void forkedProjectStarted(ExecutionEvent event) {
        if (logger.isInfoEnabled()
                && event.getMojoExecution().getForkedExecutions().size() > 1) {
            init();
            logger.info("");
            infoLine('>');

            infoMain("Forking " + event.getProject().getName() + " "
                    + event.getProject().getVersion());

            infoLine('>');
        }
    }
}
