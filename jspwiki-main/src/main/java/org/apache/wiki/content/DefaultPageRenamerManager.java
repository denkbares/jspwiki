/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.content;

import java.util.NoSuchElementException;
import java.util.Properties;

import org.apache.wiki.api.core.Context;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.engine.Initializable;
import org.apache.wiki.api.exceptions.WikiException;
import org.apache.wiki.event.WikiEventManager;
import org.apache.wiki.event.WikiPageRenameEvent;
import org.apache.wiki.pages.DefaultPageNameResolver;
import org.apache.wiki.pages.DefaultPageNameResolverManager;
import org.apache.wiki.util.ClassUtil;
import org.apache.wiki.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPageRenamerManager implements PageRenamer, Initializable {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultPageRenamerManager.class);

	String PROP_PAGENAME_RENAMER = "jspwiki.pageRenamer";

	PageRenamer delegate;

	@Override
	public void initialize(Engine engine, Properties props) throws WikiException {
		String classname = DefaultPageRenamer.class.getSimpleName();
		try {
			classname = TextUtil.getRequiredProperty(props, PROP_PAGENAME_RENAMER);
		} catch (NoSuchElementException e) {
			// property not set for setup -> use fall-back
		}

		try {
			LOG.debug("Page provider class: '{}'", classname);
			delegate = ClassUtil.buildInstance("org.apache.wiki.content", classname);
			LOG.debug("Initializing page provider class {}", delegate);
		}
		catch (final ReflectiveOperationException e) {
			LOG.error("Unable to instantiate provider class '{}' ({})", classname, e.getMessage(), e);
			throw new WikiException("Illegal provider class. (" + e.getMessage() + ")", e);
		}
	}

	@Override
	public String renamePage(Context context, String renameFrom, String renameTo, boolean changeReferrers) throws WikiException {
		String cleanedNewPageName = delegate.renamePage(context, renameFrom, renameTo, changeReferrers);
		firePageRenameEvent(renameFrom, cleanedNewPageName);
		return cleanedNewPageName;
	}

	@Override
	public void firePageRenameEvent(String oldName, String newName) {
		if (WikiEventManager.isListening(this)) {
			WikiEventManager.fireEvent(this, new WikiPageRenameEvent(this, oldName, newName));
		}
	}
}
