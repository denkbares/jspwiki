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
<%@ page import="java.security.Principal" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="org.apache.wiki.api.core.*" %>
<%@ page import="org.apache.wiki.api.spi.Wiki" %>
<%@ page import="org.apache.wiki.auth.*" %>
<%@ page import="org.apache.wiki.preferences.Preferences" %>
<%@ page import="org.apache.wiki.util.TextUtil" %>
<%@ page errorPage="/Error.jsp" %>
<%!
  public void jspInit()
  {
    wiki = Wiki.engine().find( getServletConfig() );
  }
  Logger log = LoggerFactory.getLogger("JSPWiki");
  Engine wiki;
  SecurityVerifier verifier;
%>
<!doctype html>
<html lang="en" name="top">
<%
  Context wikiContext = Wiki.context().create( wiki, request, ContextEnum.PAGE_NONE.getRequestContext() );
  if(!wiki.getManager( AuthorizationManager.class ).hasAccess( wikiContext, response )) return;
  response.setContentType("text/html; charset="+wiki.getContentEncoding() );
  verifier = new SecurityVerifier( wiki, wikiContext.getWikiSession() );

  //
  //  This is a security feature, so we will turn it off unless the user really wants to.
  //
  if( !TextUtil.isPositive(wiki.getWikiProperties().getProperty("jspwiki-x.securityconfig.enable")) )
  {
      %>
      <head>
        <title><wiki:Variable var="applicationname" default="Apache JSPWiki" />: JSPWiki Security Configuration Verifier</title>
        <base href="../"/>
        <link rel="stylesheet" media="screen, projection" type="text/css" href="<wiki:Link format="url" templatefile="jspwiki.css"/>"/>
        <wiki:IncludeResources type="stylesheet"/>
      </head>
      <body class="container">
         <h1>Disabled</h1>
         <p>JSPWiki SecurityConfig UI has been disabled.  This page could reveal important security
         details about your configuration to a potential attacker, so it has been turned off by
         default.  However, it is very easy to enable it by setting the following value</p>
         <pre>
             jspwiki-x.securityconfig.enable=true
         </pre>
         <p>in your <code>jspwiki-custom.properties</code> file.</p>
         <p>Once you are done with debugging your security configuration, please turn this page
         off again by removing the preceding line, so that your system is safe again.</p>
         <p>Have a nice day.  May the Force be with you.</p>
      </body>
      </html>
      <%
      return;
  }

%>

<head>
  <title><wiki:Variable var="applicationname" default="Apache JSPWiki" />: JSPWiki Security Configuration Verifier</title>
  <base href="../"/>
  <link rel="stylesheet" media="screen, projection" type="text/css" href="<wiki:Link format="url" templatefile="jspwiki.css"/>"/>
  <wiki:IncludeResources type="stylesheet"/>
</head>
<body>
<div id="wikibody" class="container-fixed">
<div id="page">
<div id="pagecontent">

<h1>JSPWiki Security Configuration Verifier</h1>

<p>This page examines JSPWiki's security configuration and tries to determine if it is working the way it should. Although JSPWiki comes configured with some reasonable default configuration settings out of the box, it's not always obvious what settings to change if you need to customize the security... and sooner or later, just about everyone does.</p>

<p>This page is dynamically generated by JSPWiki. It examines the authentication, authorization and security policy settings. When we think something looks funny, we'll try to communicate what the issue might be, and will make recommendations on how to fix the problem.</p>

<blockquote>
<p >Please delete this JSP when you are finished troubleshooting your system.
This diagnostic data presented on this page do not represent a security risk
to your system <em>per se</em>, but they do provide a significant amount of
contextual information that could be useful to an attacker. This page is
currently unconstrained, which means that anyone can view it: nice people, mean people
and everyone in between. You have been warned.  You can turn it off by setting</p>
<pre>
     jspwiki-x.securityconfig.enable=false
