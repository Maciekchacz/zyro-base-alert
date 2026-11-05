package pl.zyro.basealert;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class BaseScreen extends Screen {
    private TextFieldWidget topicField;
    private ButtonWidget toggleButton;

    public BaseScreen() {
        super(Text.literal("Zyro Base Alert"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = this.height / 2 - 60;

        addDrawableChild(ButtonWidget.builder(Text.literal("Zaznacz bazę"), b -> {
            BaseAlertClient.step = 1;
            BaseConfig.pos1 = null;
            BaseConfig.pos2 = null;
            close();
            BaseAlertClient.msg("§aKliknij PRAWYM przyciskiem GÓRNY róg swojej bazy.");
        }).dimensions(cx - 100, y, 200, 20).build());

        toggleButton = addDrawableChild(ButtonWidget.builder(toggleText(), b -> {
            BaseConfig.enabled = !BaseConfig.enabled;
            b.setMessage(toggleText());
            BaseConfig.save();
        }).dimensions(cx - 100, y + 28, 200, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Usuń zaznaczoną bazę"), b -> {
            BaseConfig.pos1 = null;
            BaseConfig.pos2 = null;
            BaseConfig.save();
            BaseAlertClient.msg("§eBaza usunięta.");
        }).dimensions(cx - 100, y + 56, 200, 20).build());

        topicField = new TextFieldWidget(this.textRenderer, cx - 100, y + 100, 200, 20, Text.literal("temat"));
        topicField.setMaxLength(64);
        topicField.setText(BaseConfig.topic);
        topicField.setPlaceholder(Text.literal("np. zyro-baza-123"));
        addDrawableChild(topicField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Zapisz i testuj powiadomienie"), b -> {
            BaseConfig.topic = topicField.getText().trim();
            BaseConfig.save();
            BaseAlertClient.sendPush("Test: powiadomienia z Minecrafta działają!");
            BaseAlertClient.msg("§aWysłano testowe powiadomienie.");
        }).dimensions(cx - 100, y + 126, 200, 20).build());
    }

    private Text toggleText() {
        return Text.literal("Alerty: " + (BaseConfig.enabled ? "WŁĄCZONE" : "WYŁĄCZONE"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 85, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Temat ntfy (aplikacja na telefon):", width / 2 - 100, height / 2 + 30, 0xAAAAAA);
        String status = BaseConfig.hasBase() ? "§aBaza zaznaczona" : "§cBaza niezaznaczona";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status), width / 2, height / 2 + 90, 0xFFFFFF);
    }
}
