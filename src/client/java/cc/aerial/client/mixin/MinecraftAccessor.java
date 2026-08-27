package cc.aerial.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Mutable
    @Accessor("user")
    void aerial$setUser(User user);

    @Mutable
    @Accessor("userApiService")
    void aerial$setUserApiService(UserApiService service);

    @Accessor("userApiService")
    UserApiService aerial$getUserApiService();

    @Mutable
    @Accessor("profileKeyPairManager")
    void aerial$setProfileKeyPairManager(ProfileKeyPairManager manager);
}
