package state;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Scanner;
import org.eclipse.core.runtime.IPath;
import cpm.startup.Activator;

/**
 * Provides methods for reading and writing workspace state files for the plugin.
 */
@SuppressWarnings("unused")
public class StateCache
{
    /**
     * Returns a Scanner that can be used to read from the named state file
     */
    public static Scanner read (String name) throws FileNotFoundException
    {
        IPath statePath = Activator.getStatePath();
        return new Scanner(statePath.append(name).toFile());
    }

    /**
     * Returns a PrintStream that can be used to write to the named state file
     */
    public static PrintStream write (String name) throws FileNotFoundException
    {
        IPath statePath = Activator.getStatePath();
        return new PrintStream(statePath.append(name).toFile());
    }

    /**
     * Clears the named state file
     */
    public static void clear (String name)
    {
        IPath statePath = Activator.getStatePath();
        statePath.append(name).toFile().delete();
    }
}
