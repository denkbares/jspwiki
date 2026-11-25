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
package org.apache.wiki.plugin;

import org.apache.wiki.TestEngine;
import org.apache.wiki.api.core.Context;
import org.apache.wiki.api.spi.Wiki;
import org.apache.wiki.pages.PageLock;
import org.apache.wiki.pages.PageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;


public class ListLocksPluginTest {

    static TestEngine testEngine = TestEngine.build();

    /** CVE-2026-125741: page name and locker must be HTML encoded. */
    @Test
    public void testLockOwnerAndPageAreEncoded() throws Exception {
        final PageManager pm = testEngine.getManager( PageManager.class );
        final String pageName = "Evil<img src=x onerror=alert(1)>Page";
        final String locker = "<script>alert(document.cookie)</script>\"";
        final PageLock lock = pm.lockPage( Wiki.contents().page( testEngine, pageName ), locker );
        Assertions.assertNotNull( lock );
        try {
            final Context context = Wiki.context().create( testEngine, Wiki.contents().page( testEngine, "TestPage" ) );
            final String res = new ListLocksPlugin().execute( context, new HashMap<>() );

            Assertions.assertFalse( res.contains( "<script>" ), res );
            Assertions.assertFalse( res.contains( "<img" ), res );
            Assertions.assertTrue( res.contains( "&lt;script&gt;alert(document.cookie)&lt;/script&gt;&quot;" ), res );
            Assertions.assertTrue( res.contains( ">Evil&lt;img src=x onerror=alert(1)&gt;Page</a>" ), res );
            Assertions.assertTrue( res.contains( "<a class=\"wikipage\" href=\"" ), res );
        } finally {
            pm.unlockPage( lock );
        }
    }

    @Test
    public void testNoLocks() throws Exception {
        final Context context = Wiki.context().create( testEngine, Wiki.contents().page( testEngine, "TestPage" ) );
        final String res = new ListLocksPlugin().execute( context, new HashMap<>() );
        Assertions.assertTrue( res.contains( "<td colspan=\"4\" class=\"odd\">" ), res );
    }

}
