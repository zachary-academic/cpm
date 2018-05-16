package cpm.logging;

import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;
import cpm.net.Canvas;
import cpm.startup.Activator;

public class Log {

	/**
	 * Logs the stack trace to the Eclipse error log. Also prints it to the console.
	 */
	private static void logException(Exception e) {
		e.printStackTrace();
		Activator.getDefault().getLog().log(new Status(Status.WARNING, Activator.PLUGIN_ID, e.toString(), e));
		
		try
		{
		    Canvas.logException(e);
		}
		catch (Exception ex)
		{
		    ex.printStackTrace();
		}
	}

	/**
	 * Displays a non-blocking error message
	 */
	public static void displayErrorMessage(final String title, final String message) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				MessageDialog.openError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), title,
						message);
			}
		});
	}
	
	/**
	 * Displays a non-blocking informational message
	 */
	public static void displayInfoMessage(final String title, final String message) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), title,
						message);
			}
		});
	}

	/**
	 * Logs an exception and displays an error message.
	 */
    public static void reportException (Exception e, final String string)
    {
        logException(e);
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Unexpected problem - please try again",
                        string);
            }
        });
    }
    
    public static void reportException (final Exception e)
    {
        logException(e);
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Unexpected problem - please try again",
                        e.getMessage());
            }
        });
    }
}
