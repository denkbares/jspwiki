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

import java.io.Reader;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Stack;

import org.apache.commons.lang3.StringUtils;
import org.apache.oro.text.regex.MatchResult;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.apache.wiki.api.core.Context;
import org.apache.wiki.api.core.ContextEnum;
import org.apache.wiki.api.core.Page;
import org.apache.wiki.api.exceptions.PluginException;
import org.apache.wiki.api.plugin.Plugin;
import org.apache.wiki.attachment.AttachmentManager;
import org.apache.wiki.i18n.InternationalizationManager;
import org.apache.wiki.preferences.Preferences;
import org.apache.wiki.providers.SubWikiUtils;
import org.apache.wiki.util.TextUtil;
import org.jdom2.Attribute;
import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.IllegalDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses JSPWiki-style markup into a WikiDocument DOM tree.  This class is the heart and soul of JSPWiki : make
 * sure you test properly anything that is added, or else it breaks down horribly.
 *
 * @since 2.4
 */
public class JSPWikiMarkupParserMultiWiki extends JSPWikiMarkupParser {

	private static final Logger LOG = LoggerFactory.getLogger(JSPWikiMarkupParserMultiWiki.class);

	/**
	 * Creates a markup parser.
	 *
	 * @param context The WikiContext which controls the parsing
	 * @param in      Where the data is read from.
	 */
	public JSPWikiMarkupParserMultiWiki(final Context context, final Reader in) {
		super(context, in, new LinkParsingOperationsMultiWiki(context));
	}


	protected Element makeLink(int type, final String link, String text, String section, final Iterator<Attribute> attributes, Page page) {
		Element el = null;
		if (text == null) {
			text = link;
		}
		text = callMutatorChain(m_linkMutators, text);
		section = (section != null) ? ("#" + section) : "";

		// Make sure we make a link name that can be accepted  as a valid URL.
		if (link.isEmpty()) {
			type = EMPTY;
		}
		final ResourceBundle rb = Preferences.getBundle(m_context, InternationalizationManager.CORE_BUNDLE);

		switch (type) {
			case READ:
				el = createAnchor(READ, m_context.getURL(ContextEnum.PAGE_VIEW.getRequestContext(), link), text, section);
				break;

			case EDIT:
				el = createAnchor(EDIT, m_context.getURL(ContextEnum.PAGE_EDIT.getRequestContext(), link), text, "");
				el.setAttribute("title", MessageFormat.format(rb.getString("markupparser.link.create"), link));
				break;

			case EMPTY:
				el = new Element("u").addContent(text);
				break;

			// These two are for local references - footnotes and references to footnotes.
			// We embed the page name (or whatever WikiContext gives us) to make sure the links are unique across Wiki.
			case LOCALREF:
				el = createAnchor(LOCALREF, "#ref-" + m_context.getName() + "-" + link, "[" + text + "]", "");
				break;

			case LOCAL:
				el = new Element("a").setAttribute("class", CLASS_FOOTNOTE);
				el.setAttribute("name", "ref-" + m_context.getName() + "-" + link.substring(1));
				el.addContent("[" + text + "]");
				break;

			//  With the image, external and interwiki types we need to make sure nobody can put in Javascript or
			//  something else annoying into the links themselves.  We do this by preventing a haxor from stopping
			//  the link name short with quotes in fillBuffer().
			case 	IMAGE:
				el = new Element("img").setAttribute("class", "inline");
				el.setAttribute("src", link);
				el.setAttribute("alt", text);
				break;

			case IMAGELINK:
				el = new Element("img").setAttribute("class", "inline");
				el.setAttribute("src", link);
				el.setAttribute("alt", text);
				el = createAnchor(IMAGELINK, text, "", "").addContent(el);
				break;

			case IMAGEWIKILINK:
				final String pagelink = m_context.getURL(ContextEnum.PAGE_VIEW.getRequestContext(), text);
				el = new Element("img").setAttribute("class", "inline");
				el.setAttribute("src", link);
				el.setAttribute("alt", text);
				el = createAnchor(IMAGEWIKILINK, pagelink, "", "").addContent(el);
				break;

			case EXTERNAL:
				el = createAnchor(EXTERNAL, link, text, section);
				if (m_useRelNofollow) {
					el.setAttribute("rel", "nofollow");
				}
				break;

			case INTERWIKI:
				el = createAnchor(INTERWIKI, link, text, section);
				break;

			case ATTACHMENT:
				String extendedLink = expandSubWikiNamespace(link, page.getName());
				final String attlink = m_context.getURL(ContextEnum.PAGE_ATTACH.getRequestContext(), extendedLink);
				final String infolink = m_context.getURL(ContextEnum.PAGE_INFO.getRequestContext(), extendedLink);
				final String imglink = m_context.getURL(ContextEnum.PAGE_NONE.getRequestContext(), "images/attachment_small.png");
				el = createAnchor(ATTACHMENT, attlink, text, "");
				if (m_engine.getManager(AttachmentManager.class).forceDownload(attlink)) {
					el.setAttribute("download", "");
				}

				pushElement(el);
				popElement(el.getName());

				if (m_useAttachmentImage) {
					el = new Element("img").setAttribute("src", imglink);
					el.setAttribute("border", "0");
					el.setAttribute("alt", "(info)");

					el = new Element("a").setAttribute("href", infolink).addContent(el);
					el.setAttribute("class", "infolink");
				}
				else {
					el = null;
				}
				break;

			default:
				break;
		}

		if (el != null && attributes != null) {
			while (attributes.hasNext()) {
				final Attribute attr = attributes.next();
				if (attr != null) {
					el.setAttribute(attr);
				}
			}
		}

		if (el != null) {
			flushPlainText();
			m_currentElement.addContent(el);
		}
		return el;
	}

