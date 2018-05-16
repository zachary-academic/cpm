package cpm.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Scanner;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;
import cpm.logging.CanvasProjectException;
import cpm.logging.StatusCodeException;
import cpm.net.Canvas.StreamConsumer;
import state.StateCache;

/**
 * Provides methods for performing rest calls and managing authorization.
 */
public class Rest
{
    /** Refresh token, used to obtain auth tokens */
    private static String refreshToken = null;

    /** Auth token, used to authorize requests */
    private static String authToken = null;

    /** Name of authenticated student */
    private static String studentName = null;

    /**
     * Initializes tokens and name on startup
     */
    static void initializeAuthentication (String baseURL) throws CanvasProjectException
    {
        try (Scanner scn = StateCache.read("token"))
        {
            refreshToken = scn.nextLine();
            studentName = scn.nextLine();
        }
        catch (Exception e)
        {
        }

        if (refreshToken != null)
        {
            refreshAuthToken(baseURL);
        }
    }

    /**
     * Saves the refresh token and name
     */
    private static void saveTokenAndName ()
    {
        try (PrintStream out = StateCache.write("token"))
        {
            out.println(refreshToken);
            out.println(studentName);
        }
        catch (Exception e)
        {
        }
    }

    /**
     * Clears the refresh token and name
     */
    private static void clearTokenAndName ()
    {
        refreshToken = null;
        authToken = null;
        studentName = null;
        StateCache.clear("token");
    }

    /**
     * Reports whether there is a refresh token
     */
    static boolean isRefreshToken ()
    {
        return refreshToken != null;
    }

    /**
     * Returns the student's name, or null if unknown
     */
    static String getStudentName ()
    {
        return studentName;
    }

    /**
     * Authorizes by getting refresh and auth tokens.
     */
    static void authorize (String baseURL, String code) throws CanvasProjectException
    {
        // Make the request
        JSONObject body = new JSONObject();
        body.put("grant_type", "authorization_code");
        body.put("client_id", DeveloperKey.clientID);
        body.put("client_secret", DeveloperKey.clientSecret);
        body.put("code", code);
        JSONObject object = postRequestNoToken(baseURL, "login/oauth2/token", body);

        // Pull out the tokens and name
        authToken = object.getString("access_token");
        refreshToken = object.getString("refresh_token");
        studentName = object.getJSONObject("user").getString("name");
        saveTokenAndName();
    }

