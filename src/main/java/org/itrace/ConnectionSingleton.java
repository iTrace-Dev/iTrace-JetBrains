package org.itrace;

import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConnectionSingleton {
    private static ConnectionSingleton instance;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter xmlFile;

    private final String hostName = "127.0.0.1";
    private final int port = 8008;

    public static ConnectionSingleton getInstance() {
        if(instance == null) { instance = new ConnectionSingleton(); }
        return instance;
    }

    private ConnectionSingleton() {
        try {
            socket = new Socket(hostName,port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }
        catch (IOException e) {
            System.err.println("Connection Error - iTrace-Core might not be running");
            throw new RuntimeException(e);
        }
    }

    public void ProcessCoreData(@Nullable Project project, @NotNull ProgressIndicator indicator) {
        while(socket != null && !socket.isClosed()) {
            String data = null;
            try {
                data = in.readLine();
            }
            catch (IOException e) {
                return;
            }
            String[] tokens = data.split(",");

            if(tokens[0].equals("session_start")) {
                indicator.setText("Session starting");
                try {
                    xmlFile = new BufferedWriter(new FileWriter(tokens[3]+String.format("\\itrace_jetbrains-%s.xml",System.currentTimeMillis()), true));
                    xmlFile.write("<?xml version=\"1.0\"?>\n");
                    xmlFile.write("<itrace_plugin session_id=\"" + tokens[1] + "\">\n");
                    xmlFile.write(String.format("    <environment screen_width=\"%d\" screen_height=\"%d\" plugin_type=\"JETBRAINS\"/>\n",
                            Toolkit.getDefaultToolkit().getScreenSize().width,
                            Toolkit.getDefaultToolkit().getScreenSize().height));
                    xmlFile.write("    <gazes>\n");
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            else if(tokens[0].equals("session_end")) {
                try {
                    xmlFile.write("    </gazes>\n");
                    xmlFile.write("</itrace_plugin>");
                    xmlFile.close();
                    CloseConnection();
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                indicator.setText("Session ended.");
            }

            else if(tokens[0].equals("gaze")) {
                try {
                    int x = Integer.parseInt(tokens[2]);
                    int y = Integer.parseInt(tokens[3]);
                    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                    int line_height = -1;//editor.getComponent().getFontMetrics(editor.getColorsScheme().getFontPreferences().getFontType()).getHeight();
                    float font_size = editor.getColorsScheme().getEditorFontSize2D();
                    String filename = FileEditorManager.getInstance(project).getSelectedFiles()[0].getPath();
                    int editor_x = editor.getContentComponent().getLocationOnScreen().x;
                    int editor_y = editor.getContentComponent().getLocationOnScreen().y;
                    Point gaze_point = new Point(x-editor_x,y-editor_y);
                    //LogicalPosition logicalPosition = editor.xyToLogicalPosition(gaze_point);

                    AtomicReference<LogicalPosition> logicalPositionRef = new AtomicReference<>();

                    ApplicationManager.getApplication().invokeAndWait(() -> {
                        // Safely access xyToLogicalPosition on the EDT
                        logicalPositionRef.set(editor.xyToLogicalPosition(gaze_point));
                    });

                    LogicalPosition logicalPosition = logicalPositionRef.get();

                    indicator.setText(tokens[2]+","+tokens[3]+" -> " + String.valueOf(logicalPosition.line+1) +","+String.valueOf(logicalPosition.column+1));


                    xmlFile.write(String.format("        <response event_id=\"%s\" plugin_time=\"%d\" x=\"%d\" y=\"%d\" gaze_target=\"%s\" gaze_target_type=\"%s\" source_file_path=\"%s\" source_file_line=\"%d\" source_file_col=\"%d\" editor_line_height=\"%s\" editor_font_height=\"%f\" editor_line_base_x=\"\" editor_line_base_y=\"\"/>\n",
                                  tokens[1], // event ID
                                  System.currentTimeMillis(), //Plugin Time
                                  x, // x
                                  y, // y
                                  filename.split("/")[filename.split("/").length-1], // Gaze Target
                                  filename.split("\\.")[filename.split("\\.").length-1], // Gaze Target Type
                                  filename, // Source File Path
                                  logicalPosition.line+1, // Source File Line
                                  logicalPosition.column+1, // Source File Column
                                  line_height, // Line Height
                                  font_size // Editor Font Height
                            ));
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void CloseConnection() {
        try {
            if (in != null) { in.close(); }
            if (socket != null) { socket.close(); }
        }
        catch (IOException e) {
            System.err.println("Error closing connection: " + e.getMessage());
        }
        finally {
            socket = null;
            in = null;
        }
    }

}
