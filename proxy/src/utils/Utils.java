package utils;

import static utils.Secrets.*;
import static utils.RemoteServer.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * Variety of utilities that support the proxy
 */
@SuppressWarnings("serial")
public class Utils extends HttpServlet
{
	/**
	 * Reports whether the path info of the incoming request meets the pattern
	 */
	public static boolean requestAllowed(Pattern pattern, HttpServletRequest incomingRequest)
	{
		String pathInfo = incomingRequest.getPathInfo();
		pathInfo = (pathInfo == null) ? "" : pathInfo;
		return pattern.matcher(pathInfo).matches();
	}
	
	
	/**
	 * Uses the URL of the incoming request to set the URL of the outgoing request.
	 */
	public static void mapRequestURL(HttpRequestBase outgoingRequest, HttpServletRequest incomingRequest)
			throws ServletException
	{
		try
		{
			String query = incomingRequest.getQueryString();
			StringBuffer path = new StringBuffer();
			path.append(incomingRequest.getServletPath());
			String pathinfo = incomingRequest.getPathInfo();
			if (pathinfo != null) path.append(pathinfo);
			if (query != null) path.append("?").append(query);
			URL url;
			if (OUTGOING_PORT == null)
			{
				url = new URL(OUTGOING_PROTOCOL, OUTGOING_HOST, path.toString());
			}
			else
			{
				url = new URL(OUTGOING_PROTOCOL, OUTGOING_HOST, OUTGOING_PORT, path.toString());
			}
			outgoingRequest.setURI(url.toURI());
		}
		catch (MalformedURLException | URISyntaxException e)
		{
			throw new ServletException(e.getMessage());
		}
	}

	/**
	 * Copies the headers from the incoming request into the outgoing request, except that x-forwarded-* and content-length 
	 * headers are ignored, the host header is set to OUTGOING_HOST, and any authorization header is decrypted.
	 */
	public static void filterRequestHeaders(HttpRequestBase outgoingRequest, HttpServletRequest incomingRequest)
			throws ServletException
	{
		// Set headers of outgoingRequest
		Enumeration<String> headers = incomingRequest.getHeaderNames();
		while (headers.hasMoreElements())
		{
			String header = headers.nextElement();
			if (header.toLowerCase().startsWith("x-forwarded-")) continue;
			if (header.toLowerCase().equals("content-length")) continue;
			Enumeration<String> values = incomingRequest.getHeaders(header);
			while (values.hasMoreElements())
			{
				String value = values.nextElement();
				if (header.toLowerCase().equals("host")) value = OUTGOING_HOST;
				if (header.toLowerCase().equals("authorization")) value = decryptAuthorization(value);
				outgoingRequest.addHeader(header, value);
			}
		}
	}

	/**
	 * Copies the body of the incoming request as the body of the outgoing request.
	 */
	public static void copyRequestBody(HttpPost outgoingRequest, HttpServletRequest incomingRequest) throws IOException
	{
		outgoingRequest.setEntity(new InputStreamEntity(incomingRequest.getInputStream()));
	}

	/**
	 * Converts the body of the incoming request into JSON, makes some changes, and uses the result as the body
	 * of the outgoing request.  Specifically, a (meta) client_secret is replaced with the real client secret and
	 * refresh/access tokens are decrypted.
	 */
	@SuppressWarnings("unchecked")
	public static void mapRequestBody(HttpPost outgoingRequest, HttpServletRequest incomingRequest)
			throws IOException, ServletException
	{
		// Obtain the incoming JSON
		JSONObject params = (JSONObject) JSONValue.parse(incomingRequest.getReader());

		// If the meta client secret is present, replace it with the real client secret.
		// Otherwise, leave it alone which will lead to an authorization failure later.
		if (META_CLIENT_SECRET.equals(params.get("client_secret")))
		{
			params.put("client_secret", CLIENT_SECRET);
		}

		// If there are incoming refresh or access tokens, decrypt them
		decryptToken(params, "refresh_token");
		decryptToken(params, "access_token");

		// Store the params into the outgoingRequest body
		try
		{
			outgoingRequest.setEntity(new StringEntity(params.toString()));
		}
		catch (UnsupportedEncodingException e)
		{
			throw new ServletException(e.getMessage());
		}
	}