    /**
     * Logs out by deleting the refresh token from the server and the cache
     */
    static void deleteRefeshToken (String baseURL) throws CanvasProjectException
    {
        try (CloseableHttpClient client = HttpClients.createDefault())
        {
            // Compose and execute the request
            HttpDelete deleteRequest = new HttpDelete(baseURL + "login/oauth2/token");
            deleteRequest.addHeader("Authorization", "Bearer " + authToken);
            deleteRequest.addHeader("accept", "application/json");
            HttpResponse response = client.execute(deleteRequest);
            int statusCode = response.getStatusLine().getStatusCode();

            // If we were unauthorized, refresh the authorization token and retry request.
            if (authTokenExpired(response))
            {
                refreshAuthToken(baseURL);
                deleteRequest.removeHeaders("Authorization");
                deleteRequest.addHeader("Authorization", "Bearer " + authToken);
                response = client.execute(deleteRequest);
                statusCode = response.getStatusLine().getStatusCode();
            }

            // Deal with response
            if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_CREATED)
            {
                throw new CanvasProjectException(statusCode);
            }
        }
        catch (IOException e)
        {
            throw new CanvasProjectException(e, "Problem contacting remote server");
        }
        finally
        {
            clearTokenAndName();
        }
    }

    /**
     * Uses the refresh token to obtain a new authorization token. If the refresh token has been invalidated, deletes it
     * to eventually force a new login.
     */
    private static void refreshAuthToken (String baseURL) throws CanvasProjectException
    {
        try (CloseableHttpClient client = HttpClients.createMinimal())
        {
            // Build body
            JSONObject body = new JSONObject();
            body.put("grant_type", "refresh_token");
            body.put("client_id", DeveloperKey.clientID);
            body.put("client_secret", DeveloperKey.clientSecret);
            body.put("refresh_token", refreshToken);

            // Build and execute request
            HttpPost postRequest = new HttpPost(baseURL + "login/oauth2/token");
            postRequest.addHeader("accept", "application/json");
            StringEntity requestEntity = new StringEntity(body.toString(), ContentType.APPLICATION_JSON);
            postRequest.setEntity(requestEntity);
            HttpResponse response = client.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();

            // If the refresh token isn't accepted, null it out to force a new login
            if (statusCode == HttpStatus.SC_UNAUTHORIZED)
            {
                clearTokenAndName();
                throw new CanvasProjectException("Access credentials rejected");
            }

            // If an unexpected status code comes back, report an error
            else if (statusCode != HttpStatus.SC_OK)
            {
                throw new CanvasProjectException(statusCode);
            }

            // Extract and set the authorization token
            else
            {
                authToken = new JSONObject(getPayloadString(response)).getString("access_token");
            }
        }
        catch (ClientProtocolException e)
        {
            throw new CanvasProjectException(e, "Problem updating access credentials");
        }
        catch (IOException e)
        {
            throw new CanvasProjectException(e, "Problem contacting remote server");
        }
    }

    /**
     * Reports whether the response is indicating that the auth token has expired.
     */
    private static boolean authTokenExpired (HttpResponse response)
    {
        return response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED && response.getFirstHeader("WWW-Authenticate") != null;
    }

    /**
     * Runs the GET request encoded in the URL and returns the JSONObject contained in the response. Refreshes the
     * authentication token as necessary.
     */
    static JSONObject getRequestObject (String base, String url) throws CanvasProjectException
    {
        try (CloseableHttpClient client = HttpClients.createDefault())
        {
            // Execute the request
            HttpGet getRequest = new HttpGet(base + url);
            getRequest.addHeader("Authorization", "Bearer " + authToken);
            getRequest.addHeader("accept", "application/json");
            HttpResponse response = client.execute(getRequest);

            // If we were unauthorized, refresh the authorization token and retry request
            if (authTokenExpired(response))
            {
                refreshAuthToken(base);
                getRequest.removeHeaders("Authorization");
                getRequest.addHeader("Authorization", "Bearer " + authToken);
                response = client.execute(getRequest);
            }

            // Deal with response
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK)
            {
                throw new CanvasProjectException(statusCode);
            }
            else
            {
                return new JSONObject(getPayloadString(response));
            }
        }
        catch (IOException e)
        {
            throw new CanvasProjectException(e, "Problem making server request");
        }
    }

    /**
     * Runs the GET request encoded in the URL and returns the JSONArray contained in the response. Refreshes the
     * authentication token if necessary. If an unexpected status code is received, throws a StatusCodeException.
     */
    static JSONArray getRequestArray (String base, String url) throws CanvasProjectException, StatusCodeException
    {
        try (CloseableHttpClient client = HttpClients.createDefault())
        {
            // The URL and final result
            url = base + url;
            JSONArray finalArray = null;

            // Repeat until the entire array is obtained since it can require multiple requests
            while (url != null)
            {
                // Execute the request
                HttpGet getRequest = new HttpGet(url);
                getRequest.addHeader("Authorization", "Bearer " + authToken);
                getRequest.addHeader("accept", "application/json");
                HttpResponse response = client.execute(getRequest);

                // If we were unauthorized, refresh the authorization token and retry request.
                if (authTokenExpired(response))
                {
                    refreshAuthToken(base);
                    getRequest.removeHeaders("Authorization");
                    getRequest.addHeader("Authorization", "Bearer " + authToken);
                    response = client.execute(getRequest);
                }

                // Deal with unexpected response
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != HttpStatus.SC_OK)
                {
                    throw new StatusCodeException(statusCode);
                }

                // Get the array from the response and append to finalArray
                JSONArray array = new JSONArray(getPayloadString(response));
                finalArray = (finalArray == null) ? array : append(finalArray, array);

                // There can be multiple pages so we have to concatenate them
                url = null;
                if (response.getHeaders("Link").length > 0)
                {
                    HeaderElement[] headerElements = response.getHeaders("Link")[0].getElements();
                    for (HeaderElement element : headerElements)
                    {
                        if (element.getParameterByName("rel").getValue().equals("next"))
                        {
                            url = element.toString();
                            url = url.substring(1, url.indexOf('>'));
                            break;
                        }
                    }
                }
            }

            // The entire response has been read
            return finalArray;
        }
        catch (IOException e)
        {
            throw new CanvasProjectException(e, "Problem contacting remote server");
        }
    }

    /**
     * Makes a get request on the provided absolute url, then passes the response body's input stream to the consumer.
     * This type of request requires no authentication.
     */
    static <T> T getRequestStream (String absoluteURL, String base, StreamConsumer<T> consumer) throws CanvasProjectException
    {
        try (CloseableHttpClient client = HttpClients.createDefault())
        {
            HttpGet getRequest = new HttpGet(absoluteURL);
            HttpResponse response = client.execute(getRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK)
            {
                throw new CanvasProjectException(statusCode);
            }
            else
            {
                return consumer.consume(response.getEntity().getContent());
            }
        }
        catch (IOException e)
        {
            throw new CanvasProjectException(e, "Problem contacting remote server");
        }
    }

    /**
     * Posts the body to the url and returns the JSONObject that comes back as the response payload. Sends an
     * authorization token, and deals with failed authorization.
     */
    static JSONObject postRequest (String base, String url, JSONObject body) throws CanvasProjectException
    {
        try (CloseableHttpClient client = HttpClients.createMinimal())
        {
            // Execute the request
            HttpPost postRequest = new HttpPost(base + url);
            postRequest.addHeader("Authorization", "Bearer " + authToken);
            postRequest.addHeader("accept", "application/json");
            StringEntity requestEntity = new StringEntity(body.toString(), ContentType.APPLICATION_JSON);
            postRequest.setEntity(requestEntity);
            HttpResponse response = client.execute(postRequest);

            // If we were unauthorized, refresh the authorization token and retry request.
            if (authTokenExpired(response))
            {
                refreshAuthToken(base);
                postRequest.removeHeaders("Authorization");
                postRequest.addHeader("Authorization", "Bearer " + authToken);
                response = client.execute(postRequest);
            }

            // Deal with response
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED)
            {
                return new JSONObject(getPayloadString(response));
            }
            else
            {
                throw new CanvasProjectException(statusCode);
            }
        }
        catch (IOException e)
        {
            throw new CanvasProjectException(e, "Problem contacting remote server");
        }
    }

    /**
     * Posts the body to the url and returns the JSONObject that comes back as the response payload. Doesn't send an
     * authorization token, and as a result doesn't have to deal with failed authorization.
     */
    static JSONObject postRequestNoToken (String base, String url, JSONObject body) throws CanvasProjectException
    {
        try (CloseableHttpClient client = HttpClients.createMinimal())
        {
            // Execute the request
            HttpPost postRequest = new HttpPost(base + url);
            postRequest.addHeader("accept", "application/json");
            StringEntity requestEntity = new StringEntity(body.toString(), ContentType.APPLICATION_JSON);
            postRequest.setEntity(requestEntity);
            HttpResponse response = client.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();

            // Deal with response
            if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED)
            {
                return new JSONObject(getPayloadString(response));
            }
            else
            {
                throw new CanvasProjectException(statusCode);
            }
        }
        catch (IOException e)
        {
            throw new CanvasProjectException(e, "Problem contacting remote server");
        }
    }

    /**
     * Posts (without authentication) a multipart request and returns the JSONObject that comes back as the response
     * payload
     */
    static JSONObject postRequestMultipart (String uploadURL, MultipartEntityBuilder builder) throws CanvasProjectException
    {
        try (CloseableHttpClient client = HttpClients.createDefault())
        {
            // Execute the request
            HttpPost postRequest = new HttpPost(uploadURL);
            HttpEntity entity = builder.build();
            postRequest.setEntity(entity);
            HttpResponse response = client.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();

            // Deal with response
            if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED)
            {
                return new JSONObject(getPayloadString(response));
            }
            else
            {
                throw new CanvasProjectException(statusCode);
            }
        }
        catch (IOException e)
        {
            throw new CanvasProjectException(e, "Problem contacting remote server");
        }
    }
    
    /**
     * Logs the info on the server
     */
    static void logException (String base, String url, String info) throws CanvasProjectException
    {
        try (CloseableHttpClient client = HttpClients.createMinimal())
        {
            HttpPost postRequest = new HttpPost(base + url);
            StringEntity requestEntity = new StringEntity(info, ContentType.TEXT_PLAIN);
            postRequest.setEntity(requestEntity);
            HttpResponse response = client.execute(postRequest);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK)
            {
                throw new CanvasProjectException(statusCode);
            }
        }
        catch (IOException e)
        {
            throw new CanvasProjectException(e, "Problem contacting remote server");
        }
    }

    /**
     * Appends the elements of arr2 to the end of arr1.
     */
    private static JSONArray append (JSONArray arr1, JSONArray arr2)
    {
        for (int i = 0; i < arr2.length(); i++)
        {
            arr1.put(arr2.get(i));
        }
        return arr1;
    }

    /**
     * Reads and returns the payload of the response as a string.
     */
    private static String getPayloadString (HttpResponse response) throws IOException
    {
        StringBuilder jsonString = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent()))))
        {
            String output;
            while ((output = br.readLine()) != null)
            {
                jsonString.append(output);
            }
        }
        return jsonString.toString();
    }
}
