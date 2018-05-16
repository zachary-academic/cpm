package cpm.wizards;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Date;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import canvasProjectManager.annotations.Skip;
import cpm.commands.VerificationHandler;
import cpm.data.CourseProfile;
import cpm.logging.CanvasProjectException;
import cpm.logging.Log;
import cpm.net.Authentication;
import cpm.net.Canvas;
import cpm.net.Canvas.StreamConsumer;
import cpm.pages.AssignmentPage;
import cpm.pages.AuthorizationPage;
import cpm.pages.CoursePage;

/**
 * A wizard that supports adding projects by downloading them from Canvas.
 */
public class ProjectVerificationWizard extends CanvasWizard
{
    /** Displayed when the user is unauthorized */
    private AuthorizationPage authPage;

    /** First page: pick a course */
    private CoursePage coursePage;

    /** Second page: pick an assignment */
    private AssignmentPage assignmentPage;

    /** The selected project */
    private IProject project;

    /**
     * Creates this wizard, which operates on project
     */
    public ProjectVerificationWizard (IProject project)
    {
        this.project = project;
    }

    @Override
    public void addPages ()
    {
        if (!Canvas.authorized())
        {
            addPage(authPage = new AuthorizationPage("Authorization"));
        }
        else
        {
            addPage(coursePage = new CoursePage("Courses", "Select the course within which you wish to verify a project"));
            addPage(assignmentPage = new AssignmentPage("Assignments", "Select the assignment against which you wish to verify", coursePage));
        }
    }

    @Override
    public IWizardPage getNextPage (IWizardPage currentPage)
    {
        if (currentPage == coursePage)
        {
            return assignmentPage;
        }
        else
        {
            return null;
        }
    }

    @Override
    public boolean canFinish ()
    {
        return authPage != null || assignmentPage.isPageComplete();
    }

    @Override
    public boolean performFinish ()
    {
        if (authPage != null)
        {
            Authentication.doAuthorization(false);
        }
        else
        {
            verifyProject();
        }
        return true;
    }

