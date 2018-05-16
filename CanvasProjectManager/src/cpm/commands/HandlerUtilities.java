package cpm.commands;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

/**
 * Utilities for context menu event handlers
 */
public class HandlerUtilities
{
    /**
     * Returns the project on which the context menu was invoked.
     */
    static IResource extractSelection (ISelection sel)
    {
        if (!(sel instanceof IStructuredSelection))
        {
            return null;
        }
        else
        {
            Object element = ((IStructuredSelection) sel).getFirstElement();
            if (element instanceof IResource)
            {
                return (IResource) element;
            }
            else if (element instanceof IAdaptable)
            {
                return (IResource) ((IAdaptable) element).getAdapter(IResource.class);
            }
            else
            {
                return null;
            }
        }
    }
}
