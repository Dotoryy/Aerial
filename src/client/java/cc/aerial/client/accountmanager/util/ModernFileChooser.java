package cc.aerial.client.accountmanager.util;

import net.minecraft.client.Minecraft;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class ModernFileChooser {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private ModernFileChooser() {
    }

    public static void showOpenDialog(String title, File startDirectory, String filterDescription, String[] extensions,
                                       Consumer<File> onSelected, Runnable onCanceled) {
        File directory = resolveDirectory(startDirectory);
        EXECUTOR.execute(() -> {
            File selected = isWindows() ? pickWindowsDialog(title, directory, filterDescription, extensions) : pickAwtDialog(title, directory);
            Runnable callback = () -> {
                if (selected != null && selected.exists()) {
                    if (onSelected != null) {
                        onSelected.accept(selected);
                    }
                } else if (onCanceled != null) {
                    onCanceled.run();
                }
            };
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) {
                minecraft.execute(callback);
            } else {
                callback.run();
            }
        });
    }

    private static File pickWindowsDialog(String title, File directory, String filterDescription, String[] extensions) {
        String filter = buildPowerShellFilter(filterDescription, extensions);
        String directoryPath = escapePowerShell(directory.getAbsolutePath());
        String script = "Add-Type -AssemblyName System.Windows.Forms | Out-Null; $dialog = New-Object System.Windows.Forms.OpenFileDialog; "
                + "$dialog.Title = '" + escapePowerShell(title) + "'; $dialog.InitialDirectory = '" + directoryPath + "'; "
                + "$dialog.Filter = '" + filter + "'; $dialog.FilterIndex = 1; "
                + "if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { [Console]::Out.Write($dialog.FileName) }";
        try {
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA", "-Command", script)
                    .redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            int exitCode = process.waitFor();
            String path = output.toString().trim();
            if (exitCode == 0 && !path.isEmpty()) {
                return new File(path);
            }
        } catch (Exception e) {
            System.err.println("[ModernFileChooser] Windows dialog failed: " + e.getMessage());
        }
        return pickAwtDialog(title, directory);
    }

    private static File pickAwtDialog(String title, File directory) {
        try {
            FileDialog dialog = new FileDialog((Frame) null, title, FileDialog.LOAD);
            dialog.setDirectory(directory.getAbsolutePath());
            dialog.setVisible(true);
            String file = dialog.getFile();
            String dir = dialog.getDirectory();
            return file != null && dir != null ? new File(dir, file) : null;
        } catch (Exception e) {
            System.err.println("[ModernFileChooser] AWT dialog failed: " + e.getMessage());
            return null;
        }
    }

    private static String buildPowerShellFilter(String description, String[] extensions) {
        if (extensions == null || extensions.length == 0) {
            return "All files (*.*)|*.*";
        }
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < extensions.length; i++) {
            if (i > 0) {
                pattern.append(';');
            }
            pattern.append("*.").append(extensions[i]);
        }
        String label = description != null && !description.isEmpty() ? description : "Supported files";
        return label + "|" + pattern + "|All files (*.*)|*.*";
    }

    private static File resolveDirectory(File startDirectory) {
        return startDirectory != null && startDirectory.isDirectory() ? startDirectory : new File(System.getProperty("user.home"), "Downloads");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static String escapePowerShell(String value) {
        return value.replace("'", "''");
    }
}
