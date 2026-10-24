package pl.zyro.basealert;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

public class BaseAlertClient implements ClientModInitializer {
    /** 0 = nic, 1 = czekam na górny róg, 2 = czekam na dolny róg */
    public static int step = 0;

    private static KeyBinding openKey;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static long lastAlert = 0;
    private static int tick = 0;

    @Override
    public void onInitializeClient() {
        BaseConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.zyrobase.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.zyrobase"));

        // Prawy klik w blok podczas zaznaczania bazy
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient() || step == 0 || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            BlockPos p = hit.getBlockPos();
            if (step == 1) {
                BaseConfig.pos1 = p;
                step = 2;
                msg("§aGórny róg: " + p.toShortString() + " §7— teraz kliknij PRAWYM DOLNY róg bazy.");
            } else {
                BaseConfig.pos2 = p;
                step = 0;
                BaseConfig.save();
                msg("§aDolny róg: " + p.toShortString() + " §7— baza zaznaczona! Alerty aktywne.");
            }
            return ActionResult.SUCCESS; // nie otwiera skrzynek itp. podczas zaznaczania
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                client.setScreen(new BaseScreen());
            }
            if (++tick % 20 == 0) checkBase(client);
        });
    }

    private static void checkBase(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (!BaseConfig.enabled || !BaseConfig.hasBase() || BaseConfig.topic.isEmpty()) return;

        BlockPos a = BaseConfig.pos1, b = BaseConfig.pos2;
        Box box = new Box(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1
        ).expand(BaseConfig.radius);

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player) continue;
            if (box.contains(p.getPos())) {
                long now = System.currentTimeMillis();
                if (now - lastAlert > 60_000) { // max 1 alert na minutę
                    lastAlert = now;
                    alert(client, p);
                }
                return;
            }
        }
    }

    private static void alert(MinecraftClient client, PlayerEntity p) {
        String name = p.getName().getString();

        StringBuilder sb = new StringBuilder();
        sb.append("Gracz ").append(name).append(" jest blisko Twojej bazy!\n");
        sb.append("Odległość: ").append(Math.round(p.distanceTo(client.player))).append(" bloków\n");
        sb.append("Pozycja: ").append(p.getBlockPos().toShortString()).append("\n");

        sb.append("Serca: ").append(num(p.getHealth() / 2f)).append("/").append(num(p.getMaxHealth() / 2f));
        if (p.getAbsorptionAmount() > 0) {
            sb.append(" (+").append(num(p.getAbsorptionAmount() / 2f)).append(" złote)");
        }
        sb.append("\n");

        sb.append("W ręce: ").append(describe(p.getMainHandStack())).append("\n");
        sb.append("Druga ręka: ").append(describe(p.getOffHandStack())).append("\n");
        sb.append("Hełm: ").append(describe(p.getEquippedStack(EquipmentSlot.HEAD))).append("\n");
        sb.append("Napierśnik: ").append(describe(p.getEquippedStack(EquipmentSlot.CHEST))).append("\n");
        sb.append("Spodnie: ").append(describe(p.getEquippedStack(EquipmentSlot.LEGS))).append("\n");
        sb.append("Buty: ").append(describe(p.getEquippedStack(EquipmentSlot.FEET)));

        String skinUrl = null;
        if (BaseConfig.skinPhoto) {
            skinUrl = "https://minotar.net/armor/body/" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "/200.png";
        }

        sendPush(sb.toString(), skinUrl);
        msg("§c⚠ " + name + " jest blisko Twojej bazy!");

        if (BaseConfig.screenshot) sendScreenshot(client);
    }

    private static String describe(ItemStack stack) {
        if (stack.isEmpty()) return "brak";
        String s = stack.getName().getString();
        if (stack.getCount() > 1) s += " x" + stack.getCount();
        return s;
    }

    private static String num(float v) {
        if (v == Math.floor(v)) return String.valueOf((int) v);
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** Wysyła powiadomienie przez ntfy.sh. attachUrl = adres obrazka pokazanego w powiadomieniu (lub null). */
    public static void sendPush(String text, String attachUrl) {
        if (BaseConfig.topic.isEmpty()) return;
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create("https://ntfy.sh/" + BaseConfig.topic))
                .header("Title", "Minecraft Base Alert")
                .header("Priority", "high")
                .header("Tags", "warning");
        if (attachUrl != null) {
            rb.header("Attach", attachUrl).header("Filename", "gracz.png");
        }
        rb.POST(HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8));
        HTTP.sendAsync(rb.build(), HttpResponse.BodyHandlers.discarding());
    }

    /** Robi zrzut ekranu gry i wysyła go jako obrazek w osobnym powiadomieniu. */
    private static void sendScreenshot(MinecraftClient client) {
        try {
            NativeImage img = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
            File f = File.createTempFile("zyrobase", ".png");
            img.writeTo(f);
            img.close();
            byte[] data = Files.readAllBytes(f.toPath());
            f.delete();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://ntfy.sh/" + BaseConfig.topic))
                    .header("Title", "Minecraft - zrzut ekranu")
                    .header("Filename", "zrzut.png")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                    .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }
}
