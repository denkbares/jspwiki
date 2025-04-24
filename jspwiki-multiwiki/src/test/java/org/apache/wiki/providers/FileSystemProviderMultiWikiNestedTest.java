/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.apache.wiki.TestEngine;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.pages.PageManager;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

import static org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest.*;

public class FileSystemProviderMultiWikiNestedTest extends FileSystemProviderTest {


	@Override
	@BeforeEach
	public void setUp() throws Exception {

		props.setProperty( PageManager.PROP_PAGEPROVIDER, "FileSystemProviderMultiWiki" );
		props.setProperty( FileSystemProvider.PROP_PAGEDIR, "./target/jspwiki_test_pages/" );

		Properties propertiesNonNestedVersioning = getAdditionalProperties();

		props.putAll(propertiesNonNestedVersioning);

		m_engine = TestEngine.build(props);
		m_provider = new FileSystemProviderMultiWiki();
		m_provider.initialize( m_engine, props );

		propsUTF8.putAll(props);
		propsUTF8.setProperty( Engine.PROP_ENCODING, StandardCharsets.UTF_8.name() );
		m_providerUTF8 = new FileSystemProviderMultiWiki();
		m_providerUTF8.initialize( m_engine, propsUTF8 );

	}

	public static Properties getTestProperties() {
		return TestEngine.getTestProperties();
	}

	protected @NotNull Properties getAdditionalProperties() {
		Properties propertiesNonNestedVersioning = getTestProperties();
		propertiesNonNestedVersioning.put(JSPWIKI_MAIN_FOLDER_PROPERTY, WIKI_PREFIX_MAIN);
		propertiesNonNestedVersioning.put("jspwiki.applicationName", "TestEngineNonNestedVersioning");
		addStandardMultiWikiProperties(propertiesNonNestedVersioning);
		return propertiesNonNestedVersioning;
	}

	@Override
	protected  @NotNull File getPageFile(String pageDir, String fileName) {
		Properties wikiProperties = m_engine.getWikiProperties();
		String wikiFolder = wikiProperties.getProperty(AbstractFileProvider.PROP_PAGEDIR);
		String result = wikiFolder;
		String mainFolder = wikiProperties.getProperty(JSPWIKI_MAIN_FOLDER_PROPERTY);
		if (mainFolder != null && !mainFolder.isBlank()) {
			result = wikiFolder + File.separator + mainFolder;
		}
		return new File(result, fileName);
	}

}
