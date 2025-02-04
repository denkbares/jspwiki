/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiWiki;

import org.apache.wiki.TestEngine;
import org.apache.wiki.providers.SubWikiUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.wiki.TestEngine.with;
import static org.apache.wiki.multiWiki.MultiWikiTestData.*;

public class MultiWikiNestedExistingLinksATest extends AbstractMultiWikiTest{


	private static final String WIKI_PREFIX_MAIN = "Main";

	@BeforeAll
	public static void init() {
		testEngine = TestEngine.build(with("jspwiki.mainFolder", WIKI_PREFIX_MAIN));
	}

	@Test
	public void testExistingPageLinksA() throws Exception {
		testEngine.saveText(WIKI_PREFIX_MAIN + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_A1, PAGE_CONTENT_A1);
		testEngine.saveText(WIKI_PREFIX_MAIN + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_A2, PAGE_CONTENT_A2);
		testEngine.saveText(WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B2, PAGE_CONTENT_B2);
		final String res = testEngine.getI18nHTML(PAGE_NAME_A1);
		Assertions.assertEquals("<a class=\"wikipage\" href=\"/test/Wiki.jsp?page=Main%26%26PageA2\">PageA2</a>\n" +
						"<hr />\n" +
						"<a class=\"wikipage\" href=\"/test/Wiki.jsp?page=WikiB%26%26PageB2\">WikiB&amp;&amp;PageB2</a>",
				res.trim());

	}
}
