/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiWiki.links;

import java.util.Properties;

import org.apache.wiki.TestEngine;
import org.apache.wiki.providers.SubWikiUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.wiki.TestEngine.getTestProperties;
import static org.apache.wiki.multiWiki.ParameterizedNestedNonNestedMultiWikiTest.getGlobalPageNameHTML;
import static org.apache.wiki.multiWiki.ParameterizedNestedNonNestedMultiWikiTest.getGlobalPageNameUrlencoded;
import static org.apache.wiki.multiWiki.links.MultiWikiLinkTestData.*;

/**
 * Testing the case of internal link from a sub-wiki into itself to a _non-existing_ page:
 * a) link without prefix
 * b) link with prefix (which is probably never used, compliance-functionality only)
 */
public class MultiWikiNonNestedNonExistingLinksBTest extends AbstractMultiWikiTest {

	@BeforeAll
	public static void init() {
		Properties props =  getTestProperties();
		addStandardMultiWikiProperties(props);
		testEngine = TestEngine.build(props);
	}

	@Test
	public void testCreatePageLinksB() throws Exception {
		String globalPageNameB1 = WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B1;
		String globalPageNameB2 = WIKI_PREFIX_B + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + PAGE_NAME_B2;
		testEngine.saveText(globalPageNameB1, PAGE_CONTENT_B1);
		final String res = testEngine.getI18nHTML(globalPageNameB1);
		Assertions.assertEquals("<a class=\"createpage\" href=\"/test/Edit.jsp?page="+getGlobalPageNameUrlencoded(globalPageNameB2)+"\" title=\"Create &quot;"+getGlobalPageNameHTML(globalPageNameB2)+"&quot;\">PageB2</a>\n" +
						"<hr />\n" +
						"<a class=\"createpage\" href=\"/test/Edit.jsp?page="+getGlobalPageNameUrlencoded(globalPageNameB2)+"\" title=\"Create &quot;"+getGlobalPageNameHTML(globalPageNameB2)+"&quot;\">"+getGlobalPageNameHTML(globalPageNameB2)+"</a>",
				res.trim());
	}
}
