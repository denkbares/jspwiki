/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.pages;

import java.util.Properties;

import org.jetbrains.annotations.NotNull;

/**
 * Resolves to identity page name.
 */
public class DefaultPageNameResolver implements PageNameResolver {

	@Override
	public @NotNull String resolvePageName(@NotNull String pageName, Properties props) {
		// identity implementation
		return pageName;
	}
}
