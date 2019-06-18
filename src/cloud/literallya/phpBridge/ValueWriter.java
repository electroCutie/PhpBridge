package cloud.literallya.phpBridge;

import static cloud.literallya.phpBridge.ProtocolConstants.bridge_A;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_D;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_EXCEPTION;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_J;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_S;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_V;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_Z;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;

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

@SuppressWarnings({ "unchecked", "rawtypes" })
class ValueWriter{

  private final OutputStream out;
  private final Map<Object, Long> objectToId;
  private long javaRefId = 1;

  public ValueWriter(OutputStream out, Map<Object, Long> o){
    this.out = out;
    this.objectToId = o;
  }

  private long getIdForObject(Object o){
    return objectToId.computeIfAbsent(o, v -> ++javaRefId);
  }

  private void write(int byteValue){
    try{
      out.write(byteValue);
    }catch(IOException e){
      throw new RuntimeException(e);
    }
  }

  private void writeWide(int wide){
    for(int i = 3; i >= 0; i--)
      write(0xff & (wide >>> (i << 3)));
  }

  private void writeDoubleWide(long wide){
    for(int i = 7; i >= 0; i--)
      write((int) (0xffl & (wide >>> (i << 3))));
  }

  private void sendNull(){
    write(bridge_V);
  }

  private void sendBoolean(boolean b){
    write(bridge_Z);
    write((byte) (b ? 1 : 0));
  }

  private void sendLong(long l){
    write(bridge_J);
    writeDoubleWide(l);
  }

  private void sendDouble(double d){
    write(bridge_D);
    writeDoubleWide(Double.doubleToRawLongBits(d));
  }

  private void sendStringLiteral(CharSequence str){
    int length = Strings.utf8Length(str);
    writeWide(length);
    str.codePoints().forEachOrdered(Strings.utf8CodepointWriter(this::write));
  }

  private void sendString(CharSequence str){
    write(bridge_S);
    writeDoubleWide(getIdForObject(str));
    sendStringLiteral(str);
  }

  public void sendException(Exception e){
    write(bridge_EXCEPTION);
    sendStringLiteral(Strings.notEmptyOr(e.getMessage(), "No Error Message"));
  }

  private void sendJavaRef(Object o){
    if(null == o){
      sendNull();
    }else{
      int flags = 0;
      if(o.getClass().isArray())
        flags |= ProtocolConstants.bridge_ARRAY_TYPE;
      else if(o instanceof List)
        flags |= ProtocolConstants.bridge_INDEXED_TYPE;
      else if(o instanceof Map)
        flags |= ProtocolConstants.bridge_MAP_TYPE;
      else if(o instanceof Iterable)
        flags |= ProtocolConstants.bridge_ITERABLE_TYPE;

      write(bridge_A);
      writeWide(flags);
      writeDoubleWide(getIdForObject(o));
    }
  }

  private static BiConsumer<ValueWriter, ?> compose(Function f, BiConsumer<ValueWriter, ?> c){
    return (vr, o) -> ((BiConsumer) c).accept(vr, f.apply(o));
  }

  private static final Map<Class, BiConsumer> primWriters;
  static{
    ImmutableMap.Builder<Class, BiConsumer<ValueWriter, ? extends Object>> b = ImmutableMap.builder();
    b.put(Boolean.class, (BiConsumer<ValueWriter, Boolean>) ValueWriter::sendBoolean);

    b.put(Character.class, compose(PrimitiveUtils.getWidener(Character.class),
      (BiConsumer<ValueWriter, Long>) ValueWriter::sendLong));
    b.put(Byte.class, compose(PrimitiveUtils.getWidener(Byte.class),
      (BiConsumer<ValueWriter, Long>) ValueWriter::sendLong));
    b.put(Short.class, compose(PrimitiveUtils.getWidener(Short.class),
      (BiConsumer<ValueWriter, Long>) ValueWriter::sendLong));
    b.put(Integer.class, compose(PrimitiveUtils.getWidener(Integer.class),
      (BiConsumer<ValueWriter, Long>) ValueWriter::sendLong));
    b.put(Long.class, (BiConsumer<ValueWriter, Long>) ValueWriter::sendLong);

    b.put(Float.class, compose(PrimitiveUtils.getWidener(Float.class),
      (BiConsumer<ValueWriter, Long>) ValueWriter::sendDouble));
    b.put(Double.class, (BiConsumer<ValueWriter, Long>) ValueWriter::sendDouble);

    primWriters = (Map) b.build();
  }

  private Consumer<Object> fromBi(BiConsumer c){
    return o -> c.accept(this, o);
  }

  private void sendPrim(Object o){
    primWriters.get(o.getClass()).accept(this, o);
  }

  public Consumer<Object> getSender(Class clazz){
    if(void.class == clazz){
      return (v) -> sendNull();
    }else if(CharSequence.class.isAssignableFrom(clazz)){
      return (v) -> sendString((CharSequence) v);
    }else if(Primitives.isWrapperType(clazz)){
      return fromBi(primWriters.get(clazz));
    }else{
      return this::sendJavaRef;
    }
  }

  public void sendValue(Object o){
    if(null == o){
      sendNull();
    }else if(o instanceof CharSequence){
      sendString((CharSequence) o);
    }else if(Primitives.isWrapperType(o.getClass())){
      sendPrim(o);
    }else{
      sendJavaRef(o);
    }
  }

}
