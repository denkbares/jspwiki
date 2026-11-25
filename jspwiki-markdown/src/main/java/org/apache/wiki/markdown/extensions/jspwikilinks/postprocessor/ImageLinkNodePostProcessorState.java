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
package org.apache.wiki.markdown.extensions.jspwikilinks.postprocessor;

import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.NodeTracker;
import com.vladsch.flexmark.util.sequence.CharSubSequence;
import org.apache.wiki.api.core.Context;
import org.apache.wiki.markdown.nodes.JSPWikiLink;
import org.apache.wiki.parser.LinkParsingOperations;
import org.apache.wiki.util.TextUtil;

import java.util.Locale;


/**
 * {@link NodePostProcessorState} which further post processes image links.
 */
public class ImageLinkNodePostProcessorState implements NodePostProcessorState< JSPWikiLink > {

    private final boolean isLinkFromText;
    private final String urlRef;
    private final LinkParsingOperations linkOperations;

    public ImageLinkNodePostProcessorState( final Context wikiContext, final String urlRef, final boolean isLinkFromText ) {
        this.isLinkFromText = isLinkFromText;
        this.urlRef = urlRef;
        this.linkOperations = new LinkParsingOperations( wikiContext );
    }

    /**
     * {@inheritDoc}
     *
     * @see NodePostProcessorState#process(NodeTracker, Node)
     */
    @Override
    public void process( final NodeTracker state, final JSPWikiLink link ) {
        // url and text are written into attributes, so they must be encoded (JSPWIKI-1296, CVE-2026-125949)
        final WikiHtmlInline img = WikiHtmlInline.of( "<img class=\"inline\" " +
                                                               "src=\"" + TextUtil.replaceEntities( sanitizeUrl( urlRef ) ) + "\" " +
                                                               "alt=\"" + TextUtil.replaceEntities( link.getText().toString() ) + "\" />" );
        if( ( isLinkFromText && linkOperations.isExternalLink( link.getText().toString() ) ) ||
                ( isLinkFromText && linkOperations.linkExists( link.getText().toString() ) ) ) {
            link.setUrl( CharSubSequence.of( sanitizeUrl( urlRef ) ) );
            link.removeChildren();
            link.appendChild( img );
            state.nodeAdded( img );
        } else {
            NodePostProcessorStateCommonOperations.addContent( state, link, img );
        }
    }

    /**
     * Neutralizes script and data urls the same way the Image plugin does. Browsers ignore whitespace and control
     * characters inside the scheme, so these are ignored for the check.
     */
    static String sanitizeUrl( final String url ) {
        final String scheme = url.toLowerCase( Locale.ROOT ).replaceAll( "[\\s\\p{Cntrl}]+", "" );
        if( scheme.startsWith( "javascript:" ) || scheme.startsWith( "vbscript:" ) || scheme.startsWith( "data:" ) ) {
            return "http://invalid_url" + url;
        }
        return url;
    }

}
