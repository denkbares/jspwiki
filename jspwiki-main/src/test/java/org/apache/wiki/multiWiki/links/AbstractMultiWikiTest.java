/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiWiki.links;

import java.util.Properties;

import org.apache.wiki.TestEngine;
import org.junit.jupiter.api.AfterEach;

public class AbstractMultiWikiTest {

	static final String WIKI_PREFIX_MAIN = "Main";

	protected static TestEngine testEngine;

	@AfterEach
	public void tearDown() {
		testEngine.stop();
	}

	public static void addStandardMultiWikiProperties(Properties properties) {
		properties.put("jspwiki.renderingManager.markupParser", "org.apache.wiki.parser.JSPWikiMarkupParserMultiWiki");
		properties.put("jspwiki.pageProvider", "VersioningFileProviderMultiWiki");
		properties.put("jspwiki.attachmentProvider", "BasicAttachmentProviderMultiWiki");
	}

	public static void addStandardMultiWikiPropertiesWithoutVersioning(Properties properties) {
		properties.put("jspwiki.renderingManager.markupParser", "org.apache.wiki.parser.JSPWikiMarkupParserMultiWiki");
		properties.put("jspwiki.pageProvider", "FileSystemProviderMultiWiki");
		properties.put("jspwiki.attachmentProvider", "BasicAttachmentProviderMultiWiki");
	}

}
