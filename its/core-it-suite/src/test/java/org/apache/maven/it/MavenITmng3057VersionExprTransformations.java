/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.it.util.IOUtil;
import org.apache.maven.it.util.ResourceExtractor;
import org.apache.maven.it.util.StringUtils;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * This is a test set for <a href="http://jira.codehaus.org/browse/MNG-3057">MNG-3057</a>.
 *
 * @todo Fill in a better description of what this test verifies!
 * 
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @author jdcasey
 * 
 */
public class MavenITmng3057VersionExprTransformations
    extends AbstractMavenIntegrationTestCase
{

    public MavenITmng3057VersionExprTransformations()
    {
        // TODO: port to 3.x
        super( "[2.1.0,)" ); // only test in 2.1.0+
    }

    public void testitMNG3057 ()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/mng-3057" );
        
        File remoteRepo = new File( testDir, "target/deployment" );

        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.deleteArtifacts( "org.apache.maven.its.mng3057" );
        
        Properties properties = verifier.newDefaultFilterProperties();
        properties.setProperty( "@deployTo@", remoteRepo.toURI().toURL().toExternalForm() );

        verifier.filterFile( "pom.xml", "pom-filtered.xml", "UTF-8", properties );

        List cliOptions = new ArrayList();
        cliOptions.add( "-V" );
        cliOptions.add( "-DtestVersion=1" );
        cliOptions.add( "-f pom-filtered.xml" );

        verifier.setCliOptions( cliOptions );
        
//        Map envars = new HashMap();
//        envars.put( "MAVEN_OPTS", "-Xdebug -Xnoagent -Xrunjdwp:transport=dt_socket,server=y,address=5005 -Djava.compiler=NONE" );
//        verifier.executeGoal( "deploy", envars );
        
        verifier.executeGoal( "deploy" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();

        assertVersions( new File( verifier.getArtifactPath( "org.apache.maven.its.mng3057", "mng-3057", "1", "pom" ) ), "1", null );
        assertVersions( new File( verifier.getArtifactPath( "org.apache.maven.its.mng3057", "level2", "1", "pom" ) ), "1", "1" );
        assertVersions( new File( verifier.getArtifactPath( "org.apache.maven.its.mng3057", "level3", "1", "pom" ) ), "1", "1" );
        
        assertVersions( new File( remoteRepo, "org/apache/maven/its/mng3057/mng-3057/1/mng-3057-1.pom" ), "1", null );
        assertVersions( new File( remoteRepo, "org/apache/maven/its/mng3057/level2/1/level2-1.pom" ), "1", "1" );
        assertVersions( new File( remoteRepo, "org/apache/maven/its/mng3057/level2/1/level2-1.pom" ), "1", "1" );
    }

    private void assertVersions( File file, String version, String parentVersion )
        throws XmlPullParserException, IOException
    {
        Xpp3Dom dom = Xpp3DomBuilder.build( ReaderFactory.newXmlReader( file ) );        
        assertEquals( version, dom.getChild( "version" ).getValue() );
        Xpp3Dom parent = dom.getChild( "parent" );
        if ( parentVersion != null )
        {
            assertNotNull( parent );
            assertEquals( parentVersion, parent.getChild( "version" ).getValue() );
        }
        else
        {
            assertNull( parent );
        }
    }    
}
