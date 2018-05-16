package cpm.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import cpm.wizards.ProjectSubmissionWizard;

/**
 * Handles clicks on the Submit context menu
 */
public class SubmissionHandler extends AbstractHandler
{
    /**
     * Handles clicks on the Submit context menu
     */
    @Override
    public Object execute (ExecutionEvent event) throws ExecutionException
    {
        Shell activeShell = HandlerUtil.getActiveShell(event);
        IResource project = HandlerUtilities.extractSelection(HandlerUtil.getActiveMenuSelection(event)).getProject();
        IWizard wizard = new ProjectSubmissionWizard(project);
        WizardDialog dialog = new WizardDialog(activeShell, wizard);
        dialog.open();
        return null;
    }
}
