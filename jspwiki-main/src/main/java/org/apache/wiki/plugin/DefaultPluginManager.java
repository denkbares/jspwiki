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

package org.apache.wiki.plugin;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.MatchResult;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.apache.wiki.InternalWikiException;
import org.apache.wiki.ajax.WikiAjaxDispatcherServlet;
import org.apache.wiki.ajax.WikiAjaxServlet;
import org.apache.wiki.api.core.Context;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.exceptions.PluginException;
import org.apache.wiki.api.plugin.InitializablePlugin;
import org.apache.wiki.api.plugin.Plugin;
import org.apache.wiki.modules.BaseModuleManager;
import org.apache.wiki.modules.WikiModuleInfo;
import org.apache.wiki.preferences.Preferences;
import org.apache.wiki.util.ClassUtil;
import org.apache.wiki.util.FileUtil;
import org.apache.wiki.util.TextUtil;
import org.apache.wiki.util.XHTML;
import org.apache.wiki.util.XhtmlUtil;
import org.apache.wiki.util.XmlUtil;
import org.jdom2.Element;

import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

/**
 *  Manages plugin classes.  There exists a single instance of PluginManager
 *  per each instance of Engine, that is, each JSPWiki instance.
 *  <P>
 *  A plugin is defined to have three parts:
 *  <OL>
 *    <li>The plugin class
 *    <li>The plugin parameters
 *    <li>The plugin body
 *  </ol>
 *
 *  For example, in the following line of code:
 *  <pre>
 *  [{INSERT org.apache.wiki.plugin.FunnyPlugin  foo='bar'
 *  blob='goo'
 *
 *  abcdefghijklmnopqrstuvw
 *  01234567890}]
 *  </pre>
 *
 *  The plugin class is "org.apache.wiki.plugin.FunnyPlugin", the
 *  parameters are "foo" and "blob" (having values "bar" and "goo",
 *  respectively), and the plugin body is then
 *  "abcdefghijklmnopqrstuvw\n01234567890".   The plugin body is
 *  accessible via a special parameter called "_body".
 *  <p>
 *  If the parameter "debug" is set to "true" for the plugin,
 *  JSPWiki will output debugging information directly to the page if there
 *  is an exception.
 *  <P>
 *  The class name can be shortened, and marked without the package.
 *  For example, "FunnyPlugin" would be expanded to
 *  "org.apache.wiki.plugin.FunnyPlugin" automatically.  It is also
 *  possible to define other packages, by setting the
 *  "jspwiki.plugin.searchPath" property.  See the included
 *  jspwiki.properties file for examples.
 *  <P>
 *  Even though the nominal way of writing the plugin is
 *  <pre>
 *  [{INSERT pluginclass WHERE param1=value1...}],
 *  </pre>
 *  it is possible to shorten this quite a lot, by skipping the
 *  INSERT, and WHERE words, and dropping the package name.  For
 *  example:
 *
 *  <pre>
 *  [{INSERT org.apache.wiki.plugin.Counter WHERE name='foo'}]
 *  </pre>
 *
 *  is the same as
 *  <pre>
 *  [{Counter name='foo'}]
 *  </pre>
 *  <h3>Plugin property files</h3>
 *  <p>
 *  Since 2.3.25 you can also define a generic plugin XML properties file per
 *  each JAR file.
 *  <pre>
 *  <modules>
 *   <plugin class="org.apache.wiki.foo.TestPlugin">
 *       <author>Janne Jalkanen</author>
 *       <script>foo.js</script>
 *       <stylesheet>foo.css</stylesheet>
 *       <alias>code</alias>
 *   </plugin>
 *   <plugin class="org.apache.wiki.foo.TestPlugin2">
 *       <author>Janne Jalkanen</author>
 *   </plugin>
 *   </modules>
 *  </pre>
 *  <h3>Plugin lifecycle</h3>
 *
 *  <p>Plugin can implement multiple interfaces to let JSPWiki know at which stages they should
 *  be invoked:
 *  <ul>
 *  <li>InitializablePlugin: If your plugin implements this interface, the initialize()-method is
 *      called once for this class
 *      before any actual execute() methods are called.  You should use the initialize() for e.g.
 *      precalculating things.  But notice that this method is really called only once during the
 *      entire Engine lifetime.  The InitializablePlugin is available from 2.5.30 onwards.</li>
 *  <li>ParserStagePlugin: If you implement this interface, the executeParse() method is called
 *      when JSPWiki is forming the DOM tree.  You will receive an incomplete DOM tree, as well
 *      as the regular parameters.  However, since JSPWiki caches the DOM tree to speed up later
 *      places, which means that whatever this method returns would be irrelevant.  You can do some DOM
 *      tree manipulation, though.  The ParserStagePlugin is available from 2.5.30 onwards.</li>
 *  <li>Plugin: The regular kind of plugin which is executed at every rendering stage.  Each
 *      new page load is guaranteed to invoke the plugin, unlike with the ParserStagePlugins.</li>
 *  </ul>
 *
 *  @since 1.6.1
 */
