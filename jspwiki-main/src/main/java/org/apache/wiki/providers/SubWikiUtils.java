/*
 * Copyright (C) 2024 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.providers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains util methods to handle the page name conversion between local page name and global page names with sub-wiki
 * prefix.
 */
public class SubWikiUtils {

	public static final String subFolderPrefixSeparator = "&&";
	private static final Logger LOG = LoggerFactory.getLogger(SubWikiUtils.class);
	private static final String MESSAGE_INVALID_PAGE_NAME = "Page name with multiple wiki-subfolder separators found! ";

	/**
	 * For a global page name (with sub-wiki prefix e.g. MyWiki&&Main)
	 * this method returns the sub-wiki name (which is always equal to the subfolder name).
	 *
	 * @param page global page name
	 * @return sub-wiki folder name
	 */
	public static String getSubFolderNameOfPage(@NotNull String page) {
		String[] split = page.split(subFolderPrefixSeparator);
		if (split.length == 1) {
			// page of main base wiki folder
			return "";
		}
		else if (split.length == 2) {
			return split[0];
		}
		else {
			LOG.error(MESSAGE_INVALID_PAGE_NAME + page);
			return null;
		}
	}

	/**
	 * Returns the local name (inside a sub-wiki scope) of a global page name (with sub-wiki prefix).
	 *
	 * @param globalPageName global page name
	 * @return the local page name (without sub-wiki prefix)
	 */
	static String getLocalPageName(@NotNull String globalPageName) {
		String[] split = globalPageName.split(subFolderPrefixSeparator);

		if (split.length == 1) {
			// globalPageName of main base wiki folder
			return globalPageName;
		}
		else if (split.length == 2) {
			return split[1];
		}
		else {
			LOG.error(MESSAGE_INVALID_PAGE_NAME + globalPageName);
			return null;
		}
	}

	/**
	 * Concatenates a local page name and a sub-wiki name to a global page name.
	 *
	 * @param subwiki       sub-wiki folder name
	 * @param localPageName local page name
	 * @return global page name
	 */
	public static @NotNull String concatSubWikiAndLocalPageName(@NotNull String subwiki, @NotNull String localPageName) {
		return subwiki + subFolderPrefixSeparator + localPageName;
	}

	/**
	 * Scans the given folder for wiki sub-folders. Returns the set of detected subfoldres.
	 *
	 * @param m_pageDirectory folder path to be scanned
	 * @return list of folders
	 */
	static List<String> initSubFolders(String m_pageDirectory) {
		List<String> result = new ArrayList<>();
		IOFileFilter trueFilter = new IOFileFilter() {
			@Override
			public boolean accept(File file) {
				return true;
			}

			@Override
			public boolean accept(File dir, String name) {
				return true;
			}
		};
		Collection<File> folders = FileUtils.listFilesAndDirs(new File(m_pageDirectory), trueFilter, trueFilter);

		Collection<File> filteredFolders = folders.stream().filter(file ->
				file.isDirectory()
						&& !file.getAbsolutePath().equals(m_pageDirectory)
						&& !file.getName().equals("OLD")
						&& !file.getParentFile().getName().equals("OLD")
						&& !file.getName().endsWith("-att")
						&& !file.getParentFile().getName().endsWith("-att")
		).toList();
		filteredFolders.forEach(folder -> result.add(folder.getName()));
		return result;
	}
}
