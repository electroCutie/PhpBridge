package cloud.literallya.phpBridge;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.collect.Iterators;
import com.google.common.primitives.Longs;

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

/**
 * Skeletal implementation of a LongMap with default implementations for most methods.
 * <br>
 * Some methods should be overridden for efficiency if possible, these are clearly mark with <i>Override if possible</i>
 *
 * @param <V>
 */
public abstract class AbstractLongMap<V> extends AbstractMap<Long, V> implements LongMap<V>{

  @Override
  public V get(Object key){
    if(key instanceof Long)
      return get(((Long) key).longValue());
    return null;
  }

  @Override
  public V put(long key, V value){
    throw new UnsupportedOperationException();
  }

  @Override
  public V put(Long key, V value){
    return put(key.longValue(), value);
  }

  /**
   * Copies all entries from the specified LongMap to this LongMap by iterating over the specified map's <code>entrySet()</code>
   * and calling this map's <code>put</code> method.
   * <br>
   * If both maps take pains to avoid boxing over the entry set and put methods then this implementation will also not incur
   * boxing or un-boxing
   */
  @Override
  public void putAll(LongMap<? extends V> m){
    for(LongMap.Entry<? extends V> e : m.longEntrySet())
      put(e.longKey(), e.getValue());
  }

  /**
   * <i>Override if possible</i>
   * <br>
   * Inefficient entry iterator implementation
   */
  @Override
  public V remove(long key){
    Iterator<LongMap.Entry<V>> itr = entryIterator();
    while(itr.hasNext()){
      LongMap.Entry<V> e = itr.next();
      if(e.getKey() == key){
        V val = e.getValue();
        itr.remove();
        return val;
      }
    }
    return null;
  }

  @Override
  public V remove(Object key){
    if(key instanceof Long)
      return remove(((Long) key).longValue());
    return null;
  }

  @Override
  public boolean containsKey(Object key){
    if(key instanceof Long)
      return containsKey(((Long) key).longValue());
    return false;
  }

  //
  @Override
  public int size(){
    return Iterators.size(entryIterator());
  }

  //

  /**
   * Hash code implementation that is compatible with that of a <code>Map&lt;Long, V&gt;</code> and avoids boxing
   */
  @SuppressWarnings("rawtypes")
  @Override
  public int hashCode(){
    int c = 0;
    for(LongMap.Entry e : longEntrySet()){
      c += Longs.hashCode(e.longKey())
        ^ (e.getValue() == null ? 0 : e.getValue().hashCode());
    }
    return c;
  }

