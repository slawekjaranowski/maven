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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.maven.api.Constants;
import org.apache.maven.api.ProtoSession;
import org.apache.maven.api.annotations.Experimental;
import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Collects problems that were encountered during project building.
 *
 * @param <P> The type of the problem.
 * @since 4.0.0
 */
@Experimental
public interface ProblemCollector<P extends BuilderProblem> {
    /**
     * Returns {@code true} if there is at least one problem collected with severity equal or more severe than
     * {@link org.apache.maven.api.services.BuilderProblem.Severity#WARNING}. This check is logically equivalent
     * to "is there any problem reported?", given warning is the lowest severity.
     */
    default boolean hasWarningProblems() {
        return hasProblemsFor(BuilderProblem.Severity.WARNING);
    }

    /**
     * Returns {@code true} if there is at least one problem collected with severity equal or more severe than
     * {@link org.apache.maven.api.services.BuilderProblem.Severity#ERROR}.
     */
    default boolean hasErrorProblems() {
        return hasProblemsFor(BuilderProblem.Severity.ERROR);
    }

    /**
     * Returns {@code true} if there is at least one problem collected with severity equal or more severe than
     * {@link org.apache.maven.api.services.BuilderProblem.Severity#FATAL}.
     */
    default boolean hasFatalProblems() {
        return hasProblemsFor(BuilderProblem.Severity.FATAL);
    }

