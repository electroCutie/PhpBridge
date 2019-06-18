package cloud.literallya.phpBridge;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.function.Function;
import java.util.function.LongFunction;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Chars;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Primitives;
import com.google.common.primitives.Shorts;

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

@SuppressWarnings({ "rawtypes", "unchecked" })
class PrimitiveUtils{

  private PrimitiveUtils(){
  }

  private static final Map<Class, Function> wideners;
  static{
    ImmutableMap.Builder<Class, Function> b = ImmutableMap.builder();
    b.put(Float.class, (Function<Number, Double>) Number::doubleValue);
    b.put(Double.class, Function.identity());

    b.put(Character.class, (Function<Character, Long>) c -> (long) c.charValue());
    b.put(Byte.class, (Function<Number, Long>) Number::longValue);
    b.put(Short.class, (Function<Number, Long>) Number::longValue);
    b.put(Integer.class, (Function<Number, Long>) Number::longValue);
    b.put(Long.class, (Function<Number, Long>) Number::longValue);

    wideners = b.build();
  }

  public static Object widen(Object o){
    return checkNotNull(wideners.get(o.getClass())).apply(o);
  }

  public static Function<Object, Object> getWidener(Class clazz){
    return checkNotNull(wideners.get(clazz));
  }

  private static byte saturatedByte(long v){
    final long narrowed = v & 0xff;
    return narrowed == v ? (byte) narrowed : v > Byte.MAX_VALUE ? Byte.MAX_VALUE : Byte.MIN_VALUE;
  }

  private static final Map<Class, LongFunction> narrowers;
  static{
    ImmutableMap.Builder<Class, LongFunction> b = ImmutableMap.builder();
    b.put(Character.class, (LongFunction<Character>) Chars::saturatedCast);
    b.put(Byte.class, (LongFunction<Byte>) PrimitiveUtils::saturatedByte);
    b.put(Short.class, (LongFunction<Short>) Shorts::saturatedCast);
    b.put(Integer.class, (LongFunction<Integer>) Ints::saturatedCast);
    b.put(Long.class, l -> l);

    narrowers = b.build();
  }

  public static Function<Object, Object> narrower(Class narrowTo){
    narrowTo = Primitives.wrap(narrowTo);
    if(narrowers.containsKey(narrowTo)){
      LongFunction<Object> narrower = narrowers.get(narrowTo);
      return v -> narrower.apply(((Number) v).longValue());
    }

    if(Double.class == narrowTo)
      return v -> ((Number) v).doubleValue();
    if(Float.class == narrowTo)
      return v -> ((Number) v).floatValue();

    throw new UnsupportedOperationException();
  }

  public static Object narrow(Class narrowTo, Object value){
    narrowTo = Primitives.wrap(narrowTo);
    if(narrowers.containsKey(narrowTo))
      return narrow(narrowTo, ((Number) value).longValue());
    return narrow(narrowTo, ((Number) value).doubleValue());
  }

  public static Object narrow(Class narrowTo, long value){
    return checkNotNull(narrowers.get(Primitives.wrap(narrowTo))).apply(value);
  }

  public static Object narrow(Class narrowTo, double value){
    narrowTo = Primitives.wrap(narrowTo);
    if(Double.class == narrowTo)
      return value;
    else if(Float.class == narrowTo)
      return (float) value;
    throw new UnsupportedOperationException();
  }

}
