package pl.zyro.basealert;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class BaseScreen extends Screen {
    private static final int PW = 350, PH = 236;
    private static final int TAB_COUNT = 4;
    private static final String[] TABS = {"Główna", "Telefon", "Bezpieczni", "Ustawienia"};
    private static final String[] STYLES = {"Ciemny", "Bardzo ciemny", "Szkło"};
    private static final int[] PANEL_BG = {0xE6101418, 0xF4000000, 0x9A101418};
    private static final int[] SWATCHES = {
            0x4DA6FF, 0x55FF55, 0xFF5555, 0xFFAA00, 0xFFD43B,
            0xAA66FF, 0xFF66CC, 0x55FFFF, 0xFFFFFF, 0x9AA0A6};
    private static final int ROWS_PER_PAGE = 6;

    private static final String[] SHIELD = {
            "###########",
            "#ooooooooo#",
            "#oooowoooo#",
            "#ooowwwooo#",
            "#oooowoooo#",
            "#ooooooooo#",
            ".#ooooooo#.",
            ".#ooooooo#.",
            "..#ooooo#..",
            "...#ooo#...",
            "....#o#....",
            ".....#....."};

    private static int tab = 0;
    private static int safePage = 0;

    private TextFieldWidget topicField, safeField, hexField;
    private String safeMsg = "";
    private QrCode qr;
    private String qrFor = "";

    public BaseScreen() {
        super(Text.literal("Zyro Base Alert"));
    }

    // ------------------------------------------------------------ pomocnicze

    private int left() { return (this.width - PW) / 2; }

    private int top() { return Math.max(4, (this.height - PH) / 2); }

    private static int accent() { return 0xFF000000 | BaseConfig.accent; }

    private static int withAlpha(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    private static Text onOff(String label, boolean v) {
        return Text.literal(label + ": " + (v ? "§aWŁ." : "§cWYŁ."));
    }

    private void rebuild() {
        clearChildren();
        buildTab();
    }

    private void addToggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(onOff(label, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(onOff(label, get.getAsBoolean()));
            BaseConfig.save();
        }).dimensions(x, y, w, 20).build());
    }

    private void addStepper(int x0, int y, int min, int max, int step, IntSupplier get, IntConsumer set) {
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> {
            set.accept(Math.max(min, get.getAsInt() - step));
            BaseConfig.save();
        }).dimensions(x0 + 178, y, 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            set.accept(Math.min(max, get.getAsInt() + step));
            BaseConfig.save();
        }).dimensions(x0 + 316, y, 24, 20).build());
    }

    private int wrapped(DrawContext ctx, String s, int x, int y, int w, int color) {
        List<OrderedText> lines = textRenderer.wrapLines(Text.literal(s), w);
        for (OrderedText line : lines) {
            ctx.drawTextWithShadow(textRenderer, line, x, y, color);
            y += 10;
        }
        return y;
    }

    // ------------------------------------------------------------ budowanie zakładek

    @Override
    protected void init() {
        buildTab();
    }

    @Override
    public void removed() {
        BaseConfig.save();
        super.removed();
    }

    private void buildTab() {
        int x0 = left(), y0 = top();
        int tw = (PW - 20) / TAB_COUNT;
        for (int i = 0; i < TAB_COUNT; i++) {
            final int idx = i;
            String label = (i == tab ? "§f" : "§7") + TABS[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                tab = idx;
                rebuild();
            }).dimensions(x0 + 10 + i * tw, y0 + 26, tw - 2, 18).build());
        }

        int cy = y0 + 54;
        switch (tab) {
            case 0 -> buildMain(x0, cy);
            case 1 -> buildPhone(x0, cy);
            case 2 -> buildSafe(x0, cy);
            default -> buildSettings(x0, cy);
        }
    }

    private void buildMain(int x0, int cy) {
        addDrawableChild(ButtonWidget.builder(Text.literal("Zaznacz bazę"), b -> {
            BaseAlertClient.step = 1;
            BaseConfig.pos1 = null;
            BaseConfig.pos2 = null;
            close();
            BaseAlertClient.msg("§aKliknij PRAWYM przyciskiem GÓRNY róg swojej bazy.");
        }).dimensions(x0 + 10, cy + 60, 162, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Szybka baza (±16 bloków)"), b -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            BlockPos c = mc.player.getBlockPos();
            BaseConfig.pos1 = c.add(-16, 16, -16);
            BaseConfig.pos2 = c.add(16, -4, 16);
            BaseConfig.save();
            BaseAlertClient.msg("§aBaza ustawiona wokół Ciebie (±16 bloków).");
        }).dimensions(x0 + 178, cy + 60, 162, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Usuń bazę"), b -> {
            BaseConfig.pos1 = null;
            BaseConfig.pos2 = null;
            BaseConfig.save();
            BaseAlertClient.msg("§eBaza usunięta.");
        }).dimensions(x0 + 10, cy + 82, 162, 20).build());

        addToggle(x0 + 178, cy + 82, 162, "Alarm", () -> BaseConfig.enabled, v -> BaseConfig.enabled = v);
        addToggle(x0 + 10, cy + 104, 162, "Zdjęcie gracza", () -> BaseConfig.skinPhoto, v -> BaseConfig.skinPhoto = v);
        addToggle(x0 + 178, cy + 104, 162, "Zrzut ekranu", () -> BaseConfig.screenshot, v -> BaseConfig.screenshot = v);
    }

    private void buildPhone(int x0, int cy) {
        topicField = new TextFieldWidget(textRenderer, x0 + 10, cy + 12, 128, 18, Text.literal("temat"));
        topicField.setMaxLength(64);
        topicField.setTextPredicate(s -> s.matches("[A-Za-z0-9_-]*"));
        topicField.setText(BaseConfig.topic);
        topicField.setPlaceholder(Text.literal("np. zyro-baza-123"));
        topicField.setChangedListener(s -> BaseConfig.topic = s.trim());
        addDrawableChild(topicField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Losuj"), b -> {
            BaseConfig.topic = BaseAlertClient.randomTopic();
            topicField.setText(BaseConfig.topic);
            BaseConfig.save();
        }).dimensions(x0 + 142, cy + 11, 52, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Kopiuj link"), b -> {
            if (BaseConfig.validTopic()) {
                MinecraftClient.getInstance().keyboard.setClipboard(BaseAlertClient.subscribeLink());
                BaseAlertClient.msg("§aLink skopiowany do schowka.");
            }
        }).dimensions(x0 + 10, cy + 36, 91, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Wyślij test"), b -> {
            BaseConfig.save();
            BaseAlertClient.sendPush("✔ Test: telefon jest połączony z Minecraftem!", null);
        }).dimensions(x0 + 103, cy + 36, 91, 20).build());

        addDrawableChild(ButtonWidget.builder(qrText(), b -> {
            BaseConfig.qrApp = !BaseConfig.qrApp;
            b.setMessage(qrText());
            BaseConfig.save();
        }).dimensions(x0 + 10, cy + 60, 184, 20).build());
    }

    private static Text qrText() {
        return Text.literal("Kod QR: " + (BaseConfig.qrApp ? "aplikacja ntfy" : "przeglądarka"));
    }

    private void buildSafe(int x0, int cy) {
        safeField = new TextFieldWidget(textRenderer, x0 + 10, cy + 22, 230, 18, Text.literal("nick"));
        safeField.setMaxLength(16);
        safeField.setPlaceholder(Text.literal("nick gracza"));
        addDrawableChild(safeField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Dodaj"), b -> addSafeFromField())
                .dimensions(x0 + 244, cy + 21, 96, 20).build());

        List<String> list = BaseConfig.safePlayers;
        int pages = Math.max(1, (list.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        if (safePage >= pages) safePage = pages - 1;
        int from = safePage * ROWS_PER_PAGE;
        for (int i = from; i < Math.min(list.size(), from + ROWS_PER_PAGE); i++) {
            final String nick = list.get(i);
            int ry = cy + 48 + (i - from) * 18;
            addDrawableChild(ButtonWidget.builder(Text.literal("§cX"), b -> {
                BaseConfig.removeSafe(nick);
                safeMsg = "§eUsunięto " + nick;
                rebuild();
            }).dimensions(x0 + 316, ry, 24, 16).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            if (safePage > 0) { safePage--; rebuild(); }
        }).dimensions(x0 + 10, cy + 160, 24, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            if (safePage < pages - 1) { safePage++; rebuild(); }
        }).dimensions(x0 + 36, cy + 160, 24, 16).build());
    }

    private void addSafeFromField() {
        String nick = safeField.getText().trim();
        if (BaseConfig.addSafe(nick)) {
            safeMsg = "§aDodano " + nick;
            int pages = Math.max(1, (BaseConfig.safePlayers.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
            safePage = pages - 1;
            rebuild();
        } else {
            safeMsg = "§cZły nick albo już jest na liście";
        }
    }

    private void buildSettings(int x0, int cy) {
        hexField = new TextFieldWidget(textRenderer, x0 + 10, cy + 38, 84, 18, Text.literal("hex"));
        hexField.setMaxLength(7);
        hexField.setText(String.format("#%06X", BaseConfig.accent));
        hexField.setChangedListener(s -> {
            String t = s.trim();
            if (t.startsWith("#")) t = t.substring(1);
            if (t.matches("[0-9a-fA-F]{6}")) BaseConfig.accent = Integer.parseInt(t, 16);
        });
        addDrawableChild(hexField);

        addDrawableChild(ButtonWidget.builder(styleText(), b -> {
            BaseConfig.panelStyle = (BaseConfig.panelStyle + 1) % STYLES.length;
            b.setMessage(styleText());
            BaseConfig.save();
        }).dimensions(x0 + 100, cy + 37, 240, 20).build());

        addToggle(x0 + 10, cy + 62, 162, "Dźwięk alarmu", () -> BaseConfig.sound, v -> BaseConfig.sound = v);
        addToggle(x0 + 178, cy + 62, 162, "Baner na ekranie", () -> BaseConfig.banner, v -> BaseConfig.banner = v);

        addStepper(x0, cy + 88, 0, 64, 2, () -> BaseConfig.radius, v -> BaseConfig.radius = v);
        addStepper(x0, cy + 112, 10, 600, 10, () -> BaseConfig.cooldownSec, v -> BaseConfig.cooldownSec = v);

        addDrawableChild(ButtonWidget.builder(Text.literal("Przywróć domyślny wygląd"), b -> {
            BaseConfig.accent = 0x4DA6FF;
            BaseConfig.panelStyle = 0;
            BaseConfig.save();
            rebuild();
        }).dimensions(x0 + 10, cy + 140, 330, 20).build());
    }

    private static Text styleText() {
        return Text.literal("Styl panelu: " + STYLES[BaseConfig.panelStyle]);
    }

    // ------------------------------------------------------------ rysowanie

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        int x0 = left(), y0 = top();
        int acc = accent();

        // tło panelu i ramka
        ctx.fill(x0, y0, x0 + PW, y0 + PH, PANEL_BG[BaseConfig.panelStyle]);
        int border = withAlpha(BaseConfig.accent, 0xAA);
        ctx.fill(x0, y0, x0 + PW, y0 + 1, border);
        ctx.fill(x0, y0 + PH - 1, x0 + PW, y0 + PH, border);
        ctx.fill(x0, y0, x0 + 1, y0 + PH, border);
        ctx.fill(x0 + PW - 1, y0, x0 + PW, y0 + PH, border);

        // nagłówek z gradientem
        ctx.fillGradient(x0 + 1, y0 + 1, x0 + PW - 1, y0 + 23, withAlpha(BaseConfig.accent, 0x77), withAlpha(BaseConfig.accent, 0x00));
        ctx.fill(x0 + 1, y0 + 22, x0 + PW - 1, y0 + 23, acc);

        // narożniki
        int c = 7;
        ctx.fill(x0 - 1, y0 - 1, x0 + c, y0, acc);
        ctx.fill(x0 - 1, y0 - 1, x0, y0 + c, acc);
        ctx.fill(x0 + PW - c, y0 - 1, x0 + PW + 1, y0, acc);
        ctx.fill(x0 + PW, y0 - 1, x0 + PW + 1, y0 + c, acc);
        ctx.fill(x0 - 1, y0 + PH, x0 + c, y0 + PH + 1, acc);
        ctx.fill(x0 - 1, y0 + PH - c, x0, y0 + PH + 1, acc);
        ctx.fill(x0 + PW - c, y0 + PH, x0 + PW + 1, y0 + PH + 1, acc);
        ctx.fill(x0 + PW, y0 + PH - c, x0 + PW + 1, y0 + PH + 1, acc);

        // tarcza z pikseli
        int dark = 0xFF000000 | ((BaseConfig.accent >> 1) & 0x7F7F7F);
        for (int r = 0; r < SHIELD.length; r++) {
            for (int col = 0; col < SHIELD[r].length(); col++) {
                char ch = SHIELD[r].charAt(col);
                if (ch == '.') continue;
                int color = ch == '#' ? acc : (ch == 'o' ? dark : 0xFFFFFFFF);
                ctx.fill(x0 + 9 + col, y0 + 5 + r, x0 + 10 + col, y0 + 6 + r, color);
            }
        }

        // linia pod zakładkami
        ctx.fill(x0 + 10, y0 + 45, x0 + PW - 10, y0 + 46, 0x55FFFFFF);
        int tw = (PW - 20) / TAB_COUNT;
        ctx.fill(x0 + 10 + tab * tw, y0 + 45, x0 + 10 + tab * tw + tw - 2, y0 + 47, acc);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int x0 = left(), y0 = top(), cy = y0 + 54;
        int acc = accent();

        // tytuł i status
        ctx.drawTextWithShadow(textRenderer, "Zyro Base Alert", x0 + 25, y0 + 7, 0xFFFFFFFF);
        boolean armed = BaseConfig.enabled && BaseConfig.hasBase();
        boolean pulse = (System.currentTimeMillis() / 500) % 2 == 0;
        String status = armed ? "CHRONIONA" : (BaseConfig.hasBase() ? "ALARM WYŁĄCZONY" : "BRAK BAZY");
        int dot = armed ? (pulse ? 0xFF55FF55 : 0xFF2E8B2E) : 0xFFFF5555;
        int sw = textRenderer.getWidth(status);
        ctx.drawTextWithShadow(textRenderer, status, x0 + PW - 10 - sw, y0 + 7, dot);
        ctx.fill(x0 + PW - 10 - sw - 10, y0 + 8, x0 + PW - 10 - sw - 4, y0 + 14, dot);

        // stopka
        ctx.drawTextWithShadow(textRenderer, "K - menu  •  v" + BaseAlertClient.VERSION, x0 + 10, y0 + PH - 12, 0xFF777777);

        switch (tab) {
            case 0 -> renderMain(ctx, x0, cy, acc);
            case 1 -> renderPhone(ctx, x0, cy, acc);
            case 2 -> renderSafe(ctx, x0, cy, acc);
            default -> renderSettings(ctx, x0, cy, acc);
        }
    }

    private void renderMain(DrawContext ctx, int x0, int cy, int acc) {
        // karta statusu
        ctx.fill(x0 + 10, cy, x0 + PW - 10, cy + 54, 0x66000000);
        ctx.fill(x0 + 10, cy, x0 + 12, cy + 54, acc);
        int tx = x0 + 18;

        String base;
        if (BaseConfig.hasBase()) {
            BlockPos a = BaseConfig.pos1, b = BaseConfig.pos2;
            int dx = Math.abs(a.getX() - b.getX()) + 1;
            int dy = Math.abs(a.getY() - b.getY()) + 1;
            int dz = Math.abs(a.getZ() - b.getZ()) + 1;
            base = "§aZaznaczona §7(" + dx + " x " + dy + " x " + dz + " bloków)";
        } else if (BaseAlertClient.step == 1) {
            base = "§eKliknij górny róg bazy";
        } else if (BaseAlertClient.step == 2) {
            base = "§eKliknij dolny róg bazy";
        } else {
            base = "§cNiezaznaczona";
        }
        ctx.drawTextWithShadow(textRenderer, "Baza: " + base, tx, cy + 5, 0xFFFFFFFF);

        String phone = BaseConfig.validTopic() ? "§apołączony §7(temat: " + BaseConfig.topic + ")" : "§cnie ustawiony §7(zakładka Telefon)";
        ctx.drawTextWithShadow(textRenderer, "Telefon: " + phone, tx, cy + 17, 0xFFFFFFFF);

        int safe = BaseConfig.safePlayers.size();
        ctx.drawTextWithShadow(textRenderer, "Bezpieczni gracze: §f" + safe, tx, cy + 29, 0xFFCCCCCC);
        ctx.drawTextWithShadow(textRenderer, "Promień ochrony: §f+" + BaseConfig.radius + " bloków", tx, cy + 41, 0xFFCCCCCC);

        wrapped(ctx, "Jak zaznaczyć bazę: kliknij \"Zaznacz bazę\", potem PRAWYM przyciskiem górny róg, a na końcu dolny róg bazy.",
                x0 + 10, cy + 132, 330, 0xFF999999);
    }

    private void renderPhone(DrawContext ctx, int x0, int cy, int acc) {
        ctx.drawTextWithShadow(textRenderer, "Temat (działa jak hasło):", x0 + 10, cy, 0xFFAAAAAA);

        int y = wrapped(ctx, "1. Zainstaluj aplikację ntfy (Google Play / App Store).", x0 + 10, cy + 88, 184, 0xFFDDDDDD);
        y = wrapped(ctx, "2. Zeskanuj kod QR aparatem telefonu albo wpisz ten sam temat w aplikacji.", x0 + 10, y + 2, 184, 0xFFDDDDDD);
        wrapped(ctx, "3. Kliknij \"Wyślij test\" - telefon powinien zadzwonić.", x0 + 10, y + 2, 184, 0xFFDDDDDD);

        int bx = x0 + PW - 10 - 124, by = cy + 10;
        ctx.fill(bx - 2, by - 2, bx + 126, by + 126, acc);
        drawQr(ctx, bx, by, 124);

        ctx.drawTextWithShadow(textRenderer, "Ostatnia wysyłka:", bx, cy + 134, 0xFFAAAAAA);
        wrapped(ctx, BaseAlertClient.lastResult, bx, cy + 145, 124, 0xFFFFFFFF);
    }

    private void drawQr(DrawContext ctx, int bx, int by, int box) {
        ctx.fill(bx, by, bx + box, by + box, 0xFFFFFFFF);
        if (!BaseConfig.validTopic()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "§0Wpisz lub", bx + box / 2, by + box / 2 - 10, 0xFF000000);
            ctx.drawCenteredTextWithShadow(textRenderer, "§0wylosuj temat", bx + box / 2, by + box / 2 + 2, 0xFF000000);
            return;
        }
        String link = BaseAlertClient.subscribeLink();
        if (!link.equals(qrFor)) {
            try {
                qr = QrCode.encode(link);
            } catch (Exception e) {
                qr = null;
            }
            qrFor = link;
        }
        if (qr == null) return;
        int quiet = 2;
        int mods = qr.size + quiet * 2;
        int scale = Math.max(1, box / mods);
        int total = mods * scale;
        int ox = bx + (box - total) / 2 + quiet * scale;
        int oy = by + (box - total) / 2 + quiet * scale;
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.get(x, y)) {
                    ctx.fill(ox + x * scale, oy + y * scale, ox + (x + 1) * scale, oy + (y + 1) * scale, 0xFF000000);
                }
            }
        }
    }

    private void renderSafe(DrawContext ctx, int x0, int cy, int acc) {
        wrapped(ctx, "Wpisz nick gracza. Gdy wejdzie do Twojej bazy, nie dostaniesz alarmu ani powiadomienia.",
                x0 + 10, cy - 2, 330, 0xFFAAAAAA);

        List<String> list = BaseConfig.safePlayers;
        int pages = Math.max(1, (list.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        int from = safePage * ROWS_PER_PAGE;
        if (list.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "Lista jest pusta.", x0 + 14, cy + 54, 0xFF777777);
        }
        for (int i = from; i < Math.min(list.size(), from + ROWS_PER_PAGE); i++) {
            int ry = cy + 48 + (i - from) * 18;
            ctx.fill(x0 + 10, ry, x0 + 312, ry + 16, 0x55000000);
            ctx.fill(x0 + 10, ry, x0 + 12, ry + 16, acc);
            ctx.drawTextWithShadow(textRenderer, list.get(i), x0 + 18, ry + 4, 0xFFFFFFFF);
        }
        ctx.drawTextWithShadow(textRenderer, (safePage + 1) + "/" + pages, x0 + 66, cy + 164, 0xFFAAAAAA);
        if (!safeMsg.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, safeMsg, x0 + 104, cy + 164, 0xFFFFFFFF);
        }
    }

    private void renderSettings(DrawContext ctx, int x0, int cy, int acc) {
        ctx.drawTextWithShadow(textRenderer, "Kolor interfejsu (kliknij kolor lub wpisz HEX):", x0 + 10, cy, 0xFFAAAAAA);
        for (int i = 0; i < SWATCHES.length; i++) {
            int sx = x0 + 10 + i * 33;
            int sy = cy + 11;
            boolean selected = SWATCHES[i] == BaseConfig.accent;
            if (selected) ctx.fill(sx - 2, sy - 2, sx + 26, sy + 24, 0xFFFFFFFF);
            ctx.fill(sx, sy, sx + 24, sy + 22, 0xFF000000 | SWATCHES[i]);
        }

        ctx.drawTextWithShadow(textRenderer, "Promień wokół bazy:", x0 + 10, cy + 94, 0xFFDDDDDD);
        ctx.drawCenteredTextWithShadow(textRenderer, "+" + BaseConfig.radius + " bl.", x0 + 247, cy + 94, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Odstęp między alarmami:", x0 + 10, cy + 118, 0xFFDDDDDD);
        ctx.drawCenteredTextWithShadow(textRenderer, BaseConfig.cooldownSec + " s", x0 + 247, cy + 118, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == 3 && button == 0) {
            int x0 = left(), sy = top() + 54 + 11;
            for (int i = 0; i < SWATCHES.length; i++) {
                int sx = x0 + 10 + i * 33;
                if (mx >= sx && mx < sx + 24 && my >= sy && my < sy + 22) {
                    BaseConfig.accent = SWATCHES[i];
                    if (hexField != null) hexField.setText(String.format("#%06X", BaseConfig.accent));
                    BaseConfig.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == 2 && safeField != null && safeField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            addSafeFromField();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