public class DefaultPluginManager extends BaseModuleManager implements PluginManager {

    private static final String PLUGIN_INSERT_PATTERN = "\\{?(INSERT)?\\s*([\\w\\._]+)[ \\t]*(WHERE)?[ \\t]*";
    private static final Logger LOG = LoggerFactory.getLogger( DefaultPluginManager.class );
    private static final String DEFAULT_FORMS_PACKAGE = "org.apache.wiki.forms";

    private final ArrayList< String > m_searchPath = new ArrayList<>();
    private final ArrayList< String > m_externalJars = new ArrayList<>();
    private final Pattern m_pluginPattern;
    private boolean m_pluginsEnabled = true;

    /** Keeps a list of all known plugin classes. */
    private final Map< String, WikiPluginInfo > m_pluginClassMap = new HashMap<>();

    /**
     *  Create a new PluginManager.
     *
     *  @param engine Engine which owns this manager.
     *  @param props Contents of a "jspwiki.properties" file.
     */
    public DefaultPluginManager( final Engine engine, final Properties props ) {
        super( engine );
        final String packageNames = props.getProperty( Engine.PROP_SEARCHPATH );
        if ( packageNames != null ) {
            final StringTokenizer tok = new StringTokenizer( packageNames, "," );
            while( tok.hasMoreTokens() ) {
                m_searchPath.add( tok.nextToken().trim() );
            }
        }

        final String externalJars = props.getProperty( PROP_EXTERNALJARS );
        if( externalJars != null ) {
            final StringTokenizer tok = new StringTokenizer( externalJars, "," );
            while( tok.hasMoreTokens() ) {
                m_externalJars.add( tok.nextToken().trim() );
            }
        }

        registerPlugins();

        //  The default packages are always added.
        m_searchPath.add( DEFAULT_PACKAGE );
        m_searchPath.add( DEFAULT_FORMS_PACKAGE );

        final PatternCompiler compiler = new Perl5Compiler();
        try {
            m_pluginPattern = compiler.compile( PLUGIN_INSERT_PATTERN );
        } catch( final MalformedPatternException e ) {
			LOG.error( "Internal error: someone messed with pluginmanager patterns.", e );
            throw new InternalWikiException( "PluginManager patterns are broken" , e );
        }
    }

    /** {@inheritDoc} */
    @Override
    public void enablePlugins( final boolean enabled ) {
        m_pluginsEnabled = enabled;
    }

    /** {@inheritDoc} */
    @Override
    public boolean pluginsEnabled() {
        return m_pluginsEnabled;
    }

    /** {@inheritDoc} */
    @Override
    public Pattern getPluginPattern() {
		return m_pluginPattern;
	}

