package cpm.wizards;

import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ProgressBar;
import cpm.logging.CanvasProjectException;
import cpm.logging.Log;
import cpm.net.Canvas;
import cpm.pages.CoursePage;

/**
 * Wizard page where user can make a choice from a list. This abstract class makes Canvas calls and deals with the
 * required multi-threading and error logging. Derived classes don't need to do any of this.
 */
public abstract class CPMWizardPage<T> extends WizardPage
{
    /** The top-level container of what appears in the window */
    private Composite container;

    /** Button for refreshing window */
    private Button refreshButton;

    /** Button for logging out and closing window */
    private Button logoutButton;

    /** Displayed when work is being done on a separate thread */
    private ProgressBar progressBar;

    /** Message displayed when separate thread is active */
    private String downloadMessage;

    /** True when this page has never been displayed */
    private boolean undisplayed = true;

    /** Set to true when Cancel button is clicked */
    private boolean canceled = false;

    /**
     * Construct page given name, description, and download message.
     */
    protected CPMWizardPage (String pageName, String pageDescription, String downloadMessage)
    {
        super(pageName);
        setTitle(pageName);
        setDescription(pageDescription);
        this.downloadMessage = downloadMessage;
    }

    /**
     * Clears cached data, closes the wizard, and logs out from Canvas
     */
    private void logout ()
    {
        // Set the controls
        refreshButton.setEnabled(false);
        logoutButton.setEnabled(false);
        progressBar.setVisible(true);

        // Clear the course cache so that it will be refreshed next time
        CoursePage.logout();

        // Log out from Canvas
        Job logoutJob = Job.create("Logging off Canvas", new ICoreRunnable()
        {
            @Override
            public void run (IProgressMonitor monitor)
            {
                try
                {
                    Canvas.logout();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    Log.reportException(e);
                }
                finally
                {
                    Display.getDefault().syncExec(new Runnable()
                    {
                        @Override
                        public void run ()
                        {
                            getContainer().getShell().close();
                        }
                    });
                }
            }
        });
        logoutJob.schedule();
    }

    /**
     * Lay out the window
     */
    @Override
    public void createControl (Composite parent)
    {
        // Set up overall window
        container = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 5;
        layout.makeColumnsEqualWidth = true;
        container.setLayout(layout);

        // Set the layout so it's the biggest thing
        Control mainControl = createMainControl(container);
        GridData tempdata = new GridData();
        tempdata.horizontalAlignment = GridData.FILL;
        tempdata.horizontalSpan = 5;
        tempdata.grabExcessVerticalSpace = true;
        tempdata.verticalAlignment = GridData.FILL;
        mainControl.setLayoutData(tempdata);

        // Button to refresh item list
        refreshButton = new Button(container, SWT.NONE);
        tempdata = new GridData();
        tempdata.horizontalAlignment = GridData.FILL;
        tempdata.horizontalSpan = 1;
        refreshButton.setLayoutData(tempdata);
        refreshButton.setText("Refresh");

        // Button to logout
        logoutButton = new Button(container, SWT.NONE);
        tempdata = new GridData();
        tempdata.horizontalAlignment = GridData.FILL;
        tempdata.horizontalSpan = 1;
        logoutButton.setLayoutData(tempdata);
        logoutButton.setText("Logout");

        // Progress bar
        progressBar = new ProgressBar(container, SWT.BORDER | SWT.SINGLE | SWT.INDETERMINATE);
        tempdata = new GridData();
        tempdata.horizontalAlignment = GridData.FILL;
        tempdata.horizontalSpan = 3;
        progressBar.setLayoutData(tempdata);
        progressBar.setVisible(false);

        // Wire refresh button actions
        refreshButton.addSelectionListener(new SelectionListener()
        {
            @Override
            public void widgetSelected (SelectionEvent e)
            {
                refreshData();
            }

            @Override
            public void widgetDefaultSelected (SelectionEvent e)
            {
            }
        });

        // Wire logout button actions
        logoutButton.addSelectionListener(new SelectionListener()
        {
            @Override
            public void widgetSelected (SelectionEvent e)
            {
                logout();
            }

            @Override
            public void widgetDefaultSelected (SelectionEvent e)
            {
            }
        });

        // Finish up
        setControl(container);
        setPageComplete(false);
    }

    /**
     * Refresh or redisplay as necessary when window becomes visible
     */
    @Override
    public void setVisible (boolean visible)
    {
        if (visible)
        {
            if (getPageData() == null)
            {
                refreshData();
            }
            else if (undisplayed)
            {
                redisplayMainControl();
            }
            undisplayed = false;
        }
        super.setVisible(visible);
    }
    

    @Override
    public boolean isPageComplete ()
    {
        return super.isPageComplete() && isCurrentPage();
    }
    
    @Override
    public void setPageComplete (boolean complete)
    {
        if (complete)
        {
            CPMWizardPage<?> nextPage = (CPMWizardPage<?>) getNextPage();
            if (nextPage != null)
            {
                nextPage.setPageData(null);
                nextPage.clearMainControl();
            }
        }
        super.setPageComplete(complete);
    }

    /**
     * Redisplay everything
     */
    private void redisplay ()
    {
        refreshButton.setEnabled(true);
        logoutButton.setEnabled(true);
        setPageComplete(false);
        progressBar.setVisible(false);
        redisplayMainControl();
        container.layout();
    }

    /**
     * Record that cancel button was pressed
     */
    public void cancel ()
    {
        canceled = true;
    }

    /**
     * Get a new copy of this window's data from Canvas
     */
    private void refreshData ()
    {
        // Set up for an update
        setPageData(null);
        clearMainControl();
        refreshButton.setEnabled(false);
        logoutButton.setEnabled(false);
        setPageComplete(false);
        progressBar.setVisible(true);
        container.layout();

        // Do the download
        Job downloadCoursesJob = Job.create(downloadMessage, new ICoreRunnable()
        {
            @Override
            public void run (IProgressMonitor monitor)
            {
                T data = null;
                try
                {
                    data = getDataFromServer();
                }
                catch (Exception e)
                {
                    Log.reportException(e);
                    data = null;
                }

                // Use the UI thread to redisplay
                final T newData = data;
                Display.getDefault().asyncExec(new Runnable()
                {
                    @Override
                    public void run ()
                    {
                        if (!canceled && !container.isDisposed())
                        {
                            setPageData(newData);
                            refreshButton.setEnabled(true);
                            logoutButton.setEnabled(true);
                            setPageComplete(false);
                            progressBar.setVisible(false);
                            if (newData != null)
                            {
                                redisplay();
                            }
                        }
                    }
                });

            }
        });
        downloadCoursesJob.schedule();
    }

    /**
     * This is overridden by derived classes to create the page-specific control.
     */
    protected abstract Control createMainControl (Composite container);

    /**
     * This is overridden by derived classes to clear the page-specific control.
     */
    protected abstract void clearMainControl ();

    /**
     * This is overridden by derived classes to redisplay the page-specific control.
     */
    protected abstract void redisplayMainControl ();

    /**
     * This is overridden by derived classes to get page-specific data from the server.
     */
    protected abstract T getDataFromServer () throws CanvasProjectException;

    /**
     * This is overridden by derived classes to return the page-specific data.
     */
    protected abstract T getPageData ();

    /**
     * This is overridden by derived classes to set the page-specific data.
     */
    protected abstract void setPageData (T data);
}
