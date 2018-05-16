package cpm.pages;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import cpm.data.CourseProfile;
import cpm.data.ProjectType;
import cpm.logging.CanvasProjectException;
import cpm.net.Canvas;
import cpm.wizards.CPMWizardPage;

/**
 * Wizard page where user chooses what type of project to download.
 */
public class ProjectTypePage extends CPMWizardPage<CourseProfile>
{
    /** Radio button that is used to indicate user wants a blank project */
    private Button blankButton;
    
    /** Name of blank project */
    private Text projectNameBox;
    
    /** The page that came before this one */
    private CoursePage coursePage;
    
    /** The profile of the course selected on the coursePage */
    private CourseProfile profile;
    
    /** The page-specific control */
    private Composite mainControl;
    
    /** The selected project type, or null if none selected */
    private String selectedProjectTypeName;

    /**
     * Creates this page
     */
    public ProjectTypePage (String pageName, CoursePage coursePage)
    {
        super(pageName, "Select the type of project you wish to create", "Downloading CPM configuration file");
        this.coursePage = coursePage;
    }

    /**
     * Returns profile of current course
     */
    public CourseProfile getCourseProfile ()
    {
        return profile;
    }

    /**
     * Returns the type of project that was selected, or null if none selected
     */
    public ProjectType getSelectedProjectType ()
    {
        for (ProjectType p: profile.getProjectTypes())
        {
            if (p.getType().equals(selectedProjectTypeName))
            {
                return p;
            }
        }
        return null;
    }

    /**
     * Return the name provided for a blank project, or null if a blank project wasn't selected.
     */
    public String getProjectName ()
    {
        return (blankButton.getSelection()) ? projectNameBox.getText() : null;
    }
    
    /**
     * Reports whether a blank project was selected
     */
    public boolean isBlankProject ()
    {
        return blankButton.getSelection();
    }

    @Override
    protected Control createMainControl (Composite container)
    {
        mainControl = new Composite(container, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 3;
        layout.makeColumnsEqualWidth = true;
        mainControl.setLayout(layout);
        return mainControl;
    }

    @Override
    protected void clearMainControl ()
    {
        for (Control control : mainControl.getChildren())
        {
            control.dispose();
        }
        selectedProjectTypeName = null;
    }

    @Override
    protected void redisplayMainControl ()
    {
        clearMainControl();

        // Add a button for each project type
        for (ProjectType projectType : profile.getProjectTypes())
        {
            Button button = new Button(mainControl, SWT.RADIO);
            button.addSelectionListener(new SelectionListener()
            {
                @Override
                public void widgetSelected (SelectionEvent e)
                {
                    projectNameBox.setEnabled(false);
                    setPageComplete(true);
                    selectedProjectTypeName = ((Button) e.getSource()).getText();
                }

                @Override
                public void widgetDefaultSelected (SelectionEvent e)
                {
                }
            });
            GridData tempdata = new GridData();
            tempdata.horizontalAlignment = GridData.FILL;
            tempdata.horizontalSpan = 3;
            button.setLayoutData(tempdata);
            button.setText(projectType.getType());
        }

        // Add a button for a blank project
        blankButton = new Button(mainControl, SWT.RADIO);
        blankButton.addSelectionListener(new SelectionListener()
        {
            @Override
            public void widgetSelected (SelectionEvent e)
            {
                projectNameBox.setEnabled(true);
                setPageComplete(projectNameBox.getText().length() > 0);
                selectedProjectTypeName = null;
            }

            @Override
            public void widgetDefaultSelected (SelectionEvent e)
            {
            }
        });
        
        GridData tempdata = new GridData();
        tempdata.horizontalAlignment = GridData.FILL;
        tempdata.horizontalSpan = 1;
        blankButton.setLayoutData(tempdata);
        blankButton.setText("Blank project called");

        // Add a text box for holding blank project name
        projectNameBox = new Text(mainControl, SWT.SINGLE | SWT.BORDER);
        tempdata = new GridData();
        tempdata.horizontalAlignment = GridData.FILL;
        tempdata.horizontalSpan = 2;
        projectNameBox.setLayoutData(tempdata);
        projectNameBox.setEnabled(false);

        // Project name listener
        projectNameBox.addModifyListener(new ModifyListener()
        {
            @Override
            public void modifyText (ModifyEvent e)
            {
                setPageComplete(projectNameBox.getText().length() > 0);
            }
        });
        
        mainControl.layout();
    }

    @Override
    protected CourseProfile getDataFromServer () throws CanvasProjectException
    {
        return Canvas.getCourseProfile(coursePage.getSelectedCourse().getCourseID());
    }
    

    @Override
    protected CourseProfile getPageData ()
    {
        return profile;
    }

    @Override
    protected void setPageData (CourseProfile data)
    {
        profile = data;
    }
}