</pre>
<p>in your jspwiki-custom.properties.
</p>
</blockquote>

<!--
  *********************************************
  **** A U T H E N T I C A T I O N         ****
  *********************************************
-->
<h2>Authentication Configuration</h2>
<!--
  *********************************************
  **** Container Authentication Verifier   ****
  *********************************************
-->
<h3>Container-Managed Authentication</h3>
<%
  boolean isContainerAuth = wiki.getManager( AuthenticationManager.class ).isContainerAuthenticated();
  AuthorizationManager authorizationManager = wiki.getManager( AuthorizationManager.class );
  if( isContainerAuth ) {
%>
    <!-- We are using container auth -->
    <p>I see that you've configured container-managed authentication. Very nice.</p>
<%
  } else {
%>
    <!-- We are not using container auth -->
    <p>Container-managed authentication appears to be disabled, according to your <code>WEB-INF/web.xml</code> file.</p>
<%
  }
%>


<!--
  *********************************************
  **** JAAS Authentication Config Verifier ****
  *********************************************
-->
<h3>JAAS Login Configuration</h3>

<!-- Notify users which JAAS configs we need to find -->
<p>JSPWiki wires up its own JAAS to define the authentication process, and does not rely on the JRE configuration. By default, JSPWiki configures its JAAS login stack to use the UserDatabaseLoginModule. You can specify a custom login module by setting the <code>jspwiki.loginModule.class</code> property in <code>jspwiki.properties</code>.</p>

<wiki:Messages div="information" topic='<%=SecurityVerifier.INFO+"java.security.auth.login.config"%>' prefix="Good news: "/>
<wiki:Messages div="warning" topic='<%=SecurityVerifier.WARNING+"java.security.auth.login.config"%>' prefix="We found some potential problems with your configuration: "/>
<wiki:Messages div="error" topic='<%=SecurityVerifier.ERROR+"java.security.auth.login.config"%>' prefix="We found some errors with your configuration: " />

<!-- Print JAAS configuration status -->
<p>The JAAS login configuration is correctly configured if the <code>jspwiki.loginModule.class</code> property specifies
a class we can find on the classpath. This class must also be a LoginModule implementation. We will check for both conditions.</p>

<wiki:Messages div="information" topic="<%=SecurityVerifier.INFO_JAAS%>" prefix="Good news: "/>
<wiki:Messages div="warning" topic="<%=SecurityVerifier.WARNING_JAAS%>" prefix="We found some potential problems with your configuration: "/>
<wiki:Messages div="error" topic="<%=SecurityVerifier.ERROR_JAAS%>" prefix="We found some errors with your configuration: " />

<!--
  *********************************************
  **** A U T H O R I Z A T I O N           ****
  *********************************************
-->
<h2>Authorization Configuration</h2>

<!--
  *********************************************
  **** Container Authorization Verifier    ****
  *********************************************
-->
<h3>Container-Managed Authorization</h3>
<%
  if ( isContainerAuth )
  {
%>
    <!-- We are using container auth -->
    <p>I see that you've configured container-managed authorization. Very nice.</p>
<%
    Principal[] roles = verifier.webContainerRoles();
    if ( roles.length > 0 )
    {
%>
      <!-- Even better, we are using the standard authorizer, which
           allows us to identify the roles the container knows about -->
      <p>Your <code>WEB-INF/web.xml</code> file defines the following roles:</p>
      <ul>
<%
        for( int i = 0; i < roles.length; i++ )
        {
%>
          <li><%=roles[i].getName()%></li>
<%
        }
%>
      </ul>
<%
    }
    else
    {
%>
      <!-- No roles! That's very odd -->
      <div class="error">Your <code>WEB-INF/web.xml</code> file does not define any roles. This is an error.</div>
<%
    }
  }
  else
  {
%>
    <!-- We are not using container auth -->
    <p>Container-managed authorization appears to be disabled, according to your <code>WEB-INF/web.xml</code> file.</p>
<%
  }
