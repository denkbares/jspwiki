/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiwiki.persistence;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.wiki.TestEngine;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.api.exceptions.WikiException;
import org.apache.wiki.multiwiki.ParameterizedNestedNonNestedMultiWikiTest;
import org.apache.wiki.providers.SubWikiUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.apache.wiki.providers.AbstractFileProvider.FILE_EXT;
import static org.junit.jupiter.api.Assertions.*;

public  class MultiWikiPersistenceTest extends ParameterizedNestedNonNestedMultiWikiTest {


	@ParameterizedTest
	@MethodSource("provideEnginesAll")
	public void saveNewPageMain(TestEngine testEngine) throws WikiException, IOException {
		String mainPrefix = getMainPrefix(testEngine);
		String globalPageName = getGlobalPageName(mainPrefix, LOCAL_PAGE_NAME);
		doTestCreateNewPage(testEngine, globalPageName, mainPrefix);
		doTestAttachment(testEngine, globalPageName, mainPrefix);
	}

	@ParameterizedTest
	@MethodSource("provideEnginesAll")
	public void saveNewPageWiki2(TestEngine testEngine) throws WikiException, IOException {
		String pageName = WIKI_PREFIX_WIKI2 + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + "MyPage";
		doTestCreateNewPage(testEngine, pageName, WIKI_PREFIX_WIKI2);
		doTestAttachment(testEngine, pageName, WIKI_PREFIX_WIKI2);
		testEngine.stop();
	}

	private static void doTestAttachment(TestEngine testEngine, String pageName, String subwiki) throws ProviderException, IOException {
		String attachmentName = "Attachment1.foo";
		String attachedContent = "attachedContent";
		testEngine.addAttachment(pageName, attachmentName, attachedContent.getBytes(StandardCharsets.UTF_8));
		File attachment = findAttachment(testEngine, pageName, subwiki, attachmentName);
		assertTrue(attachment.exists());
	}

	private void doTestCreateNewPage(TestEngine testEngine, String pageName, String wikiPrefix) throws WikiException, IOException {
		String content = "MyContent";
		File pageFile = findPage(testEngine, pageName, wikiPrefix);
		assertFalse(pageFile.exists());
		testEngine.saveText(pageName, content);

		assertTrue(pageFile.exists());
		String fileContent = FileUtils.readFileToString(pageFile, Charset.defaultCharset());
		assertEquals(content, fileContent.trim());
	}


	/**
	 * Finds a Wiki page from the page repository.
	 *
	 * @param page The name of the page.
	 * @return A File to the page.  May be null.
	 */
	public static File findPage(TestEngine engine, String page, String subwikiFolder) {
		String localPageName = SubWikiUtils.getLocalPageName(page);
		String folder = getFolder(engine, subwikiFolder);
		String mangledName = TestEngine.mangleName(localPageName);
		return new File(folder, mangledName + FILE_EXT);
	}

	public static File findOLDPageFile(TestEngine engine, String page, String subwikiFolder, int version) {
		String oldFolderPage = getOldFolderForPage(engine, page, subwikiFolder);
		return new File(oldFolderPage, version + FILE_EXT);
	}

	public static @NotNull String getOldFolderForPage(TestEngine engine, String page, String subwikiFolder) {
		String localPageName = SubWikiUtils.getLocalPageName(page);
		String folder = getFolder(engine, subwikiFolder);
		String oldFolder = folder+File.separator+"OLD";
		String oldFolderPage = oldFolder+File.separator+TestEngine.mangleName(localPageName);
		return oldFolderPage;
	}

	private static @NotNull String getFolder(TestEngine engine, String subwikiFolder) {
		Properties wikiProperties = engine.getWikiProperties();
		String m_pageDirectory = wikiProperties.getProperty("jspwiki.fileSystemProvider.pageDir");
		return m_pageDirectory + File.separator + subwikiFolder;
	}

	protected static File findAttachment(TestEngine engine, String page, String subwikiFolder, String attachmentFileName) {
		String folder = getFolder(engine, subwikiFolder);
		String attachmentFolderName = folder + File.separator + SubWikiUtils.getLocalPageName(page) + "-att" + File.separator;
		File attachmentFolder = new File(attachmentFolderName);
		File attachmentFileFolder = new File(attachmentFolder, attachmentFileName + "-dir");
		String fileEnding = attachmentFileName.substring(attachmentFileName.lastIndexOf("."));
		File attachmentFile = new File(attachmentFileFolder, "1" + fileEnding);
		return attachmentFile;
	}
}
