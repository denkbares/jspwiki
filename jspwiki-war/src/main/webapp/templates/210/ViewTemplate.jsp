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

<%@ taglib uri="http://jspwiki.apache.org/tags" prefix="wiki" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ page import="org.apache.wiki.api.core.*" %>
<fmt:setBundle basename="templates.default"/>
<<<<<<< HEAD:jspwiki-war/src/main/webapp/templates/210/ViewTemplate.jsp
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">

<html lang="en" id="top" xmlns="http://www.w3.org/1999/xhtml" xmlns:jspwiki="http://jspwiki.apache.org">

<head>
  <title>
    <fmt:message key="view.title.view">
      <fmt:param><wiki:Variable var="ApplicationName" default="Apache JSPWiki" /></fmt:param>
      <fmt:param><wiki:PageName /></fmt:param>
    </fmt:message>
  </title>
  <wiki:Include page="commonheader.jsp"/>
  <wiki:CheckVersion mode="notlatest">
    <meta name="robots" content="noindex,nofollow" />
  </wiki:CheckVersion>
  <wiki:CheckRequestContext context="diff|info">
    <meta name="robots" content="noindex,nofollow" />
  </wiki:CheckRequestContext>
  <wiki:CheckRequestContext context="!view">
    <meta name="robots" content="noindex,follow" />
  </wiki:CheckRequestContext>
=======
<!doctype html>
<html lang="${prefs.Language}" name="top">
<head>

	<title>
		<fmt:message key="view.title.view">
			<fmt:param><wiki:Variable var="ApplicationName"/></fmt:param>
			<fmt:param><wiki:PageName/></fmt:param>
		</fmt:message>
	</title>
	<wiki:Include page="commonheader.jsp"/>
	<wiki:CheckVersion mode="notlatest">
		<meta name="robots" content="noindex,nofollow"/>
	</wiki:CheckVersion>
	<wiki:CheckRequestContext context="diff|info">
		<meta name="robots" content="noindex,nofollow"/>
	</wiki:CheckRequestContext>
	<wiki:CheckRequestContext context="!view">
		<meta name="robots" content="noindex,follow"/>
	</wiki:CheckRequestContext>
>>>>>>> 1da44a92d (Replace dynamically injected header spacer with static one):jspwiki-war/src/main/webapp/templates/haddock/ViewTemplate.jsp
</head>

<body class="view">

<<<<<<< HEAD:jspwiki-war/src/main/webapp/templates/210/ViewTemplate.jsp
<div id="wikibody" class="${prefs.Orientation}">
=======
<div class="container${prefs.Layout=='fixed' ? '' : '-fluid' } ${prefs.Orientation} fixed-header">
	<wiki:Include page="Header.jsp"/>
>>>>>>> 1da44a92d (Replace dynamically injected header spacer with static one):jspwiki-war/src/main/webapp/templates/haddock/ViewTemplate.jsp

	<c:set var="sidebarState"><wiki:Variable var="sidebar" default="${prefs.Sidebar}"/></c:set>
	<c:set var="sidebarCookie" value="Sidebar"/>
	<wiki:CheckRequestContext context='login|prefs|createGroup|viewGroup|conflict'>
		<c:set var="sidebarState" value=""/>
		<c:set var="sidebarCookie" value=""/>
	</wiki:CheckRequestContext>

<<<<<<< HEAD:jspwiki-war/src/main/webapp/templates/210/ViewTemplate.jsp
  <div id="content">

    <div id="page">
      <wiki:Include page="PageActionsTop.jsp"/>
      <wiki:Content/>
      <wiki:Include page="PageActionsBottom.jsp"/>
    </div>

    <wiki:Include page="Favorites.jsp"/>

	<div class="clearbox"></div>
  </div>

  <wiki:Include page="Footer.jsp" />
=======
	<div class="content ${sidebarState}" data-toggle="li#menu,.sidebar>.close"
		 data-toggle-pref="${sidebarCookie}">
		<div class="page">
			<wiki:Content/>
			<wiki:Include page="PageInfo.jsp"/>
		</div>
		<wiki:Include page="Sidebar.jsp"/>
	</div>
	<wiki:Include page="Footer.jsp"/>
>>>>>>> 1da44a92d (Replace dynamically injected header spacer with static one):jspwiki-war/src/main/webapp/templates/haddock/ViewTemplate.jsp

</div>

</body>
</html>