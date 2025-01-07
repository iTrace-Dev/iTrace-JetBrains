# iTrace-JetBrains
iTrace-JetBrains is a plugin for the [JetBrains](https://www.jetbrains.com/) family of IDEs. The plugin will establish a connection to the [iTrace Core](https://github.com/iTrace-Dev/iTrace-Core) desktop application. Once connected to the Core, the plugin will accept eye-tracking information from the Core and translate it to editor-specific data and output said data to an XML file.

# Installation
1. Download the latest version of the plugin. The file is named `iTrace-JetBrains-X.X.X.zip`.
2. Open the desired JetBrains editor. iTrace-JetBrains was tested on IntelliJ IDEA, PyCharm, WebStorm, and RustRover - however, it should work on any of the various JetBrains IDEs.
3. Open the Settings menu and navigate to "Plugins".
4. Click on the gear icon next to "Installed". Then, select "Install Plugin from Disk...".
5. Navigate to and select the .zip file you downloaded earlier.
6. Accept any prompts you are given, and click "Apply" in the Settings menu.

# Usage
To use iTrace-JetBrains, make sure you have iTrace-Core installed.
1. Open the project/files you wish to view.
2. Run iTrace-Core and set up the parameters of your tracking session.
3. Go to the "Tools" tab in your JetBrains editor.
4. Select "Connect to iTrace-Core". If the connection is successful, a notification at the bottom of the screen should alert you.
5. iTrace-JetBrains is now connected, you can control the tracking session using iTrace-Core.
6. Once a tracking session is started, iTrace-JetBrains will begin writing to a file in the location specified in iTrace-Core. When the tracking session is finished, two files will be present - one from iTrace-JetBrains and the other from iTrace-Core. iTrace-JetBrains will recongize which JetBrains IDE you are using and automatically name the file appropriately.

# How to Install From Source
If you want to install or run the plugin from source, follow these steps:
1. Download or clone the source code.
2. Open IntelliJ IDEA.
3. Select "Open" and select the folder containing iTrace-JetBrains
You should now have the project open in IntelliJ. You have two options for building the plugin.
## Running the Pluign
If you want to run and debug the plugin, go to the top and select the "Current File" dropdown. Select "Run Plugin" and then click the green play button. This will launch a new instance of IntelliJ with the plugin running, and you can debug it.
## Building the Plugin for Distribution
To build the plugin for distribution and installation, follow these steps:
1. Open the project in IntelliJ.
2. Select View->Tool Windows->Terminal.
3. Run `.\gradlew buildPlugin` in the opened terminal.
4. After running, the built plugin will be in the `build\distributions`.

# Further Steps
After gathering your data, you can use our other tools [iTrace-Toolkit](https://github.com/iTrace-Dev/iTrace-Toolkit) and [iTrace-Visualize](https://github.com/iTrace-Dev/iTrace-Visualize) to analyze and process the tracking sessions.
