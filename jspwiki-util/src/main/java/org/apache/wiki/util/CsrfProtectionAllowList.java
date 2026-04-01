package org.apache.wiki.util;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.servlet.http.HttpServletRequest;

/**
 * Thread-safe registry of request checkers that may explicitly allowlist individual POST requests for the CSRF
 * protection filter.
 */
public final class CsrfProtectionAllowList {

	private static final Set<CsrfProtectionAllowListChecker> CHECKERS = new CopyOnWriteArraySet<>();

	private CsrfProtectionAllowList() {
	}

	/**
	 * Registers the given allowlist checker.
	 *
	 * @param checker the checker to register
	 */
	public static void register(CsrfProtectionAllowListChecker checker) {
		if (checker != null) {
			CHECKERS.add(checker);
		}
	}

	/**
	 * Returns whether any registered checker allowlists the given request.
	 *
	 * @param request the current HTTP request
	 * @return {@code true} if the request is allowlisted, otherwise {@code false}
	 */
	public static boolean isAllowListed(HttpServletRequest request) {
		for (CsrfProtectionAllowListChecker checker : CHECKERS) {
			if (checker.isAllowListed(request)) {
				return true;
			}
		}
		return false;
	}
}
