package meteordevelopment.starscript.compiler;

import java.util.List;
import java.util.function.Consumer;

public abstract class Expr {
   public final int start;
   public final int end;

   public Expr(int start, int end) {
      this.start = start;
      this.end = end;
   }

   public abstract void accept(Expr.Visitor var1);

   public java.lang.String getSource(java.lang.String source) {
      return source.substring(this.start, this.end);
   }

   public void forEach(Consumer<Expr> consumer) {
   }

   public static class Binary extends Expr {
      public final Expr left;
      public final Token op;
      public final Expr right;

      public Binary(int start, int end, Expr left, Token op, Expr right) {
         super(start, end);
         this.left = left;
         this.op = op;
         this.right = right;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitBinary(this);
      }

      @Override
      public void forEach(Consumer<Expr> consumer) {
         consumer.accept(this.left);
         consumer.accept(this.right);
      }
   }

   public static class Block extends Expr {
      public final Expr expr;

      public Block(int start, int end, Expr expr) {
         super(start, end);
         this.expr = expr;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitBlock(this);
      }

      @Override
      public void forEach(Consumer<Expr> consumer) {
         if (this.expr != null) {
            consumer.accept(this.expr);
         }
      }
   }

   public static class Bool extends Expr {
      public final boolean bool;

      public Bool(int start, int end, boolean bool) {
         super(start, end);
         this.bool = bool;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitBool(this);
      }
   }

   public static class Call extends Expr {
      public final Expr callee;
      public final List<Expr> args;

      public Call(int start, int end, Expr callee, List<Expr> args) {
         super(start, end);
         this.callee = callee;
         this.args = args;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitCall(this);
      }

      @Override
      public void forEach(Consumer<Expr> consumer) {
         consumer.accept(this.callee);

         for (Expr arg : this.args) {
            consumer.accept(arg);
         }
      }
   }

   public static class Conditional extends Expr {
      public final Expr condition;
      public final Expr trueExpr;
      public final Expr falseExpr;

      public Conditional(int start, int end, Expr condition, Expr trueExpr, Expr falseExpr) {
         super(start, end);
         this.condition = condition;
         this.trueExpr = trueExpr;
         this.falseExpr = falseExpr;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitConditional(this);
      }

      @Override
      public void forEach(Consumer<Expr> consumer) {
         consumer.accept(this.condition);
         consumer.accept(this.trueExpr);
         consumer.accept(this.falseExpr);
      }
   }

   public static class Get extends Expr {
      public final Expr object;
      public final java.lang.String name;

      public Get(int start, int end, Expr object, java.lang.String name) {
         super(start, end);
         this.object = object;
         this.name = name;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitGet(this);
      }

      @Override
      public void forEach(Consumer<Expr> consumer) {
         consumer.accept(this.object);
      }
   }

   public static class Group extends Expr {
      public final Expr expr;

      public Group(int start, int end, Expr expr) {
         super(start, end);
         this.expr = expr;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitGroup(this);
      }

      @Override
      public void forEach(Consumer<Expr> consumer) {
         consumer.accept(this.expr);
      }
   }

   public static class Logical extends Expr {
      public final Expr left;
      public final Token op;
      public final Expr right;

      public Logical(int start, int end, Expr left, Token op, Expr right) {
         super(start, end);
         this.left = left;
         this.op = op;
         this.right = right;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitLogical(this);
      }

      @Override
      public void forEach(Consumer<Expr> consumer) {
         consumer.accept(this.left);
         consumer.accept(this.right);
      }
   }

   public static class Null extends Expr {
      public Null(int start, int end) {
         super(start, end);
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitNull(this);
      }
   }

   public static class Number extends Expr {
      public final double number;

      public Number(int start, int end, double number) {
         super(start, end);
         this.number = number;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitNumber(this);
      }
   }

   public static class Section extends Expr {
      public final int index;
      public final Expr expr;

      public Section(int start, int end, int index, Expr expr) {
         super(start, end);
         this.index = index;
         this.expr = expr;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitSection(this);
      }

      @Override
      public void forEach(Consumer<Expr> consumer) {
         consumer.accept(this.expr);
      }
   }

   public static class String extends Expr {
      public final java.lang.String string;

      public String(int start, int end, java.lang.String string) {
         super(start, end);
         this.string = string;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitString(this);
      }
   }

   public static class Unary extends Expr {
      public final Token op;
      public final Expr right;

      public Unary(int start, int end, Token op, Expr right) {
         super(start, end);
         this.op = op;
         this.right = right;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitUnary(this);
      }

      @Override
      public void forEach(Consumer<Expr> consumer) {
         consumer.accept(this.right);
      }
   }

   public static class Variable extends Expr {
      public final java.lang.String name;

      public Variable(int start, int end, java.lang.String name) {
         super(start, end);
         this.name = name;
      }

      @Override
      public void accept(Expr.Visitor visitor) {
         visitor.visitVariable(this);
      }
   }

   public interface Visitor {
      void visitNull(Expr.Null var1);

      void visitString(Expr.String var1);

      void visitNumber(Expr.Number var1);

      void visitBool(Expr.Bool var1);

      void visitBlock(Expr.Block var1);

      void visitGroup(Expr.Group var1);

      void visitBinary(Expr.Binary var1);

      void visitUnary(Expr.Unary var1);

      void visitVariable(Expr.Variable var1);

      void visitGet(Expr.Get var1);

      void visitCall(Expr.Call var1);

      void visitLogical(Expr.Logical var1);

      void visitConditional(Expr.Conditional var1);

      void visitSection(Expr.Section var1);
   }
}
