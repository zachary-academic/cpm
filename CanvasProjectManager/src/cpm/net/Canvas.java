package cpm.net;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import cpm.data.Assignment;
import cpm.data.Course;
import cpm.data.CourseProfile;
import cpm.data.Project;
import cpm.data.ProjectType;
import cpm.logging.CanvasProjectException;
import cpm.logging.StatusCodeException;

public class Canvas
{
    /** Base URL used for all Canvas requests */
    private static final String baseURL = 
            "https://joe.coe.utah.edu/proxy/";     // This is a proxy that passes only certain requests to Canvas
            // "https://utah.instructure.com/";    // This is the Canvas server

    /** Size of buffer used in file downloads */
    private final static int BUFFER_SIZE = 2096;

    /** Name of the configuration file */
    private final static String CONFIG_FILE = "CPMConfig.xml";
    
    /**
     * Set up authentication on startup
     */
    static
    {
        try
        {
        Rest.initializeAuthentication(baseURL);
        }
        catch (Exception e)
        { 
        }
    }

    /**
     * Returns the name of the student, or null if not authorized yet.
     */
    public static String getStudentName ()
    {
        return Rest.getStudentName();
    }

    /**
     * Reports whether the student is authorized
     */
    public static boolean authorized ()
    {
        return Rest.isRefreshToken();
    }

    /**
     * Implement this interface to consume streams
     */
    public interface StreamConsumer<T>
    {
        public T consume (InputStream input) throws CanvasProjectException;
    }

    /**
     * Implement this interface to be able to filter by filename
     */
    public interface FilenameFilter
    {
        public boolean accept (String filename);
    }

    /**
     * Logs out from the Canvas server
     */
    public static void logout () throws CanvasProjectException
    {
        Rest.deleteRefeshToken(baseURL);
    }

    /**
     * Returns the name of the authorized user or null if not authorized
     */
    public static String getUsername ()
    {
        return Rest.getStudentName();
    }

    /**
     * Uses the code to authorize with the Canvas server.
     */
    public static void authorize (String code) throws CanvasProjectException
    {
        Rest.authorize(baseURL, code);
    }

    /**
     * Returns an ArrayList containing a Course object for each Canvas course that contains the config in the root file
     * folder.
     */
    public static ArrayList<Course> getCourses () throws CanvasProjectException
    {
        try
        {
            // This will contain the information about courses that have the special XML file
            ArrayList<Course> list = new ArrayList<>();

            // Get the courses in which the user is enrolled
            JSONArray courses = Rest.getRequestArray(baseURL, "api/v1/courses?per_page=100&enrollment_state=active");

            // Identify courses that have the special XML file
            for (int i = 0; i < courses.length(); i++)
            {
                JSONObject course = courses.getJSONObject(i);
                try
                {
                    JSONArray files = Rest.getRequestArray(baseURL, "api/v1/courses/" + course.getInt("id") + "/files?search_term=" + CONFIG_FILE);
                    if (files.length() == 1)
                    {
                        list.add(new Course(course));
                    }
                }
                catch (StatusCodeException e)
                {
                    if (e.getStatus() != HttpStatus.SC_UNAUTHORIZED) throw e;
                }
            }

            // Return the final list
            return list;
        }
        catch (StatusCodeException e)
        {
            throw new CanvasProjectException(e, "while finding courses");
        }
    }

    /**
     * Returns an ArrayList containing each assignment to which submissions of zip files are possible at this time
     */
    public static ArrayList<Assignment> getAssignments (int courseID) throws CanvasProjectException
    {
        try
        {
            // Get all the assignments
            JSONArray assignments = Rest.getRequestArray(baseURL, "api/v1/courses/" + courseID + "/assignments");

            // Find the ones that are pertinent
            ArrayList<Assignment> openAssignments = new ArrayList<>();
            for (int i = 0; i < assignments.length(); i++)
            {
                Assignment assignment = new Assignment(assignments.getJSONObject(i));
                if (assignment.isOpen() && assignment.acceptsZip())
                {
                    openAssignments.add(assignment);
                }
            }
            return openAssignments;
        }
        catch (StatusCodeException e)
        {
            throw new CanvasProjectException(e, "while getting assignments");
        }
    }

