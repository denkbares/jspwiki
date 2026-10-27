/*
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
 */
package org.apache.wiki.api.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tests {@link Engine#findConfigFile(String)}, which locates configuration files such as the
 * security policy ({@code jspwiki.policy}) that a deployed web application ships under its
 * {@code WEB-INF} folder.
 * <p>
 * In a real deployment the policy file lives under {@code WEB-INF} and is <em>not</em> on the
 * classpath, so it can only be found via the web application root path. These tests reproduce that
 * layout (no classpath copy, no servlet context) to guard the root-path resolution branch.
 */
public class EngineFindConfigFileTest {

    /**
     * When the web application root path is known, a config file that exists only under
     * {@code WEB-INF} (as in a deployed war) must be resolved from there.
     */
    @Test
    public void findsConfigFileUnderWebInfWhenRootPathIsSet( @TempDir final Path rootPath ) throws Exception {
        final Path webInf = Files.createDirectories( rootPath.resolve( "WEB-INF" ) );
        final Path policy = Files.writeString( webInf.resolve( "jspwiki.policy" ), "// policy\n" );

        final Engine engine = Mockito.mock( Engine.class );
        Mockito.when( engine.getRootPath() ).thenReturn( rootPath.toString() );
        Mockito.when( engine.getServletContext() ).thenReturn( null );
        Mockito.when( engine.findConfigFile( Mockito.anyString() ) ).thenCallRealMethod();

        final URL url = engine.findConfigFile( "jspwiki.policy" );

        Assertions.assertNotNull( url, "config file under WEB-INF should be found when the root path is set" );
        Assertions.assertEquals( policy.toRealPath(), Paths.get( url.toURI() ).toRealPath() );
    }

    /**
     * An absolute path to an existing file is honoured even when it is not located under
     * {@code WEB-INF} of the web application root path.
     */
    @Test
    public void findsConfigFileByAbsolutePath( @TempDir final Path tempDir ) throws Exception {
        final Path rootPath = Files.createDirectories( tempDir.resolve( "app" ) );
        final Path policy = Files.writeString( tempDir.resolve( "custom.policy" ), "// policy\n" );

        final Engine engine = Mockito.mock( Engine.class );
        Mockito.when( engine.getRootPath() ).thenReturn( rootPath.toString() );
        Mockito.when( engine.getServletContext() ).thenReturn( null );
        Mockito.when( engine.findConfigFile( Mockito.anyString() ) ).thenCallRealMethod();

        final URL url = engine.findConfigFile( policy.toString() );

        Assertions.assertNotNull( url, "an absolute path to an existing file should be found" );
        Assertions.assertEquals( policy.toRealPath(), Paths.get( url.toURI() ).toRealPath() );
    }

}
