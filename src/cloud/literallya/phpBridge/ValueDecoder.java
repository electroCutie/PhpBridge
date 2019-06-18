package cloud.literallya.phpBridge;

import static cloud.literallya.phpBridge.ProtocolConstants.bridge_A;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_D;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_J;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_L;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_MAP;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_REF;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_S;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_V;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_Z;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;

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
class ValueDecoder{

  private final InputStream input;
  private final Map<Long, Object> idToObject;
  private final LongMap<Map<Object, Object>> phpRefs = new HashingLongMap<>();

  ValueDecoder(InputStream i, Map<Long, Object> id){
    input = i;
    idToObject = id;
  }

  void resetPhpRefs(){
    phpRefs.clear();
  }

  private int read(){
    try{
      int r = input.read();
      checkState(r > -1);
      assert r == (r & 0xff);
      return r;
    }catch(IOException e){
      throw new IllegalStateException(e);
    }
  }

  private boolean readBoolean(){
    return 0 != read();
  }

  long readLong(){
    long l = 0;
    for(int i = 0; i < 8; i++)
      l = (l << 8) | read(); // Big Endian
    return l;
  }

  int readInt(){
    int v = 0;
    for(int i = 3; i >= 0; i--)
      v |= read() << (i << 3); // Big Endian
    return v;
  }

  private double readDouble(){
    return Double.longBitsToDouble(readLong());
  }

  private Object readObjectRef(){
    return checkNotNull(idToObject.get(readLong()));
  }

  private String readString(){
    final int len = readInt();
    checkArgument(len >= 0);
    final byte[] raw = new byte[len];
    for(int i = 0; i < len; i++)
      raw[i] = (byte) read();
    return new String(raw, Charsets.UTF_8);
  }

  private ClassWrapper readClass(){
    try{
      return ClassWrapper.get(Class.forName(readString()));
    }catch(ClassNotFoundException e){
      throw new RuntimeException(e);
    }
  }

  private Map readPhpRef(){
    return checkNotNull(phpRefs.get(readLong()));
  }

  private int readMapDepth = 0;

  private Map readMap(){
    readMapDepth++;
    final Map<Object, Object> map = Maps.newHashMap();
    checkState(null == phpRefs.put(readLong(), map));

    do{
      final int type = read();
      if(type == ProtocolConstants.bridge_MAP_END)
        break;
      map.put(readValue(type), readValue(read()));
    }while(true);

    if(0 == (--readMapDepth))
      phpRefs.clear(); // Refs are only expected to be valid within a recursive map structure

    return map;
  }

  Object readValue(int typeCode){
    switch(typeCode){
      case bridge_Z:
        return readBoolean();
      case bridge_J:
        return readLong();
      case bridge_D:
        return readDouble();
      case bridge_L:
        return readClass();
      case bridge_A:
        return readObjectRef();
      case bridge_S:
        return readString();
      case bridge_V:
        return null;
      case bridge_MAP:
        return readMap();
      case bridge_REF:
        return readPhpRef();
      default:
        throw new IllegalStateException();
    }
  }

}
