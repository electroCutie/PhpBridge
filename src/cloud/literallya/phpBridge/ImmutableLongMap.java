package cloud.literallya.phpBridge;

import static cloud.literallya.phpBridge.LongHashing.key;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

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

public abstract class ImmutableLongMap<V> extends HashingLongMap<V>{

  private ImmutableLongMap(HashingLongMap<V> basis){
    super(basis);
  }

  private ImmutableLongMap(){
  }

  @Override
  public final V remove(long key){
    throw new UnsupportedOperationException();
  }

  @Override
  public final V put(long key, V value){
    throw new UnsupportedOperationException();
  }

  @Override
  public final void clear(){
    throw new UnsupportedOperationException();
  }

  @Override
  public final void putAll(LongMap<? extends V> m){
    throw new UnsupportedOperationException();
  }

  private static class RegularImmutableLongMap<V> extends ImmutableLongMap<V>{
    private RegularImmutableLongMap(Builder<V> b){
      super(b.mutableTmp);
    }

    private long hashCode = Long.MIN_VALUE;

    @Override
    public int hashCode(){
      if(hashCode == Long.MIN_VALUE)
        hashCode = super.hashCode();
      return (int) hashCode;
    }

    class AbsImmItr extends AbsItr{
      @Override
      public final void remove(){
        throw new UnsupportedOperationException();
      }
    }

    class EntryItr extends AbsImmItr implements Iterator<LongMap.Entry<V>>{
      @Override
      public LongMap.Entry<V> next(){
        return new ImmutableEntry(advanceIdx(), this);
      }
    }

    private final class ImmutableEntry extends HashingLongMap<V>.Entry{
      public ImmutableEntry(int idx, AbsItr itr){
        super(idx, itr);
      }

      @Override
      public final V setValue(V value){
        throw new UnsupportedOperationException();
      }
    }

    @Override
    protected Iterator<LongMap.Entry<V>> entryIterator(){
      return new EntryItr();
    }

    final class KeyItr extends AbsImmItr implements LongIterator{
      @Override
      public long nextLong(){
        return key(advanceIdx(), index);
      }
    }

    @Override
    protected LongIterator keyIterator(){
      return new KeyItr();
    }
  }

  private static class EmptyImmutableLongMap<V> extends ImmutableLongMap<V>{
    private EmptyImmutableLongMap(){
    }

    @Override
    public V get(long key){
      return null;
    }

    @Override
    public boolean containsKey(long key){
      return false;
    }

    @Override
    public Set<Map.Entry<Long, V>> entrySet(){
      return ImmutableSet.of();
    }

    @Override
    protected Iterator<LongMap.Entry<V>> entryIterator(){
      return ImmutableSet.<LongMap.Entry<V>> of().iterator();
    }
  }

  @SuppressWarnings("rawtypes")
  private static final EmptyImmutableLongMap emptySingleton =
    new EmptyImmutableLongMap();

  public static <V> Builder<V> builder(){
    return new Builder<>();
  }

  public static class Builder<V> {
    private final HashingLongMap<V> mutableTmp = new HashingLongMap<>();

    private Builder(){
    }

    public Builder<V> put(long key, V value){
      checkArgument(!mutableTmp.containsKey(key));
      mutableTmp.put(key, checkNotNull(value));
      return this;
    }

    @SuppressWarnings("unchecked")
    public ImmutableLongMap<V> build(){
      if(mutableTmp.isEmpty())
        return emptySingleton;
      return new RegularImmutableLongMap<>(this);
    }
  }

}
