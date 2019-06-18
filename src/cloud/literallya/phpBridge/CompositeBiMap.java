package cloud.literallya.phpBridge;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.BiMap;

/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

public class CompositeBiMap<K, V> extends AbstractMap<K, V> implements BiMap<K, V>{

  private final Map<K, V> forward;
  private final Map<V, K> reverse;

  private BiMap<V, K> inverse;

  public CompositeBiMap(Map<K, V> f, Map<V, K> r){
    this.forward = f;
    this.reverse = r;
  }

  private CompositeBiMap(CompositeBiMap<V, K> inv){
    this.forward = inv.reverse;
    this.reverse = inv.forward;
    this.inverse = inv;
  }

  @Override
  public boolean containsKey(Object key){
    return forward.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value){
    return reverse.containsValue(value);
  }

  @Override
  public V get(Object key){
    return forward.get(key);
  }

  @Override
  public V put(K key, V value){
    checkNotNull(key);
    checkNotNull(value);
    checkState(!reverse.containsKey(value));

    V old = forward.put(key, value);
    reverse.put(value, key);
    return old;
  }

  @Override
  public V forcePut(K key, V value){
    V old = remove(key);
    inverse().remove(value, key);
    put(key, value);
    return old;
  }

  @Override
  public V remove(Object key){
    V old = forward.remove(key);
    reverse.remove(old);
    return old;
  }

  @Override
  public int size(){
    return forward.size();
  }

  @Override
  public void clear(){
    forward.clear();
    reverse.clear();
  }

  @Override
  public BiMap<V, K> inverse(){
    BiMap<V, K> inv = inverse;
    if(null == inv)
      inv = (inverse = new CompositeBiMap<>(this));
    return inv;
  }

  /*
   * Views
   */

  private class EntryItr<T> implements Iterator<T>{
    private final Iterator<Map.Entry<K, V>> itr = forward.entrySet().iterator();
    private Map.Entry<K, V> previous;
    private final Function<Map.Entry<K, V>, T> valueFunction;

    public EntryItr(Function<Map.Entry<K, V>, T> f){
      valueFunction = f;
    }

    @Override
    public boolean hasNext(){
      return itr.hasNext();
    }

    @Override
    public T next(){
      return valueFunction.apply(previous = itr.next());
    }

    @Override
    public void remove(){
      itr.remove();
      reverse.remove(previous.getValue());
    }
  }

  private class EntryView<T> extends AbstractSet<T>{
    private final Function<Map.Entry<K, V>, T> valueFunction;

    public EntryView(Function<Map.Entry<K, V>, T> f){
      valueFunction = f;
    }

    @Override
    public boolean remove(Object o){
      return null != CompositeBiMap.this.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c){
      boolean change = false;
      for(Object o : c)
        change |= null != CompositeBiMap.this.remove(o);
      return change;
    }

    @Override
    public int size(){
      return forward.size();
    }

    @Override
    public Iterator<T> iterator(){
      return new EntryItr<>(valueFunction);
    }
  }

  @Override
  public Set<Map.Entry<K, V>> entrySet(){
    return new EntryView<>(Function.identity());
  }

  @Override
  public Set<K> keySet(){
    return new EntryView<K>(Map.Entry::getKey);
  }

  @Override
  public Set<V> values(){
    return inverse().keySet();
  }

}
