package cpm.data;

import java.util.ArrayList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Represents information from course configuration file
 */
public class CourseProfile
{
    /** Canvas course ID */
    private int courseID;

    /** Canvas folder containing files read by this plugin */
    private String rootFolder;

    /** File within root folder that contains formatting rules */
    private String formatFileName;

    /** File within root folder that contains course library */
    private String libraryFileName;

    /** Information about projects contained within root folder */
    private ArrayList<ProjectType> projectTypes;

    /**
     * Create a CourseProfile
     */
    public CourseProfile (int courseID, Document configFile)
    {
        // Get the pertinent portion of config file
        Element course = (Element) configFile.getElementsByTagName("course").item(0);

        // Get the attributes
        this.courseID = courseID;
        rootFolder = course.getAttribute("root");
        formatFileName = course.getAttribute("format");
        libraryFileName = course.getAttribute("library");

        // Accumulate the project types
        NodeList projects = configFile.getElementsByTagName("project");
        projectTypes = new ArrayList<>();
        for (int i = 0; i < projects.getLength(); i++)
        {
            Element project = (Element) projects.item(i);
            projectTypes.add(new ProjectType(project.getAttribute("name"), project.getAttribute("folder")));
        }
    }

    /**
     * Returns Canvas course ID
     */
    public int getCourseID ()
    {
        return courseID;
    }

    /**
     * Returns project type information
     */
    public ArrayList<ProjectType> getProjectTypes ()
    {
        return projectTypes;
    }

    /**
     * Returns the root folder
     */
    public String getRootFolder ()
    {
        return rootFolder;
    }

    /**
     * Returns the format filename
     */
    public String getFormatFilename ()
    {
        return formatFileName;
    }

    /**
     * Returns the library filename
     */
    public String getLibraryFilename ()
    {
        return libraryFileName;
    }
}
