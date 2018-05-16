package cpm.pages;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/**
 * Wizard page that tells user to authorize with Canvas.
 */
public class AuthorizationPage extends WizardPage
{
    /**
     * Create this page
     */
    public AuthorizationPage (String pageName)
    {
        super(pageName);
        setTitle(pageName);
    }

    @Override
    public void createControl (Composite parent)
    {
        // Layout
        Composite container = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 1;
        container.setLayout(layout);

        // Label
        Label label = new Label(container, SWT.NONE);
        GridData tempdata = new GridData();
        tempdata.horizontalAlignment = GridData.FILL;
        tempdata.horizontalSpan = 1;
        label.setLayoutData(tempdata);
        label.setText(
                "You need to authorize the Canvas Project Manager Wizard before you can use it." + "\nPress finish to continue to authorization.");

        // Finish
        setPageComplete(true);
        setControl(container);
    }
}