	/**
     *  Attempts to locate a plugin class from the class path set in the property file.
     *
     *  @param classname Either a fully fledged class name, or just the name of the file (that is, "org.apache.wiki.plugin.Counter" or just plain "Counter").
     *  @return A found class.
     *  @throws ClassNotFoundException if no such class exists.
     */
    private Class< ? > findPluginClass( final String classname ) throws ClassNotFoundException {
        return ClassUtil.findClass( m_searchPath, m_externalJars, classname );
    }

    /** Outputs an HTML-formatted version of a stack trace. */
    private String stackTrace( final Map<String,String> params, final Throwable t ) {
        final Element div = XhtmlUtil.element( XHTML.div, "Plugin execution failed, stack trace follows:" );
        div.setAttribute( XHTML.ATTR_class, "debug" );

        final StringWriter out = new StringWriter();
        t.printStackTrace( new PrintWriter( out ) );
        div.addContent( XhtmlUtil.element( XHTML.pre, out.toString() ) );
        div.addContent( XhtmlUtil.element( XHTML.b, "Parameters to the plugin" ) );

        final Element list = XhtmlUtil.element( XHTML.ul );
        for( final Map.Entry< String, String > e : params.entrySet() ) {
            final String key = e.getKey();
            list.addContent( XhtmlUtil.element( XHTML.li, key + "'='" + e.getValue() ) );
        }
        div.addContent( list );
        return XhtmlUtil.serialize( div );
    }

