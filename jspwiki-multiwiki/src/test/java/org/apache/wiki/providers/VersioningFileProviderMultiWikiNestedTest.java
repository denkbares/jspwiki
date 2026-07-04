/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.util.Properties;

import org.apache.wiki.TestEngine;

import static org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest.*;

public class VersioningFileProviderMultiWikiNestedTest extends VersioningFileProviderMultiWikiNonNestedTest {

	@Override
	protected TestEngine createEngine() {
		Properties multiWikiNonNestedVersioningProperties = getMultiWikiNestedVersioningProperties();
		Properties otherProps = TestEngine.getTestProperties("/jspwiki-vers-custom.properties");
		otherProps.putAll(multiWikiNonNestedVersioningProperties);
		return TestEngine.build(otherProps);
	}

	@Override
	protected String getPageName1() {
		return NAME1;
	}


}
