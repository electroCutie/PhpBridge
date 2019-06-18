package cloud.literallya.phpBridge;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

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
public final class ClassWrapper{
  private static LoadingCache<Class, ClassWrapper> memoizedInstances = CacheBuilder.newBuilder()
    .weakValues()
    .build(CacheLoader.from(ClassWrapper::new));

  private final Class clazz;

  private ClassWrapper(Class clazz){
    checkArgument(!clazz.equals(ClassWrapper.class));
    this.clazz = clazz;
  }

  public Class getClazz(){
    return clazz;
  }

  @Override
  public String toString(){
    return String.format("ClassWrapper<%s>", clazz.getName());
  }

  public static ClassWrapper get(Class c){
    return memoizedInstances.getUnchecked(c);
  }

  public static Object unwrap(Object o){
    if(o instanceof ClassWrapper)
      return ((ClassWrapper) o).getClazz();
    return o;
  }

}
