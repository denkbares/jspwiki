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
package org.apache.wiki.ui;

import org.apache.wiki.api.core.Context;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.utils.WikiPageUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Properties;
import java.util.Vector;
import java.util.stream.Stream;


@ExtendWith( MockitoExtension.class )
class TemplateManagerTest {

    @Mock
    Context ctx;

    @Mock
    Engine engine;

    @ParameterizedTest
    @MethodSource( "provideArgumentsForAddResourceRequest" )
    void shouldCheckAddResourceRequest( final String type, final String res, final String expected ) {
        if( res.startsWith( "engine://" ) ) {
            final Properties properties = new Properties();
            properties.setProperty( "jspwiki.syntax.plain", "plain/wiki-snips-jspwiki.js" );
            Mockito.doReturn( engine ).when( ctx ).getEngine();
            Mockito.doReturn( properties ).when( engine ).getWikiProperties();
        }

        Mockito.doAnswer( invocationOnMock -> {
            final HashMap< String, Vector< String > > map = invocationOnMock.getArgument( 1, HashMap.class );
            Assertions.assertEquals( expected, map.get( type ).get( 0 ) );
            return null;
        } ).when( ctx ).setVariable( Mockito.eq( TemplateManager.RESOURCE_INCLUDES ), Mockito.any( HashMap.class ) );

        TemplateManager.addResourceRequest( ctx, type, res );
    }

    static Stream< Arguments > provideArgumentsForAddResourceRequest() {
        return Stream.of(
                Arguments.of( TemplateManager.RESOURCE_SCRIPT, "engine://jspwiki.syntax.plain", "<script type='text/javascript' src='plain/wiki-snips-jspwiki.js?version=" + WikiPageUtils.getJarVersion() + "'></script>" ),
                Arguments.of( TemplateManager.RESOURCE_SCRIPT, "scripts/haddock-edit.js", "<script type='text/javascript' src='scripts/haddock-edit.js?version=" + WikiPageUtils.getJarVersion() + "'></script>" ),
                Arguments.of( TemplateManager.RESOURCE_SCRIPT, "scripts/editor.js?version=42", "<script type='text/javascript' src='scripts/editor.js?version=42'></script>" ),
                Arguments.of( TemplateManager.RESOURCE_SCRIPT, "//cdn.example.org/editor.js", "<script type='text/javascript' src='//cdn.example.org/editor.js'></script>" ),
                Arguments.of( TemplateManager.RESOURCE_STYLESHEET, "templates/default/haddock-wysiwyg.css", "<link rel='stylesheet' type='text/css' href='templates/default/haddock-wysiwyg.css?version=" + WikiPageUtils.getJarVersion() + "' />" ),
                Arguments.of( TemplateManager.RESOURCE_STYLESHEET, "engine://jspwiki.whatever.not.exists", "<link rel='stylesheet' type='text/css' href='engine://jspwiki.whatever.not.exists' />" ),
                Arguments.of( TemplateManager.RESOURCE_INLINECSS, "color: white", "<style type='text/css'>\ncolor: white\n</style>\n" ),
                Arguments.of( TemplateManager.RESOURCE_JSFUNCTION, "function whatever() {}", "function whatever() {}" )
        );
    }

}
