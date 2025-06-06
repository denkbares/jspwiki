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
package org.apache.wiki.util;

import javax.activation.DataHandler;
import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>Contains static methods for sending e-mails to recipients using JNDI-supplied
 * <a href="http://java.sun.com/products/javamail/">JavaMail</a>
 * Sessions supplied by a web container (preferred) or configured via
 * <code>jspwiki.properties</code>; both methods are described below.
 * Because most e-mail servers require authentication,
 * for security reasons implementors are <em>strongly</em> encouraged to use
 * container-managed JavaMail Sessions so that passwords are not exposed in
 * <code>jspwiki.properties</code>.</p>
 * <p>To enable e-mail functions within JSPWiki, administrators must do three things:
 * ensure that the required JavaMail JARs are on the runtime classpath, configure
 * JavaMail appropriately, and (recommended) configure the JNDI JavaMail session factory.</p>
 * <strong>JavaMail runtime JARs</strong>
 * <p>The first step is easy: JSPWiki bundles
 * recent versions of the required JavaMail <code>mail.jar</code> and
 * <code>activation.jar</code> into the JSPWiki WAR file; so, out of the box
 * this is already taken care of. However, when using JNDI-supplied
 * Session factories, these should be moved, <em>not copied</em>, to a classpath location
 * where the JARs can be shared by both the JSPWiki webapp and the container. For example,
 * Tomcat 5 provides the directory <code><var>$CATALINA_HOME</var>/common/lib</code>
 * for storage of shared JARs; move <code>mail.jar</code> and <code>activation.jar</code>
 * there instead of keeping them in <code>/WEB-INF/lib</code>.</p>
 * <strong>JavaMail configuration</strong>
 * <p>Regardless of the method used for supplying JavaMail sessions (JNDI container-managed
 * or via <code>jspwiki.properties</code>, JavaMail needs certain properties
 * set in order to work correctly. Configurable properties are these:</p>
 * <table border="1">
 *   <tr>
 *   <thead>
 *     <th>Property</th>
 *     <th>Default</th>
 *     <th>Definition</th>
 *   <thead>
 *   </tr>
 *   <tr>
 *     <td><code>jspwiki.mail.jndiname</code></td>
 *     <td><code>mail/Session</code></td>
 *     <td>The JNDI name of the JavaMail session factory</td>
 *   </tr>
 *   <tr>
 *     <td><code>mail.smtp.host</code></td>
 *     <td><code>127.0.0.1</code></td>
 *     <td>The SMTP mail server from which messages will be sent.</td>
 *   </tr>
 *   <tr>
 *     <td><code>mail.smtp.port</code></td>
 *     <td><code>25</code></td>
 *     <td>The port number of the SMTP mail service.</td>
 *   </tr>
 *   <tr>
 *     <td><code>mail.smtp.account</code></td>
 *     <td>(not set)</td>
 *     <td>The user name of the sender. If this value is supplied, the JavaMail
 *     session will attempt to authenticate to the mail server before sending
 *     the message. If not supplied, JavaMail will attempt to send the message
 *     without authenticating (i.e., it will use the server as an open relay).
 *     In real-world scenarios, you should set this value.</td>
 *   </tr>
 *   <tr>
 *     <td><code>mail.smtp.password</code></td>
 *     <td>(not set)</td>
 *     <td>The password of the sender. In real-world scenarios, you
 *     should set this value.</td>
 *   </tr>
 *   <tr>
 *     <td><code>mail.from</code></td>
 *     <td><code><var>${user.name}</var>@<var>${mail.smtp.host}</var>*</code></td>
 *     <td>The e-mail address of the sender.</td>
 *   </tr>
 *   <tr>
 *     <td><code>mail.smtp.timeout</code></td>
 *     <td><code>5000*</code></td>
 *     <td>Socket I/O timeout value, in milliseconds. The default is 5 seconds.</td>
 *   </tr>
 *   <tr>
 *     <td><code>mail.smtp.connectiontimeout</code></td>
 *     <td><code>5000*</code></td>
 *     <td>Socket connection timeout value, in milliseconds. The default is 5 seconds.</td>
 *   </tr>
 *   <tr>
 *     <td><code>mail.smtp.starttls.enable</code></td>
 *     <td><code>true*</code></td>
 *     <td>If true, enables the use of the STARTTLS command (if
 *     supported by the server) to switch the connection to a
 *     TLS-protected connection before issuing any login commands.
 *     Note that an appropriate trust store must configured so that
 *     the client will trust the server's certificate. By default,
 *     the JRE trust store contains root CAs for most public certificate
 *     authorities.</td>
 *   </tr>
 * </table>
 * <p>*These defaults apply only if the stand-alone Session factory is used
 * (that is, these values are obtained from <code>jspwiki.properties</code>).
 * If using a container-managed JNDI Session factory, the container will
 * likely supply its own default values, and you should probably override
 * them (see the next section).</p>
 * <strong>Container JNDI Session factory configuration</strong>
 * <p>You are strongly encouraged to use a container-managed JNDI factory for
 * JavaMail sessions, rather than configuring JavaMail through <code>jspwiki.properties</code>.
 * To do this, you need to two things: uncomment the <code>&lt;resource-ref&gt;</code> block
 * in <code>/WEB-INF/web.xml</code> that enables container-managed JavaMail, and
 * configure your container's JavaMail resource factory. The <code>web.xml</code>
 * part is easy: just uncomment the section that looks like this:</p>
 * <pre>&lt;resource-ref&gt;
 *   &lt;description>Resource reference to a container-managed JNDI JavaMail factory for sending e-mails.&lt;/description&gt;
 *   &lt;res-ref-name>mail/Session&lt;/res-ref-name&gt;
 *   &lt;res-type>javax.mail.Session&lt;/res-type&gt;
 *   &lt;res-auth>Container&lt;/res-auth&gt;
 * &lt;/resource-ref&gt;</pre>
 * <p>To configure your container's resource factory, follow the directions supplied by
 * your container's documentation. For example, the
 * <a href="http://tomcat.apache.org/tomcat-5.5-doc/jndi-resources-howto.html#JavaMail%20Sessions">Tomcat
 * 5.5 docs</a> state that you need a properly configured <code>&lt;Resource&gt;</code>
 * element inside the JSPWiki webapp's <code>&lt;Context&gt;</code> declaration. Here's an example shows
 * how to do it:</p>
 * <pre>&lt;Context ...&gt;
 * ...
 * &lt;Resource name="mail/Session" auth="Container"
 *           type="javax.mail.Session"
 *           mail.smtp.host="127.0.0.1"/&gt;
 *           mail.smtp.port="25"/&gt;
 *           mail.smtp.account="your-account-name"/&gt;
 *           mail.smtp.password="your-password"/&gt;
 *           mail.from="Snoop Dogg &lt;snoop@dogg.org&gt;"/&gt;
 *           mail.smtp.timeout="5000"/&gt;
 *           mail.smtp.connectiontimeout="5000"/&gt;
 *           mail.smtp.starttls.enable="true"/&gt;
 * ...
 * &lt;/Context&gt;</pre>
 * <p>Note that with Tomcat (and most other application containers) you can also declare the JavaMail
 * JNDI factory as a global resource, shared by all applications, instead of as a local JSPWiki
 * resource as we have done here. For example, the following entry in
 * <code><var>$CATALINA_HOME</var>/conf/server.xml</code> creates a global resource:</p>
 * <pre>&lt;GlobalNamingResources&gt;
 *   &lt;Resource name="mail/Session" auth="Container"
 *             type="javax.mail.Session"
 *             ...
 *             mail.smtp.starttls.enable="true"/&gt;
 * &lt;/GlobalNamingResources&gt;</pre>
 * <p>This approach &#8212; creating a global JNDI resource &#8212; yields somewhat decreased
 * deployment complexity because the JSPWiki webapp no longer needs its own JavaMail resource
 * declaration. However, it is slightly less secure because it means that all other applications
 * can now obtain a JavaMail session if they want to. In many cases, this <em>is</em> what
 * you want.</p>
 * <p>NOTE: Versions of Tomcat 5.5 later than 5.5.17, and up to and including 5.5.23 have a
 * b0rked version of <code><var>$CATALINA_HOME</var>/common/lib/naming-factory.jar</code>
 * that prevents usage of JNDI. To avoid this problem, you should patch your 5.5.23 version
 * of <code>naming-factory.jar</code> with the one from 5.5.17. This is a known issue
 * and the bug report (#40668) is
 * <a href="http://issues.apache.org/bugzilla/show_bug.cgi?id=40668">here</a>.
 *
 */
public final class MailUtil {

    private static final String JAVA_COMP_ENV = "java:comp/env";

    private static final String FALSE = "false";

    private static final String TRUE = "true";

    private static boolean c_useJndi = true;

    private static final String PROP_MAIL_AUTH = "mail.smtp.auth";
    private static final String PROP_MAILS_AUTH = "mail.smtps.auth";

    static final Logger LOG = LoggerFactory.getLogger(MailUtil.class);

    static final String DEFAULT_MAIL_JNDI_NAME       = "mail/Session";

    static final String DEFAULT_MAIL_HOST            = "localhost";

    static final String DEFAULT_MAIL_PORT            = "25";

    static final String DEFAULT_MAIL_TIMEOUT         = "5000";

    static final String DEFAULT_MAIL_CONN_TIMEOUT    = "5000";

    static final String DEFAULT_SENDER               = "jspwiki@localhost";

    static final String PROP_MAIL_JNDI_NAME          = "jspwiki.mail.jndiname";

    static final String MAIL_PROPS                   = "mail.smtp";

    static final String PROP_MAIL_HOST               = "mail.smtp.host";
    static final String PROP_MAILS_HOST              = "mail.smtps.host";

    static final String PROP_MAIL_PORT               = "mail.smtp.port";
    static final String PROP_MAILS_PORT              = "mail.smtps.port";

    static final String PROP_MAIL_ACCOUNT            = "mail.smtp.account";
    static final String PROP_MAILS_ACCOUNT           = "mail.smtps.account";

    static final String PROP_MAIL_PASSWORD           = "mail.smtp.password";
    static final String PROP_MAILS_PASSWORD          = "mail.smtps.password";

    static final String PROP_MAIL_TIMEOUT            = "mail.smtp.timeout";
    static final String PROP_MAILS_TIMEOUT           = "mail.smtps.timeout";

    static final String PROP_MAIL_CONNECTION_TIMEOUT = "mail.smtp.connectiontimeout";
    static final String PROP_MAILS_CONNECTION_TIMEOUT= "mail.smtps.connectiontimeout";

    static final String PROP_MAIL_TRANSPORT          = "smtp";

    static final String PROP_MAIL_SENDER             = "mail.from";

    static final String PROP_MAIL_STARTTLS           = "mail.smtp.starttls.enable";
    static final String PROP_MAILS_STARTTLS          = "mail.smtps.starttls.enable";

    static final String PROP_MAIL_SSL_PROTOCOLS      = "mail.smtp.ssl.protocols";

    private static String c_fromAddress;
    
    /**
     *  Private constructor prevents instantiation.
     */
    private MailUtil()
    {
    }

    /**
     * <p>Sends an e-mail to a specified receiver using a JavaMail Session supplied
     * by a JNDI mail session factory (preferred) or a locally initialized
     * session based on properties in <code>jspwiki.properties</code>.
     * See the top-level JavaDoc for this class for a description of
     * required properties and their default values.</p>
     * <p>The e-mail address used for the <code>to</code> parameter must be in
     * RFC822 format, as described in the JavaDoc for {@link javax.mail.internet.InternetAddress}
     * and more fully at
     * <a href="http://www.freesoft.org/CIE/RFC/822/index.htm">http://www.freesoft.org/CIE/RFC/822/index.htm</a>.
     * In other words, e-mail addresses should look like this:</p>
     * <blockquote><code>Snoop Dog &lt;snoop.dog@shizzle.net&gt;<br/>
     * snoop.dog@shizzle.net</code></blockquote>
     * <p>Note that the first form allows a "friendly" user name to be supplied
     * in addition to the actual e-mail address.</p>
     *
     * @param props the properties that contain mail session properties
     * @param to the receiver
     * @param subject the subject line of the message
     * @param content the contents of the mail message, as plain text
     * @throws AddressException If the address is invalid
     * @throws MessagingException If the message cannot be sent.
     */
    public static void sendMessage(final Properties props, final String to, final String subject, final String content)
        throws AddressException, MessagingException
    {
        final Session session = getMailSession( props );
        setSenderEmailAddress(session, props);

        try {
            // Create and address the message
            final MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(c_fromAddress));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
            msg.setSubject(subject, StandardCharsets.UTF_8.name());
            msg.setText(content, StandardCharsets.UTF_8.name());
            msg.setSentDate(new Date());

            // Send and log it
            Transport.send(msg);
            LOG.info("Sent e-mail to={}, subject=\"{}\", used {} mail session.", to, subject, (c_useJndi ? "JNDI" : "standalone") );
        } catch (final MessagingException e) {
            LOG.error("Error while sending", e);
            throw e;
        }
    }

	public static void sendMultiPartMessage(Properties props, String to, String subject, String plainContent,
											String htmlContent, Map<String, URL> imageUrlsByCid) throws MessagingException{
		Session session = getMailSession( props );
		setSenderEmailAddress(session, props);

		try
		{
			// Create and address the message
			MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(c_fromAddress));
			msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
			msg.setSubject(subject, "UTF-8");
			msg.setSentDate(new Date());

			// most of the code here taken from
			// https://stackoverflow.com/questions/3902455/mail-multipart-alternative-vs-multipart-mixed
			// but slightly easier organized and renamed
			final MimeMultipart rootMp = new MimeMultipart("mixed");
			msg.setContent(rootMp);
			{
				// alternative
				final MimeMultipart alternativeMp = newChild(rootMp, "alternative");
				{
					// Note: MUST RENDER HTML LAST otherwise iPad mail client only renders the last image and no email

					// text
					if (plainContent != null && !plainContent.isEmpty()) {
						final MimeBodyPart textBodyPart = new MimeBodyPart();
						textBodyPart.setText(plainContent, "UTF-8");
						textBodyPart.setHeader("Content-Type", "text/plain; charset=UTF-8");
						alternativeMp.addBodyPart(textBodyPart);
					}

					// html
					final MimeMultipart relatedMp = newChild(alternativeMp,"related");

					final MimeBodyPart htmlBodyPart = new MimeBodyPart();
					htmlBodyPart.setText(htmlContent, "UTF-8");
					htmlBodyPart.setHeader("Content-Type", "text/html; charset=UTF-8");
					relatedMp.addBodyPart(htmlBodyPart);

					// add multi-part for image
					for (Map.Entry<String, URL> entry : imageUrlsByCid.entrySet()) {
						MimeBodyPart part = new MimeBodyPart();
						DataHandler dh = new DataHandler(entry.getValue());
						part.setDataHandler(dh);
						part.addHeader("Content-ID", "<" + entry.getKey() + ">");
						part.addHeader("Content-Type", dh.getContentType());
						part.setDisposition(Part.INLINE);
						relatedMp.addBodyPart(part);
					}
				}

				// attachments
				// TODO.. (~ addAttachments(mpMixed,attachments))
			}

			// Send and log it
			Transport.send(msg);
			if (LOG.isInfoEnabled())
			{
				LOG.info("Sent e-mail to=" + to + ", subject=\"" + subject + "\", used "
						 + (c_useJndi ? "JNDI" : "standalone") + " mail session.");
			}
		}
		catch (MessagingException e)
		{
			LOG.error("Error while sending multipart message", e);
			throw e;
		}
	}
    
    // --------- JavaMail Session Helper methods  --------------------------------

    /**
     * Adds a MimeBodyPart instance to the given parent, which again holds a MimeMultipart child instance as content.
     * This child instance is returned.
     */
    private static MimeMultipart newChild(MimeMultipart parent, String subtype) throws MessagingException {
        final MimeBodyPart mbp = new MimeBodyPart();
        parent.addBodyPart(mbp);

        MimeMultipart child = new MimeMultipart(subtype);
        mbp.setContent(child);
        return child;
    }

    /**
     * Gets the Sender's email address from JNDI Session if available, otherwise
     * from the jspwiki.properties or lastly the default value.
     * @param pSession <code>Session</code>
     * @param pProperties <code>Properties</code>
     */
    static void setSenderEmailAddress( final Session pSession, final Properties pProperties ) {
        if( c_fromAddress == null ) {
            // First, attempt to get the email address from the JNDI Mail Session.
            if( pSession != null && c_useJndi ) {
                c_fromAddress = pSession.getProperty( MailUtil.PROP_MAIL_SENDER );
            }
            // If unsuccessful, get the email address from the properties or default.
            if( c_fromAddress == null ) {
                c_fromAddress = pProperties.getProperty( PROP_MAIL_SENDER, DEFAULT_SENDER ).trim();
                LOG.debug( "Attempt to get the sender's mail address from the JNDI mail session failed, will use \"{}" +
                           "\" (configured via jspwiki.properties or the internal default).", c_fromAddress );
            } else {
                LOG.debug( "Attempt to get the sender's mail address from the JNDI mail session was successful ({}).", c_fromAddress );
            }
        }
    }

    /**
     * Returns the Mail Session from either JNDI or creates a stand-alone.
     * @param props the properties that contain mail session properties
     * @return <code>Session</code>
     */
    private static Session getMailSession(final Properties props)
    {
        Session result = null;
        final String jndiName = props.getProperty(PROP_MAIL_JNDI_NAME, DEFAULT_MAIL_JNDI_NAME).trim();

        if (c_useJndi)
        {
            // Try getting the Session from the JNDI factory first
            LOG.debug("Try getting a mail session via JNDI name \"{}\".", jndiName);
            try {
                result = getJNDIMailSession(jndiName);
            } catch (final NamingException e) {
                // Oops! JNDI factory must not be set up
                c_useJndi = false;
                LOG.info("Unable to get a mail session via JNDI, will use custom settings at least until next startup.");
            }
        }

        // JNDI failed; so, get the Session from the standalone factory
        if (result == null)
        {
            LOG.debug("Getting a standalone mail session configured by jspwiki.properties and/or internal default values.");
            result = getStandaloneMailSession(props);
        }
        return result;
    }

    /**
     * Returns a stand-alone JavaMail Session by looking up the correct mail account, password, host and others from a
     * supplied set of properties. If the JavaMail property {@value #PROP_MAIL_ACCOUNT} is set to a value that is
     * non-<code>null</code> and of non-zero length, the Session will be initialized with an instance of
     * {@link javax.mail.Authenticator}.
     *
     * @param props the properties that contain mail session properties.
     * @return the initialized JavaMail Session.
     *
     * @see <a href="https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html#properties">SMTP Properties</a>
     * for a list of valid <code>mail.smtp</code> / <code>mail.smtps</code> properties, used to create the JavaMail session.
     */
    static Session getStandaloneMailSession( final Properties props ) {
        // Read the JSPWiki settings from the properties
        final String host     = Objects.toString( props.getProperty( PROP_MAIL_HOST, props.getProperty( PROP_MAILS_HOST, DEFAULT_MAIL_HOST ) ) );
        final String port     = Objects.toString( props.getProperty( PROP_MAIL_PORT, props.getProperty( PROP_MAILS_PORT, DEFAULT_MAIL_PORT ) ) );
        final String account  = Objects.toString( props.getProperty( PROP_MAIL_ACCOUNT ), props.getProperty( PROP_MAILS_ACCOUNT ) );
        final String password = Objects.toString( props.getProperty( PROP_MAIL_PASSWORD ),props.getProperty( PROP_MAILS_PASSWORD ) );
        final String timeout  = Objects.toString( props.getProperty( PROP_MAIL_TIMEOUT, props.getProperty( PROP_MAILS_TIMEOUT, DEFAULT_MAIL_TIMEOUT ) ) );
        final String conntimeout = Objects.toString( props.getProperty( PROP_MAIL_CONNECTION_TIMEOUT, props.getProperty( PROP_MAILS_CONNECTION_TIMEOUT, DEFAULT_MAIL_CONN_TIMEOUT ) ) );
        final String starttls = Boolean.toString( TextUtil.getBooleanProperty( props, PROP_MAIL_STARTTLS, TextUtil.getBooleanProperty( props, PROP_MAILS_STARTTLS, true ) ) );
        final boolean useAuthentication = account != null && !account.isEmpty();

        // Set JavaMail properties
        final Properties mailProps = new Properties();
        final Set< String > keys = props.stringPropertyNames();
        for( final String key : keys) {
            if( key.startsWith( MAIL_PROPS ) ) {
                mailProps.setProperty( key, props.getProperty( key ) );
            }
        }
		mailProps.put(PROP_MAIL_SSL_PROTOCOLS, "TLSv1.2");  // required for JavaMail 1.4.7, not required for >= 1.6.2

		// Add SMTP authentication if required
        final Session session;
        if ( useAuthentication ) {
            mailProps.put( PROP_MAIL_AUTH, TRUE );
            mailProps.put( PROP_MAILS_AUTH, TRUE ); // just in case, cover mail.stmps config as well
            final SmtpAuthenticator auth = new SmtpAuthenticator( account, password );

            session = Session.getInstance( mailProps, auth );
        } else {
            session = Session.getInstance( mailProps );
        }

        final String mailServer = host + ":" + port + ", account=" + account + ", password not displayed, timeout=" +
                                  timeout + ", connectiontimeout=" + conntimeout + ", starttls.enable=" + starttls +
                                  ", use authentication=" + ( useAuthentication ? TRUE : FALSE );
        LOG.debug( "JavaMail session obtained from standalone mail factory: {}", mailServer );
        return session;
    }

    /**
     * Returns a JavaMail Session instance from a JNDI container-managed factory.
     * @param jndiName the JNDI name for the resource. If <code>null</code>, the default value
     * of <code>mail/Session</code> will be used
     * @return the initialized JavaMail Session
     * @throws NamingException if the Session cannot be obtained; for example, if the factory is not configured
     */
    static Session getJNDIMailSession( final String jndiName ) throws NamingException {
        final Session session;
        try {
            final Context initCtx = new InitialContext();
            final Context ctx = ( Context ) initCtx.lookup( JAVA_COMP_ENV );
            session = ( Session )ctx.lookup( jndiName );
        } catch( final NamingException e ) {
            LOG.warn( "JNDI mail session initialization error: {}", e.getMessage() );
            throw e;
        }
        LOG.debug( "mail session obtained from JNDI mail factory: {}", jndiName );
        return session;
    }

    /**
     * Simple {@link javax.mail.Authenticator} subclass that authenticates a user to
     * an SMTP server.
     */
    protected static class SmtpAuthenticator extends Authenticator {

        private static final String BLANK = "";
        private final String m_pass;
        private final String m_login;

        /**
         * Constructs a new SmtpAuthenticator with a supplied username and password.
         *
         * @param login the username
         * @param pass the password
         */
        public SmtpAuthenticator( final String login, final String pass ) {
            super();
            m_login =   login == null ? BLANK : login;
            m_pass =     pass == null ? BLANK : pass;
        }

        /**
         * Returns the password used to authenticate to the SMTP server.
         *
         * @return <code>PasswordAuthentication</code>.
         */
        @Override
        public PasswordAuthentication getPasswordAuthentication() {
            if( BLANK.equals( m_pass ) ) {
                return null;
            }
            return new PasswordAuthentication( m_login, m_pass );
        }

    }

}
