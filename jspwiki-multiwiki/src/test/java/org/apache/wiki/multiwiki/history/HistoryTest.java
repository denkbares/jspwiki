/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiwiki.history;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.apache.wiki.TestEngine;
import org.apache.wiki.api.exceptions.WikiException;
import org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.apache.wiki.multiwiki.persistence.MultiWikiPersistenceTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests whether the VersioningFileProvider manages the old versions of the wiki pages for different
 * sub-wikis in distinct OLD-folders in the respective sub-wiki-folder.
 * Tests for nested and non-nested mode of main wiki.
 */
public class HistoryTest extends ParameterizedNestedNonNestedMultiWikiTest {

	@ParameterizedTest
	@MethodSource("provideEnginesOnlyVersioning")
	public void historyTestMain(TestEngine testEngine) throws WikiException, IOException {
		String mainPrefix = getMainPrefix(testEngine);
		String localPageNameMain = LOCAL_PAGE_NAME;
		String globalPageNameMain = getGlobalPageName(mainPrefix, localPageNameMain);
		doTestHistory(testEngine, globalPageNameMain, mainPrefix, localPageNameMain);
	}


	@ParameterizedTest
	@MethodSource("provideEnginesOnlyVersioning")
	public void historyTestB(TestEngine testEngine) throws WikiException, IOException {
		String prefix = "WikiB";
		String globalPageNameMain = getGlobalPageName(prefix, LOCAL_PAGE_NAME);
		doTestHistory(testEngine, globalPageNameMain, prefix, LOCAL_PAGE_NAME);
	}

	private static void doTestHistory(TestEngine testEngine, String globalPageName, String wikiPrefix, String localPageName) throws WikiException, IOException {
		// initially the page file is not present
		File pageFile = findPage(testEngine, globalPageName, wikiPrefix);
		assertFalse(pageFile.exists());

		// initially no OLD folder is present
		File oldFolderOfPage = new File(getOldFolderForPage(testEngine, localPageName, wikiPrefix));
		assertFalse(oldFolderOfPage.exists());


		String content1 = "foo1";
		testEngine.saveText(globalPageName, content1);  // initial page version
		assertTrue(pageFile.exists());
		assertTrue(oldFolderOfPage.exists());


		String content2 = "foo2";
		testEngine.saveText(globalPageName, content2); // modify to a new version
		assertTrue(oldFolderOfPage.exists());
		File pageFileVersion1 = findOLDPageFile(testEngine, localPageName, wikiPrefix, 1);
		assertTrue(pageFileVersion1.exists());
		assertEquals(content1, FileUtils.readFileToString(pageFileVersion1, Charset.defaultCharset()).trim());


		testEngine.saveText(globalPageName, "foo3"); // modify to a third version
		assertTrue(oldFolderOfPage.exists());
		assertTrue(pageFileVersion1.exists());
		assertEquals(content1, FileUtils.readFileToString(pageFileVersion1, Charset.defaultCharset()).trim());
		File pageFileVersion2 = findOLDPageFile(testEngine, localPageName, wikiPrefix, 2);
		assertTrue(pageFileVersion2.exists());
		assertEquals(content2, FileUtils.readFileToString(pageFileVersion2, Charset.defaultCharset()).trim());
	}
}
