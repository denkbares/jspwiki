/*
 * Copyright 2026 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wiki.plugin;

import org.apache.wiki.TestEngine;
import org.apache.wiki.render.RenderingManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 *
 */
public class ImageTest {

    static TestEngine testEngine = TestEngine.build();

    /**
     * Test of execute method, of class Image.
     */
    @Test
    public void mixedCase() throws Exception {
        final String src = "[{Image src=’img.png’ link=‘JavaScript:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }

    @Test
    public void withSpaces() throws Exception {
        final String src = "[{Image src=’img.png’ link=‘Java Script:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }

    @Test
    public void withTabSpaces() throws Exception {
        final String src = "[{Image src=’img.png’ link=‘Java\t Script:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }

    @Test
    public void withLeadingSpaces() throws Exception {
        final String src = "[{Image src=’img.png’ link=‘ Java\t Script:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }

    @Test
    public void withLeading2Spaces() throws Exception {
        final String src = "[{Image src=’img.png’ link=‘\tJava\t Script:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }

    @Test
    public void dataurl() throws Exception {
        final String src = "[{Image src=’img.png’ link=‘\tDATA:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }

    @Test
    public void vbscripturl() throws Exception {
        final String src = "[{Image src=’img.png’ link=‘\tVbs c  ripT:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }
    
    
   
    
    @Test
    public void SRCmixedCase() throws Exception {
        final String src = "[{Image src=‘JavaScript:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"));

    }

    @Test
    public void srcwithSpaces() throws Exception {
        final String src = "[{Image src=‘Java Script:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }

    @Test
    public void srcwithTabSpaces() throws Exception {
        final String src = "[{Image src=‘Java\t Script:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }

    @Test
    public void srcwithLeadingSpaces() throws Exception {
        final String src = "[{Image src=‘ Java\t Script:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }

    @Test
    public void srcwithLeading2Spaces() throws Exception {
        final String src = "[{Image src=‘\tJava\t Script:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }

    @Test
    public void SRCdataurl() throws Exception {
        final String src = "[{Image src=‘\tDATA:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }

    @Test
    public void SRCvbscripturl() throws Exception {
        final String src = "[{Image src=‘\tVbs c  ripT:alert(document.cookie)>’}]";

        testEngine.saveText("ThisPage", src);

        // Just check that it contains a proper error message; don't bother do HTML checking.
        final String res = testEngine.getManager(RenderingManager.class).getHTML("ThisPage");
        Assertions.assertTrue(!res.contains("document.cookie"), res);

    }


    /** JSPWIKI-1297 (CVE-2026-78110): css which takes the image out of the page flow is dropped. */
    @Test
    public void styleCssInjection() throws Exception {
        final String src = "[{Image src='img.png' style='position:fixed; top:0; left:0; z-index:9999; width:100%; "
                           + "background-image:url(http://evil.example/x.png); margin:-500px; border:1px solid red'}]";

        testEngine.saveText( "ThisPage", src );
        final String res = testEngine.getManager( RenderingManager.class ).getHTML( "ThisPage" );

        Assertions.assertFalse( res.contains( "position" ), res );
        Assertions.assertFalse( res.contains( "z-index" ), res );
        Assertions.assertFalse( res.contains( "evil.example" ), res );
        Assertions.assertFalse( res.contains( "-500px" ), res );
        Assertions.assertTrue( res.contains( "style=\"width: 100%; border: 1px solid red;\"" ), res );
    }

    @Test
    public void sanitizeStyle() {
        Assertions.assertNull( Image.sanitizeStyle( null ) );
        Assertions.assertNull( Image.sanitizeStyle( "position:fixed;top:0" ) );
        Assertions.assertNull( Image.sanitizeStyle( "width:expression(alert(1))" ) );
        Assertions.assertNull( Image.sanitizeStyle( "width:100px\\;position:fixed" ) );
        Assertions.assertNull( Image.sanitizeStyle( "width:/**/100px" ) );
        Assertions.assertNull( Image.sanitizeStyle( "width:100px\"onmouseover=\"alert(1)" ) );
        Assertions.assertNull( Image.sanitizeStyle( "margin:calc(0px - 500px)" ) );
        Assertions.assertNull( Image.sanitizeStyle( "background-color:var(--x)" ) );
        Assertions.assertEquals( "width: 50%; float: right; border-color: #ff0000;", Image.sanitizeStyle( "WIDTH:50%;float : right;;border-color:#ff0000;" ) );
        Assertions.assertEquals( "margin: 0 auto;", Image.sanitizeStyle( "margin:0 auto" ) );
        Assertions.assertEquals( "color: rgb(10, 20, 30);", Image.sanitizeStyle( "color:rgb(10, 20, 30)" ) );
    }

    @Test
    public void classInjection() throws Exception {
        final String src = "[{Image src='img.png' class='good also-good \" onmouseover=\"alert(1) evil<b>'}]";

        testEngine.saveText( "ThisPage", src );
        final String res = testEngine.getManager( RenderingManager.class ).getHTML( "ThisPage" );

        Assertions.assertFalse( res.contains( "onmouseover" ), res );
        Assertions.assertFalse( res.contains( "evil" ), res );
        Assertions.assertTrue( res.contains( "class=\"good also-good\"" ), res );
        Assertions.assertNull( Image.sanitizeCssClass( "\"><script>" ) );
    }

    /** JSPWIKI-1276 (CVE-2026-71903): only plain sizes and known alignments/targets are accepted. */
    @Test
    public void sizeAlignTargetValidation() throws Exception {
        final String src = "[{Image src='img.png' width='100px;position:fixed' height='50%' border='1' align='left;position:fixed' "
                           + "target='x onclick=alert(1)' link='http://www.example.com/'}]";

        testEngine.saveText( "ThisPage", src );
        final String res = testEngine.getManager( RenderingManager.class ).getHTML( "ThisPage" );

        Assertions.assertFalse( res.contains( "position" ), res );
        Assertions.assertFalse( res.contains( "onclick" ), res );
        Assertions.assertFalse( res.contains( "width=" ), res );
        Assertions.assertFalse( res.contains( "float" ), res );
        Assertions.assertFalse( res.contains( "target=" ), res );
        Assertions.assertTrue( res.contains( "height=\"50%\"" ), res );
        Assertions.assertTrue( res.contains( "border=\"1\"" ), res );

        Assertions.assertEquals( "200", Image.sanitizeSize( " 200 " ) );
        Assertions.assertEquals( "200px", Image.sanitizeSize( "200px" ) );
        Assertions.assertNull( Image.sanitizeSize( "-200" ) );
        Assertions.assertNull( Image.sanitizeSize( "200\" onerror=\"alert(1)" ) );
        Assertions.assertEquals( "center", Image.sanitizeAlign( "Center" ) );
        Assertions.assertNull( Image.sanitizeAlign( "left; position:fixed" ) );
    }

    @Test
    public void validTarget() throws Exception {
        final String src = "[{Image src='img.png' target='_blank' link='http://www.example.com/'}]";

        testEngine.saveText( "ThisPage", src );
        final String res = testEngine.getManager( RenderingManager.class ).getHTML( "ThisPage" );

        Assertions.assertTrue( res.contains( "target=\"_blank\"" ), res );
    }

    @Test
    public void linkWithControlCharacters() throws Exception {
        final String src = "[{Image src='img.png' link='\u0001javascript:alert(document.cookie)'}]";

        testEngine.saveText( "ThisPage", src );
        final String res = testEngine.getManager( RenderingManager.class ).getHTML( "ThisPage" );

        Assertions.assertTrue( res.contains( "href=\"http://invalid_url" ), res );
    }

}
