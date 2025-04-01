/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiwiki.links;

import org.apache.wiki.providers.SubWikiUtils;

public class MultiWikiLinkTestData {

	public static final String WIKI_PREFIX_B = "WikiB";

	public static final String PAGE_NAME_A1 = "PageA1";
	public static final String PAGE_NAME_A2 = "PageA2";
	static final String PAGE_NAME_B1 = "PageB1";
	public static final String PAGE_NAME_B2 = "PageB2";

	public static final String PAGE_CONTENT_A1 = "[PageA2]\n" +
			"----\n" +
			"[WikiB" + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + "PageB2]\n";
	public static final String PAGE_CONTENT_A2 = "PageContentA2";
	static final String PAGE_CONTENT_B1 = "[PageB2]\n" +
			"----\n" +
			"[WikiB" + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + "PageB2]\n";
	public static final String PAGE_CONTENT_B2 = "PageContentB2";


}
