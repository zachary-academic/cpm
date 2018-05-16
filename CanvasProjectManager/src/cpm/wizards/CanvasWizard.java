package cpm.wizards;

import java.io.File;
import org.apache.commons.io.FileUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import cpm.net.Canvas;

/**
 * Base class for creating wizards that interact with Canvas.
 */
public abstract class CanvasWizard extends Wizard implements INewWizard
{
    /**
     * Creates this wizard. Integrates the username (if known) into the window title.
     */
    public CanvasWizard ()
    {
        String username = Canvas.getUsername();
        username = (username == null) ? "" : " - " + username;
        setWindowTitle("Canvas Project Creation" + username);
    }

    @Override
    public void init (IWorkbench workbench, IStructuredSelection selection)
    {
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean performCancel ()
    {
        for (IWizardPage page: getPages())
        {
            ((CPMWizardPage) page).cancel();
        }
        return true;
    }
    
    /**
     * Throws OperationCanceledException if the monitor has been canceled.
     */
    protected static void checkCancel (IProgressMonitor monitor) throws OperationCanceledException
    {
        if (monitor.isCanceled()) throw new OperationCanceledException();
    }
    
    /**
     * Forcibly and quietly deletes a project
     */
    protected void forceProjectDeletion (IProject projectHandle)
    {
        try
        {
            projectHandle.delete(true, null);
        }
        catch (Exception e)
        {
        }
    }

    /**
     * Forcibly and quietly deletes a file or folder
     */
    protected void forceFileDeletion (File file)
    {
        try
        {
            FileUtils.forceDelete(file);
        }
        catch (Exception e)
        {
        }
    }

    /**
     * Forcibly and quietly deletes a resource
     */
    protected void deleteResource (String name)
    {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
        forceProjectDeletion(project);

        File file = new File(ResourcesPlugin.getWorkspace().getRoot().getLocation().toString() + "/" + name);
        forceFileDeletion(file);
    }
    
    /**
     * Forcibly and quietly deletes a file
     */
    protected static void deleteFile (File file)
    {
        try
        {
            file.delete();
        }
        catch (Exception e)
        {
        }
    }
}