	private String expandSubWikiNamespace(String link, String globalPageName) {
		if(SubWikiUtils.isGlobalName(link)) {
			// is already expanded
			return link;
		}
		String subWikiFolder = null;
		if (globalPageName != null) {
			subWikiFolder = SubWikiUtils.getSubFolderNameOfPage(globalPageName, m_engine.getWikiProperties());
		}
		if (subWikiFolder != null && !subWikiFolder.isEmpty()) {
			link = SubWikiUtils.concatSubWikiAndLocalPageNameNonMain(subWikiFolder, link);
		}
		return link;
	}


	protected int flushPlainText() {
		final int numChars = m_plainTextBuf.length();
		if (numChars > 0) {
			String buf;

			if (!m_allowHTML) {
				buf = escapeHTMLEntities(m_plainTextBuf.toString());
			}
			else {
				buf = m_plainTextBuf.toString();
			}
			//  We must first empty the buffer because the side effect of calling makeCamelCaseLink() is to call this routine.
			m_plainTextBuf = new StringBuilder(20);
			try {
				// This is the heaviest part of parsing, and therefore we can do some optimization here.
				// 1) Only when the length of the buffer is big enough, we try to do the match
				if (m_camelCaseLinks && !m_isEscaping && buf.length() > 3) {
					while (m_camelCaseMatcher.contains(buf, m_camelCasePattern)) {
						final MatchResult result = m_camelCaseMatcher.getMatch();
						final String firstPart = buf.substring(0, result.beginOffset(0));
						String prefix = result.group(1);
						if (prefix == null) {
							prefix = "";
						}

						final String camelCase = result.group(2);
						final String protocol = result.group(3);
						String uri = protocol + result.group(4);
						buf = buf.substring(result.endOffset(0));

						m_currentElement.addContent(firstPart);
						//  Check if the user does not wish to do URL or WikiWord expansion
						if (prefix.endsWith("~") || prefix.indexOf('[') != -1) {
							if (prefix.endsWith("~")) {
								if (m_wysiwygEditorMode) {
									m_currentElement.addContent("~");
								}
								prefix = prefix.substring(0, prefix.length() - 1);
							}
							if (camelCase != null) {
								m_currentElement.addContent(prefix + camelCase);
							}
							else if (protocol != null) {
								m_currentElement.addContent(prefix + uri);
							}
							continue;
						}

						// Fine, then let's check what kind of link this was and emit the proper elements
						if (protocol != null) {
							final char c = uri.charAt(uri.length() - 1);
							if (c == '.' || c == ',') {
								uri = uri.substring(0, uri.length() - 1);
								buf = c + buf;
							}
							// System.out.println("URI match "+uri);
							m_currentElement.addContent(prefix);
							makeDirectURILink(uri);
						}
						else {
							// System.out.println("Matched: '"+camelCase+"'");
							// System.out.println("Split to '"+firstPart+"', and '"+buf+"'");
							// System.out.println("prefix="+prefix);
							m_currentElement.addContent(prefix);
							makeCamelCaseLink(camelCase, m_context.getPage(), m_context.getRealPage());
						}
					}
					m_currentElement.addContent(buf);
				}
				else {
					//  No camelcase asked for, just add the elements
					m_currentElement.addContent(buf);
				}
			}
			catch (final IllegalDataException e) {
				// Sometimes it's possible that illegal XML chars is added to the data. Here we make sure it does not stop parsing.
				m_currentElement.addContent(makeError(cleanupSuspectData(e.getMessage())));
			}
		}

		return numChars;
	}

