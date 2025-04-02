/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.io.File;
import java.util.Properties;

import org.apache.wiki.TestEngine;

import static org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest.*;

/**
 * This tests that the {@link VersioningFileProviderMultiWiki} in a non-nested setting can pass every test in the same way that the
 * standard {@link VersioningFileProvider} does.
 */
class VersioningFileProviderMultiWikiNonNestedTest extends VersioningFileProviderTest {


	@Override
	protected TestEngine createEngine() {
		Properties multiWikiNonNestedVersioningProperties = getMultiWikiNonNestedVersioningProperties();
		Properties otherProps = TestEngine.getTestProperties("/jspwiki-vers-custom.properties");
		otherProps.putAll(multiWikiNonNestedVersioningProperties);
		return TestEngine.build(otherProps);
	}

	@Override
	protected String getPageName1() {
		return WIKI_PREFIX_MAIN+SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR+NAME1;
	}

	@Override
	protected String getWikiPageDirectory() {
		Properties wikiProperties = engine.getWikiProperties();
		String wikiFolder = wikiProperties.getProperty(AbstractFileProvider.PROP_PAGEDIR);
		String result = wikiFolder;
		String mainFolder = wikiProperties.getProperty(JSPWIKI_MAIN_FOLDER_PROPERTY);
		if (mainFolder != null && !mainFolder.isBlank()) {
			result = wikiFolder + File.separator + mainFolder;
		}
		return result;
	}
}
