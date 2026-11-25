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

import java.util.Locale;
import org.apache.wiki.api.core.Attachment;
import org.apache.wiki.api.core.Context;
import org.apache.wiki.api.core.ContextEnum;
import org.apache.wiki.api.core.Engine;
import org.apache.wiki.api.exceptions.PluginException;
import org.apache.wiki.api.exceptions.ProviderException;
import org.apache.wiki.api.plugin.Plugin;
import org.apache.wiki.attachment.AttachmentManager;
import org.apache.wiki.parser.MarkupParser;
import org.apache.wiki.util.TextUtil;

import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;


/**
 *  Provides an image plugin for better control than is possible with a simple image inclusion.
 *  <br> Most parameters are equivalents of the html image attributes.
 *
 *  <p>Parameters : </p>
 *  <ul>
 *  <li><b>src</b> - the source (a URL) of the image (required parameter)</li>
 *  <li><b>align</b> - the alignment of the image</li>
 *  <li><b>height</b> - the height of the image</li>
 *  <li><b>width</b> - the width of the image</li>
 *  <li><b>alt</b> - alternate text</li>
 *  <li><b>caption</b> - the caption for the image</li>
 *  <li><b>link</b> - the hyperlink for the image</li>
 *  <li><b>target</b> - the target (frame) to be used for opening the image</li>
 *  <li><b>style</b> - the style attribute of the image</li>
 *  <li><b>class</b> - the associated class for the image</li>
 *  <li><b>border</b> - the border for the image</li>
 *  <li><b>title</b> - the title for the image, can be presented as a tooltip to the user</li>
 *  </ul>
 *
 *  @since 2.1.4.
 */
// FIXME: It is not yet possible to do wiki internal links.  In order to do this cleanly, a TranslatorReader revamp is needed.
public class Image implements Plugin {

    /** The parameter name for setting the src.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_SRC      = "src";
    /** The parameter name for setting the align parameter.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_ALIGN    = "align";
    /** The parameter name for setting the height.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_HEIGHT   = "height";
    /** The parameter name for setting the width.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_WIDTH    = "width";
    /** The parameter name for setting the alt.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_ALT      = "alt";
    /** The parameter name for setting the caption.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_CAPTION  = "caption";
    /** The parameter name for setting the link.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_LINK     = "link";
    /** The parameter name for setting the target.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_TARGET   = "target";
    /** The parameter name for setting the style.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_STYLE    = "style";
    /** The parameter name for setting the class.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_CLASS    = "class";
    /** The parameter name for setting the border.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_BORDER   = "border";
    /** The parameter name for setting the title.  Value is <tt>{@value}</tt>. */
    public static final String PARAM_TITLE    = "title";

    /**
     *  This method is used to clean away things like quotation marks which
     *  a malicious user could use to stop processing and insert javascript.
     */
    private static String getCleanParameter( final Map< String, String > params, final String paramId ) {
        return TextUtil.replaceEntities( params.get( paramId ) );
    }

    /** Valid values for the width, height and border parameters, e.g. <tt>200</tt>, <tt>200px</tt> or <tt>50%</tt>. */
    private static final Pattern SIZE_PATTERN = Pattern.compile( "\\d{1,5}(\\.\\d{1,3})?(px|%)?" );

    /** Valid values for the align parameter (it ends up as <tt>float</tt> of the plugin's table). */
    private static final Set< String > VALID_ALIGN = Set.of( "left", "right", "center", "none" );

    /** A single css class name. */
    private static final Pattern CSS_CLASS_PATTERN = Pattern.compile( "-?[_a-zA-Z][_a-zA-Z0-9-]*" );

    /** A frame name usable as link target (besides the reserved <tt>_blank</tt>, <tt>_self</tt>, ...). */
    private static final Pattern TARGET_PATTERN = Pattern.compile( "[a-zA-Z][a-zA-Z0-9_-]*" );

    /**
     *  CSS properties allowed in the style parameter. Everything that could take the image out of the page flow
     *  (position, z-index, transform, ...) or load external resources is not listed, see JSPWIKI-1297 (CVE-2026-78110).
     */
    private static final Set< String > ALLOWED_STYLE_PROPERTIES = Set.of(
            "width", "height", "max-width", "max-height", "min-width", "min-height",
            "margin", "margin-top", "margin-right", "margin-bottom", "margin-left",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
            "border", "border-width", "border-style", "border-color", "border-radius",
            "border-top", "border-right", "border-bottom", "border-left",
            "float", "clear", "display", "vertical-align", "text-align",
            "color", "background-color", "opacity", "box-shadow", "object-fit",
            "font-size", "font-style", "font-weight", "font-family" );

