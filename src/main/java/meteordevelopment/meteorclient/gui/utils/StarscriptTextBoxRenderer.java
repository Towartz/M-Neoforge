package meteordevelopment.meteorclient.gui.utils;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.systems.hud.elements.TextHud;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.starscript.compiler.Parser;

public class StarscriptTextBoxRenderer implements WTextBox.Renderer {
   private static final String[] KEYWORDS = new String[]{"null", "true", "false", "and", "or"};
   private static final Color RED = new Color(225, 25, 25);
   private String lastText;
   private final List<StarscriptTextBoxRenderer.Section> sections = new ArrayList<>();

   @Override
   public void render(GuiRenderer renderer, double x, double y, String text, Color color) {
      if (this.lastText == null || !this.lastText.equals(text)) {
         this.generate(renderer.theme, text, color);
      }

      for (StarscriptTextBoxRenderer.Section section : this.sections) {
         renderer.text(section.text, x, y, section.color, false);
         x += renderer.theme.textWidth(section.text);
      }
   }

   @Override
   public List<String> getCompletions(String text, int position) {
      List<String> completions = new ArrayList<>();
      MeteorStarscript.ss.getCompletions(text, position, (completion, function) -> completions.add(function ? completion + "(" : completion));
      completions.sort(String::compareToIgnoreCase);
      return completions;
   }

