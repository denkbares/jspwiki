/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiWiki;

import org.apache.wiki.TestEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.wiki.multiWiki.MultiWikiTestData.*;

/**
 * Testing the case of a link from the main wiki
 * a) into itself to a _non-existing_ page
 * b) into a sub-wiki to a _non-existing_ page
 */
public class MultiWikiNonNestedLinksATest extends AbstractMultiWikiTest {

	@BeforeAll
	public static void init() {
		testEngine = TestEngine.build();
	}
	@Test
	public void testCreatePageLinksA() throws Exception {
		testEngine.saveText(PAGE_NAME_A1, PAGE_CONTENT_A1);
		final String res = testEngine.getI18nHTML(PAGE_NAME_A1);
		Assertions.assertEquals("<a class=\"createpage\" href=\"/test/Edit.jsp?page=PageA2\" title=\"Create &quot;PageA2&quot;\">PageA2</a>\n" +
						"<hr />\n" +
						"<a class=\"createpage\" href=\"/test/Edit.jsp?page=WikiB%26%26PageB2\" title=\"Create &quot;WikiB&amp;&amp;PageB2&quot;\">WikiB&amp;&amp;PageB2</a>",
				res.trim());
	}
}
