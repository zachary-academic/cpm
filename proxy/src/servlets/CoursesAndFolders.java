package servlets;

import utils.Utils;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Servlet for access to /api/v1/courses and /api/v1/folders portiona of Canvas API
 */
@SuppressWarnings("serial")
@WebServlet(urlPatterns= {"/api/v1/courses/*", "/api/v1/folders/*"})
public class CoursesAndFolders extends HttpServlet
{
	/**
	 * Allowable get requests
	 */
	private static Pattern getPattern = 
			Pattern.compile("^(() | (/\\d+/files) | (/\\d+/folders) | (/\\d+/folders/by_path/.*) |  (/\\d+/assignments))$", Pattern.COMMENTS);

	protected void doGet(HttpServletRequest incomingRequest, HttpServletResponse incomingResponse)
			throws ServletException, IOException
	{
		// Make sure the request is allowed
		if (!Utils.requestAllowed(getPattern, incomingRequest))
		{
			incomingResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		// Compose the request
		HttpGet outgoingRequest = new HttpGet();
		Utils.mapRequestURL(outgoingRequest, incomingRequest);
		Utils.filterRequestHeaders(outgoingRequest, incomingRequest);

		// Execute the request and compose the response
		try (CloseableHttpClient client = HttpClients.createMinimal();
				CloseableHttpResponse outgoingResponse = client.execute(outgoingRequest))
		{
			Utils.filterResponseHeaders(outgoingResponse, incomingResponse, incomingRequest);
			Utils.copyResponseBody(outgoingResponse, incomingResponse);
		}
	}
	
	/**
	 * Allowable post requests
	 */
	private static Pattern postPattern = 
			Pattern.compile("^((/\\d+/assignments/\\d+/submissions/self/files) | (/\\d+/assignments/\\d+/submissions))$", Pattern.COMMENTS);

	protected void doPost(HttpServletRequest incomingRequest, HttpServletResponse incomingResponse)
			throws ServletException, IOException
	{
		// Make sure the request is allowed
		if (!Utils.requestAllowed(postPattern, incomingRequest))
		{
			incomingResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		// Compose the request
		HttpPost outgoingRequest = new HttpPost();
		Utils.mapRequestURL(outgoingRequest, incomingRequest);
		Utils.filterRequestHeaders(outgoingRequest, incomingRequest);
		Utils.copyRequestBody(outgoingRequest, incomingRequest);

		// Execute the request; compose and send the response
		try (CloseableHttpClient client = HttpClients.createMinimal();
				CloseableHttpResponse outgoingResponse = client.execute(outgoingRequest))
		{
			Utils.filterResponseHeaders(outgoingResponse, incomingResponse, incomingRequest);
			Utils.copyResponseBody(outgoingResponse, incomingResponse);
		}
	}
}
