/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import org.apache.wiki.SubWikiInit;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.core.Page;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.api.providers.PageProvider;
import org.apache.wiki.api.providers.WikiProvider;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AbstractFileProvider supporting MultiWiki mechanism.
 */
public abstract class AbstractMultiWikiFileProvider extends AbstractFileProvider implements MultiWikiPageProvider {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractMultiWikiFileProvider.class);

	// sub-wiki-folders; for jspwiki the folder is a prefix/namespace in the page name, e.g. /Subwiki/Main.txt is page Subwiki::Main

	private Collection<String> subfolders;

	@Override
	public void initialize(final Engine engine, final Properties properties) throws NoRequiredPropertyException, IOException, FileNotFoundException {
		super.initialize(engine, properties);
		this.subfolders = SubWikiInit.getAllSubWikiFoldersWithoutMain(properties);
	}

	@Override
	String getPageDirectory() {
		return getPageDirectory(null);
	}

	@Override
	public Collection<String> getAllSubWikiFolders() {
		return subfolders;
	}

	@Override
	String getPageDirectory(@Nullable String pageName) {
		return SubWikiUtils.getPageDirectory(pageName, m_pageDirectory, this.m_engine.getWikiProperties());
	}

	/**
	 * Finds a Wiki page from the page repository.
	 *
	 * @param page The name of the page.
	 * @return A File to the page.  May be null.
	 */
	@Override
	protected File findPage(String page) {
		String localPageName = SubWikiUtils.getLocalPageName(page);
		String subfolderPathExtension = "";
		String subwikiFolder = SubWikiUtils.getSubFolderNameOfPage(page, m_engine.getWikiProperties());
		if (subwikiFolder != null && !subwikiFolder.isEmpty()) {
			subfolderPathExtension = File.separator + subwikiFolder;
		}
		String mangledName = mangleName(localPageName);
		String folder = m_pageDirectory + subfolderPathExtension;
		File folderFile = new File(folder);
		if (!folderFile.exists()) {
			// might be that the first page of a new sub-wiki is created -> then create new folder
			folderFile.mkdirs();
		}
		return new File(folder, mangledName + AbstractFileProvider.FILE_EXT);
	}

	@Override
	public Collection<Page> getAllPages() throws ProviderException {
		LOG.debug("Getting all pages...");

		final Collection<Page> basePages = collectAllWikiPagesInFolder(getPageDirectory());
		final Collection<Page> allPages = new HashSet<>(basePages);
		for (String subfolder : subfolders) {
			List<Page> folderPages = collectAllWikiPagesInFolder(m_pageDirectory + File.separator + subfolder);
			allPages.addAll(folderPages);
		}

		final Collection<Page> returnedPages = new ArrayList<>();
		for (final Page page : allPages) {
			final Page info = getPageInfo(page.getName(), WikiProvider.LATEST_VERSION);
			returnedPages.add(info);
		}

		return returnedPages;
	}

	private List<Page> collectAllWikiPagesInFolder(String folder) throws ProviderException {
		final ArrayList<Page> set = new ArrayList<>();
		final File wikipagedir = new File(folder);
		if (!wikipagedir.exists()) {
			// might be start of an empty (nested-)wiki (e.g. testing)
			wikipagedir.mkdirs();
		}
		final File[] wikipages = wikipagedir.listFiles(new WikiFileFilter());
		if (wikipages == null) {
			LOG.error("Wikipages directory '" + folder + "' does not exist! Please check " + AbstractFileProvider.PROP_PAGEDIR + " in jspwiki.properties.");
			throw new ProviderException("Page directory does not exist");
		}
		String pageDirectory = m_pageDirectory;
		if (!m_pageDirectory.endsWith(File.separator)) {
			pageDirectory += File.separator;
		}
		String subFolder = folder.substring(pageDirectory.length());
		String prefix = subFolder.isEmpty() ? "" : subFolder + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR;

		for (final File wikiFile : wikipages) {
			final String wikiFileName = wikiFile.getName();
			final int cutpoint = wikiFileName.lastIndexOf(AbstractFileProvider.FILE_EXT);
			String pageName = prefix + unmangleName(wikiFileName.substring(0, cutpoint));
			final Page page = getPageInfo(pageName, PageProvider.LATEST_VERSION);
			if (page == null) {
				// This should not really happen.
				// FIXME: Should we throw an exception here?
				LOG.error("Page " + wikiFileName + " was found in directory listing, but could not be located individually.");
				continue;
			}

			set.add(page);
		}
		return set;
	}
}

