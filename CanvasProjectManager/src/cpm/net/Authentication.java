package cpm.net;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.browser.TitleEvent;
import org.eclipse.swt.browser.TitleListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import cpm.logging.Log;

/**
 * Provides methods in support of obtaining Canvas credentials.
 */
public class Authentication
{
    /**
     * The URL fragment to look for when extracting the authorization code
     */
    private final static String CODE_URL = "login/oauth2/auth?code=";

    /**
     * Reports whether we are authorized to use Canvas.
     */
    public static boolean isAuthorized ()
    {
        return Rest.isRefreshToken();
    }

    /**
     * Does the authentication procedure. The parameter determines whether or not we display a notification first.
     */
    public static void doAuthorization (final boolean notify)
    {
        // Use a thread because there can only be one display per thread
        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
        {
            @Override
            public void run ()
            {
                // Notify the user if requested
                if (notify)
                {
                    Log.displayInfoMessage("Authorize", "You need to authorize the Canvas Project Manager before you can use it.");
                }

                doAuthorization();
            }
        });
    }

    /**
     * Continues authorization following notification
     */
    private static void doAuthorization ()
    {
        // Launch a web browser so the user can log in
        final Display display = PlatformUI.getWorkbench().getDisplay();
        final Shell shell = new Shell(display.getActiveShell(), SWT.SHELL_TRIM);
        shell.setLayout(new FillLayout());
        final Browser browser = new Browser(shell, SWT.NONE);

        // Arrange for the shell window to have the same title as the browser
        browser.addTitleListener(new TitleListener()
        {
            @Override
            public void changed (TitleEvent event)
            {
                shell.setText(event.title);
            }
        });

        // Set the URL and open the shell
        browser.setBounds(0, 0, 900, 600);
        browser.setUrl("https://utah.instructure.com/" + "login/oauth2/auth?client_id=" + DeveloperKey.clientID
                + "&response_type=code&redirect_uri=urn:ietf:wg:oauth:2.0:oob");
        shell.open();

        // When this listener tells us the location has changed, we pull out the authorization code that we need to pass
        // to Canvas to obtain tokens
        browser.addLocationListener(new LocationListener()
        {
            @Override
            public void changing (LocationEvent event)
            {
            }

            @Override
            public void changed (LocationEvent event)
            {
                // Extract the code and use it to obtain access tokens from Canvas
                int index = event.location.indexOf(CODE_URL);
                if (index > 0)
                {
                    final String code = event.location.substring(index + CODE_URL.length());

                    // Close things down
                    browser.close();
                    shell.close();

                    // Put up a progress monitor that allows cancellation
                    // TODO: Shutdown executor
                    ProgressMonitorDialog dialog = new ProgressMonitorDialog(null);
                    try
                    {
                        dialog.run(true, true, new IRunnableWithProgress()
                        {
                            @Override
                            public void run (final IProgressMonitor monitor)
                            {
                                monitor.beginTask("Obtaining Canvas keys", IProgressMonitor.UNKNOWN);

                                // Obtain the keys on another thread; this thread will deal
                                // with cancellation if requested. The result of the
                                // future is true for success and false for failure.
                                ExecutorService executor = Executors.newFixedThreadPool(1);
                                Future<Boolean> restFuture = executor.submit(new Callable<Boolean>()
                                {
                                    @Override
                                    public Boolean call ()
                                    {
                                        try
                                        {
                                            Canvas.authorize(code);
                                            return true;
                                        }
                                        catch (Exception e)
                                        {
                                            Log.reportException(e);
                                            if (!monitor.isCanceled())
                                            {
                                                Log.displayErrorMessage("Authentication", "There was an error obtaining your Canvas keys");
                                            }
                                            return false;
                                        }
                                    }
                                });

                                // Wait until either Cancel is used or the Future completes.
                                while (!restFuture.isDone())
                                {
                                    try
                                    {
                                        if (monitor.isCanceled()) return;
                                        Thread.sleep(500);
                                    }
                                    catch (Exception e)
                                    {
                                    }
                                }

                                // Report success if that was the case. Otherwise, the exception
                                // handler inside the Future will report failure.
                                try
                                {
                                    if (restFuture.get())
                                    {
                                        PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable()
                                        {
                                            @Override
                                            public void run ()
                                            {
                                                Log.displayInfoMessage("Authorization",
                                                        "The Canvas Project Manager has been authorized to access Canvas on your behalf");
                                            }
                                        });
                                    }
                                }
                                catch (Exception e)
                                {
                                    // This shouldn't happen
                                }
                            }
                        });
                    }
                    catch (Exception e)
                    {
                        Log.displayErrorMessage("Authentication", "There was an error obtaining your Canvas keys");
                        Log.reportException(e);
                    }
                }
            }
        });
    }
}
