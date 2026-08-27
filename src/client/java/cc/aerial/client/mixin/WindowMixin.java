package cc.aerial.client.mixin;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.IconSet;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.server.packs.PackResources;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Mixin(Window.class)
public abstract class WindowMixin {

    @Shadow
    public abstract long handle();

    private static final String[] AERIAL$ICONS = {
            "/assets/aerial/textures/icon/16.png",
            "/assets/aerial/textures/icon/32.png",
            "/assets/aerial/textures/icon/48.png",
            "/assets/aerial/textures/icon/128.png"
    };

    @Inject(method = "setIcon", at = @At("TAIL"))
    private void aerial$replaceIcon(PackResources pack, IconSet iconSet, CallbackInfo ci) {
        int platform = GLX.getGlfwPlatform();
        if (platform != GLFW.GLFW_PLATFORM_WIN32 && platform != GLFW.GLFW_PLATFORM_X11) {
            return;
        }

        List<NativeImage> images = new ArrayList<>();
        List<ByteBuffer> buffers = new ArrayList<>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            for (String path : AERIAL$ICONS) {
                try (InputStream stream = WindowMixin.class.getResourceAsStream(path)) {
                    if (stream == null) {
                        continue;
                    }
                    images.add(NativeImage.read(stream));
                } catch (Exception ignored) {
                }
            }
            if (images.isEmpty()) {
                return;
            }

            GLFWImage.Buffer buffer = GLFWImage.malloc(images.size(), stack);
            for (int i = 0; i < images.size(); i++) {
                NativeImage image = images.get(i);
                int[] pixels = image.getPixelsABGR();
                ByteBuffer bytes = MemoryUtil.memAlloc(pixels.length * 4);
                bytes.asIntBuffer().put(pixels);
                buffers.add(bytes);
                buffer.position(i);
                buffer.width(image.getWidth());
                buffer.height(image.getHeight());
                buffer.pixels(bytes);
            }
            buffer.position(0);
            GLFW.glfwSetWindowIcon(handle(), buffer);
        } catch (Exception ignored) {
        } finally {
            buffers.forEach(MemoryUtil::memFree);
            images.forEach(NativeImage::close);
        }
    }
}
