package cpm.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import cpm.wizards.ProjectVerificationWizard;

/**
 * Handles clicks on the Verify context menu
 */
public class VerificationHandler extends AbstractHandler
{
    /**
     * Handles clicks on the Verify context menu
     */
    @Override
    public Object execute (ExecutionEvent event) throws ExecutionException
    {
        Shell activeShell = HandlerUtil.getActiveShell(event);
        IProject project = HandlerUtilities.extractSelection(HandlerUtil.getActiveMenuSelection(event)).getProject();
        IWizard wizard = new ProjectVerificationWizard(project);
        WizardDialog dialog = new WizardDialog(activeShell, wizard);
        dialog.open();
        return null;
    }
}
