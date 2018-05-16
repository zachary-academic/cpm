package cpm.pages;

import java.util.ArrayList;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.List;
import cpm.data.Assignment;
import cpm.data.Course;
import cpm.logging.CanvasProjectException;
import cpm.net.Canvas;
import cpm.wizards.CPMWizardPage;

/**
 * Wizard page where user chooses which assignment to use 
 */
public class AssignmentPage extends CPMWizardPage<ArrayList<Assignment>>
{
    /** Selectable list of assignment names */
    private List assignmentList;
    
    /** List of assignments */
    private ArrayList<Assignment> assignments;
    
    /** CoursePage that came before this one */
    private CoursePage coursePage;
    
    /** Course selected on coursePage */
    private Course selectedCourse;
    
    /** Assignment selected on this page */
    private Assignment selectedAssignment;

    /**
     * Creates this page
     */
    public AssignmentPage (String pageName, String legend, CoursePage coursePage)
    {
        super(pageName, legend, "Downloading assignments");
        this.coursePage = coursePage;
    }
    
    /**
     * Returns the assignment selected on this page
     */
    public Assignment getSelectedAssignment ()
    {
        return selectedAssignment;
    }

    @Override
    public void setVisible (boolean visible)
    {
        if (visible)
        {
            selectedCourse = coursePage.getSelectedCourse();
        }
        super.setVisible(visible);
    }

    @Override
    protected Control createMainControl (Composite container)
    {
        assignmentList = new List(container, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);

        assignmentList.addSelectionListener(new SelectionListener()
        {
            @Override
            public void widgetSelected (SelectionEvent e)
            {
                int index = assignmentList.getSelectionIndex();
                if (index >= 0)
                {
                    setPageComplete(true);
                    selectedAssignment = assignments.get(index);
                }                             
            }

            @Override
            public void widgetDefaultSelected (SelectionEvent e)
            {
            }
        });
        
        return assignmentList;
    }
    
    @Override
    protected void clearMainControl ()
    {
        assignmentList.removeAll();
        selectedAssignment = null;
    }

    @Override
    protected void redisplayMainControl ()
    {
        assignmentList.removeAll();       
        for (Assignment a: assignments)
        {
            assignmentList.add(a.getName());
        }
    }

    @Override
    protected ArrayList<Assignment> getDataFromServer () throws CanvasProjectException
    {
        return Canvas.getAssignments(selectedCourse.getCourseID());
    }
    
    @Override
    protected ArrayList<Assignment> getPageData ()
    {
        return assignments;
    }

    @Override
    protected void setPageData (ArrayList<Assignment> data)
    {
        assignments = data;
    }
}
