<%--
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
--%>

<%@ page import="java.util.*" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="org.apache.wiki.api.core.*" %>
<%@ page import="org.apache.wiki.api.search.*" %>
<%@ page import="org.apache.wiki.api.spi.Wiki" %>
<%@ page import="org.apache.wiki.auth.*" %>
<%@ page import="org.apache.wiki.auth.permissions.*" %>
<%@ page import="org.apache.wiki.preferences.Preferences" %>
<%@ page import="org.apache.wiki.search.SearchManager" %>
<%@ page import="org.apache.wiki.ui.TemplateManager" %>
<%@ page import="org.apache.wiki.util.TextUtil" %>
<%@ page errorPage="/Error.jsp" %>
<%@ taglib uri="http://jspwiki.apache.org/tags" prefix="wiki" %>

<%!
    Logger log = LoggerFactory.getLogger("JSPWikiSearch");
%>

<%
    Engine wiki = Wiki.engine().find( getServletConfig() );
    // Create wiki context and check for authorization
    Context wikiContext = Wiki.context().create( wiki, request, ContextEnum.WIKI_FIND.getRequestContext() );
    if(!wiki.getManager( AuthorizationManager.class ).hasAccess( wikiContext, response )) return;
    String pagereq = wikiContext.getName();

    // Get the search results
    Collection< SearchResult > list = null;
    String query = request.getParameter( "query");
    String go    = request.getParameter("go");

    if( query != null ) {
        log.info("Searching for string "+query);
        long start = System.currentTimeMillis();
        try {
            list = wiki.getManager( SearchManager.class ).findPages( query, wikiContext );
            pageContext.setAttribute( "searchresults", list, PageContext.REQUEST_SCOPE );
        } catch( Exception e ) {
            wikiContext.getWikiSession().addMessage( e.getMessage() );
        }
        if (list != null) log.info("Found " + list.size() + " results in " + (System.currentTimeMillis() - start) + "ms");

        query = TextUtil.replaceEntities( query );

        pageContext.setAttribute( "query", query, PageContext.REQUEST_SCOPE );

        //
        //  Did the user click on "go"?
        //
        if( go != null ) {
            if( list != null && !list.isEmpty()) {
                SearchResult sr = list.iterator().next();
                Page wikiPage = sr.getPage();
                String url = wikiContext.getViewURL( wikiPage.getName() );
                response.sendRedirect( url );
                return;
            }
        }
    }

    // Set the content type and include the response content
    response.setContentType("text/html; charset="+wiki.getContentEncoding() );
    String contentPage = wiki.getManager( TemplateManager.class ).findJSP( pageContext, wikiContext.getTemplate(), "ViewTemplate.jsp" );
%><wiki:Include page="<%=contentPage%>" /><%
    log.debug("SEARCH COMPLETE");
%>