%>

<!--
  *********************************************
  **** Java Security Policy Verifier       ****
  *********************************************
-->
<h3>Security Policy</h3>
<p>JSPWiki's authorizes user actions by consulting a standard Java 2 security policy file. By default, JSPWiki installs its local security policy file at startup time. This policy file is independent of your global, JVM-wide security policy, if you have one. When checking for authorization, JSPWiki consults the global policy first, then the local policy.</p>

<p>Let's validate the local security policy file. To do this, we parse
the security policy and examine each <code>grant</code> block. If we see
a <code>permission</code> entry that is signed, we verify that the certificate
alias exists in our keystore. The keystore itself must also exist in the file system.
And as an additional check, we will try to load each <code>Permission</code> class into memory to verify that JSPWiki's classloader can find them.</p>

<wiki:Messages div="information" topic="<%=SecurityVerifier.INFO_POLICY%>" prefix="Good news: "/>
<wiki:Messages div="warning" topic="<%=SecurityVerifier.WARNING_POLICY%>" prefix="We found some potential problems with your configuration: "/>
<wiki:Messages div="error" topic="<%=SecurityVerifier.ERROR_POLICY%>" prefix="We found some errors with your configuration: " />

<%
  if ( !verifier.isSecurityPolicyConfigured() )
  {
%>
    <p>Note: JSPWiki's Policy file parser is stricter than the default parser that ships with the JVM. If you encounter parsing errors, make sure you have the correct comma and semicolon delimiters in your policy file <code>grant</code> entries. The <code>grant</code> blocks must follow this format:</p>
    <blockquote>
      <pre>grant signedBy "signer_names", codeBase "URL",
    principal principal_class_name "principal_name",
    principal principal_class_name "principal_name",
    ... {

    permission permission_class_name "target_name", "action";
    permission permission_class_name "target_name", "action";
};</pre>
    </blockquote>

    <p>Note: JSPWiki versions prior to 2.4.6 accidentally omitted commas after the <code>signedBy</code> entries, so you should fix this if you are using a policy file based on a version earlier than 2.4.6.</p>
<%
  }
%>

<h2>Access Control Validation</h2>

<h3>Security Policy Restrictions</h3>

<p>Now comes the <em>really</em> fun part. Using the current security policy, we will test the PagePermissions each JSPWiki role possesses for a range of pages. The roles we will test include the standard JSPWiki roles (Authenticated, All, etc.) plus any others you may have listed in the security policy. In addition to the PagePermissions, we will also test the WikiPermissions. The results of these tests should tell you what behaviors you can expect based on your security policy file. If we had problems finding, parsing or verifying the policy file, these tests will likely fail.</p>

<p>The colors in each cell show the results of the test. <span style="background-color: #c0ffc0;">&nbsp;Green&nbsp;</span> means success; <span style="background-color: #ffc0c0;">&nbsp;red&nbsp;</span> means failure. Hovering over a role name or individual cell will display more detailed information about the role or test.</p>

<%=verifier.policyRoleTable()%>

<div class="information">Important: these tests do not take into account any page-level access control lists. Page ACLs, if they exist, will contrain access further than what is shown in the table.
<%
  if ( isContainerAuth )
  {
%>
In addition, because you are using container-managed security, constraints on user activities might be stricter than what is shown in this table. If the container requires that users accessing <code>Edit.jsp</code> possess the container role "Admin," for example, this will override an "edit" PagePermission granted to role "Authenticated." See below.
<%
  }
%>
</div>

