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
package org.apache.maven.lifecycle;

import javax.inject.Named;
import javax.inject.Singleton;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.MojoExecutionListener;
import org.apache.maven.plugin.MojoExecutionException;

@Named
@Singleton
public class DelegatingMojoExecutionListener implements MojoExecutionListener {
    private final List<MojoExecutionListener> listeners = new CopyOnWriteArrayList<>();

    @Override
    public void beforeMojoExecution(MojoExecutionEvent event) throws MojoExecutionException {
        for (MojoExecutionListener listener : listeners) {
            listener.beforeMojoExecution(event);
        }
    }

    @Override
    public void afterMojoExecutionSuccess(MojoExecutionEvent event) throws MojoExecutionException {
        for (MojoExecutionListener listener : listeners) {
            listener.afterMojoExecutionSuccess(event);
        }
    }

    @Override
    public void afterExecutionFailure(MojoExecutionEvent event) {
        for (MojoExecutionListener listener : listeners) {
            listener.afterExecutionFailure(event);
        }
    }

    public void addMojoExecutionListener(MojoExecutionListener listener) {
        this.listeners.add(listener);
    }

    public void removeMojoExecutionListener(MojoExecutionListener listener) {
        this.listeners.remove(listener);
    }
}
