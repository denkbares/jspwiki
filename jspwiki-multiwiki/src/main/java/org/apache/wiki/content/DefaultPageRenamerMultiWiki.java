/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.content;

import org.apache.wiki.providers.SubWikiUtils;

public class DefaultPageRenamerMultiWiki extends DefaultPageRenamer {

	/**
	 * This method does a correct replacement of a single link, taking into account anchors and attachments.
	 */
	@Override
	protected String replaceSingleLink(final String original, String fromPageGlobalName, String toPageGlobalName ) {
		// this method needs to work for local name links as well as for prefixed global name links
		// to do this, we adapt the from/to name to be also global/local as the original wiki link source
		boolean linkIsLocal = SubWikiUtils.isLocalName(original);
		if(linkIsLocal) {
			// original link source is local
			// make from/to also to be local
			fromPageGlobalName = SubWikiUtils.getLocalPageName(fromPageGlobalName);
			toPageGlobalName = SubWikiUtils.getLocalPageName(toPageGlobalName);
		} else {
			// original link source is global
			// noting to do as the incoming from/to page names are already global
		}

		return doReplaceSingleLink(original, fromPageGlobalName, toPageGlobalName);
	}
}
