package meteordevelopment.meteorclient.utils.misc;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class ValueComparableMap<K extends Comparable<K>, V> extends TreeMap<K, V> {
   private final transient Map<K, V> valueMap;

   public ValueComparableMap(Comparator<? super V> partialValueComparator) {
      this(partialValueComparator, new HashMap<>());
   }

   private ValueComparableMap(Comparator<? super V> partialValueComparator, HashMap<K, V> valueMap) {
      super((k1, k2) -> {
         if (k1 == null && k2 == null) return 0;
         if (k1 == null) return -1;
         if (k2 == null) return 1;

         V v1 = valueMap.get(k1);
         V v2 = valueMap.get(k2);

         if (v1 != null && v2 != null) {
            int cmp = partialValueComparator.compare(v1, v2);
            if (cmp != 0) return cmp;
         } else if (v1 != null) {
            return -1;
         } else if (v2 != null) {
            return 1;
         }

         return k1.compareTo((K) k2);
      });
      this.valueMap = valueMap;
   }

   @Override
   public V put(K k, V v) {
      if (this.valueMap.containsKey(k)) {
         this.remove(k);
      }

      this.valueMap.put(k, v);
      return super.put(k, v);
   }

   @Override
   public boolean containsKey(Object key) {
      return this.valueMap.containsKey(key);
   }

   @Override
   public V getOrDefault(Object key, V defaultValue) {
      V val = this.valueMap.get(key);
      return val != null ? val : defaultValue;
   }
}
