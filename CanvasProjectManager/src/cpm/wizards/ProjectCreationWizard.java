package cpm.wizards;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.internal.wizards.datatransfer.ZipLeveledStructureProvider;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;
import org.osgi.service.prefs.BackingStoreException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import cpm.data.Course;
import cpm.data.CourseProfile;
import cpm.data.Project;
import cpm.data.ProjectType;
import cpm.logging.CanvasProjectException;
import cpm.logging.Log;
import cpm.net.Authentication;
import cpm.net.Canvas;
import cpm.net.Canvas.StreamConsumer;
import cpm.pages.AuthorizationPage;
import cpm.pages.CoursePage;
import cpm.pages.ProjectPage;
import cpm.pages.ProjectTypePage;

/**
 * A wizard that supports adding projects by downloading them from Canvas.
 */
public class ProjectCreationWizard extends CanvasWizard
{
    /** Displayed when the user is unauthorized */
    private AuthorizationPage authPage;

    /** First page: pick a course */
    private CoursePage coursePage;

    /** Second page: Select a project type */
    private ProjectTypePage projectTypePage;

    /** Third page: Select a project */
    private ProjectPage projectPage;

    /**
     * Create the pages, which depends on whether or not the user is authorized.
     */
    @Override
    public void addPages ()
    {
        if (!Canvas.authorized())
        {
            addPage(authPage = new AuthorizationPage("Authorization"));
        }
        else
        {
            addPage(coursePage = new CoursePage("Courses", "Select the course for which you would like to create a project"));
            addPage(projectTypePage = new ProjectTypePage("Project Types", coursePage));
            addPage(projectPage = new ProjectPage("Projects", coursePage, projectTypePage));
        }
    }

    @Override
    public IWizardPage getNextPage (IWizardPage currentPage)
    {
        if (currentPage == coursePage)
        {
            return projectTypePage;
        }
        else if (currentPage == projectTypePage)
        {
            return (projectTypePage.getProjectName() == null) ? projectPage : null;
        }
        else
        {
            return null;
        }
    }

