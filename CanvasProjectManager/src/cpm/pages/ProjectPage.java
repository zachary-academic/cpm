package cpm.pages;

import java.util.ArrayList;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.List;
import cpm.data.Course;
import cpm.data.CourseProfile;
import cpm.data.Project;
import cpm.data.ProjectType;
import cpm.logging.CanvasProjectException;
import cpm.net.Canvas;
import cpm.wizards.CPMWizardPage;

/**
 * Wizard page where user chooses which project to download/create.
 */
public class ProjectPage extends CPMWizardPage<ArrayList<Project>>
{
    /** The selectable list of names of projects */
    private List projectList;
    
    /** List of projects */
    private ArrayList<Project> projects;
    
    /** The coursePage that was first in the sequence */
    private CoursePage coursePage;
    
    /** The selection page that was second in the sequence */
    private ProjectTypePage selectionPage;
    
    /** Course selected on the coursePage */
    private Course selectedCourse;
    
    /** The CourseProfile of the selected course */
    private CourseProfile selectedCourseProfile;
    
    /** The project type selected on the selection page */
    private ProjectType selectedProjectType;
    
    /** The project selected on this page */
    private Project selectedProject;

    /**
     * Creates this page
     */
    public ProjectPage (String pageName, CoursePage coursePage, ProjectTypePage selectionPage)
    {
        super(pageName, "Select the project you would like to create", "Creating project");
        this.coursePage = coursePage;
        this.selectionPage = selectionPage;
    }

    @Override
    public void setVisible (boolean visible)
    {
        if (visible)
        {
            selectedCourse = coursePage.getSelectedCourse();
            selectedCourseProfile = selectionPage.getCourseProfile();
            selectedProjectType = selectionPage.getSelectedProjectType();
        }
        super.setVisible(visible);
    }

    @Override
    protected Control createMainControl (Composite container)
    {
        projectList = new List(container, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);

        projectList.addSelectionListener(new SelectionListener()
        {
            @Override
            public void widgetSelected (SelectionEvent e)
            {
                int index = projectList.getSelectionIndex();
                if (index >= 0)
                {
                    setPageComplete(true);
                    selectedProject = projects.get(index);
                }                             
            }

            @Override
            public void widgetDefaultSelected (SelectionEvent e)
            {
            }
        });
        
        return projectList;
    }
    
    @Override
    protected void clearMainControl ()
    {
        projectList.removeAll();
        selectedProject = null;
    }

    @Override
    protected void redisplayMainControl ()
    {
        projectList.removeAll();       
        for (Project p: projects)
        {
            projectList.add(p.getProjectName());
        }
    }

    @Override
    protected ArrayList<Project> getDataFromServer () throws CanvasProjectException
    {
        return Canvas.getProjects(selectedCourse.getCourseID(), selectedCourseProfile.getRootFolder(), selectedProjectType.getFolder(), false);
    }
    
    @Override
    protected ArrayList<Project> getPageData ()
    {
        return projects;
    }

    @Override
    protected void setPageData (ArrayList<Project> data)
    {
        projects = data;
    }

    public Project getSelectedProject ()
    {
        return selectedProject;
    }
}