   private void generate(GuiTheme theme, String text, Color defaultColor) {
      this.lastText = text;
      this.sections.clear();
      Parser.Result result = Parser.parse(text);
      StringBuilder sb = new StringBuilder();
      StringBuilder sb2 = new StringBuilder();
      int depth = 0;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         boolean addChar = true;
         int charDepth = depth;
         if (result.hasErrors()) {
            if (i == result.errors.getFirst().character) {
               this.sections.add(new StarscriptTextBoxRenderer.Section(sb.toString(), depth > 0 ? theme.starscriptTextColor() : defaultColor));
               sb.setLength(0);
            } else if (i > result.errors.getFirst().character) {
               sb.append(c);
               continue;
            }
         }

         StarscriptTextBoxRenderer.Section section = null;
         switch (c) {
            case '#':
               while (i + 1 < text.length()) {
                  char ch = text.charAt(i + 1);
                  if (!this.isDigit(ch)) {
                     break;
                  }

                  sb2.append(ch);
                  i++;
               }

               if (!sb2.isEmpty()) {
                  String str = sb2.toString();
                  section = new StarscriptTextBoxRenderer.Section("#" + str, TextHud.getSectionColor(Integer.parseInt(str)));
                  sb2.setLength(0);
               }
               break;
            case '{':
            case '}':
               if (c == '{') {
                  depth++;
               } else {
                  depth--;
               }

               section = new StarscriptTextBoxRenderer.Section(Character.toString(c), theme.starscriptBraceColor());
         }

         if (section == null && depth > 0) {
            if (c == '.') {
               this.sections.add(new StarscriptTextBoxRenderer.Section(sb.toString(), theme.starscriptAccessedObjectColor()));
               this.sections.add(new StarscriptTextBoxRenderer.Section(".", theme.starscriptDotColor()));
               sb.setLength(0);
               addChar = false;
            } else {
               switch (c) {
                  case '!':
                  case '<':
                  case '=':
                  case '>':
                     boolean equals = i + 1 < text.length() && text.charAt(i + 1) == '=';
                     if (equals) {
                        i++;
                     }

                     section = new StarscriptTextBoxRenderer.Section(equals ? c + "=" : Character.toString(c), theme.starscriptOperatorColor());
                     break;
                  case '"':
                  case '\'':
                     sb2.append(c);

                     while (i + 1 < text.length()) {
                        char ch = text.charAt(i + 1);
                        if (ch == '"' || ch == '\'') {
                           sb2.append(ch);
                           i++;
                           break;
                        }

                        sb2.append(ch);
                        i++;
                     }

                     section = new StarscriptTextBoxRenderer.Section(sb2.toString(), theme.starscriptStringColor());
                     sb2.setLength(0);
                  case '#':
                  case '$':
                  case '&':
                  case '.':
                  case '0':
                  case '1':
                  case '2':
                  case '3':
                  case '4':
                  case '5':
                  case '6':
                  case '7':
                  case '8':
                  case '9':
                  case ';':
                  case '@':
                  case 'A':
                  case 'B':
                  case 'C':
                  case 'D':
                  case 'E':
                  case 'F':
                  case 'G':
                  case 'H':
                  case 'I':
                  case 'J':
                  case 'K':
                  case 'L':
                  case 'M':
                  case 'N':
                  case 'O':
                  case 'P':
                  case 'Q':
                  case 'R':
                  case 'S':
                  case 'T':
                  case 'U':
                  case 'V':
                  case 'W':
                  case 'X':
                  case 'Y':
                  case 'Z':
                  case '[':
                  case '\\':
                  case ']':
                  default:
                     break;
                  case '%':
                  case '*':
                  case '+':
                  case '-':
                  case '/':
                  case ':':
                  case '?':
                  case '^':
                     if (c != '-' || i + 1 >= text.length() || !this.isDigit(text.charAt(i + 1))) {
                        section = new StarscriptTextBoxRenderer.Section(Character.toString(c), theme.starscriptOperatorColor());
                     }
                     break;
                  case '(':
                  case ')':
                     section = new StarscriptTextBoxRenderer.Section(Character.toString(c), theme.starscriptParenthesisColor());
                     break;
                  case ',':
                     section = new StarscriptTextBoxRenderer.Section(",", theme.starscriptCommaColor());
               }

               if (section == null) {
                  if (this.isDigit(c) || c == '-' && i + 1 < text.length() && this.isDigit(text.charAt(i + 1))) {
                     sb2.append(c);

                     while (i + 1 < text.length()) {
                        char ch = text.charAt(i + 1);
                        if (!this.isDigit(ch)) {
                           break;
                        }

                        sb2.append(ch);
                        i++;
                     }

                     if (i + 1 < text.length() && text.charAt(i + 1) == '.' && i + 2 < text.length() && this.isDigit(text.charAt(i + 2))) {
                        sb2.append('.');
                        i++;

                        while (i + 1 < text.length()) {
                           char ch = text.charAt(i + 1);
                           if (!this.isDigit(ch)) {
                              break;
                           }

                           sb2.append(ch);
                           i++;
                        }
                     }

                     section = new StarscriptTextBoxRenderer.Section(sb2.toString(), theme.starscriptNumberColor());
                     sb2.setLength(0);
                  } else {
                     for (String keyword : KEYWORDS) {
                        if (this.isKeyword(text, i, keyword)) {
                           section = new StarscriptTextBoxRenderer.Section(keyword, theme.starscriptKeywordColor());
                           i += keyword.length() - 1;
                           break;
                        }
                     }
                  }
               }
            }
         }

         if (section != null) {
            if (!sb.isEmpty()) {
               this.sections.add(new StarscriptTextBoxRenderer.Section(sb.toString(), charDepth > 0 ? theme.starscriptTextColor() : defaultColor));
               sb.setLength(0);
            }

            this.sections.add(section);
         } else if (addChar) {
            sb.append(c);
         }
      }

      if (!sb.isEmpty()) {
         this.sections.add(new StarscriptTextBoxRenderer.Section(sb.toString(), result.hasErrors() ? RED : defaultColor));
      }
   }

   private boolean isKeyword(String text, int i, String keyword) {
      if (i > 0) {
         char c = text.charAt(i - 1);
         if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_') {
            return false;
         }
      }

      for (int j = 0; j < keyword.length(); j++) {
         if (i + j >= text.length() || text.charAt(i + j) != keyword.charAt(j)) {
            return false;
         }
      }

      return true;
   }

   private boolean isDigit(char c) {
      return c >= '0' && c <= '9';
   }

   private static record Section(String text, Color color) {
   }
}
