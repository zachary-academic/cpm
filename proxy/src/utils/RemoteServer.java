package utils;

import java.util.regex.Pattern;

/**
 * Constants used for contacting the remote Canvas server.
 */
class RemoteServer
{
	/**
	 * Protocol of Canvas server
	 */
	static final String OUTGOING_PROTOCOL = "https";
	
	/**
	 * Domain name of Canvas server
	 */
	static final String OUTGOING_HOST = "utah.instructure.com";
	
	/**
	 * Port (if any) of Canvas server
	 */
	static final Integer OUTGOING_PORT = null;

	/**
	 * Protocol/domain/port of Canvas server
	 */
	static final String OUTGOING_PREFIX = OUTGOING_PROTOCOL + "://" + OUTGOING_HOST + ((OUTGOING_PORT != null) ? ":" + OUTGOING_PORT : "");
	
	/**
	 * Regex for embedded url in Link header
	 */
	static final Pattern LINK_PATTERN = Pattern.compile("(<)" + OUTGOING_PREFIX + "(/)");
}
