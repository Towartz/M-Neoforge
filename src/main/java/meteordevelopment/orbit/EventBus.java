package meteordevelopment.orbit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import meteordevelopment.orbit.listeners.IListener;
import meteordevelopment.orbit.listeners.LambdaListener;

public class EventBus implements IEventBus {
   private final Map<Object, List<IListener>> listenerCache = Collections.synchronizedMap(new IdentityHashMap<>());
   private final Map<Class<?>, List<IListener>> staticListenerCache = new ConcurrentHashMap<>();
   private final Map<Class<?>, List<IListener>> listenerMap = new ConcurrentHashMap<>();
   private final Map<Class<?>, IListener[]> listenerArrayMap = new ConcurrentHashMap<>();
   private final List<EventBus.LambdaFactoryInfo> lambdaFactoryInfos = new ArrayList<>();

   // Fast-path cached listeners for high-frequency engine events
   private Class<?> preTickClass;
   private volatile IListener[] preTickListeners;
   private Class<?> postTickClass;
   private volatile IListener[] postTickListeners;
   private Class<?> render2DClass;
   private volatile IListener[] render2DListeners;
   private Class<?> render3DClass;
   private volatile IListener[] render3DListeners;

   private void updateFastPathCache(Class<?> target) {
      if (target == null) return;
      String name = target.getName();
      if (name.endsWith("TickEvent$Pre")) {
         this.preTickClass = target;
         this.preTickListeners = this.listenerArrayMap.get(target);
      } else if (name.endsWith("TickEvent$Post")) {
         this.postTickClass = target;
         this.postTickListeners = this.listenerArrayMap.get(target);
      } else if (name.endsWith("Render2DEvent")) {
         this.render2DClass = target;
         this.render2DListeners = this.listenerArrayMap.get(target);
      } else if (name.endsWith("Render3DEvent")) {
         this.render3DClass = target;
         this.render3DListeners = this.listenerArrayMap.get(target);
      }
   }

   @Override
   public void registerLambdaFactory(String packagePrefix, LambdaListener.Factory factory) {
      synchronized (this.lambdaFactoryInfos) {
         this.lambdaFactoryInfos.add(new EventBus.LambdaFactoryInfo(packagePrefix, factory));
      }
   }

   @Override
   public boolean isListening(Class<?> eventKlass) {
      IListener[] listeners = this.listenerArrayMap.get(eventKlass);
      return listeners != null && listeners.length > 0;
   }

   @Override
   public <T> T post(T event) {
      Class<?> klass = event.getClass();
      IListener[] listeners;
      if (klass == this.preTickClass) {
         listeners = this.preTickListeners;
      } else if (klass == this.postTickClass) {
         listeners = this.postTickListeners;
      } else if (klass == this.render2DClass) {
         listeners = this.render2DListeners;
      } else if (klass == this.render3DClass) {
         listeners = this.render3DListeners;
      } else {
         listeners = this.listenerArrayMap.get(klass);
      }

      if (listeners != null) {
         for (int i = 0; i < listeners.length; i++) {
            listeners[i].call(event);
         }
      }

      return event;
   }

   @Override
   public <T extends ICancellable> T post(T event) {
      Class<?> klass = event.getClass();
      IListener[] listeners;
      if (klass == this.preTickClass) {
         listeners = this.preTickListeners;
      } else if (klass == this.postTickClass) {
         listeners = this.postTickListeners;
      } else if (klass == this.render2DClass) {
         listeners = this.render2DListeners;
      } else if (klass == this.render3DClass) {
         listeners = this.render3DListeners;
      } else {
         listeners = this.listenerArrayMap.get(klass);
      }

      if (listeners != null) {
         event.setCancelled(false);

         for (int i = 0; i < listeners.length; i++) {
            listeners[i].call(event);
            if (event.isCancelled()) {
               break;
            }
         }
      }

      return event;
   }

   @Override
   public void subscribe(Object object) {
      this.subscribe(this.getListeners(object.getClass(), object), false);
   }

   @Override
   public void subscribe(Class<?> klass) {
      this.subscribe(this.getListeners(klass, null), true);
   }

   @Override
   public void subscribe(IListener listener) {
      this.subscribe(listener, false);
   }

