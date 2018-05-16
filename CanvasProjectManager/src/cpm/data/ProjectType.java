package cpm.data;

/**
 * Describes a project type
 */
public class ProjectType
{
    /** Type of project */
    private String type;
    
    /** Name of folder that contains projects of this type */
    private String folder;
    
    /**
     * Creates a ProjectType
     */
    public ProjectType (String name, String folder)
    {
        this.type = name;
        this.folder = folder;
    }

    /**
     * Returns project type
     */
    public String getType ()
    {
        return type;
    }

    /**
     * Returns folder
     */
    public String getFolder ()
    {
        return folder;
    }
}
