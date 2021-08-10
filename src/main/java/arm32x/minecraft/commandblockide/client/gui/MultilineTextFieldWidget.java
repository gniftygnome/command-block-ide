package arm32x.minecraft.commandblockide.client.gui;

import arm32x.minecraft.commandblockide.mixin.client.TextFieldWidgetAccessor;
import arm32x.minecraft.commandblockide.util.OrderedTexts;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.StringUtils;
import static org.lwjgl.glfw.GLFW.*;

@Environment(EnvType.CLIENT)
public class MultilineTextFieldWidget extends TextFieldWidget {
	/**
	 * Allows easy and convenient access to private fields in the superclass.
	 */
	private final TextFieldWidgetAccessor self = (TextFieldWidgetAccessor)this;

	// TODO: Set and enforce maximum scroll offsets.
	private boolean horizontalScrollEnabled;
	private int horizontalScroll = 0;
	private boolean verticalScrollEnabled;
	private int verticalScroll = 0;
	public static final double SCROLL_SENSITIVITY = 15.0;

	private int lineHeight = 12;

	public MultilineTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text, boolean horizontalScrollEnabled, boolean verticalScrollEnabled) {
		super(textRenderer, x, y, width, height, text);
		this.horizontalScrollEnabled = horizontalScrollEnabled;
		this.verticalScrollEnabled = verticalScrollEnabled;
	}

	public MultilineTextFieldWidget(TextRenderer textRenderer, int x, int y, int width, int height, Text text) {
		this(textRenderer, x, y, width, height, text, true, true);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		return switch (keyCode) {
			case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> {
				if (Screen.hasShiftDown()) {
					write("\n");
					yield true;
				} else {
					yield false;
				}
			}
			default -> super.keyPressed(keyCode, scanCode, modifiers);
		};
	}

	@Override
	public void write(String text) {
		int normalizedSelectionStart = Math.min(self.getSelectionStart(), self.getSelectionEnd());
		int normalizedSelectionEnd = Math.max(self.getSelectionStart(), self.getSelectionEnd());
		int remainingLength = self.invokeGetMaxLength() - self.getText().length() - (normalizedSelectionStart - normalizedSelectionEnd);
		int length = text.length();
		if (remainingLength < length) {
			text = text.substring(0, remainingLength);
			length = remainingLength;
		}

		String newText = new StringBuilder(self.getText())
			.replace(normalizedSelectionStart, normalizedSelectionEnd, text)
			.toString();
		if (self.getTextPredicate().test(newText)) {
			this.setText(newText);
			this.setSelectionStart(normalizedSelectionStart + length);
			this.setSelectionEnd(self.getSelectionStart());
			self.invokeOnChanged(this.getText());
		}
	}

	@Override
	public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		if (!isVisible()) {
			return;
		}

		if (self.getDrawsBackground()) {
			int borderColor = this.isFocused() ? 0xFFFFFFFF : 0xFFA0A0A0;
			fill(matrices, this.x - 1, this.y - 1, this.x + this.width + 1, this.y + this.height + 1, borderColor);
			fill(matrices, this.x, this.y, this.x + this.width, this.y + this.height, 0xFF000000);
		}

		Window window = MinecraftClient.getInstance().getWindow();
		double scaleFactor = window.getScaleFactor();
		RenderSystem.enableScissor(
			(int)Math.round(this.x * scaleFactor),
			// OpenGL coordinates start from the bottom left.
			window.getHeight() - (int)Math.round(this.y * scaleFactor + this.height * scaleFactor),
			(int)Math.round(this.width * scaleFactor),
			(int)Math.round(this.height * scaleFactor)
		);

		int textColor = self.isEditable() ? self.getEditableColor() : self.getUneditableColor();
		int x = this.x + (self.getDrawsBackground() ? 4 : 0) - horizontalScroll;
		int y = this.y + (self.getDrawsBackground() ? 3 : 0) - verticalScroll;

		boolean showCursor = isFocused() && self.getFocusedTicks() / 6 % 2 == 0;
		boolean lineCursor = self.getSelectionStart() < self.getText().length() || self.getText().length() >= self.invokeGetMaxLength();

		int cursorLine = (int)self.getText()
			.substring(0, self.getSelectionStart())
			.codePoints()
			.filter(point -> point == '\n')
			.count();

		int cursorX = x;
		int cursorY = y + lineHeight * cursorLine;

		OrderedText text = self.getRenderTextProvider().apply(self.getText(), 0);
		List<OrderedText> lines = OrderedTexts.split('\n', text);
		for (int index = 0; index < lines.size(); index++) {
			OrderedText line = lines.get(index);
			if (index == cursorLine) {
				int indexOfLastNewlineBeforeCursor = self.getText().lastIndexOf('\n', Math.max(self.getSelectionStart() - 1, 0));
				int codePointsBeforeCursor;
				if (indexOfLastNewlineBeforeCursor != -1) {
					codePointsBeforeCursor = self.getText().codePointCount(indexOfLastNewlineBeforeCursor, Math.max(self.getSelectionStart() - 1, 0));
				} else {
					codePointsBeforeCursor = self.getText().codePointCount(0, self.getSelectionStart());
				}
				int endX = self.getTextRenderer().drawWithShadow(matrices, OrderedTexts.limit(codePointsBeforeCursor, line), x, y + lineHeight * index, textColor) - 1;
				self.getTextRenderer().drawWithShadow(matrices, OrderedTexts.skip(codePointsBeforeCursor, line), endX, y + lineHeight * index, textColor);
				cursorX = endX - 1;
			} else {
				self.getTextRenderer().drawWithShadow(matrices, line, x, y + lineHeight * index, textColor);
			}
		}

		if (showCursor) {
			if (lineCursor) {
				fill(matrices, cursorX, cursorY - 1, cursorX + 1, cursorY + 10, 0xFFD0D0D0);
			} else {
				self.getTextRenderer().drawWithShadow(matrices, "_", cursorX + 1, cursorY, textColor);
			}
		}

		// TODO: Draw selection.

		RenderSystem.disableScissor();
	}

	@Override
	public boolean charTyped(char chr, int modifiers) {
		if (isActive()) {
			write(Character.toString(chr));
			return true;
		} else {
			return false;
		}
	}

	public int getLineCount() {
		return (int)self.getText()
			.codePoints()
			.filter(point -> point == '\n')
			.count() + 1;
	}

	protected int getHorizontalScroll() {
		return horizontalScroll;
	}

	protected boolean setHorizontalScroll(int horizontalScroll) {
		int previous = this.horizontalScroll;
		int max = Math.max(0, Arrays.stream(self.getText().split("\n"))
			.mapToInt(self.getTextRenderer()::getWidth)
			.max()
			.orElse(0) + 8 - width);
		this.horizontalScroll = MathHelper.clamp(horizontalScroll, 0, max);
		return this.horizontalScroll != previous;
	}

	protected int getVerticalScroll() {
		return verticalScroll;
	}

	protected boolean setVerticalScroll(int verticalScroll) {
		int previous = this.verticalScroll;
		int max = Math.max(0, getLineCount() * getLineHeight() + 2 - height);
		this.verticalScroll = MathHelper.clamp(verticalScroll, 0, max);
		return this.verticalScroll != previous;
	}

	public boolean isHorizontalScrollEnabled() {
		return horizontalScrollEnabled;
	}

	public void setHorizontalScrollEnabled(boolean enabled) {
		horizontalScrollEnabled = enabled;
		horizontalScroll = 0;
	}

	public boolean isVerticalScrollEnabled() {
		return verticalScrollEnabled;
	}

	public void setVerticalScrollEnabled(boolean enabled) {
		verticalScrollEnabled = enabled;
		verticalScroll = 0;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!this.isVisible()) {
			return false;
		}

		if (self.isFocusUnlocked()) {
			setTextFieldFocused(isMouseOver(mouseX, mouseY));
		}

		if (isFocused() && isMouseOver(mouseX, mouseY) && button == 0) {
			int lineIndex = MathHelper.clamp(MathHelper.floor((mouseY - y - 2) / getLineHeight()), 0, getLineCount() - 1);
			int lineStart = StringUtils.ordinalIndexOf(self.getText(), "\n", lineIndex) + 1;
			int lineEnd = self.getText().indexOf('\n', lineStart);
			if (lineEnd == -1) {
				lineEnd = self.getText().length();
			}
			String line = self.getText().substring(lineStart, lineEnd);

			setCursor(self.getTextRenderer().trimToWidth(line, MathHelper.floor(mouseX) - this.x - (self.getDrawsBackground() ? 4 : 0) + 2).length() + lineStart);
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (this.isMouseOver(mouseX, mouseY)) {
			if (Screen.hasShiftDown()) {
				if (isHorizontalScrollEnabled()) {
					return setHorizontalScroll(getHorizontalScroll() - (int)Math.round(amount * SCROLL_SENSITIVITY));
				}
			} else {
				if (isVerticalScrollEnabled()) {
					return setVerticalScroll(getVerticalScroll() - (int)Math.round(amount * SCROLL_SENSITIVITY));
				}
			}
			return false;
		} else {
			return super.mouseScrolled(mouseX, mouseY, amount);
		}
	}

	public int getLineHeight() {
		return lineHeight;
	}

	public void setLineHeight(int lineHeight) {
		this.lineHeight = lineHeight;
	}

	public void setHeight(int height) {
		this.height = height;
	}
}
