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
package org.apache.maven.cling.invoker.mvnup.goals;

import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.cling.invoker.mvnup.UpgradeContext;

/**
 * The "check" goal implementation.
 */
@Named("check")
@Singleton
public class Check extends AbstractUpgradeGoal {

    @Inject
    public Check(StrategyOrchestrator orchestrator) {
        super(orchestrator);
    }

    @Override
    protected boolean shouldSaveModifications() {
        return false;
    }

    @Override
    public int execute(UpgradeContext context) throws Exception {
        context.info("Maven Upgrade Tool - Check");
        context.println();

        return super.execute(context);
    }
}
