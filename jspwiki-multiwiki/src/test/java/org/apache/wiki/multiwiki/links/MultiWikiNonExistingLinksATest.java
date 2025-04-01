/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiwiki.links;

import org.apache.wiki.TestEngine;
import org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.apache.wiki.multiwiki.links.MultiWikiLinkTestData.*;

/**
 * Testing the case of internal link from main wiki (Wiki A) to an _non existing_ page:
 * a) in same Wiki A as a link without prefix
 * b) in some other sub-wiki B via link with prefix
 */
public class MultiWikiNonExistingLinksATest extends ParameterizedNestedNonNestedMultiWikiTest {

	@ParameterizedTest
	@MethodSource("provideEnginesNoVersioning")
	public void testCreatePageLinksA(TestEngine testEngine) throws Exception {
		String globalPageNameMainA1 = getGlobalPageName(WIKI_PREFIX_MAIN, PAGE_NAME_A1);
		String globalPageNameMainA2 = getGlobalPageName(WIKI_PREFIX_MAIN, PAGE_NAME_A2);
		String globalPageNameMainB2 = getGlobalPageName(WIKI_PREFIX_B, PAGE_NAME_B2);
		testEngine.saveText(globalPageNameMainA1, PAGE_CONTENT_A1);
		final String res = testEngine.getI18nHTML(globalPageNameMainA1);
		Assertions.assertEquals("<a class=\"createpage\" href=\"/test/Edit.jsp?page="+ getGlobalPageNameUrlencoded(globalPageNameMainA2) +"\" title=\"Create &quot;"+ getGlobalPageNameHTML(globalPageNameMainA2) +"&quot;\">PageA2</a>\n" +
						"<hr />\n" +
						"<a class=\"createpage\" href=\"/test/Edit.jsp?page="+getGlobalPageNameUrlencoded(globalPageNameMainB2) +"\" title=\"Create &quot;"+ getGlobalPageNameHTML(globalPageNameMainB2) +"&quot;\">"+ getGlobalPageNameHTML(globalPageNameMainB2) +"</a>",
				res.trim());
	}


}
