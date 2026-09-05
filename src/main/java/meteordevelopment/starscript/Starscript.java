package meteordevelopment.starscript;

import java.util.function.Supplier;
import meteordevelopment.starscript.compiler.Expr;
import meteordevelopment.starscript.compiler.Parser;
import meteordevelopment.starscript.utils.CompletionCallback;
import meteordevelopment.starscript.utils.Error;
import meteordevelopment.starscript.utils.SFunction;
import meteordevelopment.starscript.utils.Stack;
import meteordevelopment.starscript.utils.StarscriptError;
import meteordevelopment.starscript.value.Value;
import meteordevelopment.starscript.value.ValueMap;

public class Starscript {
   private final ValueMap globals = new ValueMap();
   private final Stack<Value> stack = new Stack<>();

   public Section run(Script script, StringBuilder sb) {
      this.stack.clear();
      sb.setLength(0);
      int ip = 0;
      Section firstSection = null;
      Section section = null;
      int index = 0;

      while (true) {
         switch (Instruction.valueOf(script.code[ip++])) {
            case Constant: {
               this.push(script.constants.get(script.code[ip++]));
               break;
            }
            case Null: {
               this.push(Value.null_());
               break;
            }
            case True: {
               this.push(Value.bool(true));
               break;
            }
            case False: {
               this.push(Value.bool(false));
               break;
            }
            case Add: {
               Value bx = this.pop();
               Value axxxx = this.pop();
               if (axxxx.isNumber() && bx.isNumber()) {
                  this.push(Value.number(axxxx.getNumber() + bx.getNumber()));
               } else if (axxxx.isString()) {
                  this.push(Value.string(axxxx.getString() + bx.toString()));
               } else {
                  this.error("Can only add 2 numbers or 1 string and other value.");
               }
               break;
            }
            case Subtract: {
               Value bx = this.pop();
               Value axxxx = this.pop();
               if (axxxx.isNumber() && bx.isNumber()) {
                  this.push(Value.number(axxxx.getNumber() - bx.getNumber()));
                  break;
               }

               this.error("Can only subtract 2 numbers.");
               break;
            }
            case Multiply: {
               Value bx = this.pop();
               Value axxxx = this.pop();
               if (axxxx.isNumber() && bx.isNumber()) {
                  this.push(Value.number(axxxx.getNumber() * bx.getNumber()));
                  break;
               }

               this.error("Can only multiply 2 numbers.");
               break;
            }
            case Divide: {
               Value bx = this.pop();
               Value axxxx = this.pop();
               if (axxxx.isNumber() && bx.isNumber()) {
                  this.push(Value.number(axxxx.getNumber() / bx.getNumber()));
                  break;
               }

               this.error("Can only divide 2 numbers.");
               break;
            }
            case Modulo: {
               Value bx = this.pop();
               Value axxxx = this.pop();
               if (axxxx.isNumber() && bx.isNumber()) {
                  this.push(Value.number(axxxx.getNumber() % bx.getNumber()));
                  break;
               }

               this.error("Can only modulo 2 numbers.");
               break;
            }
            case Power: {
               Value bx = this.pop();
               Value axxxx = this.pop();
               if (axxxx.isNumber() && bx.isNumber()) {
                  this.push(Value.number(Math.pow(axxxx.getNumber(), bx.getNumber())));
                  break;
               }

               this.error("Can only power 2 numbers.");
               break;
            }
            case AddConstant: {
               Value b = script.constants.get(script.code[ip++]);
               Value axxx = this.pop();
               if (axxx.isNumber() && b.isNumber()) {
                  this.push(Value.number(axxx.getNumber() + b.getNumber()));
               } else if (axxx.isString()) {
                  this.push(Value.string(axxx.getString() + b.toString()));
               } else {
                  this.error("Can only add 2 numbers or 1 string and other value.");
               }
               break;
            }
            case Pop: {
               this.pop();
               break;
            }
            case Not: {
               this.push(Value.bool(!this.pop().isTruthy()));
               break;
            }
            case Negate: {
               Value axx = this.pop();
               if (axx.isNumber()) {
                  this.push(Value.number(-axx.getNumber()));
               } else {
                  this.error("This operation requires a number.");
               }
               break;
            }
            case Equals: {
               this.push(Value.bool(this.pop().equals(this.pop())));
               break;
            }
            case NotEquals: {
               this.push(Value.bool(!this.pop().equals(this.pop())));
               break;
            }
            case Greater: {
               Value b = this.pop();
               Value axxx = this.pop();
               if (axxx.isNumber() && b.isNumber()) {
                  this.push(Value.bool(axxx.getNumber() > b.getNumber()));
                  break;
               }

               this.error("This operation requires 2 number.");
               break;
            }
            case GreaterEqual: {
               Value b = this.pop();
               Value axxx = this.pop();
               if (axxx.isNumber() && b.isNumber()) {
                  this.push(Value.bool(axxx.getNumber() >= b.getNumber()));
                  break;
               }

               this.error("This operation requires 2 number.");
               break;
            }
            case Less: {
               Value b = this.pop();
               Value axxx = this.pop();
               if (axxx.isNumber() && b.isNumber()) {
                  this.push(Value.bool(axxx.getNumber() < b.getNumber()));
                  break;
               }

               this.error("This operation requires 2 number.");
               break;
            }
            case LessEqual: {
               Value b = this.pop();
               Value axxx = this.pop();
               if (axxx.isNumber() && b.isNumber()) {
                  this.push(Value.bool(axxx.getNumber() <= b.getNumber()));
                  break;
               }

               this.error("This operation requires 2 number.");
               break;
            }
            case Variable: {
               String namexxxx = script.constants.get(script.code[ip++]).getString();
               Supplier<Value> sxxx = this.globals.get(namexxxx);
               this.push(sxxx != null ? sxxx.get() : Value.null_());
               break;
            }
            case Get: {
               String namexxx = script.constants.get(script.code[ip++]).getString();
               Value vxxx = this.pop();
               if (!vxxx.isMap()) {
                  this.push(Value.null_());
               } else {
                  Supplier<Value> sxxx = vxxx.getMap().get(namexxx);
                  this.push(sxxx != null ? sxxx.get() : Value.null_());
               }
               break;
            }
            case Call: {
               int argCountx = script.code[ip++];
               Value ax = this.peek(argCountx);
               if (ax.isFunction()) {
                  Value r = ax.getFunction().run(this, argCountx);
                  this.pop();
                  this.push(r);
               } else {
                  this.error("Tried to call a %s, can only call functions.", ax.type);
               }
               break;
            }
            case Jump: {
               int jump = script.code[ip++] << 8 & 0xFF | script.code[ip++] & 255;
               ip += jump;
               break;
            }
            case JumpIfTrue: {
               int jump = script.code[ip++] << 8 & 0xFF | script.code[ip++] & 255;
               if (this.peek().isTruthy()) {
                  ip += jump;
               }
               break;
            }
            case JumpIfFalse: {
               int jump = script.code[ip++] << 8 & 0xFF | script.code[ip++] & 255;
               if (!this.peek().isTruthy()) {
                  ip += jump;
               }
               break;
            }
            case Section: {
               if (firstSection == null) {
                  firstSection = new Section(index, sb.toString());
                  section = firstSection;
               } else {
                  section.next = new Section(index, sb.toString());
                  section = section.next;
               }

               sb.setLength(0);
               index = script.code[ip++];
               break;
            }
            case Append: {
               sb.append(this.pop().toString());
               break;
            }
            case ConstantAppend: {
               sb.append(script.constants.get(script.code[ip++]).toString());
               break;
            }
            case VariableAppend: {
               Supplier<Value> sxx = this.globals.get(script.constants.get(script.code[ip++]).getString());
               sb.append((sxx == null ? Value.null_() : sxx.get()).toString());
               break;
            }
            case GetAppend: {
               String namexx = script.constants.get(script.code[ip++]).getString();
               Value vxx = this.pop();
               if (!vxx.isMap()) {
                  sb.append(Value.null_());
               } else {
                  Supplier<Value> sxx = vxx.getMap().get(namexx);
                  sb.append((sxx != null ? sxx.get() : Value.null_()).toString());
               }
               break;
            }
            case CallAppend: {
               int argCount = script.code[ip++];
               Value a = this.peek(argCount);
               if (a.isFunction()) {
                  Value r = a.getFunction().run(this, argCount);
                  this.pop();
                  sb.append(r.toString());
               } else {
                  this.error("Tried to call a %s, can only call functions.", a.type);
               }
               break;
            }
            case VariableGet: {
               String namex = script.constants.get(script.code[ip++]).getString();
               Supplier<Value> sx = this.globals.get(namex);
               Value vx = sx != null ? sx.get() : Value.null_();
               namex = script.constants.get(script.code[ip++]).getString();
               if (!vx.isMap()) {
                  this.push(Value.null_());
               } else {
                  sx = vx.getMap().get(namex);
                  this.push(sx != null ? sx.get() : Value.null_());
               }
               break;
            }
            case VariableGetAppend: {
               String name = script.constants.get(script.code[ip++]).getString();
               Supplier<Value> s = this.globals.get(name);
               Value v = s != null ? s.get() : Value.null_();
               name = script.constants.get(script.code[ip++]).getString();
               if (!v.isMap()) {
                  this.push(Value.null_());
               } else {
                  s = v.getMap().get(name);
                  v = s != null ? s.get() : Value.null_();
                  sb.append(v.toString());
               }
               break;
            }
            case End: {
               if (firstSection != null) {
                  section.next = new Section(index, sb.toString());
                  return firstSection;
               }

               return new Section(index, sb.toString());
            }
            default:
               throw new UnsupportedOperationException("Unknown instruction '" + Instruction.valueOf(script.code[ip]) + "'");
         }
      }
   }