    /** Allowed characters inside a css value: no quotes, backslashes, comments, functions with url, etc. */
    private static final Pattern STYLE_VALUE_PATTERN = Pattern.compile( "[a-zA-Z0-9#%.,()\\s-]+" );

    /** Negative lengths (e.g. negative margins) could be used to place the image over other content. */
    private static final Pattern NEGATIVE_NUMBER = Pattern.compile( "(^|[\\s(,])-\\s*\\.?\\d" );

    /**
     *  Returns the given style restricted to a set of harmless css declarations, or <tt>null</tt> if none remains.
     *  Declarations with unknown properties or suspicious values are dropped (JSPWIKI-1297, CVE-2026-78110).
     */
    static String sanitizeStyle( final String style ) {
        if( style == null ) {
            return null;
        }
        final StringJoiner result = new StringJoiner( "; " );
        for( final String declaration : style.split( ";" ) ) {
            final int colon = declaration.indexOf( ':' );
            if( colon < 0 ) {
                continue;
            }
            final String property = declaration.substring( 0, colon ).trim().toLowerCase( Locale.ROOT );
            final String value = declaration.substring( colon + 1 ).trim();
            final String lowerValue = value.toLowerCase( Locale.ROOT );
            if( ALLOWED_STYLE_PROPERTIES.contains( property )
                && !value.isEmpty()
                && STYLE_VALUE_PATTERN.matcher( value ).matches()
                && !lowerValue.contains( "url" )
                && !lowerValue.contains( "expression" )
                && !lowerValue.contains( "var(" )
                && !NEGATIVE_NUMBER.matcher( value ).find() ) {
                result.add( property + ": " + value );
            }
        }
        return result.length() == 0 ? null : result + ";";
    }

    /** Returns only the syntactically valid class names of the given class list, or <tt>null</tt> if none remains. */
    static String sanitizeCssClass( final String cssClass ) {
        if( cssClass == null ) {
            return null;
        }
        final StringJoiner result = new StringJoiner( " " );
        for( final String name : cssClass.trim().split( "\\s+" ) ) {
            if( CSS_CLASS_PATTERN.matcher( name ).matches() ) {
                result.add( name );
            }
        }
        return result.length() == 0 ? null : result.toString();
    }

    /** Returns the given size if it is a plain (positive) number with an optional <tt>px</tt> or <tt>%</tt> unit, otherwise <tt>null</tt>. */
    static String sanitizeSize( final String size ) {
        if( size == null ) {
            return null;
        }
        final String trimmed = size.trim();
        return SIZE_PATTERN.matcher( trimmed ).matches() ? trimmed : null;
    }

    /** Returns the given alignment in lower case if it is one of left, right, center or none, otherwise <tt>null</tt>. */
    static String sanitizeAlign( final String align ) {
        if( align == null ) {
            return null;
        }
        final String lower = align.trim().toLowerCase( Locale.ROOT );
        return VALID_ALIGN.contains( lower ) ? lower : null;
    }
    
    @Override
    public String getDisplayName(Locale locale) {
        final ResourceBundle rb = ResourceBundle.getBundle(PluginManager.PLUGIN_I18N_RESOURCE, locale);
        return rb.getString(this.getClass().getSimpleName());
    }
    
    @Override
    public String  getSnipExample() {
        return  "Image src='{image.jpg}'";
    }
    
    private boolean needsSanitization(String link) {
        // browsers ignore whitespace and control characters in the scheme, e.g. "java\tscript:"
        String testVal = link.toLowerCase().replaceAll("[\\s\\p{Cntrl}]+", "").trim();
        if (testVal.startsWith("data")
                || testVal.startsWith("javascript")
                || testVal.startsWith("vbscript")) {
            return true;
        }
        return false;
    }

