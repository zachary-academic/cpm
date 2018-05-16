package cpm.wizards;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.internal.wizards.datatransfer.ArchiveFileExportOperation;
import cpm.logging.Log;
import cpm.net.Authentication;
import cpm.net.Canvas;
import cpm.pages.AssignmentPage;
import cpm.pages.AuthorizationPage;
import cpm.pages.CoursePage;

/**
 * A wizard that supports submitting projects to Canvas.
 */
@SuppressWarnings("restriction")
public class ProjectSubmissionWizard extends CanvasWizard
{
    /** Displayed when the user is unauthorized */
    private AuthorizationPage authPage;

    /** First page: pick a course */
    private CoursePage coursePage;

    /** Second page: pick an assignment */
    private AssignmentPage assignmentPage;
    
    /**
     * The selected Eclipse project
     */
    private IResource project;

    /**
     * Creates this wizard, which operates on project
     */
    public ProjectSubmissionWizard (IResource project)
    {
        this.project = project;
    }

    @Override
    public void addPages ()
    {
        if (!Canvas.authorized())
        {
            addPage(authPage = new AuthorizationPage("Authorization"));
        }
        else
        {
            addPage(coursePage = new CoursePage("Courses", "Select the course to which you wish to submit a project"));
            addPage(assignmentPage = new AssignmentPage("Assignments", "Select the assignment to which you wish to submit", coursePage));
        }
    }

    @Override
    public IWizardPage getNextPage (IWizardPage currentPage)
    {
        if (currentPage == coursePage)
        {
            return assignmentPage;
        }
        else
        {
            return null;
        }
    }

    @Override
    public boolean canFinish ()
    {
        return authPage != null || assignmentPage.isPageComplete();
    }

    @Override
    public boolean performFinish ()
    {
        if (authPage != null)
        {
            Authentication.doAuthorization(false);
        }
        else
        {
            submitProject();
        }
        return true;
    }

    /**
     * Submits the selected project to Canvas
     */
    public void submitProject ()
    {
        final int courseID = coursePage.getSelectedCourse().getCourseID();
        final String assignmentID = assignmentPage.getSelectedAssignment().getID();
        final String assignmentName = assignmentPage.getSelectedAssignment().getName();
        try
        {
            ProgressMonitorDialog dialog = new ProgressMonitorDialog(null);
            dialog.run(true, false, new IRunnableWithProgress()
            {
                // Communicates the result of a forked dialog back to the monitor's thread
                private boolean keepGoing = true;

                @Override
                public void run (IProgressMonitor monitor)
                {
                    try
                    {
                        // Make sure all files are saved
                        monitor.setTaskName("Saving project");
                        PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
                        {
                            @Override
                            public void run ()
                            {
                                keepGoing = IDE.saveAllEditors(new IResource[] { project }, true);
                            }
                        });
                        if (!keepGoing) return;

                        // Validate the project
                        monitor.setTaskName("Performing verification");
                        int verificationResult = ProjectVerificationWizard.verify(courseID, assignmentName, project);
                        final Date validateDate = new Date();

                        // If the project did not verify, ask the user if the project should be submitted anyway
                        if (verificationResult < 0)
                        {
                            PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
                            {
                                @Override
                                public void run ()
                                {
                                    keepGoing = MessageDialog.openQuestion(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                                            "Submission", "Your project did not pass verification. Do you wish to submit anyway?");
                                }
                            });
                            if (!keepGoing) return;
                        }

                        // Submit the project
                        String filePath = null;

                        try
                        {
                            monitor.setTaskName("Zipping project");
                            filePath = exportProjectAsZip(project);

                            monitor.setTaskName("Uploading project to Canvas");
                            int fileID = Canvas.uploadAssignment(courseID, assignmentID, filePath);

                            monitor.setTaskName("Submitting project to Canvas");
                            String timestamp = Canvas.submitAssignment(courseID, assignmentID, fileID);
                            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                            final Date submitDate = dateFormat.parse(timestamp);
                            PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                            {
                                @Override
                                public void run ()
                                {
                                    MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Project",
                                            project.getName() + " validated on " + validateDate.toString() + ". Submitted on " + submitDate.toString()
                                                    + ".");
                                }
                            });
                        }
                        finally
                        {
                            // Quietly delete the zip file
                            try
                            {
                                new File(filePath).delete();
                            }
                            catch (Exception e)
                            {
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        Log.reportException(e);
                    }
                    finally
                    {
                        monitor.done();
                    }
                }
            });
        }
        catch (Exception e)
        {
            Log.reportException(e);
        }
    }
    
    /**
     * Zips the project and returns the file path
     */
    private String exportProjectAsZip (IResource project) throws InvocationTargetException, InterruptedException
    {
        // Create the filepath
        String filePath = project.getLocation() + ".zip";
        filePath = filePath.substring(0, filePath.lastIndexOf('/') + 1);
        filePath += project.getName().replaceAll(" ", "_") + ".zip";

        // Make the zip file
        ArchiveFileExportOperation operation = new ArchiveFileExportOperation(project, filePath);
        operation.setUseCompression(true);
        operation.setUseTarFormat(false);
        operation.run(new NullProgressMonitor());

        // Return the file path
        return filePath;
    }
}
