package cpm.pages;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Scanner;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.List;
import cpm.data.Course;
import cpm.logging.CanvasProjectException;
import cpm.net.Canvas;
import cpm.wizards.CPMWizardPage;
import state.StateCache;

/**
 * Wizard page where user chooses which course to use.
 */
public class CoursePage extends CPMWizardPage<ArrayList<Course>>
{
    /** Key used for caching courses */
    private final static String CACHE_KEY = "courses";

    /** Selectable list of courses that support CPM */
    private List courseList;

    /** Courses that support CPM. Static so that value kept across invocations. */
    private static ArrayList<Course> courses;

    /** The selected course or null if no selection */
    private Course selectedCourse;

    /**
     * Initialize courses from cache if possible
     */
    static
    {
        try (Scanner scn = StateCache.read(CACHE_KEY))
        {
            ArrayList<Course> newCourses = new ArrayList<>();
            while (scn.hasNextLine())
            {
                newCourses.add(new Course(scn));
            }
            courses = newCourses;
        }
        catch (Exception e)
        {
        }
    }

    /**
     * Save courses to cache
     */
    private static void saveCourses ()
    {
        if (courses != null)
        {
            try (PrintStream out = StateCache.write("courses"))
            {
                for (Course c : courses)
                {
                    c.save(out);
                }
            }
            catch (Exception e)
            {
            }
        }
    }

    /**
     * Clear cached courses
     */
    private static void clearCourses ()
    {
        StateCache.clear("courses");
    }

    /**
     * Logs out by clearing courses and its cached copy
     */
    public static void logout ()
    {
        courses = null;
        clearCourses();
    }
    
    /**
     * Creates this page
     */
    public CoursePage (String pageName, String legend)
    {
        super(pageName, legend, "Downloading courses");
    }

    /**
     * Returns the selected course, or null if no course is selected.
     */
    public Course getSelectedCourse ()
    {
        return selectedCourse;
    }

    @Override
    protected Control createMainControl (Composite container)
    {
        courseList = new List(container, SWT.BORDER | SWT.SINGLE);

        courseList.addSelectionListener(new SelectionListener()
        {
            @Override
            public void widgetSelected (SelectionEvent e)
            {
                int index = courseList.getSelectionIndex();
                if (index >= 0 && index < courseList.getItemCount())
                {
                    selectedCourse = courses.get(index);
                    setPageComplete(true);
                }
            }

            @Override
            public void widgetDefaultSelected (SelectionEvent e)
            {
            }
        });

        return courseList;
    }

    @Override
    protected void clearMainControl ()
    {
        courseList.removeAll();
        selectedCourse = null;
    }

    @Override
    protected void redisplayMainControl ()
    {
        clearMainControl();
        for (Course course : courses)
        {
            courseList.add(course.getName());
        }
    }

    @Override
    protected ArrayList<Course> getDataFromServer () throws CanvasProjectException
    {
        return Canvas.getCourses();
    }

    @Override
    protected ArrayList<Course> getPageData ()
    {
        return courses;
    }

    @Override
    protected void setPageData (ArrayList<Course> data)
    {
        courses = data;
        saveCourses();
    }
}