	/**
	 * When given a link to a WikiName, we just return a proper HTML link for it.  The local link mutator
	 * chain is also called.
	 */
	private Element makeCamelCaseLink(final String wikiname, Page centerPage, Page sourcePage) {
		final String matchedLink = m_linkParsingOperations.linkIfExists(wikiname,  sourcePage.getName());
		callMutatorChain(m_localLinkMutatorChain, wikiname);
		if (matchedLink != null) {
			makeLink(READ, matchedLink, wikiname, null, null, centerPage);
		}
		else {
			makeLink(EDIT, wikiname, wikiname, null, null, centerPage);
		}

		return m_currentElement;
	}

	/**
	 * Takes a URL and turns it into a regular wiki link. Unfortunately, because of the way that flushPlainText()
	 * works, it already encodes all the XML entities. But so does WikiContext.getURL(), so we
	 * have to do a reverse-replace here, so that it can again be replaced in makeLink.
	 * <p>
	 * What a crappy problem.
	 *
	 * @param url provided url.
	 * @return An anchor Element containing the link.
	 */
	private Element makeDirectURILink(String url) {
		final Element result;
		String last = null;

		if (url.endsWith(",") || url.endsWith(".")) {
			last = url.substring(url.length() - 1);
			url = url.substring(0, url.length() - 1);
		}

		callMutatorChain(m_externalLinkMutatorChain, url);

		if (m_linkParsingOperations.isImageLink(url, isImageInlining(), getInlineImagePatterns())) {
			result = handleImageLink(StringUtils.replace(url, "&amp;", "&"), url, false, this.m_context.getPage());
		}
		else {
			result = makeLink(EXTERNAL, StringUtils.replace(url, "&amp;", "&"), url, null, null, null);
			addElement(outlinkImage());
		}

		if (last != null) {
			m_plainTextBuf.append(last);
		}

		return result;
	}

	/**
	 * Image links are handled differently:
	 * 1. If the text is a WikiName of an existing page, it gets linked.
	 * 2. If the text is an external link, then it is inlined.
	 * 3. Otherwise, it becomes an ALT text.
	 *
	 * @param reallink    The link to the image.
	 * @param link        Link text portion, may be a link to somewhere else.
	 * @param hasLinkText If true, then the defined link had a link text available.
	 *                    This means that the link text may be a link to a wiki page,
	 *                    or an external resource.
	 */
	private Element handleImageLink(final String reallink, final String link, final boolean hasLinkText, Page page) {
		final String possiblePage = MarkupParser.cleanLink(link);
		if (m_linkParsingOperations.isExternalLink(link) && hasLinkText) {
			return makeLink(IMAGELINK, reallink, link, null, null, page);
		}
		else if (m_linkParsingOperations.linkExists(possiblePage) && hasLinkText) {
			callMutatorChain(m_localLinkMutatorChain, possiblePage);
			return makeLink(IMAGEWIKILINK, reallink, link, null, null, page);
		}
		else {
			return makeLink(IMAGE, reallink, link, null, null, page);
		}
	}


