package cloud.literallya.phpBridge;

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

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

@SuppressWarnings("rawtypes")
public class ClassHierarchyCollection extends AbstractCollection<Class> implements Collection<Class>{

  private final Class base;

  public ClassHierarchyCollection(Class base){
    this.base = base;
  }

  @Override
  public Iterator<Class> iterator(){
    return Spliterators.iterator(spliterator());
  }

  @Override
  public Spliterator<Class> spliterator(){
    return new Itr(base);
  }

  private int size = -1;

  @Override
  public int size(){
    int s = size;
    if(s < 0)
      size = s = Ints.saturatedCast(stream().count());
    return s;
  }

  public static Stream<Class> hiararchyStream(Class c){
    return StreamSupport.stream(new Itr(c), true);
  }

  private static class Itr implements Spliterator<Class>{
    private final Deque<Class> work = Lists.newLinkedList();
    private Class onDeck = null;

    Itr(Class clz){
      onDeck = clz;
      tryGenerateWork();
    }

    private void tryGenerateWork(){
      if(null == onDeck)
        return;
      final Class sup = onDeck.getSuperclass();
      if(null != sup)
        work.add(sup);
      work.addAll(Arrays.asList(onDeck.getInterfaces()));
    }

    @Override
    public boolean tryAdvance(Consumer<? super Class> action){
      final Class w = onDeck;
      onDeck = work.poll();
      tryGenerateWork();
      if(null == w)
        return false;

      action.accept(w);

      return true;
    }

    @Override
    public Spliterator<Class> trySplit(){
      final Class w = work.poll();
      return null == w ? null : new Itr(w);
    }

    @Override
    public long estimateSize(){
      return Long.MAX_VALUE; // Class hierarchies aren't terribly easy to predict
    }

    @Override
    public int characteristics(){
      return Spliterator.IMMUTABLE // Class hierarchy cannot change
        | Spliterator.NONNULL; // There are no null classes
      // Note that even though there is a defined encounter order for a breadth first search it cannot be maintained across
      // threads without a ton of work. Going unordered
    }
  }

}