   public Section run(Script script) {
      return this.run(script, new StringBuilder());
   }

   public void push(Value value) {
      this.stack.push(value);
   }

   public Value pop() {
      return this.stack.pop();
   }

   public Value peek() {
      return this.stack.peek();
   }

   public Value peek(int offset) {
      return this.stack.peek(offset);
   }

   public boolean popBool(String errorMsg) {
      Value a = this.pop();
      if (!a.isBool()) {
         this.error(errorMsg);
      }

      return a.getBool();
   }

   public double popNumber(String errorMsg) {
      Value a = this.pop();
      if (!a.isNumber()) {
         this.error(errorMsg);
      }

      return a.getNumber();
   }

   public String popString(String errorMsg) {
      Value a = this.pop();
      if (!a.isString()) {
         this.error(errorMsg);
      }

      return a.getString();
   }

   public void error(String format, Object... args) {
      throw new StarscriptError(String.format(format, args));
   }

   public ValueMap set(String name, Supplier<Value> supplier) {
      return this.globals.set(name, supplier);
   }

   public ValueMap set(String name, Value value) {
      return this.globals.set(name, value);
   }

   public ValueMap set(String name, boolean bool) {
      return this.globals.set(name, bool);
   }

   public ValueMap set(String name, double number) {
      return this.globals.set(name, number);
   }

