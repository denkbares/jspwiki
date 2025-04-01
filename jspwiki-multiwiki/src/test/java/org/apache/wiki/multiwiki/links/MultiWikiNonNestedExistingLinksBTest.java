/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiwiki.links;

import java.util.Properties;

import org.apache.wiki.TestEngine;
import org.apache.wiki.multiwiki.AbstractMultiWikiTest;
import org.apache.wiki.providers.SubWikiUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.wiki.TestEngine.getTestProperties;
import static org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest.*;
import static org.apache.wiki.multiwiki.links.MultiWikiLinkTestData.*;

/**
 * Testing the case of internal link from a sub-wiki into itself to an _existing_ page:
 * a) link without prefix
 * b) link with prefix (which is probably never used, compliance-functionality only)
 */
public class MultiWikiNonNestedExistingLinksBTest extends AbstractMultiWikiTest {

	@BeforeAll
	public static void init() {
		Properties props =  getTestProperties();
		addStandardMultiWikiProperties(props);
		testEngine = TestEngine.build(props);
	}

	@Test
	public void testExistingPageLinksA() throws Exception {
		String globalPageNameB1 = WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B1;
		String globalPageNameB2 = WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B2;
		testEngine.saveText(globalPageNameB1, PAGE_CONTENT_B1);
		testEngine.saveText(globalPageNameB2, PAGE_CONTENT_B2);
		final String res = testEngine.getI18nHTML(globalPageNameB1);
		Assertions.assertEquals("<a class=\"wikipage\" href=\"/test/Wiki.jsp?page="+ getGlobalPageNameUrlencoded(globalPageNameB2) +"\">PageB2</a>\n" +
						"<hr />\n" +
						"<a class=\"wikipage\" href=\"/test/Wiki.jsp?page="+ getGlobalPageNameUrlencoded(globalPageNameB2) +"\">"+ getGlobalPageNameHTML(globalPageNameB2) +"</a>",
				res.trim());

	}
}