    /**
     * Returns an ArrayList containing all projects (assignmentOnly = false) or assignment projects (assignmentOnly =
     * true) contained in the projectFolder.
     */
    public static ArrayList<Project> getProjects (int courseID, String rootFolder, String projectFolder, boolean assignmentOnly)
            throws CanvasProjectException
    {
        try
        {
            // Get information about the projectFolder
            JSONArray path = Rest.getRequestArray(baseURL,
                    "api/v1/courses/" + courseID + "/folders/by_path/" + rootFolder + "/" + projectFolder + "?per_page=100");
            int folderID = path.getJSONObject(path.length() - 1).getInt("id");

            // Get the nested folders (they contain assignment projects)
            ArrayList<Project> projects = new ArrayList<>();
            JSONArray folders = Rest.getRequestArray(baseURL, "api/v1/folders/" + folderID + "/folders?per_page=100");
            for (int i = 0; i < folders.length(); i++)
            {
                projects.add(new Project(folders.getJSONObject(i).getString("name"), true));
            }

            // Get the zip files (they contain non-assignment projects)
            if (!assignmentOnly)
            {
                JSONArray files = Rest.getRequestArray(baseURL, "api/v1/folders/" + folderID + "/files?per_page=100");
                for (int i = 0; i < files.length(); i++)
                {
                    JSONObject file = files.getJSONObject(i);
                    String name = file.getString("filename");
                    if (name.endsWith(".zip"))
                    {
                        projects.add(new Project(files.getJSONObject(i).getString("filename"), false));
                    }
                }
            }

            // Sort and return
            Collections.sort(projects);
            return projects;
        }
        catch (StatusCodeException e)
        {
            throw new CanvasProjectException(e, "while getting projects");
        }
    }

    /**
     * Downloads and returns the course configuration, or null if it doesn't exist.
     */
    public static CourseProfile getCourseProfile (int courseID) throws CanvasProjectException
    {
        String downloadURL = Canvas.getFileFromFolder(courseID, "", CONFIG_FILE);
        return (downloadURL == null) ? null : new CourseProfile(courseID, downloadXMLFile(downloadURL));
    }

    /**
     * Downloads and returns the format file from the root folder.
     */
    public static Document getFormatFile (int courseID, String rootFolder, String formatFilename) throws CanvasProjectException
    {
        String downloadURL = getFileFromFolder(courseID, rootFolder, formatFilename);
        if (downloadURL == null) throw new CanvasProjectException("Format file not found on server");
        return downloadXMLFile(downloadURL);
    }

    /**
     * Returns the download url to the library file from the root folder, or null if there isn't one.
     */
    public static String getLibraryFileURL (int courseID, String rootFolder, String libraryFileName) throws CanvasProjectException
    {
        String downloadURL = getFileFromFolder(courseID, rootFolder, libraryFileName);
        if (downloadURL == null) throw new CanvasProjectException("Library file not found on server");
        return downloadURL;
    }

    /**
     * Returns the download URL of the verification file for the assignment in the project folder, or null if there
     * isn't one.
     */
    public static String getVerificationTests (CourseProfile profile, String assignmentName) throws Exception
    {
        // Look for the assignment folder
        for (ProjectType type : profile.getProjectTypes())
        {
            ArrayList<Project> info = Canvas.getProjects(profile.getCourseID(), profile.getRootFolder(), type.getFolder(), true);
            for (Project p : info)
            {
                if (p.isAssignment() && p.getProjectName().equals(assignmentName))
                {
                    String path = profile.getRootFolder() + "/" + type.getFolder() + "/" + assignmentName;
                    return getFileFromFolder(profile.getCourseID(), path, new FilenameFilter()
                    {
                        public boolean accept (String filename)
                        {
                            return filename.toLowerCase().endsWith(".jar");
                        }
                    });
                }
            }
        }

        // Didn't find it
        return null;
    }

    /**
     * Runs the consumer on the body returned by the request encoded into the absoluteURL
     */
    public static <T> T getRequestStream (String absoluteURL, StreamConsumer<T> consumer) throws CanvasProjectException
    {
        return Rest.getRequestStream(absoluteURL, baseURL, consumer);
    }

    /**
     * Downloads the the named zipped project file. The file will be moved to a temporary folder, and the method will
     * return the absolute path to it. Returns null if there is no such file.
     */
    public static File downloadProject (int courseID, String rootFolder, String projectFolder, final String projectName, boolean isAssignment)
            throws CanvasProjectException
    {
        String downloadURL;

        if (isAssignment)
        {
            downloadURL = getFileFromFolder(courseID, rootFolder + "/" + projectFolder + "/" + projectName, new FilenameFilter()
            {
                public boolean accept (String filename)
                {
                    return filename.toLowerCase().endsWith(".zip");
                }
            });
        }
        else
        {
            downloadURL = getFileFromFolder(courseID, rootFolder + "/" + projectFolder, projectName + ".zip");
        }

        return (downloadURL == null) ? null : downloadTempFile(downloadURL, "zip");
    }

    /**
     * Uploads the file to an assignment in a course, returning the uploaded file's ID.
     */
    public static int uploadAssignment (int courseID, String assignID, String filepath) throws CanvasProjectException
    {
        // Tell canvas about the file and its size to obtain upload parameters
        File file = new File(filepath);
        JSONObject body = new JSONObject();
        body.put("name", file.getName());
        body.put("size", file.length());
        JSONObject response = Rest.postRequest(baseURL, "api/v1/courses/" + courseID + "/assignments/" + assignID + "/submissions/self/files", body);

        // Create the body for the actual upload request
        JSONObject uploadParams = response.getJSONObject("upload_params");
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        for (Object o : uploadParams.keySet())
        {
            String key = (String) o;
            builder.addPart(key, new StringBody(uploadParams.getString(key), ContentType.TEXT_PLAIN));
        }
        builder.addPart("file", new FileBody(file));

        // Now we can create the second post
        // Not done in a helper method because of multiform content type
        String uploadURL = response.getString("upload_url");
        response = Rest.postRequestMultipart(uploadURL, builder);
        return response.getInt("id");
    }

