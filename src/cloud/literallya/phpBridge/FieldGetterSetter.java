package cloud.literallya.phpBridge;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

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
class FieldGetterSetter{

  private final LoadingCache<FieldDescriptor, Function<Object, Object>> getterCache = CacheBuilder.newBuilder()
    .expireAfterAccess(60, TimeUnit.MINUTES)
    .build(CacheLoader.from(this::resolveGetter));

  private final LoadingCache<FieldDescriptor, BiConsumer<Object, Object>> setterCache = CacheBuilder.newBuilder()
    .expireAfterAccess(60, TimeUnit.MINUTES)
    .build(CacheLoader.from(this::resolveSetter));

  public FieldGetterSetter(){
  }

  public void set(Object o, String field, Object value){
    setterCache.getUnchecked(new FieldDescriptor(getClassOfObject(o), field)).accept(o, value);
  }

  public Object get(Object o, String field){
    return getterCache.getUnchecked(new FieldDescriptor(getClassOfObject(o), field)).apply(o);
  }

  private static Class getClassOfObject(Object o){
    checkNotNull(o);
    if(o instanceof ClassWrapper)
      return ((ClassWrapper) o).getClazz();
    return o.getClass();
  }

  private BiConsumer<Object, Object> resolveSetter(FieldDescriptor desc){
    final Field f = desc.getField();
    if(Modifier.isFinal(f.getModifiers()))
      throw new UnsupportedOperationException("Final Field");

    try{
      MethodHandle setter = MethodHandles.lookup().unreflectSetter(f);
      if(Modifier.isStatic(f.getModifiers())){
        return (o, v) -> {
          try{
            setter.invokeWithArguments(v);
          }catch(Throwable e){
            throw new RuntimeException(e);
          }
        };
      }else{
        return (o, v) -> {
          try{
            setter.invokeWithArguments(o, v);
          }catch(Throwable e){
            throw new RuntimeException(e);
          }
        };
      }
    }catch(IllegalAccessException e){
      throw new RuntimeException(e);
    }
  }

  private Function<Object, Object> resolveGetter(FieldDescriptor desc){
    // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=5047859
    if(desc.type.isArray() && desc.name.equals("length"))
      return Array::getLength;

    if(desc.name.equals("class"))
      return ClassWrapper::unwrap;

    try{
      final Field f = desc.getField();
      MethodHandle getter = MethodHandles.lookup().unreflectGetter(f);
      if(Modifier.isStatic(f.getModifiers())){
        return o -> {
          try{
            return getter.invokeWithArguments();
          }catch(Throwable e){
            throw new RuntimeException(e);
          }
        };
      }else{
        return o -> {
          try{
            return getter.invokeWithArguments(o);
          }catch(Throwable e){
            throw new RuntimeException(e);
          }
        };
      }
    }catch(IllegalAccessException e){
      throw new RuntimeException(e);
    }
  }

  private class FieldDescriptor{
    private final Class type;
    private final String name;

    public FieldDescriptor(Class type, String name){
      this.type = type;
      this.name = name;
    }

    @Override
    public int hashCode(){
      return type.hashCode() ^ name.hashCode();
    }

    @Override
    public boolean equals(Object obj){
      if(obj instanceof FieldDescriptor){
        FieldDescriptor o = (FieldDescriptor) obj;
        return o == this ||
          (o.type.equals(type) && o.name.equals(name));
      }
      return false;
    }

    public Field getField(){
      try{
        Field f = type.getField(name);
        checkArgument(Modifier.isPublic(f.getModifiers()));
        return f;
      }catch(NoSuchFieldException | SecurityException e){
      }

      try{
        Field f = type.getDeclaredField(name);
        checkArgument(Modifier.isPublic(f.getModifiers()));
        checkArgument(f.isAccessible());
        return f;
      }catch(NoSuchFieldException | SecurityException e){
        throw new RuntimeException(e);
      }
    }
  }

}
