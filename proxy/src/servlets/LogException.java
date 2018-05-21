package servlets;

import java.io.BufferedReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

/**
 * Servlet for logging client exceptions
 */
@SuppressWarnings("serial")
@WebServlet("/logException")
public class LogException extends HttpServlet
{
	protected void doPost(HttpServletRequest req, HttpServletResponse rsp) throws ServletException, IOException
	{
		try (BufferedReader logReader = req.getReader())
		{			
			String logEntry = IOUtils.toString(logReader);
			getServletContext().log("CLIENT " + logEntry);
		}
	}
}
