/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.providers.PageProvider;
import org.apache.wiki.pages.PageManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains util methods to handle the page name conversion between local page name and global page names with sub-wiki
 * prefix.
 */
public class SubWikiUtils {

	/*
	PREFIX-SEPARATORS START
	if you change this separator, You need to
	a) change all three constants consistently (otherwise at least the tests will break)
	b) You need real thorough checking if still everything works fine in the front-end
	c) check in particular whether MarkupParser#
	 */
	public static final String SUB_FOLDER_PREFIX_SEPARATOR = "&&";
	public static final String SUB_FOLDER_PREFIX_SEPARATOR_URLENCODED = "%26%26";
	public static final String SUB_FOLDER_PREFIX_SEPARATOR_HTML = "&amp;&amp;";
	/*
	PREFIX-SEPARATORS END
	 */

	private static final Logger LOG = LoggerFactory.getLogger(SubWikiUtils.class);
	private static final String MESSAGE_INVALID_PAGE_NAME = "Page name with multiple wiki-subfolder separators found! ";
	public static final String MAIN_FOLDER_NAME = "jspwiki.mainFolder";

	/**
	 * For a global page name (with sub-wiki prefix e.g. MyWiki&&Main)
	 * this method returns the sub-wiki name (which is always equal to the subfolder name).
	 *
	 * @param page global page name
	 * @return sub-wiki folder name
	 */
	public static @NotNull String getSubFolderNameOfPage(@NotNull String page, @NotNull Properties properties) {
		String[] split = page.split(SUB_FOLDER_PREFIX_SEPARATOR);
		if (split.length == 1) {
			// page of main base wiki folder
			return getMainWikiFolder(properties);
		}
		else if (split.length == 2) {
			return split[0];
		}
		else {
			LOG.error(MESSAGE_INVALID_PAGE_NAME + page);
			throw new IllegalStateException(MESSAGE_INVALID_PAGE_NAME + page);
		}
	}

	public static String expandPageNameWithMainPrefix(@NotNull String pageName, @NotNull Properties properties) {
		String result = pageName;
		String subFolderName = getSubFolderNameOfPage(pageName, properties);
		String localName = getLocalPageName(pageName);
		if (!subFolderName.isEmpty()) {
			if (!pageName.equals(concatSubWikiAndLocalPageNameNonMain(subFolderName, localName))) {
				result = concatSubWikiAndLocalPageNameNonMain(subFolderName, pageName);
			}
		}
		return result;
	}

	public static @NotNull String getMainWikiFolder(@NotNull Properties properties) {
		return properties.getProperty(MAIN_FOLDER_NAME, "");
	}

	/**
	 * Returns the local name (inside a sub-wiki scope) of a global page name (with sub-wiki prefix).
	 *
	 * @param globalPageName global page name
	 * @return the local page name (without sub-wiki prefix)
	 */
	public static @NotNull String getLocalPageName(@NotNull String globalPageName) {
		if (isLocalName(globalPageName)) return globalPageName; // is already local name
		String[] split = globalPageName.split(SUB_FOLDER_PREFIX_SEPARATOR);
		if (split.length == 1) {
			// globalPageName of main base wiki folder
			return globalPageName;
		}
		else if (split.length == 2) {
			return split[1];
		}
		else {
			LOG.error(MESSAGE_INVALID_PAGE_NAME + globalPageName);
			throw new IllegalStateException("Invalid page name: " + globalPageName);
		}
	}

	/**
	 * Concatenates a local page name and a sub-wiki name to a global page name.
	 * Warning: This method does not check for the main wiki, whether it is nested
	 * or not. If the call might contain pages of the main wiki plz use:
	 * {@link SubWikiUtils#concatSubWikiAndLocalPageName(String, String, Properties)}
	 *
	 * @param subWiki       sub-wiki folder name
	 * @param localPageName local page name
	 * @return global page name
	 */
	public static @NotNull String concatSubWikiAndLocalPageNameNonMain(@NotNull String subWiki, @NotNull String localPageName) {
		if (isGlobalName(localPageName)) {
			// is already global name
			if (!localPageName.startsWith(subWiki)) {
				LOG.error("Inconsistent sub-wiki prefix" + subWiki + "for page: " + localPageName);
				// TODO: this case is not nice, this input combination should not arrive here in this method
				return localPageName;
			}
			else {
				return localPageName; // is in fact a valid global name already
			}
		}
		return subWiki + SUB_FOLDER_PREFIX_SEPARATOR + localPageName;
	}

	public static @NotNull String concatSubWikiAndLocalPageName(@Nullable String subWiki, @NotNull String localPageName, @NotNull Properties wikiProperties) {
		if (subWiki == null || subWiki.isBlank()) {
			// we have the main wiki here
			return expandPageNameWithMainPrefix(localPageName, wikiProperties);
		}
		return concatSubWikiAndLocalPageNameNonMain(subWiki, localPageName);
	}

	public static @NotNull Collection<String> getAllSubWikiFoldersWithoutMain(@NotNull Engine engine) {
		String mainWikiFolder = SubWikiUtils.getMainWikiFolder(engine.getWikiProperties());
		Collection<String> allSubWikiFoldersInclMain = getAllSubWikiFoldersInclMain(engine);
		allSubWikiFoldersInclMain.remove(mainWikiFolder);
		return allSubWikiFoldersInclMain;
	}

	public static @NotNull Collection<String> getAllSubWikiFoldersInclMain(@NotNull Engine engine) {

		List<PageManager> managers = engine.getManagers(PageManager.class);
		if (!managers.isEmpty()) {
			PageManager pageManager = managers.get(0);
			PageProvider provider = pageManager.getProvider();
			if (provider instanceof CachingProvider cachingProvider) {
				provider = cachingProvider.getRealProvider();
			}
			if (provider instanceof MultiWikiPageProvider multiWikiFileProvider) {
				return multiWikiFileProvider.getAllSubWikiFolders(true);
			}
			else {
				//  we are not in a multi-wiki setup at all -> just return main wiki folder
				return Set.of(SubWikiUtils.getMainWikiFolder(engine.getWikiProperties()));
			}
		}
		else {
			throw new IllegalStateException("No PageManager found!");
		}
	}

	public static boolean isGlobalName(String pageName) {
		return pageName.contains(SUB_FOLDER_PREFIX_SEPARATOR);
	}

	public static boolean isLocalName(String pageName) {
		return !isGlobalName(pageName);
	}

	public static String getPageDirectory(@Nullable String pageName, String m_pageDirectory, Properties wikiProperties) {
		String folder;
		if (pageName != null) {
			folder = SubWikiUtils.getSubFolderNameOfPage(pageName, wikiProperties);
		}
		else {
			folder = SubWikiUtils.getMainWikiFolder(wikiProperties);
		}
		return m_pageDirectory + File.separator + folder;
	}
}
