package pl.zyro.basealert;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
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
import java.security.SecureRandom;
import java.util.Locale;

public class BaseAlertClient implements ClientModInitializer {
    public static final String VERSION = "1.2.0";

    /** 0 = nic, 1 = czekam na górny róg, 2 = czekam na dolny róg */
    public static int step = 0;

    /** Wynik ostatniej wysyłki na telefon (pokazywany w zakładce Telefon). */
    public static volatile String lastResult = "jeszcze nic nie wysłano";

    private static KeyBinding openKey;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static long lastAlert = 0;
    private static long bannerUntil = 0;
    private static String bannerText = "";
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
                msg("§aGórny róg: §f" + p.toShortString() + " §7— teraz kliknij PRAWYM §fDOLNY §7róg bazy.");
            } else {
                BaseConfig.pos2 = p;
                step = 0;
                BaseConfig.save();
                msg("§aDolny róg: §f" + p.toShortString() + " §7— baza zaznaczona! Ochrona aktywna.");
            }
            return ActionResult.SUCCESS; // nie otwiera skrzynek itp. podczas zaznaczania
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                client.setScreen(new BaseScreen());
            }
            if (++tick % 20 == 0) checkBase(client);
        });

        // Czerwony migający baner na ekranie podczas alarmu
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            long now = System.currentTimeMillis();
            if (now > bannerUntil) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            String text = "⚠ " + bannerText + " ⚠";
            int w = mc.textRenderer.getWidth(text);
            int cx = ctx.getScaledWindowWidth() / 2;
            boolean blink = (now / 300) % 2 == 0;
            ctx.fill(cx - w / 2 - 10, 10, cx + w / 2 + 10, 28, blink ? 0xCCB00000 : 0xCC700000);
            ctx.fill(cx - w / 2 - 10, 28, cx + w / 2 + 10, 30, 0xFFFF5555);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, text, cx, 15, 0xFFFFFFFF);
        });
    }

    /** Losowy, trudny do zgadnięcia temat ntfy (działa jak hasło). */
    public static String randomTopic() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder("zyro-");
        for (int i = 0; i < 10; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    /** Link do zasubskrybowania tematu na telefonie (do kodu QR i schowka). */
    public static String subscribeLink() {
        return (BaseConfig.qrApp ? "ntfy://ntfy.sh/" : "https://ntfy.sh/") + BaseConfig.topic;
    }

    private static void checkBase(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        if (!BaseConfig.enabled || !BaseConfig.hasBase()) return;

        BlockPos a = BaseConfig.pos1, b = BaseConfig.pos2;
        Box box = new Box(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1
        ).expand(BaseConfig.radius);

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player) continue;
            if (BaseConfig.isSafe(p.getName().getString())) continue; // bezpieczny gracz - bez alarmu
            if (box.contains(p.getPos())) {
                long now = System.currentTimeMillis();
                if (now - lastAlert > BaseConfig.cooldownSec * 1000L) {
                    lastAlert = now;
                    alert(client, p);
                }
                return;
            }
        }
    }

    private static void alert(MinecraftClient client, PlayerEntity p) {
        String name = p.getName().getString();

        // --- w grze: baner, dźwięk, wiadomość ---
        msg("§c⚠ §f" + name + " §cjest blisko Twojej bazy!");
        if (BaseConfig.banner) {
            bannerText = "INTRUZ: " + name;
            bannerUntil = System.currentTimeMillis() + 6000;
        }
        if (BaseConfig.sound) playAlarm(client);

        // --- na telefon ---
        if (!BaseConfig.validTopic()) return;

        StringBuilder sb = new StringBuilder();
        sb.append("⚠ Gracz ").append(name).append(" jest blisko Twojej bazy!\n");
        sb.append("📍 ").append(p.getBlockPos().toShortString())
                .append("  (").append(Math.round(p.distanceTo(client.player))).append(" bloków od Ciebie)\n");
        sb.append("❤ Serca: ").append(num(p.getHealth() / 2f)).append("/").append(num(p.getMaxHealth() / 2f));
        if (p.getAbsorptionAmount() > 0) {
            sb.append(" (+").append(num(p.getAbsorptionAmount() / 2f)).append(" złote)");
        }
        sb.append("\n⚔ W ręce: ").append(describe(p.getMainHandStack()));
        sb.append("\nDruga ręka: ").append(describe(p.getOffHandStack()));
        sb.append("\nHełm: ").append(describe(p.getEquippedStack(EquipmentSlot.HEAD)));
        sb.append("\nNapierśnik: ").append(describe(p.getEquippedStack(EquipmentSlot.CHEST)));
        sb.append("\nSpodnie: ").append(describe(p.getEquippedStack(EquipmentSlot.LEGS)));
        sb.append("\nButy: ").append(describe(p.getEquippedStack(EquipmentSlot.FEET)));

        String skinUrl = null;
        if (BaseConfig.skinPhoto) {
            skinUrl = "https://minotar.net/armor/body/" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "/200.png";
        }
        sendPush(sb.toString(), skinUrl);

        if (BaseConfig.screenshot) sendScreenshot(client);
    }

    private static void playAlarm(MinecraftClient client) {
        try {
            SoundEvent ev = Registries.SOUND_EVENT.get(Identifier.of("block.note_block.pling"));
            if (ev != null) client.getSoundManager().play(PositionedSoundInstance.master(ev, 1.0f));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        if (!BaseConfig.validTopic()) {
            lastResult = "§cnajpierw ustaw temat";
            return;
        }
        lastResult = "§ewysyłanie...";
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create("https://ntfy.sh/" + BaseConfig.topic))
                .header("Title", "Minecraft Base Alert")
                .header("Priority", "high")
                .header("Tags", "rotating_light");
        if (attachUrl != null) {
            rb.header("Attach", attachUrl).header("Filename", "gracz.png");
        }
        rb.POST(HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8));
        send(rb.build());
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
            send(req);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void send(HttpRequest req) {
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                .thenAccept(r -> lastResult = r.statusCode() == 200 ? "§aOK - wysłano" : "§cbłąd HTTP " + r.statusCode())
                .exceptionally(e -> {
                    lastResult = "§cbrak połączenia z ntfy.sh";
                    return null;
                });
    }

    public static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("§8[§bZyro§8] §r" + s), false);
    }
}