    /**
     * Verifies the selected project
     */
    public void verifyProject ()
    {
        final int courseID = coursePage.getSelectedCourse().getCourseID();
        final String assignmentName = assignmentPage.getSelectedAssignment().getName();

        try
        {
            ProgressMonitorDialog dialog = new ProgressMonitorDialog(null);
            dialog.run(true, false, new IRunnableWithProgress()
            {
                // Communicates the result of a forked dialog back to the monitor's thread
                private boolean keepGoing = true;

                @Override
                public void run (IProgressMonitor monitor)
                {
                    try
                    {
                        // Make sure all files are saved
                        monitor.setTaskName("Saving");
                        PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
                        {
                            @Override
                            public void run ()
                            {
                                keepGoing = IDE.saveAllEditors(new IResource[] { project }, true);
                            }
                        });
                        if (!keepGoing) return;

                        // Verify the project
                        monitor.setTaskName("Performing verification");
                        final int verificationResult = verify(courseID, assignmentName, project);

                        // Report success (or that there were no verification tests)
                        if (verificationResult >= 0)
                        {
                            PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
                            {
                                @Override
                                public void run ()
                                {
                                    String message;
                                    if (verificationResult > 0)
                                    {
                                        message = "Passed verification on " + new Date().toString();
                                    }
                                    else
                                    {
                                        message = "There are no verification tests for the assignment";
                                    }
                                    MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Verification",
                                            message);
                                }
                            });
                        }

                    }
                    catch (Exception e)
                    {
                        Log.reportException(e);
                    }
                    finally
                    {
                        monitor.done();
                    }
                }
            });
        }
        catch (Exception e)
        {
            Log.reportException(e);
        }
    }

    /**
     * Verifies that ...
     */
    public static int verify (int courseID, String assignmentName, IResource resource) throws CanvasProjectException
    {
        try
        {
            // Set up class loader for template project
            CourseProfile profile = Canvas.getCourseProfile(courseID);
            String templateURL = Canvas.getVerificationTests(profile, assignmentName);
            if (templateURL == null) return 0;

            try (URLClassLoader templateLoader = new URLClassLoader(new URL[] { new URL(templateURL) }, VerificationHandler.class.getClassLoader()))
            {
                // Set up class loader for student project
                ArrayList<URL> urlList = new ArrayList<URL>();
                IJavaProject jProj = (IJavaProject) resource.getProject().getNature(JavaCore.NATURE_ID);
                urlList.add(new File(resource.getWorkspace().getRoot().getLocation() + jProj.getOutputLocation().toString()).toURI().toURL());

                try (URLClassLoader studentLoader = new URLClassLoader(urlList.toArray(new URL[] {})))
                {
                    // Find all project paths to classes for parsing later.
                    // Template code is loaded through a jar because it is a jar
                    final ArrayList<String> templateClasses = new ArrayList<String>();

                    Canvas.getRequestStream(templateURL, new StreamConsumer<Void>()
                    {
                        public Void consume (InputStream input) throws CanvasProjectException
                        {
                            try (JarInputStream templateJarFile = new JarInputStream(input))
                            {
                                JarEntry entry;
                                while ((entry = templateJarFile.getNextJarEntry()) != null)
                                {
                                    if (entry.getName().endsWith(".class"))
                                    {
                                        templateClasses.add(entry.getName());
                                    }
                                }
                                templateJarFile.close();
                                return null;
                            }
                            catch (IOException e)
                            {
                                throw new CanvasProjectException(e, "while verifying");
                            }
                        }
                    });

                    // Student code is loaded through a recursive function because it's not a jar
                    File studentJarFile = new File(resource.getWorkspace().getRoot().getLocation() + jProj.getOutputLocation().toString());
                    ArrayList<String> studentClasses = listClasses(studentJarFile, "", new ArrayList<String>());

                    for (String c : studentClasses)
                    {
                        System.out.println(c);
                    }

                    // Check that packages exist
                    // Remove classes that have the @Skip annotation
                    // Have to load the Skip class before it can find it
                    templateLoader.loadClass("canvasProjectManager.annotations.Skip");
                    for (int i = 0; i < templateClasses.size(); i++)
                    {
                        String s = templateClasses.get(i);
                        for (Annotation ann : templateLoader.loadClass(s.replace('/', '.').substring(0, s.lastIndexOf('.'))).getAnnotations())
                        {
                            if (ann.annotationType().getCanonicalName().equals(Skip.class.getCanonicalName()))
                            {
                                templateClasses.remove(s);
                                i--;
                            }
                        }
                    }
                    for (String tp : templateClasses)
                    {
                        // remove the "xxx.class" part so we just have the package
                        tp = tp.substring(0, tp.lastIndexOf('/') + 1);
                        boolean contained = false;
                        for (String sp : studentClasses)
                        {
                            if (sp.startsWith(tp))
                            {
                                contained = true;
                                break;
                            }
                        }
                        if (contained == false)
                        {
                            final String tpfinal = tp;
                            PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
                            {
                                @Override
                                public void run ()
                                {
                                    MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Package",
                                            "Couldn't find Package \"" + tpfinal.replace('/', '.') + "\". "
                                                    + "Please check that package names are spelled correctly.");
                                }
                            });
                            templateLoader.close();
                            studentLoader.close();
                            return -1;
                        }
                    }

                    // check that classes exist within the packages
                    for (String tp : templateClasses)
                    {
                        if (!studentClasses.contains(tp))
                        {
                            final String tpfinal = tp;
                            PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
                            {
                                @Override
                                public void run ()
                                {
                                    MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Class",
                                            "Couldn't find class \"" + tpfinal.substring(tpfinal.lastIndexOf('/') + 1, tpfinal.lastIndexOf('.'))
                                                    + "\" in package \"" + tpfinal.substring(0, tpfinal.lastIndexOf('/')).replace('/', '.')
                                                    + "\". Please check that class names are spelled correctly.");
                                }
                            });
                            templateLoader.close();
                            studentLoader.close();
                            return -1;
                        }
                    }

                    // check if methods exist within the classes
                    for (final String tp : templateClasses)
                    {
                        Method[] sms = studentLoader.loadClass(tp.substring(0, tp.lastIndexOf('.')).replace('/', '.')).getDeclaredMethods();
                        for (final Method tm : templateLoader.loadClass(tp.substring(0, tp.lastIndexOf('.')).replace('/', '.')).getDeclaredMethods())
                        {
                            boolean skip = false;
                            for (Annotation ann : tm.getAnnotations())
                            {
                                if (ann.annotationType().getCanonicalName().equals(Skip.class.getCanonicalName()))
                                {
                                    skip = true;
                                    break;
                                }
                            }
                            if (!skip)
                            {
                                // Append the parameters to the end of the method name to create a display name
                                String displayName = tm.getName();
                                displayName += "(";
                                if (tm.getParameterTypes().length > 0)
                                {
                                    for (Class<?> type : tm.getParameterTypes())
                                    {
                                        displayName += type.getSimpleName() + ", ";
                                    }
                                    displayName = displayName.substring(0, displayName.lastIndexOf(','));
                                }
                                displayName += ")";
                                
                                // Find a student method with the same name and parameters
                                Method sm = findMatchingMethod(sms, tm);
                                
                                // Report if there's no match
                                if (sm == null)
                                {
                                    String message = "Method \"" + displayName + "\" in class \"" + tp.substring(0, tp.lastIndexOf('.')).replace('/', '.')
                                            + "\" couldn't be found. Please check method names and parameters.";
                                    final String finalMessage = message;
                                    PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
                                    {
                                        @Override
                                        public void run ()
                                        {
                                            MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Method",
                                                    finalMessage);
                                        }
                                    });
                                    templateLoader.close();
                                    studentLoader.close();
                                    return -1;
                                }

                                // check the throws declaration
                                Class<?>[] tmExceptions = tm.getExceptionTypes();
                                Class<?>[] smExceptions = sm.getExceptionTypes();
                                boolean exceptionsMatch = true;
                                if (tmExceptions.length != smExceptions.length)
                                {
                                    exceptionsMatch = false;
                                }
                                if (exceptionsMatch)
                                {
                                    for (int i = 0; i < tmExceptions.length; i++)
                                    {
                                        boolean contained = false;
                                        for (int j = 0; j < smExceptions.length; j++)
                                        {
                                            if (tmExceptions[i].getName().equals(smExceptions[j].getName()))
                                            {
                                                contained = true;
                                                break;
                                            }
                                        }
                                        if (!contained)
                                        {
                                            exceptionsMatch = false;
                                            break;
                                        }
                                    }
                                }
                                if (!exceptionsMatch)
                                {
                                    String message = "Method \"" + displayName + "\" in class \"" + tp.substring(0, tp.lastIndexOf('.')).replace('/', '.')
                                            + "\"";
                                    if (tmExceptions.length == 0)
                                    {
                                        message += "shouldn't signify that it thows exceptions.";
                                    }
                                    else
                                    {
                                        message += "needs to signify that it can throw \"";
                                        for (Class<?> exception : tmExceptions)
                                        {
                                            message += exception.getName() + ", ";
                                        }
                                        message = message.substring(0, message.lastIndexOf(',')) + "\"";
                                    }
                                    final String finalMessage = message;
                                    PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
                                    {
                                        @Override
                                        public void run ()
                                        {
                                            MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Method",
                                                    finalMessage);

                                        }
                                    });
                                    templateLoader.close();
                                    studentLoader.close();
                                    return -1;
                                }

                                // check the return type
                                if (!tm.getReturnType().getName().equals(sm.getReturnType().getName()))
                                {
                                    final String finalName = displayName;
                                    PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
                                    {
                                        @Override
                                        public void run ()
                                        {
                                            MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Method",
                                                    "Method \"" + finalName + "\" in class \""
                                                            + tp.substring(0, tp.lastIndexOf('.')).replace('/', '.') + "\" needs a return type of \""
                                                            + tm.getReturnType().getName() + "\".");

                                        }
                                    });
                                    templateLoader.close();
                                    studentLoader.close();
                                    return -1;
                                }
                                // check the modifiers eg public static
                                if (tm.getModifiers() != sm.getModifiers())
                                {
                                    int problems = tm.getModifiers() ^ sm.getModifiers();
                                    final String finalName = displayName;
                                    String message = "Method \"" + finalName + "\" in class\""
                                            + tp.substring(0, tp.lastIndexOf('.')).replace('/', '.') + "\" needs to";
                                    if ((tm.getModifiers() & problems) != 0)
                                    {
                                        message += "be \"" + Modifier.toString(tm.getModifiers() & problems) + "\"";
                                        if ((sm.getModifiers() & problems) != 0)
                                        {
                                            message += " and ";
                                        }
                                    }
                                    if ((sm.getModifiers() & problems) != 0)
                                    {
                                        message += "not be \"" + Modifier.toString(sm.getModifiers() & problems) + "\"";
                                    }
                                    message += ".";
                                    final String finalMessage = message;
                                    PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable()
                                    {
                                        @Override
                                        public void run ()
                                        {
                                            MessageDialog.openInformation(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), "Method",
                                                    finalMessage);

                                        }
                                    });
                                    templateLoader.close();
                                    studentLoader.close();
                                    return -1;
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new CanvasProjectException(e, "while validating the project");
        }

        return 1;
    }

    private static Method findMatchingMethod (Method[] sms, final Method tm)
    {
        for (int i = 0; i < sms.length; i++)
        {
            if (tm.getName().equals(sms[i].getName()))
            {
                Method sm = sms[i];
                
                // check the method parameter types
                // class loaded methods don't equal each other because
                // of different parent classes
                // so we just check the types name
                boolean parametersEqual = true;
                if (tm.getParameterTypes().length == sm.getParameterTypes().length)
                {
                    for (int j = 0; j < tm.getParameterTypes().length; j++)
                    {
                        if (!tm.getParameterTypes()[j].getName().equals(sm.getParameterTypes()[j].getName()))
                        {
                            parametersEqual = false;
                        }
                    }
                }
                else
                {
                    parametersEqual = false;
                }
                
                if (parametersEqual)
                {
                    return sm;
                }
            }
        }
        return null;
    }

    private static ArrayList<String> listClasses (File file, String pathSoFar, ArrayList<String> list)
    {
        if (file.listFiles().length < 1)
        {
            list.add(pathSoFar);
        }
        for (File subFile : file.listFiles())
        {
            if (subFile.isDirectory())
            {
                listClasses(subFile, pathSoFar + subFile.getName() + "/", list);
            }
            else if (subFile.getName().endsWith(".class"))
            {
                list.add(pathSoFar + subFile.getName());
            }
        }
        return list;
    }
}
