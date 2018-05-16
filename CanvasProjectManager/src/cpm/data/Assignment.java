package cpm.data;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Information about a Canvas assignment
 */
public class Assignment
{
    /** Name of assignment */
    private String name;
    
    /** Canvas ID of assignment */
    private String assignmentID;
    
    /** Is the assignment open for submissions? */
    private boolean isOpen;
    
    /** Does the assignment accept zip file submissions? */ 
    private boolean acceptsZip;

    /**
     * Creates an Assignment from its Canvas API description
     */
    public Assignment (JSONObject assignment)
    {
        // Obtain attributes
        name = assignment.getString("name");
        assignmentID = "" + assignment.getInt("id");
        isOpen = !assignment.getBoolean("locked_for_user");
        
        // Determine if it accepts zip files
        acceptsZip = false;
        if (assignment.has("allowed_extensions"))
        {
            JSONArray accepts = assignment.getJSONArray("allowed_extensions");
            for (int i = 0; i < accepts.length(); i++)
            {
                if (accepts.getString(i).toLowerCase().equals("zip"))
                {
                    acceptsZip = true;
                }
            }
        }
    }

    /**
     * Returns assignment name
     */
    public String getName ()
    {
        return name;
    }

    /**
     * Returns assignment ID
     */
    public String getID ()
    {
        return assignmentID;
    }

    /**
     * Reports whether assignment is open for submissions
     */
    public boolean isOpen ()
    {
        return isOpen;
    }

    /**
     * Reports whether assignment accepts zip file submissions
     */
    public boolean acceptsZip ()
    {
        return acceptsZip;
    }
}
