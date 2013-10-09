package org.mule.tooling.apikit.handlers;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Scanner;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.mule.tooling.apikit.Activator;
import org.mule.tooling.apikit.scaffolder.FlowGenerator;
import org.mule.tooling.apikit.util.APIKitHelper;
import org.mule.tooling.core.MuleCorePlugin;
import org.mule.tooling.core.MuleRuntime;
import org.mule.tooling.core.model.IMuleProject;
import org.mule.tooling.ui.utils.SaveModifiedResourcesDialog;
import org.mule.tooling.ui.utils.UiUtils;
import org.raml.parser.loader.CompositeResourceLoader;
import org.raml.parser.loader.DefaultResourceLoader;
import org.raml.parser.loader.FileResourceLoader;

public class GenerateFlowsHandler extends AbstractHandler implements IHandler {

    private IWorkbenchWindow workbenchWindow;
    private IFile ramlFile;

    @Override
    public boolean isEnabled() {
        Activator activator = Activator.getDefault();
        IWorkbench workbench = activator.getWorkbench();
        workbenchWindow = workbench.getActiveWorkbenchWindow();
        ISelectionService selectionService = workbenchWindow.getSelectionService();
        IStructuredSelection structured = (IStructuredSelection) selectionService.getSelection();

        if (structured.getFirstElement() instanceof IFile) {

            ramlFile = (IFile) structured.getFirstElement();
            File file = ramlFile.getRawLocation().toFile();

            String content;
            try {
                content = new Scanner(file).useDelimiter("\\Z").next();
                CompositeResourceLoader resourceLoader = new CompositeResourceLoader(new DefaultResourceLoader(), new FileResourceLoader(ramlFile.getParent().getRawLocation().toFile()));
                if (APIKitHelper.INSTANCE.isRamlFile(file) && APIKitHelper.INSTANCE.isValidYaml(file.getName(), content, resourceLoader)) {
                    return true;
                }

            } catch (FileNotFoundException e) {
                MuleCorePlugin.getLog().log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage()));
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IRunnableWithProgress op = new IRunnableWithProgress() {

            @Override
            public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
                doExecute(monitor);
            }
        };
        IWorkbench wb = PlatformUI.getWorkbench();
        IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
        Shell shell = win != null ? win.getShell() : null;
        try {
            new ProgressMonitorDialog(shell).run(false, true, op);
        } catch (InvocationTargetException e1) {
            MuleCorePlugin.getLog().log(new Status(IStatus.ERROR, MuleCorePlugin.PLUGIN_ID, e1.getMessage()));
            e1.printStackTrace();
        } catch (InterruptedException e1) {
            MuleCorePlugin.getLog().log(new Status(IStatus.ERROR, MuleCorePlugin.PLUGIN_ID, e1.getMessage()));
            e1.printStackTrace();
        }
        return null;
    }

    private void doExecute(IProgressMonitor monitor) {
        IProject project = ramlFile.getParent().getProject();
        try {
            IMuleProject muleProject = MuleRuntime.create(project);
            if (muleProject == null) {
                MuleCorePlugin.getLog().log(new Status(IStatus.ERROR, MuleCorePlugin.PLUGIN_ID, "Could not generate mock flows. The current project is not a Mule Project."));
                MessageDialog.openError(workbenchWindow.getShell(), "Generate flows", "The generation of flows failed. The current project is not a Mule Project.");
                return;
            }
            monitor.beginTask("Generating flows...", 4);
            if (!saveModifiedResources(project)) {
                MessageDialog.openError(workbenchWindow.getShell(), "Generate flows", "The generation of flows failed. There are unsaved resources in the project.");
                monitor.done();
                return;
            }
            FlowGenerator flowGenerator = new FlowGenerator();
            flowGenerator.run(monitor, project, ramlFile.getRawLocation().toFile());
            flowGenerator.createMuleConfigs(monitor, muleProject);
            monitor.done();
        } catch (CoreException e) {
            MuleCorePlugin.getLog().log(new Status(IStatus.ERROR, Activator.PLUGIN_ID, e.getMessage()));
            e.printStackTrace();
        }
    }

    private boolean saveModifiedResources(IProject project) {
        List<IEditorPart> dirtyEditors = UiUtils.getDirtyEditors(project);
        if (dirtyEditors.isEmpty())
            return true;
        Shell shell = workbenchWindow.getShell();
        SaveModifiedResourcesDialog dialog = new SaveModifiedResourcesDialog(shell);
        if (dialog.open(shell, dirtyEditors))
            return true;
        return false;
    }
}
