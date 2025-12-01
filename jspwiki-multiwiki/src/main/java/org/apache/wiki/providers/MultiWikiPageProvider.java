package org.apache.wiki.providers;

import java.util.Collection;

public interface MultiWikiPageProvider {

	/**
	 * Returns all sub-wiki folders that are initialized.
	 *
	 * @return all folders
	 */
	Collection<String> getAllSubWikiFolders();
}