    /**
     * Returns {@code true} if there is at least one problem collected with severity equal or more severe than
     * passed in severity.
     */
    default boolean hasProblemsFor(BuilderProblem.Severity severity) {
        requireNonNull(severity, "severity");
        for (BuilderProblem.Severity s : BuilderProblem.Severity.values()) {
            if (s.ordinal() <= severity.ordinal() && problemsReportedFor(s) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns total count of problems reported.
     */
    default int totalProblemsReported() {
        return problemsReportedFor(BuilderProblem.Severity.values());
    }

    /**
     * Returns count of problems reported for given severities.
     *
     * @param severities the severity levels to count problems for
     * @return the total count of problems for the specified severities
     */
    int problemsReportedFor(BuilderProblem.Severity... severities);

    /**
     * Returns {@code true} if reported problem count exceeded allowed count, and issues were lost. When this
     * method returns {@code true}, it means that element count of stream returned by method {@link #problems()}
     * and the counter returned by {@link #totalProblemsReported()} are not equal (latter is bigger than former).
     *
     * @return true if the problem collector has overflowed and some problems were not preserved
     */
    boolean problemsOverflow();

    /**
     * Reports a problem: always maintains the counters, but whether problem is preserved in memory, depends on
     * implementation and its configuration.
     *
     * @param problem the problem to report
     * @return {@code true} if passed problem is preserved by this call.
     */
    boolean reportProblem(P problem);

    /**
     * Returns all reported and preserved problems ordered by severity in decreasing order. Note: counters and
     * element count in this stream does not have to be equal.
     */
    @Nonnull
    default Stream<P> problems() {
        Stream<P> result = Stream.empty();
        for (BuilderProblem.Severity severity : BuilderProblem.Severity.values()) {
            result = Stream.concat(result, problems(severity));
        }
        return result;
    }

    /**
     * Returns all reported and preserved problems for given severity. Note: counters and element count in this
     * stream does not have to be equal.
     *
     * @param severity the severity level to get problems for
     * @return a stream of problems with the specified severity
     */
    @Nonnull
    Stream<P> problems(BuilderProblem.Severity severity);

    /**
     * Creates an "empty" problem collector that doesn't store any problems.
     *
     * @param <P> the type of problem
     * @return an empty problem collector
     */
    @Nonnull
    static <P extends BuilderProblem> ProblemCollector<P> empty() {
        return new ProblemCollector<>() {
            @Override
            public boolean problemsOverflow() {
                return false;
            }

            @Override
            public int problemsReportedFor(BuilderProblem.Severity... severities) {
                return 0;
            }

            @Override
            public boolean reportProblem(P problem) {
                throw new IllegalStateException("empty problem collector");
            }

            @Override
            public Stream<P> problems(BuilderProblem.Severity severity) {
                return Stream.empty();
            }
        };
    }

    /**
     * Creates new instance of problem collector with configuration from the provided session.
     *
     * @param <P> the type of problem
     * @param protoSession the session containing configuration for the problem collector
     * @return a new problem collector instance
     */
    @Nonnull
    static <P extends BuilderProblem> ProblemCollector<P> create(@Nullable ProtoSession protoSession) {
        if (protoSession != null
                && protoSession.getUserProperties().containsKey(Constants.MAVEN_BUILDER_MAX_PROBLEMS)) {
            int limit = Integer.parseInt(protoSession.getUserProperties().get(Constants.MAVEN_BUILDER_MAX_PROBLEMS));
            return create(limit, p -> true);
        } else {
            return create(100);
        }
    }

    /**
     * Creates new instance of problem collector with the specified maximum problem count limit,
     * but only preserves problems that match the given filter.
     *
     * @param <P>           the type of problem
     * @param maxCountLimit the maximum number of problems to preserve
     * @param filter        predicate to decide which problems to record
     * @return a new filtered problem collector instance
     */
    @Nonnull
    static <P extends BuilderProblem> ProblemCollector<P> create(int maxCountLimit, Predicate<? super P> filter) {
        return new Impl<>(maxCountLimit, filter);
    }

    /**
     * Creates new instance of problem collector with the specified maximum problem count limit.
     * Visible for testing only.
     *
     * @param <P> the type of problem
     * @param maxCountLimit the maximum number of problems to preserve
     * @return a new problem collector instance
     */
    @Nonnull
    static <P extends BuilderProblem> ProblemCollector<P> create(int maxCountLimit) {
        return create(maxCountLimit, p -> true);
    }

    /**
     * Default implementation of the ProblemCollector interface.
     *
     * @param <P> the type of problem
     */
    class Impl<P extends BuilderProblem> implements ProblemCollector<P> {

        private final int maxCountLimit;
        private final AtomicInteger totalCount;
        private final ConcurrentMap<BuilderProblem.Severity, LongAdder> counters;
        private final ConcurrentMap<BuilderProblem.Severity, List<P>> problems;
        private final Predicate<? super P> filter;

        private static final List<BuilderProblem.Severity> REVERSED_ORDER = Arrays.stream(
                        BuilderProblem.Severity.values())
                .sorted(Comparator.reverseOrder())
                .toList();

        private Impl(int maxCountLimit, Predicate<? super P> filter) {
            if (maxCountLimit < 0) {
                throw new IllegalArgumentException("maxCountLimit must be non-negative");
            }
            this.maxCountLimit = maxCountLimit;
            this.totalCount = new AtomicInteger();
            this.counters = new ConcurrentHashMap<>();
            this.problems = new ConcurrentHashMap<>();
            this.filter = requireNonNull(filter, "filter");
        }

        @Override
        public int problemsReportedFor(BuilderProblem.Severity... severity) {
            int result = 0;
            for (BuilderProblem.Severity s : severity) {
                result += getCounter(s).intValue();
            }
            return result;
        }

        @Override
        public boolean problemsOverflow() {
            return totalCount.get() > maxCountLimit;
        }

        @Override
        public boolean reportProblem(P problem) {
            requireNonNull(problem, "problem");
            // first apply filter
            if (!filter.test(problem)) {
                // drop without counting towards preserved problems
                return false;
            }
            int currentCount = totalCount.incrementAndGet();
            getCounter(problem.getSeverity()).increment();
            if (currentCount <= maxCountLimit || dropProblemWithLowerSeverity(problem.getSeverity())) {
                getProblems(problem.getSeverity()).add(problem);
                return true;
            }
            return false;
        }

        @Override
        public Stream<P> problems(BuilderProblem.Severity severity) {
            requireNonNull(severity, "severity");
            return getProblems(severity).stream();
        }

        private LongAdder getCounter(BuilderProblem.Severity severity) {
            return counters.computeIfAbsent(severity, k -> new LongAdder());
        }

        private List<P> getProblems(BuilderProblem.Severity severity) {
            return problems.computeIfAbsent(severity, k -> new CopyOnWriteArrayList<>());
        }

        private boolean dropProblemWithLowerSeverity(BuilderProblem.Severity severity) {
            for (BuilderProblem.Severity s : REVERSED_ORDER) {
                if (s.ordinal() > severity.ordinal()) {
                    List<P> problems = getProblems(s);
                    while (!problems.isEmpty()) {
                        try {
                            return problems.remove(0) != null;
                        } catch (IndexOutOfBoundsException e) {
                            // empty, continue
                        }
                    }
                }
            }
            return false;
        }
    }
}
