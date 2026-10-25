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
    private static final int PW = 350, PH = 246;
    private static final int TAB_COUNT = 5;
    private static final int T_MAIN = 0, T_PHONE = 1, T_RANGE = 2, T_SAFE = 3, T_SET = 4;
    private static final String[] TABS = {"Główna", "Telefon", "Zasięg", "Bezpieczni", "Ustawienia"};
    private static final String[] STYLES = {"Ciemny", "Bardzo ciemny", "Szkło"};
    private static final int[] PANEL_BG = {0xE6101418, 0xF4000000, 0x9A101418};
    private static final int[] SWATCHES = {
            0x4DA6FF, 0x55FF55, 0xFF5555, 0xFFAA00, 0xFFD43B,
            0xAA66FF, 0xFF66CC, 0x55FFFF, 0xFFFFFF, 0x9AA0A6};
    private static final String[] RANGE_NAMES = {"Góra", "Dół", "Lewo", "Prawo", "Przód", "Tył"};
    private static final int[][] RANGE_ORDER = {{0, 1}, {2, 3}, {4, 5}};
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
    private static int lastAll = 10;

    private TextFieldWidget topicField, nameField, ipField, safeField, hexField;
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
            case T_MAIN -> buildMain(x0, cy);
            case T_PHONE -> buildPhone(x0, cy);
            case T_RANGE -> buildRange(x0, cy);
            case T_SAFE -> buildSafe(x0, cy);
            default -> buildSettings(x0, cy);
        }
    }

    private void buildMain(int x0, int cy) {
        addDrawableChild(ButtonWidget.builder(Text.literal("Zaznacz bazę"), b -> {
            BaseConfig.Profile pr = BaseConfig.profile();
            BaseAlertClient.step = 1;
            pr.pos1 = null;
            pr.pos2 = null;
            close();
            BaseAlertClient.msg("§aKliknij PRAWYM przyciskiem GÓRNY róg swojej bazy.");
        }).dimensions(x0 + 10, cy + 72, 162, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Szybka baza (±16 bloków)"), b -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            BaseConfig.Profile pr = BaseConfig.profile();
            BlockPos c = mc.player.getBlockPos();
            pr.pos1 = c.add(-16, 16, -16);
            pr.pos2 = c.add(16, -4, 16);
            BaseConfig.save();
            BaseAlertClient.msg("§aBaza ustawiona wokół Ciebie (±16 bloków).");
        }).dimensions(x0 + 178, cy + 72, 162, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Usuń bazę"), b -> {
            BaseConfig.Profile pr = BaseConfig.profile();
            pr.pos1 = null;
            pr.pos2 = null;
            BaseConfig.save();
            BaseAlertClient.msg("§eBaza usunięta (tylko na tym serwerze).");
        }).dimensions(x0 + 10, cy + 94, 162, 20).build());

        addToggle(x0 + 178, cy + 94, 162, "Alarm", () -> BaseConfig.enabled, v -> BaseConfig.enabled = v);
        addToggle(x0 + 10, cy + 116, 162, "Zdjęcie gracza", () -> BaseConfig.skinPhoto, v -> BaseConfig.skinPhoto = v);
        addToggle(x0 + 178, cy + 116, 162, "Zrzut ekranu", () -> BaseConfig.screenshot, v -> BaseConfig.screenshot = v);
    }

    private void buildPhone(int x0, int cy) {
        BaseConfig.Profile pr = BaseConfig.profile();

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
        }).dimensions(x0 + 10, cy + 34, 91, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Wyślij test"), b -> {
            BaseConfig.save();
            BaseAlertClient.sendPush("✔ Test: telefon jest połączony z Minecraftem!", null);
        }).dimensions(x0 + 103, cy + 34, 91, 20).build());

        addDrawableChild(ButtonWidget.builder(qrText(), b -> {
            BaseConfig.qrApp = !BaseConfig.qrApp;
            b.setMessage(qrText());
            BaseConfig.save();
        }).dimensions(x0 + 10, cy + 57, 184, 20).build());

        // nazwa i IP serwera (osobne dla każdego serwera)
        nameField = new TextFieldWidget(textRenderer, x0 + 10, cy + 94, 184, 18, Text.literal("nazwa"));
        nameField.setMaxLength(32);
        nameField.setText(pr.name);
        nameField.setPlaceholder(Text.literal("np. Survival"));
        nameField.setChangedListener(s -> pr.name = s.trim());
        addDrawableChild(nameField);

        ipField = new TextFieldWidget(textRenderer, x0 + 10, cy + 124, 128, 18, Text.literal("ip"));
        ipField.setMaxLength(64);
        ipField.setTextPredicate(s -> s.matches("[A-Za-z0-9._:-]*"));
        ipField.setText(pr.ip);
        ipField.setPlaceholder(Text.literal("np. mc.serwer.pl"));
        ipField.setChangedListener(s -> pr.ip = s.trim());
        addDrawableChild(ipField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Wykryj"), b -> {
            String key = BaseConfig.currentKey();
            if (!key.equals("offline") && !key.equals("singleplayer")) {
                pr.ip = key;
                ipField.setText(key);
                BaseConfig.save();
            }
        }).dimensions(x0 + 142, cy + 123, 52, 20).build());
    }

    private static Text qrText() {
        return Text.literal("Kod QR: " + (BaseConfig.qrApp ? "aplikacja ntfy" : "przeglądarka"));
    }

    private void buildRange(int x0, int cy) {
        BaseConfig.Profile pr = BaseConfig.profile();
        for (int r = 0; r < RANGE_ORDER.length; r++) {
            for (int c = 0; c < 2; c++) {
                addRangeCell(x0 + 10 + c * 170, cy + 16 + r * 26, pr, RANGE_ORDER[r][c]);
            }
        }

        TextFieldWidget all = new TextFieldWidget(textRenderer, x0 + 116, cy + 99, 50, 18, Text.literal("wszystkie"));
        all.setMaxLength(3);
        all.setTextPredicate(s -> s.matches("[0-9]{0,3}"));
        all.setText(String.valueOf(lastAll));
        addDrawableChild(all);

        addDrawableChild(ButtonWidget.builder(Text.literal("Ustaw"), b -> {
            String t = all.getText();
            if (t.isEmpty()) return;
            int v = Math.min(BaseConfig.MAX_RANGE, Integer.parseInt(t));
            lastAll = v;
            java.util.Arrays.fill(pr.range, v);
            BaseConfig.save();
            rebuild();
        }).dimensions(x0 + 170, cy + 98, 60, 20).build());
    }

    private void addRangeCell(int x, int y, BaseConfig.Profile pr, int idx) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x + 66, y + 1, 50, 18, Text.literal(RANGE_NAMES[idx]));
        f.setMaxLength(3);
        f.setTextPredicate(s -> s.matches("[0-9]{0,3}"));
        f.setText(String.valueOf(pr.range[idx]));
        f.setChangedListener(s -> {
            if (s.isEmpty()) return;
            int v = Integer.parseInt(s);
            if (v > BaseConfig.MAX_RANGE) {
                f.setText(String.valueOf(BaseConfig.MAX_RANGE));
                return;
            }
            pr.range[idx] = v;
        });
        addDrawableChild(f);

        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> {
            pr.range[idx] = Math.max(0, pr.range[idx] - 10);
            f.setText(String.valueOf(pr.range[idx]));
            BaseConfig.save();
        }).dimensions(x + 42, y, 22, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            pr.range[idx] = Math.min(BaseConfig.MAX_RANGE, pr.range[idx] + 10);
            f.setText(String.valueOf(pr.range[idx]));
            BaseConfig.save();
        }).dimensions(x + 118, y, 22, 20).build());
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

        addStepper(x0, cy + 88, 10, 600, 10, () -> BaseConfig.cooldownSec, v -> BaseConfig.cooldownSec = v);

        addDrawableChild(ButtonWidget.builder(Text.literal("Przywróć domyślny wygląd"), b -> {
            BaseConfig.accent = 0x4DA6FF;
            BaseConfig.panelStyle = 0;
            BaseConfig.save();
            rebuild();
        }).dimensions(x0 + 10, cy + 116, 330, 20).build());
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
        boolean hasBase = BaseConfig.hasBase();
        boolean armed = BaseConfig.enabled && hasBase;
        boolean pulse = (System.currentTimeMillis() / 500) % 2 == 0;
        String status = armed ? "CHRONIONA" : (hasBase ? "ALARM WYŁĄCZONY" : "BRAK BAZY");
        int dot = armed ? (pulse ? 0xFF55FF55 : 0xFF2E8B2E) : 0xFFFF5555;
        int sw = textRenderer.getWidth(status);
        ctx.drawTextWithShadow(textRenderer, status, x0 + PW - 10 - sw, y0 + 7, dot);
        ctx.fill(x0 + PW - 10 - sw - 10, y0 + 8, x0 + PW - 10 - sw - 4, y0 + 14, dot);

        // stopka
        ctx.drawTextWithShadow(textRenderer, "K - menu  •  v" + BaseAlertClient.VERSION, x0 + 10, y0 + PH - 12, 0xFF777777);

        switch (tab) {
            case T_MAIN -> renderMain(ctx, x0, cy, acc);
            case T_PHONE -> renderPhone(ctx, x0, cy, acc);
            case T_RANGE -> renderRange(ctx, x0, cy);
            case T_SAFE -> renderSafe(ctx, x0, cy, acc);
            default -> renderSettings(ctx, x0, cy);
        }
    }

    private void renderMain(DrawContext ctx, int x0, int cy, int acc) {
        BaseConfig.Profile pr = BaseConfig.profile();

        // karta statusu
        ctx.fill(x0 + 10, cy, x0 + PW - 10, cy + 66, 0x66000000);
        ctx.fill(x0 + 10, cy, x0 + 12, cy + 66, acc);
        int tx = x0 + 18;

        String ip = pr.ip.isBlank() ? BaseConfig.currentKey() : pr.ip;
        String srv = pr.name.isBlank() ? ip : pr.name;
        ctx.drawTextWithShadow(textRenderer, "Serwer: §f" + srv + " §7(" + ip + ")", tx, cy + 5, 0xFFFFFFFF);

        String base;
        if (pr.hasBase()) {
            BlockPos a = pr.pos1, b = pr.pos2;
            int dx = Math.abs(a.getX() - b.getX()) + 1;
            int dy = Math.abs(a.getY() - b.getY()) + 1;
            int dz = Math.abs(a.getZ() - b.getZ()) + 1;
            base = "§aZaznaczona §7(" + dx + " x " + dy + " x " + dz + " bloków)";
        } else if (BaseAlertClient.step == 1) {
            base = "§eKliknij górny róg bazy";
        } else if (BaseAlertClient.step == 2) {
            base = "§eKliknij dolny róg bazy";
        } else {
            base = "§cNiezaznaczona na tym serwerze";
        }
        ctx.drawTextWithShadow(textRenderer, "Baza: " + base, tx, cy + 17, 0xFFFFFFFF);

        String phone = BaseConfig.validTopic() ? "§apołączony §7(temat: " + BaseConfig.topic + ")" : "§cnie ustawiony §7(zakładka Telefon)";
        ctx.drawTextWithShadow(textRenderer, "Telefon: " + phone, tx, cy + 29, 0xFFFFFFFF);

        ctx.drawTextWithShadow(textRenderer, "Bezpieczni gracze: §f" + BaseConfig.safePlayers.size(), tx, cy + 41, 0xFFCCCCCC);

        StringBuilder rg = new StringBuilder();
        for (int i = 0; i < 6; i++) rg.append(i == 0 ? "" : "/").append(pr.range[i]);
        ctx.drawTextWithShadow(textRenderer, "Zasięg G/D/L/P/Prz/T: §f" + rg, tx, cy + 53, 0xFFCCCCCC);

        wrapped(ctx, "Baza i zasięg są osobne dla każdego serwera. Jak zaznaczyć: kliknij \"Zaznacz bazę\", potem PRAWYM przyciskiem górny i dolny róg.",
                x0 + 10, cy + 142, 330, 0xFF999999);
    }

    private void renderPhone(DrawContext ctx, int x0, int cy, int acc) {
        ctx.drawTextWithShadow(textRenderer, "Temat (działa jak hasło):", x0 + 10, cy, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Nazwa tego serwera:", x0 + 10, cy + 83, 0xFFAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "IP serwera (idzie w alarmie):", x0 + 10, cy + 113, 0xFFAAAAAA);
        wrapped(ctx, "Zainstaluj ntfy na telefonie, zeskanuj kod QR i kliknij \"Wyślij test\".", x0 + 10, cy + 148, 200, 0xFF999999);

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

    private void renderRange(DrawContext ctx, int x0, int cy) {
        ctx.drawTextWithShadow(textRenderer, "Odległość od krawędzi bazy, z której wykrywam gracza (0-" + BaseConfig.MAX_RANGE + " bloków):",
                x0 + 10, cy + 2, 0xFFAAAAAA);
        for (int r = 0; r < RANGE_ORDER.length; r++) {
            for (int c = 0; c < 2; c++) {
                int x = x0 + 10 + c * 170, y = cy + 16 + r * 26;
                ctx.drawTextWithShadow(textRenderer, RANGE_NAMES[RANGE_ORDER[r][c]], x, y + 6, 0xFFFFFFFF);
            }
        }
        ctx.drawTextWithShadow(textRenderer, "Wszystkie strony:", x0 + 10, cy + 105, 0xFFDDDDDD);
        wrapped(ctx, "Lewo = zachód (-X), Prawo = wschód (+X), Przód = północ (-Z), Tył = południe (+Z). "
                        + "Uwaga: gra pokazuje innych graczy tylko w zasięgu renderowania serwera (zwykle 64-128 bloków).",
                x0 + 10, cy + 128, 330, 0xFF999999);
    }

    private void renderSafe(DrawContext ctx, int x0, int cy, int acc) {
        wrapped(ctx, "Wpisz nick gracza. Gdy wejdzie do Twojej bazy, nie dostaniesz alarmu ani powiadomienia (na wszystkich serwerach).",
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

    private void renderSettings(DrawContext ctx, int x0, int cy) {
        ctx.drawTextWithShadow(textRenderer, "Kolor interfejsu (kliknij kolor lub wpisz HEX):", x0 + 10, cy, 0xFFAAAAAA);
        for (int i = 0; i < SWATCHES.length; i++) {
            int sx = x0 + 10 + i * 33;
            int sy = cy + 11;
            boolean selected = SWATCHES[i] == BaseConfig.accent;
            if (selected) ctx.fill(sx - 2, sy - 2, sx + 26, sy + 24, 0xFFFFFFFF);
            ctx.fill(sx, sy, sx + 24, sy + 22, 0xFF000000 | SWATCHES[i]);
        }

        ctx.drawTextWithShadow(textRenderer, "Odstęp między alarmami:", x0 + 10, cy + 94, 0xFFDDDDDD);
        ctx.drawCenteredTextWithShadow(textRenderer, BaseConfig.cooldownSec + " s", x0 + 247, cy + 94, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == T_SET && button == 0) {
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
        if (tab == T_SAFE && safeField != null && safeField.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            addSafeFromField();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
