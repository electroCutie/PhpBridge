package cloud.literallya.phpBridge;

import static com.google.common.base.Preconditions.checkState;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

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

public abstract class AbstractLongSet extends AbstractSet<Long> implements LongSet{

  /**
   * <i>Override if possible</i>
   * <br>
   * Inefficient iterator based contains implementation. Override if possible
   */
  @Override
  public boolean contains(long l){
    LongIterator itr = longIterator();
    while(itr.hasNext()){
      if(itr.next() == l)
        return true;
    }
    return false;
  }

  @Override
  public boolean contains(Object o){
    if(o instanceof Long)
      return contains(((Long) o).longValue());
    return false;
  }

  /**
   * @throws UnsupportedOperationException
   *           always
   */
  @Override
  public boolean add(long l){
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean add(Long e){
    return add(e.longValue());
  }

  /**
   * @throws UnsupportedOperationException
   *           always
   */
  @Override
  public boolean remove(long l){
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object o){
    if(o instanceof Long)
      return remove(((Long) o).longValue());
    return false;
  }

  /**
   * <i>Override if possible</i>
   * <br>
   * Inefficient iterator based size implementation. Override if possible
   */
  @Override
  public int size(){
    int i = 0;
    LongIterator itr = longIterator();
    while(itr.hasNext()){
      itr.next();
      i++;
    }
    return i;
  }

  @Override
  public int hashCode(){
    LongIterator itr = longIterator();
    int sum = 0;
    while(itr.hasNext())
      sum += Longs.hashCode(itr.next());
    return sum;
  }

  private boolean primEquals(LongSet o){
    if(o == this)
      return true;
    if(o.size() != size())
      return false;

    LongIterator itr = longIterator();
    while(itr.hasNext()){
      if(!o.contains(itr.next()))
        return false;
    }
    return true;
  }

  @SuppressWarnings("rawtypes")
  private boolean fatEquals(Set o){
    if(o.size() != size())
      return false;

    Iterator itr = o.iterator();
    while(itr.hasNext()){
      if(!contains(itr.next()))
        return false;
    }
    return true;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public boolean equals(Object obj){
    if(obj instanceof LongSet)
      return primEquals((LongSet) obj);
    if(obj instanceof Set)
      return fatEquals((Set) obj);
    return false;
  }

  /**
   * non-wrapping Iterator based to-array implementation
   */
  @Override
  public long[] toLongArray(){
    long keys[] = new long[size()];
    LongIterator itr = longIterator();
    for(int i = 0; i < keys.length; i++)
      keys[i] = itr.next();
    checkState(!itr.hasNext());
    return keys;
  }

  private class BoxingKeyIterator implements Iterator<Long>{
    private final LongIterator itr = longIterator();

    @Override
    public boolean hasNext(){
      return itr.hasNext();
    }

    @Override
    public Long next(){
      return itr.next();
    }

    @Override
    public void remove(){
      itr.remove();
    }
  }

  /**
   * Implementation is a wrapper of the primitive iterator
   */
  @Override
  public Iterator<Long> iterator(){
    return new BoxingKeyIterator();
  }
}
