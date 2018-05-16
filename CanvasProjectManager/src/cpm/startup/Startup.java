package cpm.startup;

import org.eclipse.ui.IStartup;

/**
 * For adding shutdown behavior
 */
public class Startup implements IStartup
{
    /**
     * Defines what to do at shutdown. Currently does nothing.
     */
    @Override
    public void earlyStartup ()
    {
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            public void run ()
            {
            }
        });
    }
}
