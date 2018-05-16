package cpm.data;

/**
 * Contains information about a zipped project
 */
public class Project implements Comparable<Project>
{
    /** Project name */
    private String projectName;
    
    /** Is this project associate with an assignment? */
    private boolean isAssignment;
    
    /**
     * Creates a Project
     */
    public Project (String projectName, boolean isAssignment)
    {
        if (!isAssignment)
        {
            int index = projectName.lastIndexOf(".");
            if (index >= 0)
            {
                projectName = projectName.substring(0, index);
            }
        }
        this.projectName = projectName;
        this.isAssignment = isAssignment;
    }

    /**
     * Return project name
     */
    public String getProjectName ()
    {
        return projectName;
    }

    /**
     * Report whether this project is an assignment
     */
    public boolean isAssignment ()
    {
        return isAssignment;
    }

    /**
     * Compares two projects
     */
    @Override
    public int compareTo (Project p)
    {
        return this.projectName.compareToIgnoreCase(p.projectName);
    }
}
