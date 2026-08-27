package cc.aerial.client.utility;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

public final class ScreenshotHandler {
    public static final Identifier COPY_ACTION = Identifier.fromNamespaceAndPath("aerial", "copy_screenshot");

    private static File lastScreenshot;

    private ScreenshotHandler() {
    }

    public static Component buildMessage(File file) {
        lastScreenshot = file;

        MutableComponent message = ThemeText.gradient("[Aerial]");
        message.append(Component.literal(" Screenshot Taken!  ").withStyle(ChatFormatting.GRAY));
        message.append(button("[Open]", ChatFormatting.AQUA,
                new ClickEvent.OpenFile(file), "Open " + file.getName()));
        message.append(Component.literal(" "));
        message.append(button("[Copy]", ChatFormatting.GREEN,
                new ClickEvent.Custom(COPY_ACTION, Optional.empty()), "Copy the image to your clipboard"));
        return message;
    }

    private static MutableComponent button(String label, ChatFormatting color, ClickEvent click, String tooltip) {
        return Component.literal(label).withStyle(Style.EMPTY
                .withColor(color)
                .withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(tooltip))));
    }

    public static void copyLastScreenshot() {
        File file = lastScreenshot;
        if (file == null || !file.isFile()) {
            notifyResult("Nothing to copy");
            return;
        }

        Thread worker = new Thread(() -> {
            boolean copied = isWindows() ? copyImageWindows(file) : copyImageAwt(file);
            Minecraft.getInstance().execute(() -> {
                if (copied) {
                    notifyResult("Copied to clipboard");
                    return;
                }

                Minecraft.getInstance().keyboardHandler.setClipboard(file.getAbsolutePath());
                notifyResult("Clipboard unavailable - copied path");
            });
        }, "Aerial Screenshot Copy");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean copyImageWindows(File file) {
        try {
            String quoted = "'" + file.getAbsolutePath().replace("'", "''") + "'";
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-STA",
                    "-WindowStyle", "Hidden", "-Command",
                    "Add-Type -AssemblyName System.Windows.Forms,System.Drawing;"
                            + "$image = [System.Drawing.Image]::FromFile(" + quoted + ");"
                            + "[System.Windows.Forms.Clipboard]::SetDataObject($image, $true);"
                            + "$image.Dispose()")
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroy();
                LOGGER.warn("Clipboard copy timed out");
                return false;
            }
            if (process.exitValue() != 0) {
                LOGGER.warn("Clipboard copy failed with exit code {}", process.exitValue());
                return false;
            }
            return true;
        } catch (Exception exception) {
            LOGGER.warn("Could not copy the screenshot to the clipboard", exception);
            return false;
        }
    }

    private static boolean copyImageAwt(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return false;
            }
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new ImageTransferable(image), null);

            heldTransferable = image;
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Could not copy the screenshot to the clipboard", throwable);
            return false;
        }
    }

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static BufferedImage heldTransferable;

    private static void notifyResult(String description) {
        cc.aerial.client.notification.NotificationManager.INSTANCE
                .builder(cc.aerial.client.notification.NotificationType.INFO)
                .title("Screenshot")
                .description(description)
                .duration(2500)
                .buildAndPublish();
    }

    private record ImageTransferable(BufferedImage image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) {
            return image;
        }
    }
}
