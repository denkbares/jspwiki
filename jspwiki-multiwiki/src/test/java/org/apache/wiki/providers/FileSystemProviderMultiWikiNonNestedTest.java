/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.util.Properties;

import org.jetbrains.annotations.NotNull;

import static org.apache.wiki.TestEngine.getTestProperties;
import static org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest.*;

public class FileSystemProviderMultiWikiNonNestedTest extends FileSystemProviderMultiWikiNestedTest {

	@Override
	protected  @NotNull Properties getAdditionalProperties() {
		Properties propertiesNonNestedVersioning = getTestProperties();
		propertiesNonNestedVersioning.put("jspwiki.applicationName", "TestEngineNonNestedVersioning");
		addStandardMultiWikiProperties(propertiesNonNestedVersioning);
		return propertiesNonNestedVersioning;
	}
}
