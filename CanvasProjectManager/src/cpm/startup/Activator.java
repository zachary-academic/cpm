package cpm.startup;

import org.eclipse.core.runtime.IPath;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in lifecycle
 */
public class Activator extends AbstractUIPlugin
{
    /** The plug-in ID */
    public static final String PLUGIN_ID = "CPM";

    /** The shared instance */
    private static Activator plugin;

    /*
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework. BundleContext)
     */
    @Override
    public void start (BundleContext context) throws Exception
    {
        super.start(context);
        plugin = this;
    }

    /*
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    @Override
    public void stop (BundleContext context) throws Exception
    {
        plugin = null;
        super.stop(context);
    }

    /**
     * Returns the shared instance
     */
    public static Activator getDefault ()
    {
        return plugin;
    }
    
    /**
     * Returns the state path
     */
    public static IPath getStatePath ()
    {
        return plugin.getStateLocation();
    }
}
