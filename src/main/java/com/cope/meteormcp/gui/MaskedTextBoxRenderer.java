package com.cope.meteormcp.gui;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox.Renderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class MaskedTextBoxRenderer implements Renderer {
   public void render(GuiRenderer renderer, double x, double y, String text, Color color) {
      if (text != null && !text.isEmpty()) {
         renderer.text("*".repeat(text.length()), x, y, color, false);
      }
   }
}
