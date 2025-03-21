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

<%@ page import="java.text.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="org.apache.wiki.api.core.*" %>
<%@ page import="org.apache.wiki.api.spi.Wiki" %>
<%@ page import="org.apache.wiki.api.exceptions.WikiException" %>
<%@ page import="org.apache.wiki.auth.AuthorizationManager" %>
<%@ page import="org.apache.wiki.content.PageRenamer" %>
<%@ page import="org.apache.wiki.util.HttpUtil" %>
<%@ page import="org.apache.wiki.preferences.Preferences" %>
<%@ page import="org.apache.wiki.tags.BreadcrumbsTag" %>
<%@ page import="org.apache.wiki.tags.BreadcrumbsTag.FixedQueue" %>
<%@ page import="org.apache.wiki.ui.TemplateManager" %>
<%@ page import="org.apache.wiki.util.TextUtil" %>
<%@ page errorPage="/Error.jsp" %>
<%@ taglib uri="http://jspwiki.apache.org/tags" prefix="wiki" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<fmt:setBundle basename="CoreResources"/>
<%!
    Logger log = LoggerFactory.getLogger("JSPWiki");
%>

<%
    Engine wiki = Wiki.engine().find( getServletConfig() );
    // Create wiki context and check for authorization
	Context wikiContext = Wiki.context().create( wiki, request, ContextEnum.PAGE_RENAME.getRequestContext() );
	if( !wiki.getManager( AuthorizationManager.class ).hasAccess( wikiContext, response ) ) return;
    if( wikiContext.getCommand().getTarget() == null ) {
        response.sendRedirect( wikiContext.getURL( wikiContext.getRequestContext(), wikiContext.getName() ) );
        return;
    }

    String renameFrom = wikiContext.getName();
    String renameTo = request.getParameter("renameto");

    boolean changeReferences = false;

    ResourceBundle rb = Preferences.getBundle( wikiContext, "CoreResources" );

    if (request.getParameter("references") != null)
    {
        changeReferences = true;
    }

    log.info("Page rename request for page '"+renameFrom+ "' to new name '"+renameTo+"' from "+HttpUtil.getRemoteAddress(request)+" by "+request.getRemoteUser() );

    Session wikiSession = wikiContext.getWikiSession();
    try
    {
        if (!renameTo.isEmpty())
        {
            String renamedTo = wiki.getManager( PageRenamer.class ).renamePage(wikiContext, renameFrom, renameTo, changeReferences);

            FixedQueue trail = (FixedQueue) session.getAttribute( BreadcrumbsTag.BREADCRUMBTRAIL_KEY );
            if( trail != null ) {
                trail.removeItem( renameFrom );
                session.setAttribute( BreadcrumbsTag.BREADCRUMBTRAIL_KEY, trail );
            }

            log.info("Page successfully renamed to '"+renamedTo+"'");

            response.sendRedirect( wikiContext.getURL( ContextEnum.PAGE_VIEW.getRequestContext(), renamedTo ) );
            return;
        }
       wikiSession.addMessage("rename", rb.getString("rename.empty"));

      log.info("Page rename request failed because new page name was left blank");
    }
    catch (WikiException e)
    {
        if (e.getMessage().equals("You cannot rename the page to itself"))
        {
            log.info("Page rename request failed because page names are identical");
            wikiSession.addMessage("rename", rb.getString("rename.identical") );
        }
        else if (e.getMessage().startsWith("Page already exists "))
        {
            log.info("Page rename request failed because new page name is already in use");
            wikiSession.addMessage("rename", MessageFormat.format(rb.getString("rename.exists"),renameTo));
        }
        else if (e.getMessage().startsWith("Page name contains prohibited characters "))
        {
            log.info(e.getMessage());
            wikiSession.addMessage("rename", e.getMessage());
        }
        else
        {
            wikiSession.addMessage("rename",  MessageFormat.format(rb.getString("rename.unknownerror"),e.toString()));
        }

    }

    pageContext.setAttribute( "renameto", TextUtil.replaceEntities( renameTo ), PageContext.REQUEST_SCOPE );
    response.setContentType("text/html; charset=" + wiki.getContentEncoding() );
    String contentPage = wiki.getManager( TemplateManager.class ).findJSP( pageContext, wikiContext.getTemplate(), "ViewTemplate.jsp" );

%><wiki:Include page="<%=contentPage%>" />