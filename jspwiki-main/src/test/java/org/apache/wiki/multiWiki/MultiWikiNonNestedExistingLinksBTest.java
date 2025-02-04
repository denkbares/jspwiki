/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiWiki;

import org.apache.wiki.TestEngine;
import org.apache.wiki.providers.SubWikiUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.wiki.multiWiki.MultiWikiTestData.*;

/**
 * Testing the case of internal link from a sub-wiki into itself to an _existing_ page:
 * a) link without prefix
 * b) link with prefix (which is probably never used, compliance-functionality only)
 */
public class MultiWikiNonNestedExistingLinksBTest extends AbstractMultiWikiTest {

	@BeforeAll
	public static void init() {
		testEngine = TestEngine.build();
	}

	@Test
	public void testExistingPageLinksA() throws Exception {
		testEngine.saveText(WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B1, PAGE_CONTENT_B1);
		testEngine.saveText(WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B2, PAGE_CONTENT_B2);
		final String res = testEngine.getI18nHTML(WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR +PAGE_NAME_B1);
		Assertions.assertEquals("<a class=\"wikipage\" href=\"/test/Wiki.jsp?page=WikiB%26%26PageB2\">PageB2</a>\n" +
						"<hr />\n" +
						"<a class=\"wikipage\" href=\"/test/Wiki.jsp?page=WikiB%26%26PageB2\">WikiB&amp;&amp;PageB2</a>",
				res.trim());

	}
}
