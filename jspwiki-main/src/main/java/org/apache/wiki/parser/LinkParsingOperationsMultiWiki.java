/*
    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
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
