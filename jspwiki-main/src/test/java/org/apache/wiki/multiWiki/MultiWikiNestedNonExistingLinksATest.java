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
import static org.apache.wiki.multiWiki.MultiWikiTestData.PAGE_CONTENT_A1;
import static org.apache.wiki.multiWiki.MultiWikiTestData.PAGE_NAME_A1;

public class MultiWikiNestedNonExistingLinksATest extends AbstractMultiWikiTest {


	private static final String WIKI_PREFIX_MAIN = "Main";

	@BeforeAll
	public static void init() {
		testEngine = TestEngine.build(with("jspwiki.mainFolder", WIKI_PREFIX_MAIN));
	}

	@Test
	public void testCreatePageLinksA() throws Exception {
		testEngine.saveText(WIKI_PREFIX_MAIN + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_A1, PAGE_CONTENT_A1);
		final String res = testEngine.getI18nHTML(PAGE_NAME_A1);
		Assertions.assertEquals("<a class=\"createpage\" href=\"/test/Edit.jsp?page=Main%26%26PageA2\" title=\"Create &quot;Main&amp;&amp;PageA2&quot;\">PageA2</a>\n" +
						"<hr />\n" +
						"<a class=\"createpage\" href=\"/test/Edit.jsp?page=WikiB%26%26PageB2\" title=\"Create &quot;WikiB&amp;&amp;PageB2&quot;\">WikiB&amp;&amp;PageB2</a>",
				res.trim());
	}
}
