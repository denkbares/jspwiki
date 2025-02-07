/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiWiki.links;

import org.apache.wiki.TestEngine;
import org.apache.wiki.providers.SubWikiUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.wiki.TestEngine.with;
import static org.apache.wiki.multiWiki.ParameterizedNestedNonNestedMultiWikiTest.*;
import static org.apache.wiki.multiWiki.links.MultiWikiLinkTestData.*;

/**
 * Testing the case of links from a sub-wiki to a _non-existing_ page:
 * a) into itself as a link without prefix
 * b) into itself as a link with prefix
 * c) into the main wiki as a link with prefix
 */
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
		String globalPageNameB1 = WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B1;
		String globalPageNameB2 = WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B2;
		String globalPageNameA1 = WIKI_PREFIX_MAIN+ SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_A1;
		testEngine.saveText(globalPageNameB1, PAGE_CONTENT_B1);
		final String res = testEngine.getI18nHTML(globalPageNameB1);
		Assertions.assertEquals("<a class=\"createpage\" href=\"/test/Edit.jsp?page="+ getGlobalPageNameUrlencoded(globalPageNameB2) +"\" title=\"Create &quot;"+ getGlobalPageNameHTML(globalPageNameB2)+"&quot;\">PageB2</a>\n" +
						"<hr />\n" +
						"<a class=\"createpage\" href=\"/test/Edit.jsp?page="+ getGlobalPageNameUrlencoded(globalPageNameB2) +"\" title=\"Create &quot;"+ getGlobalPageNameHTML(globalPageNameB2) +"&quot;\">"+ getGlobalPageNameHTML(globalPageNameB2) +"</a>\n"+
						"<hr />\n" +
						"<a class=\"createpage\" href=\"/test/Edit.jsp?page="+ getGlobalPageNameUrlencoded(globalPageNameA1) +"\" title=\"Create &quot;"+getGlobalPageNameHTML(globalPageNameA1) +"&quot;\">"+getGlobalPageNameHTML(globalPageNameA1) +"</a>",
				res.trim());
	}
}
