/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiWiki.links;

import org.apache.wiki.providers.SubWikiUtils;

public class MultiWikiLinkTestData {

	static final String WIKI_PREFIX_B = "WikiB";

	static final String PAGE_NAME_A1 = "PageA1";
	static final String PAGE_NAME_A2 = "PageA2";
	static final String PAGE_NAME_B1 = "PageB1";
	static final String PAGE_NAME_B2 = "PageB2";

	static final String PAGE_CONTENT_A1 = "[PageA2]\n" +
			"----\n" +
			"[WikiB" + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + "PageB2]\n";
	static final String PAGE_CONTENT_A2 = "PageContentA2";
	static final String PAGE_CONTENT_B1 = "[PageB2]\n" +
			"----\n" +
			"[WikiB" + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + "PageB2]\n";
	static final String PAGE_CONTENT_B2 = "PageContentB2";


}
