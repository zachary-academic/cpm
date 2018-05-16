package cpm.propertyTesters;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

/**
 * Controls whether Submit is added to context menu of project
 */
public class IsSubmitable extends PropertyTester
{
    /**
     * Tests a project to see if it should be possible to submit it. Currently written to permit for all projects.
     */
    @Override
    public boolean test (Object receiver, String property, Object[] args, Object expectedValue)
    {
        IResource rsc = (IResource) receiver;
        @SuppressWarnings("unused")
        IProject project = rsc.getProject();
        return true;
    }
}