<%
  if ( isContainerAuth )
  {
%>
    <h3>Web Container Restrictions</h3>

    <p>Here is how your web container will control role-based access to some common JSPWiki actions and their assocated JSPs. These restrictions will be enforced even if your Java security policy is more permissive.</p>

    <p>The colors in each cell show the results of the test. <span style="background-color: #c0ffc0;">&nbsp;Green&nbsp;</span> means success; <span style="background-color: #ffc0c0;">&nbsp;red&nbsp;</span> means failure.</p>

    <!-- Print table showing role restrictions by JSP -->
    <%=verifier.containerRoleTable()%>

    <div class="information">Important: these tests do not take into account any page-level access control lists. Page ACLs, if they exist, will contrain access further than what is shown in the table.</div>

    <!-- Remind the admin their container needs to return the roles -->
    <p>Note that your web container will allow access to these pages <em>only</em> if your container's authentication realm returns the roles
<%
    Principal[] roles = verifier.webContainerRoles();
    for( int i = 0; i < roles.length; i++ )
    {
%>&nbsp;<strong><%=(roles[i].getName() + (i<(roles.length-1)?",":""))%></strong><%
    }
%>
    If your container's realm returns other role names, users won't be able to access the pages they should be allowed to see -- because the role names don't match. In that case, You should adjust the <code>&lt;role-name&gt;</code> entries in <code>web.xml</code> appropriately to match the role names returned by your container's authorization realm.</p>

    <p>Now we are going to compare the roles listed in your security policy with those from your <code>web.xml</code> file. The ones we care about are those that aren't built-in roles like "All", "Anonymous", "Authenticated" or "Asserted". If your policy shows roles other than these, we need to make sure your container knows about them, too. Container roles are defined in <code>web.xml</code> in blocks such as these:</p>
    <blockquote><pre>&lt;security-role&gt;
  &lt;description&gt;
    This logical role includes all administrative users
  &lt;/description&gt;
  &lt;role-name&gt;Admin&lt;/role-name&gt;
&lt;/security-role&gt;</pre></blockquote>

    <wiki:Messages div="information" topic="<%=SecurityVerifier.INFO_ROLES%>" prefix="Good news: "/>
    <wiki:Messages div="error" topic="<%=SecurityVerifier.ERROR_ROLES%>" prefix="We found some errors with your configuration: " />

<%
  }
%>

<h2>User and Group Databases</h2>

<h3>User Database Configuration</h3>
<p>The user database stores user profiles. It's pretty important that it functions properly. We will try to determine what your current UserDatabase implementation is, based on the current value of the <code>jspwiki.userdatabase</code> property in your <code>jspwiki.properties</code> file. In addition, once we establish that the UserDatabase has been initialized properly, we will try to add (then, delete) a random test user. If all of these things work they way they should, then you should have no problems with user self-registration.</p>

<wiki:Messages div="information" topic="<%=SecurityVerifier.INFO_DB%>" prefix="Good news: "/>
<wiki:Messages div="warning" topic="<%=SecurityVerifier.WARNING_DB%>" prefix="We found some potential problems with your configuration: "/>
<wiki:Messages div="error" topic="<%=SecurityVerifier.ERROR_DB%>" prefix="We found some errors with your configuration: " />

<h3>Group Database Configuration</h3>
<p>The group database stores wiki groups. It's pretty important that it functions properly. We will try to determine what your current GroupDatabase implementation is, based on the current value of the <code>jspwiki.groupdatabase</code> property in your <code>jspwiki.properties</code> file. In addition, once we establish that the GroupDatabase has been initialized properly, we will try to add (then, delete) a random test group. If all of these things work they way they should, then you should have no problems with wiki group creation and editing.</p>

<wiki:Messages div="information" topic="<%=SecurityVerifier.INFO_GROUPS%>" prefix="Good news: "/>
<wiki:Messages div="warning" topic="<%=SecurityVerifier.WARNING_GROUPS%>" prefix="We found some potential problems with your configuration: "/>
<wiki:Messages div="error" topic="<%=SecurityVerifier.ERROR_GROUPS%>" prefix="We found some errors with your configuration: " />

<!-- We're done... -->
</div>
</div>
</div>
</body>
</html>
