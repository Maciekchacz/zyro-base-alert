package pl.zyro.basealert;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class BaseScreen extends Screen {
    private TextFieldWidget topicField;

    public BaseScreen() {
        super(Text.literal("Zyro Base Alert"));
    }

    private static Text onOff(String label, boolean value) {
        return Text.literal(label + ": " + (value ? "WŁĄCZONE" : "WYŁĄCZONE"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int s = this.height / 2 - 100;

        addDrawableChild(ButtonWidget.builder(Text.literal("Zaznacz bazę"), b -> {
            BaseAlertClient.step = 1;
            BaseConfig.pos1 = null;
            BaseConfig.pos2 = null;
            close();
            BaseAlertClient.msg("§aKliknij PRAWYM przyciskiem GÓRNY róg swojej bazy.");
        }).dimensions(cx - 100, s, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(onOff("Alerty", BaseConfig.enabled), b -> {
            BaseConfig.enabled = !BaseConfig.enabled;
            b.setMessage(onOff("Alerty", BaseConfig.enabled));
            BaseConfig.save();
        }).dimensions(cx - 100, s + 22, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(onOff("Zdjęcie gracza (skin)", BaseConfig.skinPhoto), b -> {
            BaseConfig.skinPhoto = !BaseConfig.skinPhoto;
            b.setMessage(onOff("Zdjęcie gracza (skin)", BaseConfig.skinPhoto));
            BaseConfig.save();
        }).dimensions(cx - 100, s + 44, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(onOff("Zrzut ekranu z gry", BaseConfig.screenshot), b -> {
            BaseConfig.screenshot = !BaseConfig.screenshot;
            b.setMessage(onOff("Zrzut ekranu z gry", BaseConfig.screenshot));
            BaseConfig.save();
        }).dimensions(cx - 100, s + 66, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Usuń zaznaczoną bazę"), b -> {
            BaseConfig.pos1 = null;
            BaseConfig.pos2 = null;
            BaseConfig.save();
            BaseAlertClient.msg("§eBaza usunięta.");
        }).dimensions(cx - 100, s + 88, 200, 20).build());

        topicField = new TextFieldWidget(this.textRenderer, cx - 100, s + 124, 200, 20, Text.literal("temat"));
        topicField.setMaxLength(64);
        topicField.setText(BaseConfig.topic);
        topicField.setPlaceholder(Text.literal("np. zyro-baza-123"));
        addDrawableChild(topicField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Zapisz i testuj powiadomienie"), b -> {
            BaseConfig.topic = topicField.getText().trim();
            BaseConfig.save();
            BaseAlertClient.sendPush("Test: powiadomienia z Minecrafta działają!", null);
            BaseAlertClient.msg("§aWysłano testowe powiadomienie.");
        }).dimensions(cx - 100, s + 148, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int s = height / 2 - 100;
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, s - 14, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Temat ntfy (aplikacja na telefon):", width / 2 - 100, s + 112, 0xAAAAAA);
        String status = BaseConfig.hasBase() ? "§aBaza zaznaczona" : "§cBaza niezaznaczona";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, s + 176, 0xFFFFFF);
    }
}