    /**
     * Submits a previously uploaded file to an assignment. Returns a string containing the submission time.
     */
    public static String submitAssignment (int courseID, String assignID, int fileID) throws CanvasProjectException
    {
        JSONObject body = new JSONObject();
        JSONObject submissionType = new JSONObject();
        submissionType.put("submission_type", "online_upload");
        submissionType.append("file_ids", fileID);
        body.put("submission", submissionType);
        JSONObject result = Rest.postRequest(baseURL, "api/v1/courses/" + courseID + "/assignments/" + assignID + "/submissions", body);
        return result.getString("submitted_at");
    }
    
    /**
     * Logs the exception on the server
     */
    public static void logException (Exception exception) throws CanvasProjectException
    {
        try (StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw))
        {
            exception.printStackTrace(pw);
            Rest.logException(baseURL, "logException", sw.toString());
        }   
        catch (IOException e)
        {
            throw new CanvasProjectException(e, "while logging exception");
        }
    }

    /**
     * Returns the URL of the file with the given name that appears in the folder at the end of the path. Returns null
     * if there is no such file.
     */
    private static String getFileFromFolder (int courseID, String path, final String filename) throws CanvasProjectException
    {
        return getFileFromFolder(courseID, path, new FilenameFilter()
        {
            public boolean accept (String file)
            {
                return file.equals(filename);
            }
        });
    }

    /**
     * Returns the URL of a file that satisfies the filter that appears in the folder at the end of the path. Returns
     * null if there is no such file.
     */
    private static String getFileFromFolder (int courseID, String path, FilenameFilter filter) throws CanvasProjectException
    {
        try
        {
            // Get ID of folder
            JSONArray folderInfo = Rest.getRequestArray(baseURL, "api/v1/courses/" + courseID + "/folders/by_path/" + path + "?per_page=100");
            JSONObject folder = folderInfo.getJSONObject(folderInfo.length() - 1);
            int folderID = folder.getInt("id");

            // Get the information about the file of interest
            JSONArray files = Rest.getRequestArray(baseURL, "api/v1/folders/" + folderID + "/files?per_page=100");
            for (int i = 0; i < files.length(); i++)
            {
                JSONObject file = files.getJSONObject(i);
                String name = file.getString("filename");
                if (filter.accept(name))
                {
                    return file.getString("url");
                }
            }

            // Didn't find it
            return null;
        }
        catch (StatusCodeException e)
        {
            throw new CanvasProjectException(e, "Finding file");
        }
    }

    /**
     * Reads bytes from the downloadURL and writes them to a temporary file, which is returned.
     */
    private static File downloadTempFile (String downloadURL, String extension) throws CanvasProjectException
    {
        try
        {
            // Create a temporary file
            final File file = File.createTempFile("CPMTempFile", "." + extension);
            file.deleteOnExit();

            // Access the URL and write the bytes into the file
            getRequestStream(downloadURL, new StreamConsumer<Void>()
            {
                public Void consume (InputStream input) throws CanvasProjectException
                {
                    try (OutputStream output = new FileOutputStream(file))
                    {
                        byte[] bytes = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = input.read(bytes)) > 0)
                        {
                            output.write(bytes, 0, bytesRead);
                        }
                        return null;
                    }
                    catch (IOException e)
                    {
                        throw new CanvasProjectException(e, "Problem downloading file from Canvas");
                    }
                }
            });

            return file;
        }
        catch (IOException e)
        {
            throw new CanvasProjectException(e, "Problem downloading file from Canvas");
        }
    }

    /**
     * Reads bytes from the downloadURL and converts them into an XML document, which is returned.
     */
    private static Document downloadXMLFile (String downloadURL) throws CanvasProjectException
    {
        try
        {
            final DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document xmlFile = getRequestStream(downloadURL, new StreamConsumer<Document>()
            {
                public Document consume (InputStream input) throws CanvasProjectException
                {
                    try
                    {
                        return documentBuilder.parse(input);
                    }
                    catch (IOException | SAXException e)
                    {
                        throw new CanvasProjectException(e, "Problem downloading XML file from Canvas");
                    }
                }
            });

            // Normalize and return
            xmlFile.getDocumentElement().normalize();
            return xmlFile;
        }
        catch (ParserConfigurationException e)
        {
            throw new CanvasProjectException(e, "Problem downloading XML file from Canvas");
        }
    }
}
