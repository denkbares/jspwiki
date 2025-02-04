/*
 * Copyright (C) 2025 denkbares GmbH. All rights reserved.
 */

package org.apache.wiki.multiWiki;

import org.apache.wiki.TestEngine;
import org.junit.jupiter.api.AfterEach;

public class AbstractMultiWikiTest {

	protected static TestEngine testEngine;

	@AfterEach
	public void tearDown() {
		testEngine.stop();
	}

}
