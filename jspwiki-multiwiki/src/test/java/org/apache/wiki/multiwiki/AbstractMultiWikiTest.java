/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiwiki;

import java.util.Properties;

import org.apache.wiki.TestEngine;
import org.junit.jupiter.api.AfterEach;

public class AbstractMultiWikiTest {

	public static final String WIKI_PREFIX_MAIN = "Main";

	protected static TestEngine testEngine;

	@AfterEach
	public void tearDown() {
		testEngine.stop();
	}

	public static void addStandardMultiWikiProperties(Properties properties) {
		standardPropertiesMultiWiki(properties);
		properties.put("jspwiki.pageProvider", "VersioningFileProviderMultiWiki");
	}

	static void addStandardMultiWikiPropertiesWithoutVersioning(Properties properties) {
		standardPropertiesMultiWiki(properties);
		properties.put("jspwiki.pageProvider", "FileSystemProviderMultiWiki");
	}

	private static void standardPropertiesMultiWiki(Properties properties) {
		properties.put("jspwiki.renderingManager.markupParser", "org.apache.wiki.parser.JSPWikiMarkupParserMultiWiki");
		properties.put("jspwiki.pageRenamer", "DefaultPageRenamerMultiWiki");
		properties.put("jspwiki.attachmentProvider", "BasicAttachmentProviderMultiWiki");
	}



}
