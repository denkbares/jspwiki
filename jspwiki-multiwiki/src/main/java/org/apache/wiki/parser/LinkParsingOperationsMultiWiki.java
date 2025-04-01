/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */
package org.apache.wiki.parser;

import org.apache.wiki.api.core.Context;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.providers.SubWikiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Link parsing operations.
 *
 * @since 2.10.3
 */
public class LinkParsingOperationsMultiWiki extends LinkParsingOperations {

	private static final Logger LOG = LoggerFactory.getLogger(LinkParsingOperationsMultiWiki.class);


	public LinkParsingOperationsMultiWiki(final Context wikiContext) {
		super(wikiContext);
	}

	/**
	 * Returns link name, if it exists; otherwise it returns {@code null}.
	 *
	 * @param linkSourcePage source page
	 * @param globalOrLocalTargetPageName target page
	 * @return link name, if it exists; otherwise it returns {@code null}.
	 */
	@Override
	public String linkIfExists(String globalOrLocalTargetPageName, String linkSourcePage) {
		if (globalOrLocalTargetPageName == null || globalOrLocalTargetPageName.isEmpty()) {
			return null;
		}

		// resolve inner subfolder links
		String subFolderNameOfSourcePage = SubWikiUtils.getSubFolderNameOfPage(linkSourcePage, wikiContext.getEngine().getWikiProperties());
		String subFolderNameOfTargetPage = SubWikiUtils.getSubFolderNameOfPage(globalOrLocalTargetPageName, wikiContext.getEngine().getWikiProperties());
		String globalTargetPageName;
		if ((subFolderNameOfTargetPage == null || subFolderNameOfTargetPage.isEmpty())
				&& subFolderNameOfSourcePage != null && !subFolderNameOfSourcePage.isEmpty()
		) {
			// we need to expand the sub-wiki prefix to obtain a global name
			globalTargetPageName = subFolderNameOfTargetPage + SubWikiUtils.SUB_FOLDER_PREFIX_SEPARATOR + globalOrLocalTargetPageName;
		}
		else {
			// there is already a prefix
			globalTargetPageName = globalOrLocalTargetPageName;
		}
		try {
			return wikiContext.getEngine().getFinalPageName(globalTargetPageName);
		}
		catch (final ProviderException e) {
			LOG.warn("TranslatorReader got a faulty page name [" + globalOrLocalTargetPageName + "]!", e);
			return null;
		}
	}

}
