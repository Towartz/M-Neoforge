package meteordevelopment.starscript.compiler;

public class Lexer {
   public Token token;
   public String lexeme;
   public int line = 1;
   public int character = -1;
   public char ch;
   private final String source;
   public int start;
   public int current;
   private int expressionDepth;

   public Lexer(String source) {
      this.source = source;
   }

   public void next() {
      this.start = this.current;
      if (this.isAtEnd()) {
         this.createToken(Token.EOF);
      } else {
         if (this.expressionDepth > 0) {
            this.skipWhitespace();
            if (this.isAtEnd()) {
               this.createToken(Token.EOF);
               return;
            }

            char c = this.advance();
            if (!this.isDigit(c) && (c != '-' || !this.isDigit(this.peek()))) {
               if (this.isAlpha(c)) {
                  this.identifier();
               } else {
                  switch (c) {
                     case '!':
                        this.createToken(this.match('=') ? Token.BangEqual : Token.Bang);
                        break;
                     case '"':
                     case '\'':
                        this.string();
                        break;
                     case '#':
                        while (this.isDigit(this.peek())) {
                           this.advance();
                        }

                        this.createToken(Token.Section, this.source.substring(this.start + 1, this.current));
                        break;
                     case '$':
                     case '&':
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
                     case '_':
                     case '`':
                     case 'a':
                     case 'b':
                     case 'c':
                     case 'd':
                     case 'e':
                     case 'f':
                     case 'g':
                     case 'h':
                     case 'i':
                     case 'j':
                     case 'k':
                     case 'l':
                     case 'm':
                     case 'n':
                     case 'o':
                     case 'p':
                     case 'q':
                     case 'r':
                     case 's':
                     case 't':
                     case 'u':
                     case 'v':
                     case 'w':
                     case 'x':
                     case 'y':
                     case 'z':
                     case '|':
                     default:
                        this.unexpected();
                        break;
                     case '%':
                        this.createToken(Token.Percentage);
                        break;
                     case '(':
                        this.createToken(Token.LeftParen);
                        break;
                     case ')':
                        this.createToken(Token.RightParen);
                        break;
                     case '*':
                        this.createToken(Token.Star);
                        break;
                     case '+':
                        this.createToken(Token.Plus);
                        break;
                     case ',':
                        this.createToken(Token.Comma);
                        break;
                     case '-':
                        this.createToken(Token.Minus);
                        break;
                     case '.':
                        this.createToken(Token.Dot);
                        break;
                     case '/':
                        this.createToken(Token.Slash);
                        break;
                     case ':':
                        this.createToken(Token.Colon);
                        break;
                     case '<':
                        this.createToken(this.match('=') ? Token.LessEqual : Token.Less);
                        break;
                     case '=':
                        if (this.match('=')) {
                           this.createToken(Token.EqualEqual);
                        } else {
                           this.unexpected();
                        }
                        break;
                     case '>':
                        this.createToken(this.match('=') ? Token.GreaterEqual : Token.Greater);
                        break;
                     case '?':
                        this.createToken(Token.QuestionMark);
                        break;
                     case '^':
                        this.createToken(Token.UpArrow);
                        break;
                     case '{':
                        this.expressionDepth++;
                        this.createToken(Token.LeftBrace);
                        break;
                     case '}':
                        this.expressionDepth--;
                        this.createToken(Token.RightBrace);
                  }
               }
            } else {
               this.number();
            }
         } else {
            char c = this.advance();
            if (c == '\n') {
               this.line++;
            }

            if (c == '{') {
               this.expressionDepth++;
               this.createToken(Token.LeftBrace);
            } else if (c == '#') {
               while (this.isDigit(this.peek())) {
                  this.advance();
               }

               this.createToken(Token.Section, this.source.substring(this.start + 1, this.current));
            } else {
               for (; !this.isAtEnd() && this.peek() != '{' && this.peek() != '#'; this.advance()) {
                  if (this.peek() == '\n') {
                     this.line++;
                  }
               }

               this.createToken(Token.String);
            }
         }
      }
   }

   private void string() {
      while (!this.isAtEnd() && this.peek() != '"' && this.peek() != '\'') {
         if (this.peek() == '\n') {
            this.line++;
         }

         this.advance();
      }

      if (this.isAtEnd()) {
         this.createToken(Token.Error, "Unterminated expression.");
      } else {
         this.advance();
         this.createToken(Token.String, this.source.substring(this.start + 1, this.current - 1));
      }
   }

   private void number() {
      while (this.isDigit(this.peek())) {
         this.advance();
      }

      if (this.peek() == '.' && this.isDigit(this.peekNext())) {
         this.advance();

         while (this.isDigit(this.peek())) {
            this.advance();
         }
      }

      this.createToken(Token.Number);
   }

   private void identifier() {
      while (!this.isAtEnd() && this.isAlphaNumeric(this.peek())) {
         this.advance();
      }

      this.createToken(Token.Identifier);
      String var1 = this.lexeme;
      switch (var1) {
         case "null":
            this.token = Token.Null;
            break;
         case "true":
            this.token = Token.True;
            break;
         case "false":
            this.token = Token.False;
            break;
         case "and":
            this.token = Token.And;
            break;
         case "or":
            this.token = Token.Or;
      }
   }

   private void skipWhitespace() {
      while (!this.isAtEnd()) {
         char c = this.peek();
         switch (c) {
            case '\t':
            case '\r':
            case ' ':
               this.advance();
               break;
            case '\n':
               this.line++;
               this.advance();
               break;
            default:
               this.start = this.current;
               return;
         }
      }
   }

   private void unexpected() {
      this.createToken(Token.Error, "Unexpected character.");
   }

   private void createToken(Token token, String lexeme) {
      this.token = token;
      this.lexeme = lexeme;
   }

   private void createToken(Token token) {
      this.createToken(token, this.source.substring(this.start, this.current));
   }

   private boolean match(char expected) {
      if (this.isAtEnd()) {
         return false;
      } else if (this.source.charAt(this.current) != expected) {
         return false;
      } else {
         this.advance();
         return true;
      }
   }

   private char advance() {
      this.character++;
      return this.ch = this.source.charAt(this.current++);
   }

   private char peek() {
      return this.isAtEnd() ? '\u0000' : this.source.charAt(this.current);
   }

   private char peekNext() {
      return this.current + 1 >= this.source.length() ? '\u0000' : this.source.charAt(this.current + 1);
   }

   private boolean isAtEnd() {
      return this.current >= this.source.length();
   }

   private boolean isDigit(char c) {
      return c >= '0' && c <= '9';
   }

   private boolean isAlpha(char c) {
      return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
   }

   private boolean isAlphaNumeric(char c) {
      return this.isAlpha(c) || this.isDigit(c);
   }
}
