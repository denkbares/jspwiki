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

public class MultiWikiNestedNonExistingLinksBTest extends AbstractMultiWikiTest {

	private static final String WIKI_PREFIX_MAIN = "Main";

	@BeforeAll
	public static void init() {
		testEngine = TestEngine.build(with("jspwiki.mainFolder", WIKI_PREFIX_MAIN));
	}
	static final String PAGE_CONTENT_B1 = "[PageB2]\n" +
			"----\n" +
			"[WikiB" + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + "PageB2]\n"+
			"----\n" +
			"[Main" + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + "PageA1]\n";

	@Test
	public void testCreatePageLinksB() throws Exception {
		String globalePageName = WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B1;
		testEngine.saveText(globalePageName, PAGE_CONTENT_B1);
		final String res = testEngine.getI18nHTML(globalePageName);
		Assertions.assertEquals("<a class=\"createpage\" href=\"/test/Edit.jsp?page=WikiB%26%26PageB2\" title=\"Create &quot;WikiB&amp;&amp;PageB2&quot;\">PageB2</a>\n" +
						"<hr />\n" +
						"<a class=\"createpage\" href=\"/test/Edit.jsp?page=WikiB%26%26PageB2\" title=\"Create &quot;WikiB&amp;&amp;PageB2&quot;\">WikiB&amp;&amp;PageB2</a>\n"+
						"<hr />\n" +
						"<a class=\"createpage\" href=\"/test/Edit.jsp?page=Main%26%26PageA1\" title=\"Create &quot;Main&amp;&amp;PageA1&quot;\">Main&amp;&amp;PageA1</a>",
				res.trim());
	}
}
