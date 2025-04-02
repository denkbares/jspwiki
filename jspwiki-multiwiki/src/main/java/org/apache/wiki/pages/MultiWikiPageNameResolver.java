/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.pages;

import java.util.Properties;

import org.apache.wiki.providers.SubWikiUtils;
import org.jetbrains.annotations.NotNull;

public class MultiWikiPageNameResolver implements PageNameResolver {

	@Override
	public @NotNull String resolvePageName(@NotNull String pageName, Properties props) {
		return SubWikiUtils.expandPageNameWithMainPrefix(pageName, props);
	}
}
