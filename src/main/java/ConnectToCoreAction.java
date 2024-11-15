import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import org.itrace.ConnectionTask;


public class ConnectToCoreAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        System.out.println("Connecting to Core...");
        if(e.getProject() == null) {
            Messages.showErrorDialog("No project is open. Please open a project first.", "Error");
            return;
        }
        new ConnectionTask(e.getProject()).queue();


    }
}
