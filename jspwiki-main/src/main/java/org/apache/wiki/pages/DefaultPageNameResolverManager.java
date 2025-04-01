/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.pages;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.engine.Initializable;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.api.exceptions.WikiException;
import org.apache.wiki.util.ClassUtil;
import org.apache.wiki.util.TextUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPageNameResolverManager implements PageNameResolverManager, Initializable {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultPageNameResolverManager.class);

	String PROP_PAGENAME_RESOLVER = "jspwiki.pageNameResolver";

	PageNameResolver resolver;

	public DefaultPageNameResolverManager() {
		// standard constructor required for reflection instantiation
	}

	@Override
	public @NotNull PageNameResolver getPageNameResolver() {
		return resolver;
	}

	@Override
	public void initialize(Engine engine, Properties props) throws WikiException {
		String classname = DefaultPageNameResolver.class.getSimpleName();
		try {
			classname = TextUtil.getRequiredProperty(props, PROP_PAGENAME_RESOLVER);
		} catch (NoSuchElementException e) {
			// property not set for setup -> use fall-back
		}

		try {
			LOG.debug("Page provider class: '{}'", classname);
			resolver = ClassUtil.buildInstance("org.apache.wiki.pages", classname);
			LOG.debug("Initializing page provider class {}", resolver);
		}
		catch (final ReflectiveOperationException e) {
			LOG.error("Unable to instantiate provider class '{}' ({})", classname, e.getMessage(), e);
			throw new WikiException("Illegal provider class. (" + e.getMessage() + ")", e);
		}
	}
}
