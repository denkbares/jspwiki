package org.apache.wiki.util;

import javax.servlet.http.HttpServletRequest;

/**
 * Strategy interface used by the CSRF protection filter to exempt selected POST requests from token validation.
 * Implementations must be conservative and only allow strictly read-only requests.
 */
@FunctionalInterface
public interface CsrfProtectionAllowListChecker {

	/**
	 * Returns whether the given request is explicitly allow-listed and may bypass the CSRF token check.
	 *
	 * @param request the current HTTP request
	 * @return {@code true} if the request may bypass CSRF validation, otherwise {@code false}
	 */
	boolean isAllowListed(HttpServletRequest request);
}