  @SuppressWarnings("rawtypes")
  private boolean longEquals(LongMap o){
    if(o == this)
      return true;
    if(size() != o.size())
      return false;
    for(LongMap.Entry e : longEntrySet()){
      final long k = e.longKey();
      if(!o.containsKey(k))
        return false;
      if(!Objects.equal(o.get(k), e.getValue()))
        return false;
    }
    return true;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private boolean objectEquals(Map o){
    if(size() != o.size())
      return false;
    for(Map.Entry e : (Set<Map.Entry>) o.entrySet()){
      final Object k = e.getKey();
      if(!(k instanceof Long))
        return false;

      long l = (Long) k;

      if(!containsKey(l))
        return false;
      if(!Objects.equal(get(l), e.getValue()))
        return false;
    }
    return true;
  }

  /**
   * Equals implementation that is compatible with that of a <code>Map&lt;Long, V&gt;</code> and avoids all un-needed boxing and
   * un-boxing even when comparing to boxed maps
   */
  @Override
  @SuppressWarnings("rawtypes")
  public boolean equals(Object o){
    if(o instanceof LongMap)
      return longEquals((LongMap) o);
    if(o instanceof Map)
      return objectEquals((Map) o);
    return super.equals(o);
  }

  //

  /**
   * Entry iterator for the entry set and for naive implementations of some methods
   */
  protected abstract Iterator<LongMap.Entry<V>> entryIterator();

  private EntrySet entrySet;

  @Override
  public Set<LongMap.Entry<V>> longEntrySet(){
    if(null != entrySet)
      return entrySet;
    return entrySet = new EntrySet();
  }

  private class EntrySet extends AbstractSet<LongMap.Entry<V>>{
    @SuppressWarnings("rawtypes")
    @Override
    public boolean contains(Object o){
      if(o instanceof LongMap.Entry){
        LongMap.Entry e = (LongMap.Entry) o;
        return containsKey(e.longKey()) && Objects.equal(get(e.longKey()), e.getValue());
      }else if(o instanceof Map.Entry){
        Map.Entry e = (Map.Entry) o;
        return containsKey(e.getKey()) && Objects.equal(get(e.getKey()), e.getValue());
      }else{
        return false;
      }
    }

    private boolean rm(long key, Object value){
      if(!containsKey(key))
        return false;
      AbstractLongMap.this.remove(key);
      return true;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean remove(Object o){
      if(o instanceof LongMap.Entry){
        LongMap.Entry e = (LongMap.Entry) o;
        return rm(e.longKey(), e.getValue());
      }else if(o instanceof Map.Entry){
        Map.Entry e = (Map.Entry) o;
        Object k = e.getKey();
        if(!(k instanceof Long))
          return false;
        return rm((Long) k, e.getValue());
      }else{
        return false;
      }
    }

    @Override
    public void clear(){
      AbstractLongMap.this.clear();
    }

    @Override
    public int size(){
      return AbstractLongMap.this.size();
    }

    @Override
    public Iterator<LongMap.Entry<V>> iterator(){
      return entryIterator();
    }

  }

  //

  /**
   * Wraps the entry iterator. Not terribly efficient. Override if possible
   */
  protected LongIterator keyIterator(){
    return new WrappedKeyIterator();
  };

  private class WrappedKeyIterator implements LongIterator{
    private final Iterator<LongMap.Entry<V>> entryIterator = entryIterator();

    @Override
    public boolean hasNext(){
      return entryIterator.hasNext();
    }

    @Override
    public long nextLong(){
      return entryIterator.next().longKey();
    }

    @Override
    public void remove(){
      entryIterator.remove();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Set<Map.Entry<Long, V>> entrySet(){
    return (Set<Map.Entry<Long, V>>) (Object) longEntrySet();
  }

  private class KeySet extends AbstractLongSet{
    @Override
    public boolean contains(long l){
      return containsKey(l);
    }

    @Override
    public boolean remove(long l){
      if(!containsKey(l))
        return false;
      AbstractLongMap.this.remove(l);
      return true;
    }

    @Override
    public LongIterator longIterator(){
      return keyIterator();
    }

    @Override
    public int size(){
      return AbstractLongMap.this.size();
    }
  }

  private KeySet keySet;

  @Override
  public LongSet longKeySet(){
    if(null != keySet)
      return keySet;
    return keySet = new KeySet();
  }

  @Override
  public Set<Long> keySet(){
    return longKeySet();
  }

  public abstract static class AbstractLongEntry<V> implements LongMap.Entry<V>{
    @Override
    public final Long getKey(){
      return longKey();
    }

    private boolean primEq(LongMap.Entry o){
      return this == o ||
        (longKey() == o.longKey()
        && Objects.equal(getValue(), o.getValue()));
    }

    private boolean boxedEq(Map.Entry o){
      final Object L = o.getKey();
      return null != L
        && L instanceof Long
        && longKey() == ((Long) L).longValue()
        && Objects.equal(getValue(), o.getValue());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean equals(Object obj){
      if(obj instanceof LongMap.Entry)
        return primEq((LongMap.Entry) obj);
      if(obj instanceof Map.Entry)
        return boxedEq((Map.Entry) obj);
      return false;
    }

    @Override
    public int hashCode(){
      Object v = getValue();
      return Longs.hashCode(longKey()) ^
        (null == v ? 0 : v.hashCode());
    }

  }

}
