package org.itrace;

import java.awt.*;
import java.io.*;
import java.net.Socket;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;


public class ConnectionSingleton {
    private static ConnectionSingleton instance;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter xmlFile;

    private final Map<Document, DocumentListener> activeListeners = new HashMap<>();
    private MessageBusConnection connection;

    private final String hostName = "127.0.0.1";
    private final int port = 8008;

    private static String escape(String s) {
        return s.replace("&", "&amp")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"","&quot;")
                .replace("\n","\\n")
                .replace("\t","\\t");
    }

    public static ConnectionSingleton getInstance() {
        if(instance == null) { instance = new ConnectionSingleton(); }
        return instance;
    }

    private ConnectionSingleton() { }


    private void AttachListeners(Project project) {
        FileEditorManager editorManager = FileEditorManager.getInstance(project);

        for (FileEditor fileEditor : editorManager.getAllEditors()) {
            if (fileEditor instanceof TextEditor textEditor) {
                Editor editor = textEditor.getEditor();
                Document doc = editor.getDocument();

                if (activeListeners.containsKey(doc)) continue;


                DocumentListener listener = new DocumentListener() {
                    @Override
                    public void documentChanged(@NotNull DocumentEvent event) {
                        HandleEditEvent(editor, event);
                    }
                };

                doc.addDocumentListener(listener);
                activeListeners.put(doc, listener);
            }
        }
    }

    private void DetachListeners() {
        for (Map.Entry<Document, DocumentListener> entry : activeListeners.entrySet()) {
            entry.getKey().removeDocumentListener(entry.getValue());
        }
        activeListeners.clear();
    }

    private void AttachEditorListener(Project project) {

    }

    private void HandleEditEvent(Editor editor, DocumentEvent event) {
        Document doc = event.getDocument();

        int offset = event.getOffset();
        int line = doc.getLineNumber(offset);
        int col = offset - doc.getLineStartOffset(line);

        String inserted = event.getNewFragment().toString();
        String deleted = event.getOldFragment().toString();

        VirtualFile vf = FileDocumentManager.getInstance().getFile(doc);
        String path = (vf != null ? vf.getPath() : "");

        WriteTextEventToXML(path, line + 1, col + 1, inserted, deleted);
    }

    private void WriteTextEventToXML(String path, int line, int col, String inserted, String deleted) {

        try {
            xmlFile.write(String.format("        <text_event timestamp=\"%d\" source_file_path=\"%s\" source_file_line=\"%d\" source_file_col=\"%d\" inserted=\"%s\" deleted=\"%s\"/>\n",
                    System.currentTimeMillis(), // Plugin time
                    path, // File path
                    line, // Source File Line Number
                    col, // Source File Column Number
                    escape(inserted), // Inserted Text
                    escape(deleted) // Deleted text
            ));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

    public void ProcessCoreData(@Nullable Project project, @NotNull ProgressIndicator indicator) {
        try {
            socket = new Socket(hostName,port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }
        catch (IOException e) {
            System.err.println("Connection Error - iTrace-Core might not be running");
            throw new RuntimeException(e);
        }

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
                String ide = ApplicationInfo.getInstance().getFullApplicationName().split(" ")[0];
                try {
                    xmlFile = new BufferedWriter(new FileWriter(tokens[3]+String.format("\\itrace_%s-%d.xml",ide.toLowerCase(),System.currentTimeMillis()), true));
                    xmlFile.write("<?xml version=\"1.0\"?>\n");
                    xmlFile.write("<itrace_plugin session_id=\"" + tokens[1] + "\">\n");
                    xmlFile.write(String.format("    <environment screen_width=\"%d\" screen_height=\"%d\" plugin_type=\"%s\"/>\n",
                            Toolkit.getDefaultToolkit().getScreenSize().width,
                            Toolkit.getDefaultToolkit().getScreenSize().height,
                            ide.toUpperCase()));
                    xmlFile.write("    <gazes>\n");
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }

                AttachListeners(project);
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

                DetachListeners();

                indicator.setText("Session ended.");
                System.out.println("Session ended");
            }

            else if(tokens[0].equals("gaze")) {
                try {
                    int x = Integer.parseInt(tokens[2]);
                    int y = Integer.parseInt(tokens[3]);

                    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
                    FileEditor[] editors = fileEditorManager.getAllEditors();

                    Editor editor = null;

                    for (FileEditor fe : editors) {
                        if (!(fe instanceof TextEditor textEditor)) {
                            continue;
                        }

                        Editor e = textEditor.getEditor();
                        JComponent comp = e.getComponent();
                        if (!comp.isShowing()) { // component is hidden (likely a not focused on tab)
                            continue;
                        }
                        Point loc = comp.getLocationOnScreen();
                        Rectangle bounds = new Rectangle(loc.x, loc.y, comp.getWidth(), comp.getHeight());

                        if (bounds.contains(x,y)) {
                            editor = e;
                            break;
                        }
                    }

                    if(editor == null) {
                        continue;
                    }
                    int line_height = -1;//editor.getComponent().getFontMetrics(editor.getColorsScheme().getFontPreferences().getFontType()).getHeight();
                    float font_size = editor.getColorsScheme().getEditorFontSize2D();
                    VirtualFile vf = FileDocumentManager.getInstance().getFile(editor.getDocument());
                    String filename = (vf != null ? vf.getPath() : "");
                    int editor_x = editor.getContentComponent().getLocationOnScreen().x;
                    int editor_y = editor.getContentComponent().getLocationOnScreen().y;

                    int line;
                    int column;

                    if(x - editor_x < 0 || y - editor_y < 0) {
                        line = -1;
                        column = -1;
                    }
                    else {

                        Point gaze_point = new Point(x - editor_x, y - editor_y);

                        AtomicReference<LogicalPosition> logicalPositionRef = new AtomicReference<>();

                        Editor finalEditor = editor; // Need to do this so it's "final"?
                        ApplicationManager.getApplication().invokeAndWait(() -> {
                            // Safely access xyToLogicalPosition on the EDT
                            logicalPositionRef.set(finalEditor.xyToLogicalPosition(gaze_point));
                        });

                        LogicalPosition logicalPosition = logicalPositionRef.get();

                        line = logicalPosition.line + 1;
                        column = logicalPosition.column + 1;

                        Document doc = editor.getDocument();
                        String[] text_lines = doc.getText().split("\n");
                        if (logicalPosition.line >= text_lines.length) {
                            line = -1;
                            column = -1;
                        } else if (logicalPosition.column >= text_lines[logicalPosition.line].length()) {
                            line = -1;
                            column = -1;
                        }
                    }

                    indicator.setText(tokens[2]+","+tokens[3]+" -> " + String.valueOf(line) +","+String.valueOf(column));
                    xmlFile.write(String.format("        <response event_id=\"%s\" plugin_time=\"%d\" x=\"%d\" y=\"%d\" gaze_target=\"%s\" gaze_target_type=\"%s\" source_file_path=\"%s\" source_file_line=\"%d\" source_file_col=\"%d\" editor_line_height=\"%s\" editor_font_height=\"%f\" editor_line_base_x=\"\" editor_line_base_y=\"\"/>\n",
                                  tokens[1], // event ID
                                  System.currentTimeMillis(), //Plugin Time
                                  x, // x
                                  y, // y
                                  filename.split("/")[filename.split("/").length-1], // Gaze Target
                                  filename.split("\\.")[filename.split("\\.").length-1], // Gaze Target Type
                                  filename, // Source File Path
                                  line, // Source File Line
                                  column, // Source File Column
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
            throw new RuntimeException(e);
        }
        finally {
            socket = null;
            in = null;
        }
    }

}
