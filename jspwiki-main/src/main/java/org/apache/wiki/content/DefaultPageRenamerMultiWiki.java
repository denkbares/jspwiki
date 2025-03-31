/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.content;

import org.apache.wiki.parser.MarkupParser;
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

		final int hash = original.indexOf('#');
		final int slash = original.indexOf('/');
		String realLink = original;

		if (hash != -1) {
			realLink = original.substring(0, hash);
		}
		if (slash != -1) {
			realLink = original.substring(0, slash);
		}

		realLink = MarkupParser.cleanLink(realLink);
		final String oldStyleRealLink = MarkupParser.wikifyLink(realLink);
		//
		//  Yes, these point to the same page.
		//
		if (realLink.equals(fromPageGlobalName) || original.equals(fromPageGlobalName) || oldStyleRealLink.equals(fromPageGlobalName)) {
			//
			//  if the original contains blanks, then we should introduce a link, for example:  [My Page]  =>  [My Page|My Renamed Page]
			final int blank = realLink.indexOf(" ");

			if (blank != -1) {
				return original + "|" + toPageGlobalName;
			}

			return toPageGlobalName + ((hash > 0) ? original.substring(hash) : "") + ((slash > 0) ? original.substring(slash) : "");
		}

		return original;
	}
}
