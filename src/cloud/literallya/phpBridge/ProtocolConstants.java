package cloud.literallya.phpBridge;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Suppliers;
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

class ProtocolConstants{

  private ProtocolConstants(){
  }

  public static final int bridge_DEBUG = 0x00, // Does nothing, but allows debugging

    /* Verbs */

    // direct attribute get
    // expects the stack to be [object, field name]. For static gets this will be the class object
    bridge_GET = 0x80,

    // Array index get
    // expects the stack to be [array, index]
    bridge_ARRAY_GET = 0x87,

    // direct atribute set
    // expects the stack to be [object, field name, value]. For static sets this will be the class object
    bridge_SET = 0x81,

    // Array index set
    // expects the stack to be [array, index, value]
    bridge_ARRAY_SET = 0x88,

    // pops all the arguments off the stack and invokes the method
    bridge_INVOKE = 0x84, // Expects the stack to be [object, method name, args...]
    // If the method is static then the object must be the class for the static method

    bridge_POP = 0x85, // Pops an object off the stack and returns it

    // forgets an object, followed by an 64 bit integer literal which is the object ID
    // PHP is expected to track liveness of the IDs, possibly using reference counting
    bridge_DESTROY = 0x86,

    // Acknowledge an exception and start the protocol flowing again
    bridge_ACKEXCEPTION = 0x89,

    /* Nouns */

    // Prims
    // https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html
    // Note that php only supports wide types
    bridge_Z = 0xc0, // boolean, followed by 8 bit literal (byte oriented protocol)
    bridge_J = 0xc1, // long, followed by 64 bit Big Endian literal
    bridge_D = 0xc2, // double, followed by 64 bit Big Endian literal

    bridge_L = 0xc3, // fully qualified class, this is followed by a String's literals, see below
    bridge_A = 0xc4, // reference to a Java Object, followed by:
    // Java -> PHP 32 bits of interface flags, PHP -> Java the flags are omitted
    // a 64 Big Endian bit literal ID

    // Special
    bridge_S = 0xc5, // A utf-8 String Object
    // This behaves differently depending on the sender
    // For Java -> PHP the wire format is as follows:
    // The symbol is followed by:
    // an object ref (see above)
    // then a 32 bit Big Endian literal for length
    // and the *unterminated* literal character data
    // For PHP -> Java it is instead:
    // The symbol is followed by a followed by:
    // then a 32 bit Big Endian literal for length
    // and the *unterminated* literal character data

    bridge_V = 0xc6, // Void aka null, not followed by any further information

    // An Exception from Java
    // Followed immediately by a String literal (32 bit big endian length and that man utf8 characters)
    bridge_EXCEPTION = 0xc7,

    // Complex
    bridge_MAP = 0xc8, // Not a Java array, a php associative array as key-val pairs
    // Followed by a 64 bit map ID that may be used to refer to this array and any number of key value pairs
    // Keys and values have their types indicated, keys will be Strings or longs
    // NOTE: in php uniqueness is determined by spl_object_hash

    // Indicates the end of a map's key-value pairs
    bridge_MAP_END = 0xc9,

    // A reference to a previous PHP array, followed by a 64 bit Big Endian literal
    // Note: Refs are only considered valid valid within a single recursive map structure
    bridge_REF = 0xca,

    bridge_CLOSE = 0x40; // Closes the connection gracefully, nothing is expected after this

  /*
   * Flags
   */

  public static final int
  // Interface flags for PHP
  bridge_ITERABLE_TYPE = 1 << 0,
    bridge_INDEXED_TYPE = (1 << 1) | bridge_ITERABLE_TYPE, // All indexed types MUST be iterable
    // Map is exclusive with indexed. Indexed is just another way of saying "mapped by integer"
    bridge_MAP_TYPE = (1 << 2) | bridge_ITERABLE_TYPE, // All map types MUST be iterable (over their entry set)
    bridge_ARRAY_TYPE = (1 << 3);

  public static boolean isProtocol(int c){
    return (c & 0xc0) == 0x40;
  }

  public static boolean isVerb(int c){
    return (c & 0xc0) == 0x80;
  }

  public static boolean isNoun(int c){
    return (c & 0xc0) == 0xc0;
  }

  private static Stream<Map.Entry<Long, String>> entryStream(){
    return Arrays.stream(ProtocolConstants.class.getFields())
      .sequential()
      .filter(f -> Modifier.isPublic(f.getModifiers()))
      .filter(f -> Modifier.isStatic(f.getModifiers()))
      .filter(f -> Modifier.isStatic(f.getModifiers()))
      .filter(f -> f.getType().equals(int.class))
      .map(f -> {
        try{
          return Maps.immutableEntry(Long.valueOf(f.getInt(null)), f.getName());
        }catch(Exception e){
          throw new RuntimeException(e);
        }
      });
  }

  private static void printPhpFile() throws Exception{
    for(Field f : ProtocolConstants.class.getFields()){
      int flags = f.getModifiers();
      if(!Modifier.isPublic(flags))
        continue;
      if(!Modifier.isStatic(flags))
        continue;
      if(!Modifier.isFinal(flags))
        continue;
      if(!f.getType().equals(int.class))
        continue;

      System.out.println(String.format("define('%s', 0x%02x);", f.getName(), f.getInt(null)));
    }
  }

  private static final Supplier<Map<Long, String>> constantToName = Suppliers.<Map<Long, String>> memoize(() -> {
    return entryStream()
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  })::get;

  public static String getName(int constant){
    return Optional.ofNullable(constantToName.get().get(constant))
      .orElse("UNKNOWN");
  }

  public static void main(String[] args){
    try{
      printPhpFile();
    }catch(Exception e){
      throw new RuntimeException(e);
    }
  }

}
