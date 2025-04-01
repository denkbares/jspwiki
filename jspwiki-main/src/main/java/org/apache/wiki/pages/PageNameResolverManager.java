/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.pages;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface PageNameResolverManager {

	@NotNull PageNameResolver getPageNameResolver();

}
