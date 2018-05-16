package cpm.data;

import java.io.PrintStream;
import java.util.Scanner;
import org.json.JSONObject;

/**
 * Information about a Canvas course
 */
public class Course
{
    /** Name of course */
    private String name;
    
    /** Canvas course ID */
    private int courseID;
    
    /** Creates Course from its Canvas API description */
    public Course (JSONObject course)
    {
        this.name = course.getString("name");
        this.courseID = course.getInt("id");
    }
    
    /** 
     * Serializes course 
     * */
    public void save (PrintStream writer)
    {
        writer.println(name);
        writer.println(courseID);
    }
    
    /** 
     * Deserializes course
     */
    public Course (Scanner scn)
    {
        name = scn.nextLine();
        courseID = Integer.parseInt(scn.nextLine().trim());
    }
    
    /**
     * Returns name of course
     */
    public String getName ()
    {
        return name;
    }

    /**
     * Returns Canvas ID of course
     */
    public int getCourseID ()
    {
        return courseID;
    }
}
