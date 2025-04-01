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
package org.apache.wiki.content;

import java.util.Properties;

import org.apache.wiki.TestEngine;
import org.apache.wiki.api.core.Context;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.core.Page;
import org.apache.wiki.api.spi.Wiki;
import org.apache.wiki.pages.PageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.wiki.TestEngine.getTestProperties;
import static org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest.getGlobalPageName;
import static org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest.getMainPrefix;
import static org.apache.wiki.multiwiki.AbstractMultiWikiTest.addStandardMultiWikiProperties;
import static org.apache.wiki.multiwiki.links.MultiWikiLinkTestData.*;

/**
 * This test class extends {@link PageRenamerTest} to ensure that all those test
 * also run if a MultiWiki engine is initialised. On top of that
 * special test cases for the MultiWiki setting can be defined in this class.
 */
public class PageRenamerMultiWikiTest extends PageRenamerTest {

    @Override
	@BeforeEach
	void init() {
        Properties properties = getTestProperties();
        addStandardMultiWikiProperties(properties);
        properties.put(Engine.PROP_MATCHPLURALS, "true");
        m_engine = TestEngine.build(properties);
    }

    @Test
    public void testSimpleMultiWikiRenaming() throws Exception {
        String mainPrefix = getMainPrefix(m_engine);
        String globalPageNameA1 = getGlobalPageName(mainPrefix, PAGE_NAME_A1);
        String globalPageNameA2 = getGlobalPageName(mainPrefix, PAGE_NAME_A2);
        String globalPageNameB2 = getGlobalPageName(WIKI_PREFIX_B, PAGE_NAME_B2);
        m_engine.saveText(globalPageNameA1, PAGE_CONTENT_A1);
        m_engine.saveText(globalPageNameA2, PAGE_CONTENT_A2);
        m_engine.saveText(globalPageNameB2, PAGE_CONTENT_B2);

        final Page p = m_engine.getManager( PageManager.class ).getPage(PAGE_NAME_A1);

        final Context context = Wiki.context().create(m_engine, p);
        m_engine.getManager( PageRenamer.class ).renamePage(context, globalPageNameA2, globalPageNameA2+"foo", true);
        m_engine.getManager( PageRenamer.class ).renamePage(context, globalPageNameB2, globalPageNameB2+"foo", true);

        PageManager pageManager = m_engine.getManager(PageManager.class);
        Page page = pageManager.getPage(PAGE_NAME_A1);
        String pureText = pageManager.getPureText(page);


        Assertions.assertEquals("[PageA2foo]\n" +
                "----\n" +
				"[WikiB&&PageB2foo]\n", pureText.replace("\r\n", "\n"));
    }


}