	/**
	 * Copies the headers from the outgoing response into the incoming response, except that the content-length and
	 * transfer-encoding headers are ignored and the Link header is modified to make it work with the proxy.
	 */
	public static void filterResponseHeaders(HttpResponse outgoingResponse, HttpServletResponse incomingResponse,
			HttpServletRequest incomingRequest)
	{
		String incomingPrefix = getIncomingPrefix(incomingRequest);
		incomingResponse.setStatus(outgoingResponse.getStatusLine().getStatusCode());
		for (Header h : outgoingResponse.getAllHeaders())
		{
			if (h.getName().toLowerCase().equals("content-length")) continue;
			if (h.getName().toLowerCase().equals("transfer-encoding")) continue;
			incomingResponse.addHeader(h.getName(),
					adjustLink(h.getName(), h.getValue(), incomingPrefix));
		}
	}

	/**
	 * Copies the body of the outgoing response as the body of the incoming response.
	 */
	public static void copyResponseBody(HttpResponse outgoingResponse, HttpServletResponse incomingResponse)
			throws IOException
	{
		try (InputStream inputStream = outgoingResponse.getEntity().getContent();
				ServletOutputStream outputStream = incomingResponse.getOutputStream())
		{
			IOUtils.copy(inputStream, outputStream);
		}
	}

	/**
	 * Converts the body of the outgoing response into JSON, makes some changes, and uses the result as the body
	 * of the incoming response.  Specifically, any refresh/access tokens are encrypted.
	 */
	public static void mapResponseBody(HttpResponse outgoingResponse, HttpServletResponse incomingResponse)
			throws IOException, ServletException
	{
		try (InputStream inputStream = outgoingResponse.getEntity().getContent();
				Reader reader = new InputStreamReader(inputStream))
		{
			JSONObject params = (JSONObject) JSONValue.parse(reader);
			encryptToken(params, "refresh_token");
			encryptToken(params, "access_token");
			params.writeJSONString(incomingResponse.getWriter());
		}
	}
	
	/**
	 * Uses the incoming request to compose the prefix of the outgoing response's URL.
	 */
	private static String getIncomingPrefix(HttpServletRequest incomingRequest)
	{
		String context = incomingRequest.getServletContext().getContextPath();
		
		// Get the host
		String host = incomingRequest.getHeader("X-Forwarded-Host");
		if (host == null)
		{
		    host = incomingRequest.getHeader("host");
		}
		
		if (host != null)
		{
			// Assume https unless the host is localhost
			String protocol = (host.startsWith("localhost")) ? "http" : "https";
			StringBuffer url = new StringBuffer(protocol).append("://").append(host).append(context);
			return url.toString();
		}
		else
		{
			StringBuffer url = incomingRequest.getRequestURL();
			if (url.indexOf("http://localhost") != 0 && url.indexOf("http://") == 0)
			{
				url.insert(4, 's');
			}
			int index = url.indexOf(context + "/");			
			return url.substring(0, index + context.length());
		}
	}
	
	/**
	 * Decrypts the authorization header.
	 */
	private static String decryptAuthorization(String value) throws ServletException
	{
		value = value.trim();
		if (value.toLowerCase().startsWith("bearer"))
		{
			value = value.substring("bearer".length());
			value = value.trim();
			value = Encryption.decrypt(value);
			value = "Bearer " + ((value == null) ? "" : value);
		}
		return value;
	}
	
	/**
	 * Adjusts the Link header so that the URLs it contains work properly with the proxy.
	 */
	private static String adjustLink(String name, String value, String incomingPrefix)
	{
		if (name.toLowerCase().equals("link"))
		{
			Matcher m = LINK_PATTERN.matcher(value);
			StringBuffer sb = new StringBuffer();
			while (m.find())
			{
				m.appendReplacement(sb, "$1" + incomingPrefix + "$2");
			}
			m.appendTail(sb);
			return sb.toString();
		}
		else
		{
			return value;
		}
	}

	/**
	 * Encrypts params[tokenName] if present.  If encryption fails substitutes an empty string.
	 */
	@SuppressWarnings("unchecked")
	private static void encryptToken(JSONObject params, String tokenName) throws ServletException
	{
		String token = (String) params.get(tokenName);
		if (token != null)
		{
			String encrypted = Encryption.encrypt(token);
			params.put(tokenName, (encrypted == null) ? "" : encrypted);
		}
	}
	
	/**
	 * Decrypts params[tokenName] if present.  If decryption fails substitutes an empty string.
	 */
	@SuppressWarnings("unchecked")
	private static void decryptToken(JSONObject params, String tokenName) throws ServletException
	{
		String token = (String) params.get(tokenName);
		if (token != null)
		{
			String decrypted = Encryption.decrypt(token);
			params.put(tokenName, (decrypted == null) ? "" : decrypted);
		}
	}
}
