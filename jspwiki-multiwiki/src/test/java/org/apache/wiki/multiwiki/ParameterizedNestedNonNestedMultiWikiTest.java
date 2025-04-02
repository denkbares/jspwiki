/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiwiki;

import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.apache.wiki.TestEngine;
import org.apache.wiki.providers.SubWikiUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static org.apache.wiki.TestEngine.getTestProperties;

public abstract class ParameterizedNestedNonNestedMultiWikiTest {


	public static final String JSPWIKI_MAIN_FOLDER_PROPERTY = "jspwiki.mainFolder";
	public static final String LOCAL_PAGE_NAME = "MyPage";
	public static final String WIKI_PREFIX_MAIN = "Main";
	protected static final String WIKI_PREFIX_WIKI2 = "Wiki2";

	@BeforeAll
	public static void init() {
		doInit();
	}

	@AfterAll
	public static void tearDown() {
		doTearDown();
	}

	public static String getGlobalPageNameHTML(String globalPageName) {
		return  globalPageName.replace(SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR, SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR_HTML);
	}

	public static @NotNull String getGlobalPageNameUrlencoded(String globalPageNameA2) {
		return globalPageNameA2.replace(SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR, SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR_URLENCODED);
	}

	static Collection<TestEngine> provideEnginesAll() {
		return List.of(testEngineNested, testEngineNonNested, testEngineNonNestedVersioning,testEngineNestedVersioning);
	}

	static Collection<TestEngine> provideEnginesNoVersioning() {
		return List.of(testEngineNested, testEngineNonNested);
	}

	static Collection<TestEngine> provideEnginesOnlyVersioning() {
		return List.of(testEngineNonNestedVersioning,testEngineNestedVersioning);
	}

	static TestEngine testEngineNonNestedVersioning;
	static TestEngine testEngineNestedVersioning;
	static TestEngine testEngineNonNested;
	static TestEngine testEngineNested;


	protected static void doInit() {
		Properties propertiesNonNestedVersioning = getMultiWikiNonNestedVersioningProperties();
		testEngineNonNestedVersioning = TestEngine.build(propertiesNonNestedVersioning);


		Properties propertiesNestedVersioning = getTestProperties();
		propertiesNestedVersioning.put("jspwiki.applicationName", "TestEngineNestedVersioning");
		addStandardMultiWikiProperties(propertiesNestedVersioning);
		testEngineNestedVersioning = TestEngine.build(propertiesNestedVersioning);


		Properties propertiesNonNested = getTestProperties();
		propertiesNonNested.put(JSPWIKI_MAIN_FOLDER_PROPERTY, WIKI_PREFIX_MAIN);
		propertiesNonNested.put("jspwiki.applicationName", "TestEngineNonNested");
		addStandardMultiWikiPropertiesWithoutVersioning(propertiesNonNested);
		testEngineNonNested = TestEngine.build(propertiesNonNested);


		Properties propertiesNested = getTestProperties();
		propertiesNested.put("jspwiki.applicationName", "TestEngineNested");
		addStandardMultiWikiPropertiesWithoutVersioning(propertiesNested);
		testEngineNested = TestEngine.build(propertiesNested);

	}

	public static @NotNull Properties getMultiWikiNonNestedVersioningProperties() {
		Properties propertiesNonNestedVersioning = getTestProperties();
		propertiesNonNestedVersioning.put(JSPWIKI_MAIN_FOLDER_PROPERTY, WIKI_PREFIX_MAIN);
		propertiesNonNestedVersioning.put("jspwiki.applicationName", "TestEngineNonNestedVersioning");
		addStandardMultiWikiProperties(propertiesNonNestedVersioning);
		return propertiesNonNestedVersioning;
	}

	public static @NotNull Properties getMultiWikiNestedVersioningProperties() {
		Properties propertiesNonNestedVersioning = getTestProperties();
		propertiesNonNestedVersioning.put("jspwiki.applicationName", "TestEngineNestedVersioning");
		addStandardMultiWikiProperties(propertiesNonNestedVersioning);
		return propertiesNonNestedVersioning;
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
		properties.put("jspwiki.pageNameResolver", "MultiWikiPageNameResolver");
		properties.put("jspwiki.attachmentProvider", "BasicAttachmentProviderMultiWiki");
	}

	protected static void doTearDown() {
		testEngineNonNestedVersioning.shutdown();
		testEngineNestedVersioning.shutdown();
		testEngineNonNested.shutdown();
		testEngineNested.shutdown();
	}

	public static String getMainPrefix(TestEngine testEngine) {
		return testEngine.getWikiProperties().getProperty(JSPWIKI_MAIN_FOLDER_PROPERTY, "");
	}

	public static @NotNull String getGlobalPageName(String prefix, String localPageName) {
		String pagePrefix = "";
		if(!prefix.isBlank()) {
			pagePrefix = prefix + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR;
		}
		return pagePrefix + localPageName;
	}

}
