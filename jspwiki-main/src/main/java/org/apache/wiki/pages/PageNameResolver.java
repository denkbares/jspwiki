/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.pages;

import java.util.Properties;

import org.jetbrains.annotations.NotNull;

public interface PageNameResolver {

	/**
	 * Resolves the page name, if necessary.
	 *
	 * @param pageName name
	 * @return resolved page name
	 */
	@NotNull String resolvePageName(@NotNull String pageName, Properties props);
}
