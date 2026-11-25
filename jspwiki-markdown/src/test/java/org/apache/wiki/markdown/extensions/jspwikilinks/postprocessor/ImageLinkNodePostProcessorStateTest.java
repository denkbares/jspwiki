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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class ImageLinkNodePostProcessorStateTest {

    /** JSPWIKI-1296 (CVE-2026-125949): script and data urls are neutralized like in the Image plugin. */
    @Test
    public void testSanitizeUrl() {
        Assertions.assertEquals( "http://foobar.jpg", ImageLinkNodePostProcessorState.sanitizeUrl( "http://foobar.jpg" ) );
        Assertions.assertEquals( "/test/attach/Test/a.png", ImageLinkNodePostProcessorState.sanitizeUrl( "/test/attach/Test/a.png" ) );
        Assertions.assertEquals( "http://invalid_urljavascript:alert(1)", ImageLinkNodePostProcessorState.sanitizeUrl( "javascript:alert(1)" ) );
        Assertions.assertEquals( "http://invalid_url Java\tScript:alert(1)", ImageLinkNodePostProcessorState.sanitizeUrl( " Java\tScript:alert(1)" ) );
        Assertions.assertEquals( "http://invalid_url\u0001vbscript:x", ImageLinkNodePostProcessorState.sanitizeUrl( "\u0001vbscript:x" ) );
        Assertions.assertEquals( "http://invalid_urlDATA:text/html,x", ImageLinkNodePostProcessorState.sanitizeUrl( "DATA:text/html,x" ) );
    }

}
