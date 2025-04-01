/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiwiki.links;

import java.util.Properties;

import org.apache.wiki.TestEngine;
import org.apache.wiki.multiwiki.AbstractMultiWikiTest;
import org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest;
import org.apache.wiki.providers.SubWikiUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.wiki.TestEngine.getTestProperties;
import static org.apache.wiki.multiwiki.links.MultiWikiLinkTestData.*;
import static org.apache.wiki.multiwiki.links.MultiWikiLinkTestData.PAGE_NAME_B1;

/**
 * Testing the case of internal link from a sub-wiki to an _existing_ page:
 * a) into itself as a link without prefix
 * b) into itself as a link with prefix
 * c) into the main wiki as a link with prefix
 */
public class MultiWikiNestedExistingLinksBTest extends AbstractMultiWikiTest {



	@BeforeAll
	public static void init() {
		Properties properties = getTestProperties();
		addStandardMultiWikiProperties(properties);
		properties.put("jspwiki.mainFolder", WIKI_PREFIX_MAIN);
		testEngine = TestEngine.build(properties);
	}

	@Test
	public void testExistingPageLinksA() throws Exception {
		String globalPageNameA1 = WIKI_PREFIX_MAIN + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_A1;
		String globalPageNameB1 = WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B1;
		String globalPageNameB2 = WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B2;
		testEngine.saveText(globalPageNameA1, PAGE_CONTENT_A1);
		testEngine.saveText(globalPageNameB1, MultiWikiNestedNonExistingLinksBTest.PAGE_CONTENT_B1);
		testEngine.saveText(globalPageNameB2, PAGE_CONTENT_B2);
		final String res = testEngine.getI18nHTML(globalPageNameB1);
		Assertions.assertEquals("<a class=\"wikipage\" href=\"/test/Wiki.jsp?page="+ ParameterizedNestedNonNestedMultiWikiTest.getGlobalPageNameUrlencoded(globalPageNameB2)+"\">PageB2</a>\n" +
						"<hr />\n" +
						"<a class=\"wikipage\" href=\"/test/Wiki.jsp?page="+ ParameterizedNestedNonNestedMultiWikiTest.getGlobalPageNameUrlencoded(globalPageNameB2)+"\">"+ParameterizedNestedNonNestedMultiWikiTest.getGlobalPageNameHTML(globalPageNameB2)+"</a>\n"+
						"<hr />\n" +
						"<a class=\"wikipage\" href=\"/test/Wiki.jsp?page="+ ParameterizedNestedNonNestedMultiWikiTest.getGlobalPageNameUrlencoded(globalPageNameA1)+"\">"+ParameterizedNestedNonNestedMultiWikiTest.getGlobalPageNameHTML(globalPageNameA1)+"</a>",
				res.trim());

	}
}