    /**
     *  {@inheritDoc}
     */
    @Override
    public String execute( final Context context, final Map<String, String> params ) throws PluginException {
        final Engine engine  = context.getEngine();
        String src           = getCleanParameter( params, PARAM_SRC );
        final String align   = sanitizeAlign( params.get( PARAM_ALIGN ) );
        final String ht      = sanitizeSize( params.get( PARAM_HEIGHT ) );
        final String wt      = sanitizeSize( params.get( PARAM_WIDTH ) );
        final String alt     = getCleanParameter( params, PARAM_ALT );
        final String caption = getCleanParameter( params, PARAM_CAPTION );
        String link          = getCleanParameter( params, PARAM_LINK );
        String target        = getCleanParameter( params, PARAM_TARGET );
        // style, class and the size parameters are validated rather than just encoded, so that they cannot be used
        // to inject css which places the image over the rest of the page (JSPWIKI-1276, JSPWIKI-1297)
        final String style   = TextUtil.replaceEntities( sanitizeStyle( params.get( PARAM_STYLE ) ) );
        final String cssclass= TextUtil.replaceEntities( sanitizeCssClass( params.get( PARAM_CLASS ) ) );
        final String border  = sanitizeSize( params.get( PARAM_BORDER ) );
        final String title   = getCleanParameter( params, PARAM_TITLE );

        if( src == null ) {
            throw new PluginException("Parameter 'src' is required for Image plugin");
        }

        //if( cssclass == null ) cssclass = "imageplugin";

        if( target != null && !validTargetValue(target) ) {
            target = null; // not a valid value so ignore
        }

        try {
            final AttachmentManager mgr = engine.getManager( AttachmentManager.class );
            final Attachment att = mgr.getAttachmentInfo( context, src );

            if( att != null ) {
                src = context.getURL( ContextEnum.PAGE_ATTACH.getRequestContext(), att.getName() );
            }
        } catch( final ProviderException e ) {
            throw new PluginException( "Attachment info failed: " + e.getMessage() );
        }

        final StringBuilder result = new StringBuilder();

        result.append( "<table border=\"0\" class=\"imageplugin\"" );

        if( title != null ) {
            result.append( " title=\"" ).append( title ).append( "\"" );
        }

        if( align != null ) {
            if( align.equals( "center" ) ) {
                result.append( " style=\"margin-left: auto; margin-right: auto; text-align:center; vertical-align:middle;\"" );
            } else {
                result.append( " style=\"float:" ).append( align ).append( ";\"" );
            }
        }

        result.append( ">\n" );

        if( caption != null ) {
            result.append( "<caption>" ).append( caption ).append( "</caption>\n" );
        }

        // move css class and style to the container of the image, so it doesn't affect the caption
        result.append( "<tr><td" );

        if( cssclass != null ) {
            result.append( " class=\"" ).append( cssclass ).append( "\"" );
        }

        if( style != null ) {
            result.append( " style=\"" ).append( style );

            // Make sure that we add a ";" to the end of the style string
            if( result.charAt( result.length()-1 ) != ';' ) {
                result.append( ";" );
            }

            result.append("\"");
        }

        result.append( ">" );

        if( link != null ) {
            if( !context.getBooleanWikiProperty( MarkupParser.PROP_ALLOWHTML, false ) ) {
                if (needsSanitization(link)) {
                    link = "http://invalid_url" + link;
                }
            }
            result.append( "<a href=\"" ).append( link ).append( "\"" );
            if( target != null ) {
                result.append( " target=\"" ).append( target ).append( "\"" );
            }
            result.append(">");
        }

        if(!context.getBooleanWikiProperty(MarkupParser.PROP_ALLOWHTML, false)) {
            if (needsSanitization(src)) {
                src = "http://invalid_url" + src;
            }
        }
        result.append( "<img src=\"" ).append( src ).append( "\"" );

        if( ht != null ) {
            result.append( " height=\"" ).append( ht ).append( "\"" );
        }
        if( wt != null ) {
            result.append( " width=\"" ).append( wt ).append( "\"" );
        }
        if( alt != null ) {
            result.append( " alt=\"" ).append( alt ).append( "\"" );
        }
        if( border != null ) {
            result.append( " border=\"" ).append( border ).append( "\"" );
        }
        // if( map != null )    result.append(" map=\""+map+"\"");

        result.append(" />");
        if( link != null ) {
            result.append("</a>");
        }
        result.append("</td></tr>\n");
        result.append("</table>\n");

        return result.toString();
    }

    private boolean validTargetValue( final String s ) {
        if( s.equals("_blank")
            || s.equals("_self")
            || s.equals("_parent")
            || s.equals("_top") ) {
            return true;
        }
        // a frame name: [a-zA-Z][a-zA-Z0-9_-]*
        return TARGET_PATTERN.matcher( s ).matches();
    }

}
