package servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import utils.Utils;

/**
 * Servlet for accessing /login/oauth2/token portion of Canvas API
 */
@SuppressWarnings("serial")
@WebServlet("/login/oauth2/token")
public class Token extends HttpServlet
{
	protected void doPost(HttpServletRequest incomingRequest, HttpServletResponse incomingResponse)
			throws ServletException, IOException
	{
		// Compose the request
		HttpPost outgoingRequest = new HttpPost();
		Utils.mapRequestURL(outgoingRequest, incomingRequest);
		Utils.filterRequestHeaders(outgoingRequest, incomingRequest);
		Utils.mapRequestBody(outgoingRequest, incomingRequest);

		// Execute the request and compose the response
		try (CloseableHttpClient client = HttpClients.createMinimal();
				CloseableHttpResponse outgoingResponse = client.execute(outgoingRequest))
		{
			Utils.filterResponseHeaders(outgoingResponse, incomingResponse, incomingRequest);
			if (outgoingResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK)
			{
				Utils.mapResponseBody(outgoingResponse, incomingResponse);
			}
			else
			{
				Utils.copyResponseBody(outgoingResponse, incomingResponse);
			}
		}
	}

	protected void doDelete(HttpServletRequest incomingRequest, HttpServletResponse incomingResponse)
			throws ServletException, IOException
	{
		// Compose the request
		HttpDelete outgoingRequest = new HttpDelete();
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
}
