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
 * Testing the case of internal link from a sub-wiki into itself to a _non-existing_ page:
 * a) link without prefix
 * b) link with prefix (which is probably never used, compliance-functionality only)
 */
public class MultiWikiNonNestedLinksBTest extends AbstractMultiWikiTest {

	@BeforeAll
	public static void init() {
		testEngine = TestEngine.build();
	}

	@Test
	public void testCreatePageLinksB() throws Exception {
		String globalePageName = WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B1;
		testEngine.saveText(globalePageName, PAGE_CONTENT_B1);
		final String res = testEngine.getI18nHTML(globalePageName);
		Assertions.assertEquals("<a class=\"createpage\" href=\"/test/Edit.jsp?page=WikiB%26%26PageB2\" title=\"Create &quot;WikiB&amp;&amp;PageB2&quot;\">PageB2</a>\n" +
						"<hr />\n" +
						"<a class=\"createpage\" href=\"/test/Edit.jsp?page=WikiB%26%26PageB2\" title=\"Create &quot;WikiB&amp;&amp;PageB2&quot;\">WikiB&amp;&amp;PageB2</a>",
				res.trim());
	}
}