    @Override
    public boolean canFinish ()
    {
        return authPage != null || (projectTypePage.isPageComplete() && projectTypePage.getProjectName() != null) || projectPage.isPageComplete();
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
            createProject();
        }
        return true;
    }

    /**
     * Uses the choices selected on the courses and project pages to create a new project. Interaction with the user may
     * be required in some circumstances.
     */
    public void createProject ()
    {
        IProject theNewProject = null;

        try
        {
            // Get the desired project name and make sure that it is OK to use
            final String projectName = getProjectName();
            if (!isNameAvailable(projectName)) return;

            // Make a project using a temporary name
            final String tempProjectName = getTemporaryName();
            final IProject projectHandle = theNewProject = ResourcesPlugin.getWorkspace().getRoot().getProject(tempProjectName);

            // Extract information from the wizard pages
            final Course course = coursePage.getSelectedCourse();
            final CourseProfile courseProfile = projectTypePage.getCourseProfile();
            final ProjectType projectType = projectTypePage.getSelectedProjectType();
            final Project projectInfo = projectPage.getSelectedProject();
            final boolean isBlankProject = projectTypePage.isBlankProject();

            // Do the rest of the process on a separate thread
            ProgressMonitorDialog dialog = new ProgressMonitorDialog(null);
            dialog.run(true, true, new IRunnableWithProgress()
            {
                @Override
                public void run (final IProgressMonitor monitor)
                {
                    try
                    {
                        // Start the monitor
                        monitor.beginTask("Creating project", IProgressMonitor.UNKNOWN);

                        // Create an empty Java project
                        monitor.setTaskName("Making empty project");
                        final IJavaProject javaProject = makeEmptyJavaProject(projectHandle, tempProjectName, monitor);

                        // Add content to the project
                        if (isBlankProject)
                        {
                            monitor.setTaskName("Initializing blank project");
                            initBlankProject(projectHandle, javaProject, monitor);
                        }
                        else
                        {
                            monitor.setTaskName("Importing project from Canvas");
                            File projectZipfile = Canvas.downloadProject(course.getCourseID(), courseProfile.getRootFolder(), projectType.getFolder(),
                                    projectInfo.getProjectName(), projectInfo.isAssignment());
                            initJavaProjectFromZip(projectHandle, projectZipfile, tempProjectName, monitor);
                        }

                        // Install formatting style if it exists
                        checkCancel(monitor);
                        String formatFilename = courseProfile.getFormatFilename();
                        if (formatFilename != null)
                        {
                            monitor.setTaskName("Installing style rules");
                            Document format = Canvas.getFormatFile(course.getCourseID(), courseProfile.getRootFolder(), formatFilename);
                            checkCancel(monitor);
                            installFormattingStyle(projectHandle, format, course.getName());
                        }

                        // Install library if it exists
                        final String libraryFilename = courseProfile.getLibraryFilename();
                        if (libraryFilename != null)
                        {
                            monitor.setTaskName("Installing course library");
                            String libraryURL = Canvas.getLibraryFileURL(course.getCourseID(), courseProfile.getRootFolder(), libraryFilename);
                            checkCancel(monitor);
                            final String libraryName = courseProfile.getLibraryFilename();
                            Canvas.getRequestStream(libraryURL, new StreamConsumer<Void>()
                            {
                                public Void consume (InputStream input) throws CanvasProjectException
                                {
                                    try
                                    {
                                        installCourseLibrary(projectHandle, javaProject, input, libraryName, monitor);
                                    }
                                    catch (CoreException e)
                                    {
                                        throw new CanvasProjectException(e, "while installing library");
                                    }
                                    return null;
                                }
                            });
                        }

                        // Delete any existing resource with the desired name, then rename the project to that name.
                        monitor.setTaskName("Naming new project");
                        checkCancel(monitor);
                        deleteResource(projectName);
                        IProjectDescription description = projectHandle.getDescription();
                        description.setName(projectName);
                        projectHandle.move(description, IResource.FORCE | IResource.SHALLOW, monitor);

                    }
                    catch (OperationCanceledException e)
                    {
                        forceProjectDeletion(projectHandle);
                    }
                    catch (InterruptedException e)
                    {
                        forceProjectDeletion(projectHandle);
                    }
                    catch (Exception e)
                    {
                        forceProjectDeletion(projectHandle);
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
            forceProjectDeletion(theNewProject);
            Log.reportException(e);
        }
    }

    /**
     * Returns a (hopefully) unique temporary name for a project
     */
    private String getTemporaryName ()
    {
        return "cpm_tmp_" + System.nanoTime();
    }

    /**
     * Returns the name to use for the project
     */
    private String getProjectName ()
    {
        if (projectTypePage.isBlankProject())
        {
            return projectTypePage.getProjectName();
        }
        else if (projectPage.getSelectedProject().isAssignment())
        {
            String assignmentName = projectPage.getSelectedProject().getProjectName();
            String studentName = Canvas.getStudentName();
            int index = studentName.indexOf(' ');
            if (index < 0)
            {
                return assignmentName + "_" + studentName;
            }
            else
            {
                String firstName = studentName.substring(0, index);
                String lastName = studentName.substring(index + 1, studentName.length());
                return assignmentName + "_" + lastName + "_" + firstName;
            }
        }
        else
        {
            return projectPage.getSelectedProject().getProjectName();
        }
    }

    /**
     * If the projectName is in use and the user is unwilling to delete it, returns false. Otherwise returns true.
     */
    private boolean isNameAvailable (String projectName) throws CoreException, IOException
    {
        // Make a skeleton project with the desired name
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);

        // See if the project with that name already exists and deal with it if so
        if (project.exists())
        {
            if (MessageDialog.openConfirm(getShell(), "Overwrite?",
                    "There is already a project named " + projectName + " in your workspace. Would you like to overwrite it?"))
            {
                return true;
            }
            else
            {
                return false;
            }
        }

        // See if there's a file or folder in the workspace with that name and deal with it if so
        File projectFile = new File(ResourcesPlugin.getWorkspace().getRoot().getLocation().toString() + "/" + projectName);
        if (projectFile.exists())
        {
            if (MessageDialog.openConfirm(getShell(), "Remove",
                    "There is already a file or folder named \"" + projectName + "\" in your workspace" + " Would you like to overwrite it?"))
            {
                return true;
            }
            else
            {
                return false;
            }
        }

        // The name is not in use
        return true;
    }

    /**
     * Creates and returns an empty Java project based on the project handle and the project name. The monitor provides
     * progress feedback and cancellation support.
     */
    private static IJavaProject makeEmptyJavaProject (IProject projectHandle, String projectName, IProgressMonitor monitor) throws CoreException
    {
        projectHandle.create(monitor);
        projectHandle.open(monitor);
        IProjectDescription description = projectHandle.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        description.setName(projectName);
        projectHandle.setDescription(description, monitor);
        return JavaCore.create(projectHandle);
    }

    /**
     * Gives blanket permission for overwriting.
     */
    private final static IOverwriteQuery overwriteQuery = new IOverwriteQuery()
    {
        @Override
        public String queryOverwrite (String file)
        {
            return ALL;
        }
    };

    /**
     * Initializes javaProject to be a blank project
     */
    private static void initBlankProject (IProject projectHandle, IJavaProject javaProject, IProgressMonitor monitor) throws CoreException
    {
        IPath containerPath = new Path(JavaRuntime.JRE_CONTAINER);
        IClasspathEntry jreEntry = JavaCore.newContainerEntry(containerPath);
        javaProject.setRawClasspath(new IClasspathEntry[] { jreEntry }, monitor);
        IFolder sourceFolder = projectHandle.getFolder("src");
        if (!sourceFolder.exists())
        {
            sourceFolder.create(false, true, null);
        }
        IPackageFragmentRoot IPFRoot = javaProject.getPackageFragmentRoot(sourceFolder);
        IClasspathEntry[] oldEntries = javaProject.getRawClasspath();
        IClasspathEntry[] newEntries = new IClasspathEntry[oldEntries.length + 1];
        System.arraycopy(oldEntries, 0, newEntries, 0, oldEntries.length);
        newEntries[oldEntries.length] = JavaCore.newSourceEntry(IPFRoot.getPath());
        javaProject.setRawClasspath(newEntries, monitor);
    }

    /**
     * Creates a project by importing a zip file.
     */
    private static void initJavaProjectFromZip (IProject projectSkeleton, File zippedProjectPath, String projectName, IProgressMonitor monitor)
            throws CoreException, IOException, InvocationTargetException, InterruptedException
    {
        try
        {
            // Set up for the import, ignoring folders since empty ones cause a problem
            ZipFile zipfile = new ZipFile(zippedProjectPath.getAbsolutePath());
            ZipLeveledStructureProvider provider = new ZipLeveledStructureProvider(zipfile);
            List<Object> fileSystemObjects = new ArrayList<Object>();
            Enumeration<? extends ZipEntry> entries = zipfile.entries();
            while (entries.hasMoreElements())
            {
                ZipEntry item = entries.nextElement();
                if (!item.isDirectory())
                {
                    fileSystemObjects.add(item);
                }
            }

            // Import the elements of the zip file
            ImportOperation importOperation = new ImportOperation(projectSkeleton.getFullPath(), new ZipEntry(projectName), provider, overwriteQuery,
                    fileSystemObjects);
            importOperation.setCreateContainerStructure(false);
            importOperation.run(monitor);

            // Create the empty folders "manually"
            entries = zipfile.entries();
            while (entries.hasMoreElements())
            {
                ZipEntry item = entries.nextElement();
                if (item.isDirectory())
                {
                    int index = item.getName().indexOf('/');
                    if (index >= 0)
                    {
                        String folder = item.getName().substring(index + 1);
                        String path = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName).getLocation() + "/" + folder;
                        new File(path).mkdir();
                    }
                }
            }

            // Update the project name because the zip file overwrote the name
            IProjectDescription newDescription = projectSkeleton.getDescription();
            newDescription.setName(projectName);
            projectSkeleton.move(newDescription, true, new NullProgressMonitor());
        }
        finally
        {
            deleteFile(zippedProjectPath);
        }
    }

    /**
     * Adds style preferences to a project
     */
    private static void installFormattingStyle (IProject project, Document formatXML, String profileName) throws BackingStoreException
    {
        IScopeContext projectScope = new ProjectScope(project);
        IEclipsePreferences preferences = projectScope.getNode("org.eclipse.jdt.core");
        NodeList nodes = formatXML.getElementsByTagName("setting");
        for (int i = 0; i < nodes.getLength(); i++)
        {
            Node node = nodes.item(i);
            Element settingNode = (Element) node;
            if (settingNode.getAttribute("id").startsWith("org.eclipse.jdt.core.formatter"))
            {
                preferences.put(settingNode.getAttribute("id"), settingNode.getAttribute("value"));
            }
        }
        preferences.sync();
    }

    /**
     * Adds a library to a project
     */
    private static void installCourseLibrary (IProject project, IJavaProject javaProject, InputStream libraryResult, String libraryName,
            IProgressMonitor monitor) throws CoreException
    {
        IFolder libFolder = project.getFolder("lib");
        if (!libFolder.exists())
        {
            libFolder.create(false, true, monitor);
        }
        IFile library = libFolder.getFile(libraryName);
        if (!library.exists())
        {
            library.create(libraryResult, false, null);
            // add the library to the classpath
            IClasspathEntry relativeLibraryEntry = new ClasspathEntry(IPackageFragmentRoot.K_BINARY, IClasspathEntry.CPE_LIBRARY,
                    library.getProjectRelativePath(), ClasspathEntry.INCLUDE_ALL, ClasspathEntry.EXCLUDE_NONE, null, null, null, false,
                    ClasspathEntry.NO_ACCESS_RULES, false, ClasspathEntry.NO_EXTRA_ATTRIBUTES);
            IClasspathEntry[] oldEntries = javaProject.getRawClasspath();
            IClasspathEntry[] newEntries = new IClasspathEntry[oldEntries.length + 1];
            System.arraycopy(oldEntries, 0, newEntries, 0, oldEntries.length);
            newEntries[oldEntries.length] = relativeLibraryEntry;
            javaProject.setRawClasspath(newEntries, monitor);
        }
    }
}