	/**
	 * Gobbles up all hyperlinks that are encased in square brackets.
	 */
	@Override
	protected Element handleHyperlinks(String linktext, final int pos) {
		final ResourceBundle rb = Preferences.getBundle(m_context, InternationalizationManager.CORE_BUNDLE);
		final StringBuilder sb = new StringBuilder(linktext.length() + 80);

		if (m_linkParsingOperations.isAccessRule(linktext)) {
			return handleAccessRule(linktext);
		}

		if (m_linkParsingOperations.isMetadata(linktext)) {
			return handleMetadata(linktext);
		}

		if (m_linkParsingOperations.isPluginLink(linktext)) {
			try {
				final PluginContent pluginContent = PluginContent.parsePluginLine(m_context, linktext, pos);

				// This might sometimes fail, especially if there is something which looks like a plugin invocation but is really not.
				if (pluginContent != null) {
					addElement(pluginContent);
					pluginContent.executeParse(m_context);
				}
			}
			catch (final PluginException e) {
				LOG.info(m_context.getRealPage().getWiki() + " : " + m_context.getRealPage()
						.getName() + " - Failed to insert plugin: " + e.getMessage());
				//LOG.info( "Root cause:",e.getRootThrowable() );
				if (!m_wysiwygEditorMode) {
					final ResourceBundle rbPlugin = Preferences.getBundle(m_context, Plugin.CORE_PLUGINS_RESOURCEBUNDLE);
					return addElement(makeError(MessageFormat.format(rbPlugin.getString("plugin.error.insertionfailed"),
							m_context.getRealPage().getWiki(),
							m_context.getRealPage().getName(),
							e.getMessage())));
				}
			}
			return m_currentElement;
		}

		try {
			// e.g. Main
			Page currentCenterPage = this.m_context.getPage();

			// e. g., LeftMenu if this call renders the LeftMenu
			String linkSourcePage = this.m_context.getRealPage().getName();

			final LinkParser.Link link = m_linkParser.parse(linktext);
			linktext = link.getText();
			String linkref = link.getReference();
			//  Yes, we now have the components separated.
			//  linktext = the text the link should have
			//  linkref  = the url or page name.
			//  In many cases these are the same.  [linktext|linkref].
			if (m_linkParsingOperations.isVariableLink(linktext)) {
				final Content el = new VariableContent(linktext);
				addElement(el);
			}
			else if (m_linkParsingOperations.isExternalLink(linkref)) {
				// It's an external link, out of this Wiki
				callMutatorChain(m_externalLinkMutatorChain, linkref);
				if (m_linkParsingOperations.isImageLink(linkref, isImageInlining(), getInlineImagePatterns())) {
					handleImageLink(linkref, linktext, link.hasReference(), this.m_context.getPage());
				}
				else {
					makeLink(EXTERNAL, linkref, linktext, null, link.getAttributes(), currentCenterPage);
					addElement(outlinkImage());
				}
			}
			else if (link.isInterwikiLink()) {
				// It's an interwiki link; InterWiki links also get added to external link chain after the links have been resolved.

				// FIXME: There is an interesting issue here:  We probably should
				//        URLEncode the wikiPage, but we can't since some of the
				//        Wikis use slashes (/), which won't survive URLEncoding.
				//        Besides, we don't know which character set the other Wiki
				//        is using, so you'll have to write the entire name as it appears
				//        in the URL.  Bugger.

				final String extWiki = link.getExternalWiki();
				final String wikiPage = link.getExternalWikiPage();
				if (m_wysiwygEditorMode) {
					makeLink(INTERWIKI, extWiki + ":" + wikiPage, linktext, null, link.getAttributes(), currentCenterPage);
				}
				else {
					String urlReference = m_engine.getInterWikiURL(extWiki);
					if (urlReference != null) {
						urlReference = TextUtil.replaceString(urlReference, "%s", wikiPage);
						urlReference = callMutatorChain(m_externalLinkMutatorChain, urlReference);

						if (m_linkParsingOperations.isImageLink(urlReference, isImageInlining(), getInlineImagePatterns())) {
							handleImageLink(urlReference, linktext, link.hasReference(), this.m_context.getPage());
						}
						else {
							makeLink(INTERWIKI, urlReference, linktext, null, link.getAttributes(), currentCenterPage);
						}
						if (m_linkParsingOperations.isExternalLink(urlReference)) {
							addElement(outlinkImage());
						}
					}
					else {
						makeLink(READ, linkref, linktext, null, link.getAttributes(), currentCenterPage);
					}
				}
			}
			else if (linkref.startsWith("#")) {
				// It defines a local footnote
				makeLink(LOCAL, linkref, linktext, null, link.getAttributes(), currentCenterPage);
			}
			else if (TextUtil.isNumber(linkref)) {
				// It defines a reference to a local footnote
				makeLink(LOCALREF, linkref, linktext, null, link.getAttributes(), currentCenterPage);
			}
			else {
				final int hashMark;

				// Internal wiki link, but is it an attachment link?
				String attachment = m_engine.getManager(AttachmentManager.class)
						.getAttachmentInfoName(m_context, linkref);
				if (attachment != null) {
					callMutatorChain(m_attachmentLinkMutatorChain, attachment);
					if (m_linkParsingOperations.isImageLink(linkref, isImageInlining(), getInlineImagePatterns())) {
						attachment = m_context.getURL(ContextEnum.PAGE_ATTACH.getRequestContext(), attachment);
						sb.append(handleImageLink(attachment, linktext, link.hasReference(), currentCenterPage));
					}
					else {
						makeLink(ATTACHMENT, attachment, linktext, null, link.getAttributes(), currentCenterPage);
					}
				}
				else if ((hashMark = linkref.indexOf('#')) != -1) {
					// It's an internal Wiki link, but to a named section
					final String namedSection = linkref.substring(hashMark + 1);
					linkref = linkref.substring(0, hashMark);
					linkref = MarkupParser.cleanLink(linkref);
					callMutatorChain(m_localLinkMutatorChain, linkref);
					final String matchedLink = m_linkParsingOperations.linkIfExists(linkref, linkSourcePage);
					if (matchedLink != null) {
						String sectref = "section-" + m_engine.encodeName(matchedLink + "-" + wikifyLink(namedSection));
						sectref = sectref.replace('%', '_');
						makeLink(READ, matchedLink, linktext, sectref, link.getAttributes(), currentCenterPage);
					}
					else {
						makeLink(EDIT, linkref, linktext, null, link.getAttributes(), currentCenterPage);
					}
				}
				else {

					linkref = MarkupParser.cleanLink(linkref);
					String expandedLinkRef =  expandSubWikiNamespace(linkref, linkSourcePage);
					callMutatorChain(m_localLinkMutatorChain, expandedLinkRef);
					final String matchedLink = m_linkParsingOperations.linkIfExists(expandedLinkRef, linkSourcePage);
					if (matchedLink != null) {
						makeLink(READ, matchedLink, linktext, null, link.getAttributes(), currentCenterPage);
					}
					else {
						makeLink(EDIT, expandedLinkRef, linktext, null, link.getAttributes(), currentCenterPage);
					}
				}
			}
		}
		catch (final ParseException e) {
			LOG.info("Parser failure: ", e);
			final Object[] args = { e.getMessage() };
			addElement(makeError(MessageFormat.format(rb.getString("markupparser.error.parserfailure"), args)));
		}
		return m_currentElement;
	}

}