    /** {@inheritDoc} */
    @Override
    public String execute( final Context context, final String classname, final Map< String, String > params ) throws PluginException {
        if( !m_pluginsEnabled ) {
            return "";
        }

        final ResourceBundle rb = Preferences.getBundle( context, Plugin.CORE_PLUGINS_RESOURCEBUNDLE );
        final boolean debug = TextUtil.isPositive( params.get( PARAM_DEBUG ) );
        try {
            //   Create...
            final Plugin plugin = newWikiPlugin( classname, rb );
            if( plugin == null ) {
                return "Plugin '" + classname + "' not compatible with this version of JSPWiki";
            }

            //  ...and launch.
            try {
                return plugin.execute( context, params );
            } catch( final PluginException e ) {
                if( debug ) {
                    return stackTrace( params, e );
                }

                // Just pass this exception onward.
                throw ( PluginException )e.fillInStackTrace();
            } catch( final Throwable t ) {
                // But all others get captured here.
                LOG.info( "Plugin failed while executing:", t );
                if( debug ) {
                    return stackTrace( params, t );
                }

                throw new PluginException( rb.getString( "plugin.error.failed" ), t );
            }

        } catch( final ClassCastException e ) {
            throw new PluginException( MessageFormat.format( rb.getString( "plugin.error.notawikiplugin" ), classname ), e );
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map< String, String > parseArgs( final String argstring ) throws IOException {
        final Map< String, String > arglist = new HashMap<>();
        //  Protection against funny users.
        if( argstring == null ) {
            return arglist;
        }

        arglist.put( PARAM_CMDLINE, argstring );
        final StringReader in = new StringReader( argstring );
        final StreamTokenizer tok = new StreamTokenizer( in );
        tok.eolIsSignificant( true );

        String param = null;
        String value;
        boolean potentialEmptyLine = false;
        boolean quit = false;
        while( !quit ) {
            final String s;
            final int type = tok.nextToken();

            switch( type ) {
            case StreamTokenizer.TT_EOF:
                quit = true;
                s = null;
                break;

            case StreamTokenizer.TT_WORD:
                s = tok.sval;
                potentialEmptyLine = false;
                break;

            case StreamTokenizer.TT_EOL:
                quit = potentialEmptyLine;
                potentialEmptyLine = true;
                s = null;
                break;

            case StreamTokenizer.TT_NUMBER:
                s = Integer.toString( ( int )tok.nval );
                potentialEmptyLine = false;
                break;

            case '\'':
                s = tok.sval;
                break;

            default:
                s = null;
            }

            //  Assume that alternate words on the line are parameter and value, respectively.
            if( s != null ) {
                if( param == null ) {
                    param = s;
                } else {
                    value = s;
                    arglist.put( param, value );
                    param = null;
                }
            }
        }

        //  Now, we'll check the body.
        if( potentialEmptyLine ) {
            final StringWriter out = new StringWriter();
            FileUtil.copyContents( in, out );
            final String bodyContent = out.toString();
            if( bodyContent != null ) {
                arglist.put( PARAM_BODY, bodyContent );
            }
        }

        return arglist;
    }

    /** {@inheritDoc} */
    @Override
    public String execute( final Context context, final String commandline ) throws PluginException {
        if( !m_pluginsEnabled ) {
            return "";
        }

        final ResourceBundle rb = Preferences.getBundle( context, Plugin.CORE_PLUGINS_RESOURCEBUNDLE );
        final PatternMatcher matcher = new Perl5Matcher();

        try {
            if( matcher.contains( commandline, m_pluginPattern ) ) {
                final MatchResult res = matcher.getMatch();
                final String plugin = res.group( 2 );
                final int endIndex = commandline.length() - ( commandline.charAt( commandline.length() - 1 ) == '}' ? 1 : 0 );
                final String args = commandline.substring( res.endOffset( 0 ), endIndex );
                final Map< String, String > arglist = parseArgs( args );
                return execute( context, plugin, arglist );
            }
        } catch( final NoSuchElementException e ) {
            final String msg =  "Missing parameter in plugin definition: " + commandline;
            LOG.warn( msg, e );
            throw new PluginException( MessageFormat.format( rb.getString( "plugin.error.missingparameter" ), commandline ) );
        } catch( final IOException e ) {
            final String msg = "Zyrf.  Problems with parsing arguments: " + commandline;
            LOG.warn( msg, e );
            throw new PluginException( MessageFormat.format( rb.getString( "plugin.error.parsingarguments" ), commandline ) );
        }

        // FIXME: We could either return an empty string "", or the original line.  If we want unsuccessful requests
        // to be invisible, then we should return an empty string.
        return commandline;
    }

    /** Register a plugin. */
    private void registerPlugin( final WikiPluginInfo pluginClass ) {
        String name;

        // Register the plugin with the className without the package-part
        name = pluginClass.getName();
        if( name != null ) {
            LOG.debug( "Registering plugin [name]: " + name );
            m_pluginClassMap.put( name, pluginClass );
        }

        // Register the plugin with a short convenient name.
        name = pluginClass.getAlias();
        if( name != null ) {
            LOG.debug( "Registering plugin [shortName]: " + name );
            m_pluginClassMap.put( name, pluginClass );
        }

        // Register the plugin with the className with the package-part
        name = pluginClass.getClassName();
        if( name != null ) {
            LOG.debug( "Registering plugin [className]: " + name );
            m_pluginClassMap.put( name, pluginClass );
        }

        pluginClass.initializePlugin( pluginClass, m_engine, m_searchPath, m_externalJars );
    }

    private void registerPlugins() {
        // Register all plugins which have created a resource containing its properties.
        LOG.info( "Registering plugins" );
        final List< Element > plugins = XmlUtil.parse( PLUGIN_RESOURCE_LOCATION, "/modules/plugin" );

        // Get all resources of all plugins.
        for( final Element pluginEl : plugins ) {
            final String className = pluginEl.getAttributeValue( "class" );
            final WikiPluginInfo pluginInfo = WikiPluginInfo.newInstance( className, pluginEl ,m_searchPath, m_externalJars );
            if( pluginInfo != null ) {
                registerPlugin( pluginInfo );
            }
        }
    }

    /**
     *  Contains information about a bunch of plugins.
     */
    // FIXME: This class needs a better interface to return all sorts of possible information from the plugin XML.  In fact, it probably
    //  should have some sort of a superclass system.
    public static final class WikiPluginInfo extends WikiModuleInfo {

        private String    m_className;
        private String    m_alias;
        private String    m_ajaxAlias;
        private Class< Plugin >  m_clazz;

        private boolean m_initialized;

        /**
         *  Creates a new plugin info object which can be used to access a plugin.
         *
         *  @param className Either a fully qualified class name, or a "short" name which is then checked against the internal list of plugin packages.
         *  @param el A JDOM Element containing the information about this class.
         *  @param searchPath A List of Strings, containing different package names.
         *  @param externalJars the list of external jars to search
         *  @return A WikiPluginInfo object.
         */
        static WikiPluginInfo newInstance( final String className, final Element el, final List<String> searchPath, final List<String> externalJars ) {
            if( className == null || className.isEmpty() ) {
                return null;
            }

            final WikiPluginInfo info = new WikiPluginInfo( className );
            info.initializeFromXML( el );
            return info;
        }

        /**
         *  Initializes a plugin, if it has not yet been initialized. If the plugin extends {@link HttpServlet} it will automatically
         *  register it as AJAX using {@link WikiAjaxDispatcherServlet#registerServlet(String, WikiAjaxServlet)}.
         *
         *  @param engine The Engine
         *  @param searchPath A List of Strings, containing different package names.
         *  @param externalJars the list of external jars to search
         */
        void initializePlugin( final WikiPluginInfo info, final Engine engine , final List<String> searchPath, final List<String> externalJars) {
            if( !m_initialized ) {
                // This makes sure we only try once per class, even if init fails.
                m_initialized = true;

                try {
                    final Plugin p = newPluginInstance(searchPath, externalJars);
                    if( p instanceof InitializablePlugin ) {
                        ( ( InitializablePlugin )p ).initialize( engine );
                    }
                    if( p instanceof WikiAjaxServlet ) {
                    	WikiAjaxDispatcherServlet.registerServlet( (WikiAjaxServlet) p );
                    	final String ajaxAlias = info.getAjaxAlias();
                    	if (StringUtils.isNotBlank(ajaxAlias)) {
                    		WikiAjaxDispatcherServlet.registerServlet( info.getAjaxAlias(), (WikiAjaxServlet) p );
                    	}
                    }
                } catch( final Exception e ) {
                    LOG.info( "Cannot initialize plugin " + m_className, e );
                }
            }
        }

        /**
         *  {@inheritDoc}
         */
        @Override
        protected void initializeFromXML( final Element el ) {
            super.initializeFromXML( el );
            m_alias = el.getChildText( "alias" );
            m_ajaxAlias = el.getChildText( "ajaxAlias" );
        }

        /**
         *  Create a new WikiPluginInfo based on the Class information.
         *
         *  @param clazz The class to check
         *  @return A WikiPluginInfo instance
         */
        static WikiPluginInfo newInstance( final Class< ? > clazz ) {
        	return new WikiPluginInfo( clazz.getName() );
        }

        private WikiPluginInfo( final String className ) {
            super( className );
            setClassName( className );
        }

        private void setClassName( final String fullClassName ) {
            m_name = ClassUtils.getShortClassName( fullClassName );
            m_className = fullClassName;
        }

        /**
         *  Returns the full class name of this object.
         *  @return The full class name of the object.
         */
        public String getClassName() {
            return m_className;
        }

        /**
         *  Returns the alias name for this object.
         *  @return An alias name for the plugin.
         */
        public String getAlias() {
            return m_alias;
        }

        /**
         *  Returns the ajax alias name for this object.
         *  @return An ajax alias name for the plugin.
         */
        public String getAjaxAlias() {
            return m_ajaxAlias;
        }

        /**
         *  Creates a new plugin instance.
         *
         *  @param searchPath A List of Strings, containing different package names.
         *  @param externalJars the list of external jars to search
         *  @return A new plugin.
         *  @throws ClassNotFoundException If the class declared was not found.
         *  @throws InstantiationException If the class cannot be instantiated-
         *  @throws IllegalAccessException If the class cannot be accessed.
         */

        public Plugin newPluginInstance( final List< String > searchPath, final List< String > externalJars) throws ReflectiveOperationException {
            if( m_clazz == null ) {
                m_clazz = ClassUtil.findClass( searchPath, externalJars ,m_className );
            }

            return ClassUtil.buildInstance( m_clazz );
        }

        /**
         *  Returns a text for IncludeResources.
         *
         *  @param type Either "script" or "stylesheet"
         *  @return Text, or an empty string, if there is nothing to be included.
         */
        public String getIncludeText( final String type ) {
            try {
                if( "script".equals( type ) ) {
                    return getScriptText();
                } else if( "stylesheet".equals( type ) ) {
                    return getStylesheetText();
                }
            } catch( final Exception ex ) {
                // We want to fail gracefully here
                return ex.getMessage();
            }

            return null;
        }

        private String getScriptText() throws IOException {
            if( m_scriptText != null ) {
                return m_scriptText;
            }

            if( m_scriptLocation == null ) {
                return "";
            }

            try {
                m_scriptText = getTextResource(m_scriptLocation);
            } catch( final IOException ex ) {
                // Only throw this exception once!
                m_scriptText = "";
                throw ex;
            }

            return m_scriptText;
        }

        private String getStylesheetText() throws IOException {
            if( m_stylesheetText != null ) {
                return m_stylesheetText;
            }

            if( m_stylesheetLocation == null ) {
                return "";
            }

            try {
                m_stylesheetText = getTextResource(m_stylesheetLocation);
            } catch( final IOException ex ) {
                // Only throw this exception once!
                m_stylesheetText = "";
                throw ex;
            }

            return m_stylesheetText;
        }

        /**
         *  Returns a string suitable for debugging.  Don't assume that the format would stay the same.
         *
         *  @return Something human-readable
         */
        @Override
        public String toString() {
            return "Plugin :[name=" + m_name + "][className=" + m_className + "]";
        }

    } // WikiPluginClass

    /**
     *  {@inheritDoc}
     */
    @Override
    public Collection< WikiModuleInfo > modules() {
        return modules( m_pluginClassMap.values().iterator() );
    }

    /**
     *  {@inheritDoc}
     */
    @Override
    public WikiPluginInfo getModuleInfo( final String moduleName) {
        return m_pluginClassMap.get(moduleName);
    }

    /**
     * Creates a {@link Plugin}.
     *
     * @param pluginName plugin's classname
     * @param rb {@link ResourceBundle} with i18ned text for exceptions.
     * @return a {@link Plugin}.
     * @throws PluginException if there is a problem building the {@link Plugin}.
     */
    @Override
    public Plugin newWikiPlugin( final String pluginName, final ResourceBundle rb ) throws PluginException {
        Plugin plugin = null;
        WikiPluginInfo pluginInfo = m_pluginClassMap.get( pluginName );
        try {
            if( pluginInfo == null ) {
                pluginInfo = WikiPluginInfo.newInstance( findPluginClass( pluginName ) );
                registerPlugin( pluginInfo );
            }

            if( !checkCompatibility( pluginInfo ) ) {
                final String msg = "Plugin '" + pluginInfo.getName() + "' not compatible with this version of JSPWiki";
                LOG.info( msg );
            } else {
                plugin = pluginInfo.newPluginInstance(m_searchPath, m_externalJars);
            }
        } catch( final ClassNotFoundException e ) {
            throw new PluginException( MessageFormat.format( rb.getString( "plugin.error.couldnotfind" ), pluginName ), e );
        } catch( final InstantiationException e ) {
            throw new PluginException( MessageFormat.format( rb.getString( "plugin.error.cannotinstantiate" ), pluginName ), e );
        } catch( final IllegalAccessException e ) {
            throw new PluginException( MessageFormat.format( rb.getString( "plugin.error.notallowed" ), pluginName ), e );
        } catch( final Exception e ) {
            throw new PluginException( MessageFormat.format( rb.getString( "plugin.error.instantationfailed" ), pluginName ), e );
        }
        return plugin;
    }

}