   public ValueMap set(String name, String string) {
      return this.globals.set(name, string);
   }

   public ValueMap set(String name, SFunction function) {
      return this.globals.set(name, function);
   }

   public ValueMap set(String name, ValueMap map) {
      return this.globals.set(name, map);
   }

   public ValueMap getGlobals() {
      return this.globals;
   }

   public void getCompletions(String source, int position, CompletionCallback callback) {
      Parser.Result result = Parser.parse(source);

      for (Expr expr : result.exprs) {
         this.completionsExpr(source, position, expr, callback);
      }

      for (Error error : result.errors) {
         if (error.expr != null) {
            this.completionsExpr(source, position, error.expr, callback);
         }
      }
   }

   private void completionsExpr(String source, int position, Expr expr, CompletionCallback callback) {
      if (position >= expr.start && (position <= expr.end || position == source.length())) {
         if (expr instanceof Expr.Variable) {
            Expr.Variable var = (Expr.Variable)expr;
            String start = source.substring(var.start, position);

            for (String key : this.globals.keys()) {
               if (!key.startsWith("_") && key.startsWith(start)) {
                  callback.onCompletion(key, this.globals.get(key).get().isFunction());
               }
            }
         } else if (expr instanceof Expr.Get) {
            Expr.Get get = (Expr.Get)expr;
            if (position >= get.end - get.name.length()) {
               Value value = this.resolveExpr(get.object);
               if (value != null && value.isMap()) {
                  String start = source.substring(get.object.end + 1, position);

                  for (String keyx : value.getMap().keys()) {
                     if (!keyx.startsWith("_") && keyx.startsWith(start)) {
                        callback.onCompletion(keyx, value.getMap().get(keyx).get().isFunction());
                     }
                  }
               }
            } else {
               expr.forEach(child -> this.completionsExpr(source, position, child, callback));
            }
         } else if (expr instanceof Expr.Block) {
            if (((Expr.Block)expr).expr == null) {
               for (String keyxx : this.globals.keys()) {
                  if (!keyxx.startsWith("_")) {
                     callback.onCompletion(keyxx, this.globals.get(keyxx).get().isFunction());
                  }
               }
            } else {
               expr.forEach(child -> this.completionsExpr(source, position, child, callback));
            }
         } else {
            expr.forEach(child -> this.completionsExpr(source, position, child, callback));
         }
      }
   }

   private Value resolveExpr(Expr expr) {
      if (expr instanceof Expr.Variable) {
         Supplier<Value> supplier = this.globals.get(((Expr.Variable)expr).name);
         return supplier != null ? supplier.get() : null;
      } else if (expr instanceof Expr.Get) {
         Value value = this.resolveExpr(((Expr.Get)expr).object);
         if (value != null && value.isMap()) {
            Supplier<Value> supplier = value.getMap().get(((Expr.Get)expr).name);
            return supplier != null ? supplier.get() : null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }
}
