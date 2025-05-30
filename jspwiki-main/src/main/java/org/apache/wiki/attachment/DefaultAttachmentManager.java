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
package org.apache.wiki.attachment;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.wiki.api.core.Attachment;
import org.apache.wiki.api.core.Context;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.core.Page;
import org.apache.wiki.api.exceptions.NoRequiredPropertyException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.api.providers.AttachmentProvider;
import org.apache.wiki.api.spi.Wiki;
import org.apache.wiki.cache.CachingManager;
import org.apache.wiki.pages.PageManager;
import org.apache.wiki.parser.MarkupParser;
import org.apache.wiki.references.ReferenceManager;
import org.apache.wiki.search.SearchManager;
import org.apache.wiki.util.ClassUtil;
import org.apache.wiki.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation for {@link AttachmentManager}.
 * <p>
 * {@inheritDoc}
 *
 * @since 1.9.28
 */
public class DefaultAttachmentManager implements AttachmentManager {

	/**
	 * List of attachment types which are forced to be downloaded
	 */
	private String[] m_forceDownloadPatterns;

	private static final Logger LOG = LoggerFactory.getLogger(DefaultAttachmentManager.class);
	private AttachmentProvider m_provider;
	private final Engine m_engine;
	private final CachingManager cachingManager;

	/**
	 * Creates a new AttachmentManager.  Note that creation will never fail, but it's quite likely that attachments do
	 * not function.
	 * <p><strong>DO NOT CREATE</strong> an AttachmentManager on your own, unless you really know what you're doing.
	 * Just use
	 * Wikiengine.getManager( AttachmentManager.class ) if you're making a module for JSPWiki.
	 *
	 * @param engine The wikiengine that owns this attachment manager.
	 * @param props  A list of properties from which the AttachmentManager will seek its configuration. Typically, this
	 *               is the "jspwiki.properties".
	 */
	public DefaultAttachmentManager(final Engine engine, final Properties props) {
		m_engine = engine;
		cachingManager = m_engine.getManager(CachingManager.class);
		final String classname;
		if (cachingManager.enabled(CachingManager.CACHE_ATTACHMENTS_DYNAMIC)) {
			classname = "org.apache.wiki.providers.CachingAttachmentProvider";
		}
		else {
			classname = TextUtil.getRequiredProperty(props, PROP_PROVIDER, PROP_PROVIDER_DEPRECATED);
		}

		//  If no class defined, then will just simply fail.
		if (classname == null) {
			LOG.info("No attachment provider defined - disabling attachment support.");
			return;
		}

		//  Create and initialize the provider.
		try {
			m_provider = ClassUtil.buildInstance("org.apache.wiki.providers", classname);
			m_provider.initialize(m_engine, props);
		}
		catch (final ReflectiveOperationException e) {
			LOG.error("Attachment provider class could not be instantiated", e);
		}
		catch (final NoRequiredPropertyException e) {
			LOG.error("Attachment provider did not find a property that it needed: {}", e.getMessage(), e);
			m_provider = null; // No, it did not work.
		}
		catch (final IOException e) {
			LOG.error("Attachment provider reports IO error", e);
			m_provider = null;
		}

		final String forceDownload = TextUtil.getStringProperty(props, PROP_FORCEDOWNLOAD, null);
		if (forceDownload != null && !forceDownload.isEmpty()) {
			m_forceDownloadPatterns = forceDownload.toLowerCase().split("\\s");
		}
		else {
			m_forceDownloadPatterns = new String[0];
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean attachmentsEnabled() {
		return m_provider != null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getAttachmentInfoName(final Context context, final String attachmentname) {
		final Attachment att;
		try {
			att = getAttachmentInfo(context, attachmentname);
		}
		catch (final ProviderException e) {
			LOG.warn("Finding attachments failed: ", e);
			return null;
		}

		if (att != null) {
			return att.getName();
		}
		else if (attachmentname.indexOf('/') != -1) {
			return attachmentname;
		}

		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Attachment getAttachmentInfo(final Context context, String attachmentname, final int version) throws ProviderException {
		if (m_provider == null) {
			return null;
		}

		Page currentPage = null;

		if (context != null) {
			currentPage = context.getPage();
		}

		//  Figure out the parent page of this attachment.  If we can't find it, we'll assume this refers directly to the attachment.
		final int cutpt = attachmentname.lastIndexOf('/');
		if (cutpt != -1) {

			String parentPage = attachmentname.substring(0, cutpt);
			// If we for some reason have an empty parent page name; this can't be an attachment
			if (parentPage.isEmpty()) {
				return null;
			}
			attachmentname = attachmentname.substring(cutpt + 1);
			currentPage = findCurrentPage(parentPage);
		}

		//  If the page cannot be determined, we cannot possibly find the attachments.
		if (currentPage == null || currentPage.getName().isEmpty()) {
			return null;
		}

		//  Finally, figure out whether this is a real attachment or a generated attachment.
		Attachment att = getDynamicAttachment(currentPage.getName() + "/" + attachmentname);
		if (att == null) {
			att = m_provider.getAttachmentInfo(currentPage, attachmentname, version);
		}

		return att;
	}

	private Page findCurrentPage(String parentPage) {
		PageManager manager = m_engine.getManager(PageManager.class);
		String parentPageCleaned = MarkupParser.cleanLink(parentPage);
		Page currentPage = manager.getPage(parentPageCleaned);

		// Go check for legacy name
		// FIXME: This should be resolved using CommandResolver, not this adhoc way.  This also assumes that the
		//        legacy charset is a subset of the full allowed set.
		if (currentPage == null) {
			currentPage = manager.getPage(MarkupParser.wikifyLink(parentPageCleaned));
		}

		if (currentPage == null) {
			// for some reason in some cases the attachment name is coming HTML-Encoded while should not... try without
			currentPage = manager.getPage(TextUtil.unReplaceEntities(parentPage));
		}

		return currentPage;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Attachment> listAttachments(final Page wikipage) throws ProviderException {
		if (m_provider == null) {
			return new ArrayList<>();
		}

		final List<Attachment> atts = new ArrayList<>(m_provider.listAttachments(wikipage));
		atts.sort(Comparator.comparing(Attachment::getName, m_engine.getManager(PageManager.class).getPageSorter()));

		return atts;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean forceDownload(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}

		name = name.toLowerCase();
		if (name.indexOf('.') == -1) {
			return true;  // force download on attachments without extension or type indication
		}

		for (final String forceDownloadPattern : m_forceDownloadPatterns) {
			if (name.endsWith(forceDownloadPattern) && !forceDownloadPattern.isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public InputStream getAttachmentStream(final Context ctx, final Attachment att) throws ProviderException, IOException {
		if (m_provider == null) {
			return null;
		}

		if (att instanceof DynamicAttachment) {
			return ((DynamicAttachment) att).getProvider().getAttachmentData(ctx, att);
		}

		return m_provider.getAttachmentData(att);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void storeDynamicAttachment(final Context ctx, final DynamicAttachment att) {
		cachingManager.put(CachingManager.CACHE_ATTACHMENTS_DYNAMIC, att.getName(), att);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DynamicAttachment getDynamicAttachment(final String name) {
		return cachingManager.get(CachingManager.CACHE_ATTACHMENTS_DYNAMIC, name, () -> null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void storeAttachment(final Attachment att, final InputStream in) throws IOException, ProviderException {
		if (m_provider == null) {
			return;
		}

		// Checks if the actual, real page exists without any modifications or aliases. We cannot store an attachment to a non-existent page.
		if (!m_engine.getManager(PageManager.class).pageExists(att.getParentName())) {
			// the caller should catch the exception and use the exception text as an i18n key
			throw new ProviderException("attach.parent.not.exist");
		}

		m_provider.putAttachmentData(att, in);
		m_engine.getManager(ReferenceManager.class).updateReferences(att.getName(), new ArrayList<>());

		final Page parent = Wiki.contents().page(m_engine, att.getParentName());
		m_engine.getManager(ReferenceManager.class).updateReferences(parent);
		m_engine.getManager(SearchManager.class).reindexPage(att);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Attachment> getVersionHistory(final String attachmentName) throws ProviderException {
		if (m_provider == null) {
			return null;
		}

		final Attachment att = getAttachmentInfo(null, attachmentName);
		if (att != null) {
			return m_provider.getVersionHistory(att);
		}

		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Collection<Attachment> getAllAttachments() throws ProviderException {
		if (attachmentsEnabled()) {
			return m_provider.listAllChanged(new Date(0L));
		}

		return new ArrayList<>();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AttachmentProvider getCurrentProvider() {
		return m_provider;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deleteVersion(final Attachment att) throws ProviderException {
		if (m_provider == null) {
			return;
		}

		m_provider.deleteVersion(att);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	// FIXME: Should also use events!
	public void deleteAttachment(final Attachment att) throws ProviderException {
		if (m_provider == null) {
			return;
		}

		m_provider.deleteAttachment(att);
		m_engine.getManager(SearchManager.class).pageRemoved(att);
		m_engine.getManager(ReferenceManager.class).clearPageEntries(att.getName());
	}
}