   private void subscribe(List<IListener> listeners, boolean onlyStatic) {
      for (IListener listener : listeners) {
         this.subscribe(listener, onlyStatic);
      }
   }

   private void subscribe(IListener listener, boolean onlyStatic) {
      if (onlyStatic) {
         if (listener.isStatic()) {
            this.insert(this.listenerMap.computeIfAbsent(listener.getTarget(), aClass -> new CopyOnWriteArrayList<>()), listener);
         }
      } else {
         this.insert(this.listenerMap.computeIfAbsent(listener.getTarget(), aClass -> new CopyOnWriteArrayList<>()), listener);
      }
   }

   private void insert(List<IListener> listeners, IListener listener) {
      int i = 0;

      while (i < listeners.size() && listener.getPriority() <= listeners.get(i).getPriority()) {
         i++;
      }

      listeners.add(i, listener);
      this.listenerArrayMap.put(listener.getTarget(), listeners.toArray(new IListener[0]));
      this.updateFastPathCache(listener.getTarget());
   }

   @Override
   public void unsubscribe(Object object) {
      this.unsubscribe(this.getListeners(object.getClass(), object), false);
      this.listenerCache.remove(object);
   }

   @Override
   public void unsubscribe(Class<?> klass) {
      this.unsubscribe(this.getListeners(klass, null), true);
   }

   @Override
   public void unsubscribe(IListener listener) {
      this.unsubscribe(listener, false);
   }

   private void unsubscribe(List<IListener> listeners, boolean staticOnly) {
      for (IListener listener : listeners) {
         this.unsubscribe(listener, staticOnly);
      }
   }

   private void unsubscribe(IListener listener, boolean staticOnly) {
      List<IListener> l = this.listenerMap.get(listener.getTarget());
      if (l != null) {
         if (staticOnly) {
            if (listener.isStatic()) {
               l.remove(listener);
               this.listenerArrayMap.put(listener.getTarget(), l.toArray(new IListener[0]));
               this.updateFastPathCache(listener.getTarget());
            }
         } else {
            l.remove(listener);
            this.listenerArrayMap.put(listener.getTarget(), l.toArray(new IListener[0]));
            this.updateFastPathCache(listener.getTarget());
         }
      }
   }

   private List<IListener> getListeners(Class<?> klass, Object object) {
      Function<Object, List<IListener>> func = o -> {
         List<IListener> listeners = new CopyOnWriteArrayList<>();
         this.getListeners(listeners, klass, object);
         return listeners;
      };
      if (object == null) {
         return this.staticListenerCache.computeIfAbsent(klass, func);
      } else {
         List<IListener> listeners = this.listenerCache.get(object);
         if (listeners != null) {
            return listeners;
         }

         listeners = func.apply(object);
         this.listenerCache.put(object, listeners);
         return listeners;
      }
   }

   private void getListeners(List<IListener> listeners, Class<?> klass, Object object) {
      for (Method method : klass.getDeclaredMethods()) {
         if (this.isValid(method)) {
            listeners.add(new LambdaListener(this.getLambdaFactory(klass), klass, object, method));
         }
      }

      if (klass.getSuperclass() != null) {
         this.getListeners(listeners, klass.getSuperclass(), object);
      }
   }

   private boolean isValid(Method method) {
      if (!method.isAnnotationPresent(EventHandler.class)) {
         return false;
      } else if (method.getReturnType() != void.class) {
         return false;
      } else {
         return method.getParameterCount() != 1 ? false : !method.getParameters()[0].getType().isPrimitive();
      }
   }

   private LambdaListener.Factory getLambdaFactory(Class<?> klass) {
      synchronized (this.lambdaFactoryInfos) {
         for (EventBus.LambdaFactoryInfo info : this.lambdaFactoryInfos) {
            if (klass.getName().startsWith(info.packagePrefix)) {
               return info.factory;
            }
         }
      }

      throw new NoLambdaFactoryException(klass);
   }

   private static class LambdaFactoryInfo {
      public final String packagePrefix;
      public final LambdaListener.Factory factory;

      public LambdaFactoryInfo(String packagePrefix, LambdaListener.Factory factory) {
         this.packagePrefix = packagePrefix;
         this.factory = factory;
      }
   }
}
