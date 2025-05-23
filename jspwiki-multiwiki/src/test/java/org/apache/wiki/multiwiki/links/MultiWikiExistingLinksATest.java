/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiwiki.links;

import org.apache.wiki.TestEngine;
import org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest;
import org.apache.wiki.providers.SubWikiUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.apache.wiki.multiwiki.links.MultiWikiLinkTestData.*;

public class MultiWikiExistingLinksATest extends ParameterizedNestedNonNestedMultiWikiTest {

	// the test is not interesting for each of those parameterized engines, however doesn't hurt to run them all
	@ParameterizedTest
	@MethodSource("provideEnginesNoVersioning")
	public void testExistingPageLinksA(TestEngine testEngine) throws Exception {
		String mainPrefix = getMainPrefix(testEngine);
		String globalPageNameA1 = getGlobalPageName(mainPrefix, PAGE_NAME_A1);
		String globalPageNameA2 = getGlobalPageName(mainPrefix, PAGE_NAME_A2);
		String globalPageNameB2 = getGlobalPageName(WIKI_PREFIX_B, PAGE_NAME_B2);
		testEngine.saveText(globalPageNameA1, PAGE_CONTENT_A1);
		testEngine.saveText(globalPageNameA2, PAGE_CONTENT_A2);
		testEngine.saveText(globalPageNameB2, PAGE_CONTENT_B2);
		final String res = testEngine.getI18nHTML(PAGE_NAME_A1);
		Assertions.assertEquals("<a class=\"wikipage\" href=\"/test/Wiki.jsp?page="+ getGlobalPageNameUrlencoded(globalPageNameA2) +"\">PageA2</a>\n" +
						"<hr />\n" +
						"<a class=\"wikipage\" href=\"/test/Wiki.jsp?page="+ getGlobalPageNameUrlencoded(globalPageNameB2) +"\">"+ ParameterizedNestedNonNestedMultiWikiTest.getGlobalPageNameHTML(globalPageNameB2) +"</a>",
				res.trim());

	}


}
