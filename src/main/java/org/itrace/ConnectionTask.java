package org.itrace;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import org.jetbrains.annotations.NotNull;

public class ConnectionTask extends Task.Backgroundable {
    private Project project;

    public ConnectionTask(@NotNull Project project) {
        super(project, "Connecting to iTrace Core", false); // "true" means the task can be canceled
        this.project = project;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
        ConnectionSingleton connection = ConnectionSingleton.getInstance();
        System.out.println("Established!");
        indicator.setText("Connection Established! Waiting for session start...");
        connection.ProcessCoreData(this.project, indicator);
    }
}
