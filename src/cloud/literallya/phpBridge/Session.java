package cloud.literallya.phpBridge;

import static cloud.literallya.phpBridge.ClassWrapper.unwrap;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_ACKEXCEPTION;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_ARRAY_GET;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_ARRAY_SET;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_CLOSE;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_DESTROY;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_GET;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_INVOKE;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_POP;
import static cloud.literallya.phpBridge.ProtocolConstants.bridge_SET;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.BiMap;
import com.google.common.collect.Maps;
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

class Session{

  private final BiMap<Object, Long> javaRefs = new CompositeBiMap<>(Maps.newIdentityHashMap(), Maps.newHashMap());
  private final ValueDecoder decoder;
  private final ValueWriter encoder;

  private final Dispatch dispatch;
  private final FieldGetterSetter attrs;

  // I/O
  private final InputStream in;
  private final OutputStream out;

  // State
  private final LinkedList<Object> valueStack = new LinkedList<>();
  private boolean awaitingExceptionAck = false;

  public Session(InputStream in, OutputStream out, Dispatch dispatch, FieldGetterSetter attrs){
    this.in = checkNotNull(in);
    this.out = checkNotNull(out);

    this.dispatch = dispatch;
    this.attrs = attrs;

    decoder = new ValueDecoder(in, javaRefs.inverse());
    encoder = new ValueWriter(out, javaRefs);
  }

  private int read(){
    try{
      int c = in.read();
      checkState(c > -1, "Input Closed Unexpectedly");
      return c;
    }catch(IOException e){
      throw new RuntimeException(e);
    }
  }

  private List<Object> popWholeStack(){
    List<Object> pop = new ArrayList<>(valueStack);
    valueStack.clear();
    pop.replaceAll(ClassWrapper::unwrap);
    return pop;
  }

  private void act(int action){
    switch(action){
      case bridge_GET:
        valueStack.push(attrs.get(valueStack.removeLast(), (String) valueStack.removeLast()));
        break;
      case bridge_SET:
        attrs.set(valueStack.removeLast(), (String) valueStack.removeLast(), unwrap(valueStack.removeLast()));
        break;

      case bridge_ARRAY_GET:
        valueStack.push(Array.get(valueStack.removeLast(), Ints.checkedCast((Long) valueStack.removeLast())));
        break;
      case bridge_ARRAY_SET:
        Array.set(valueStack.removeLast(), Ints.checkedCast((Long) valueStack.removeLast()),
          unwrap(valueStack.removeLast()));
        break;

      case bridge_INVOKE:
        valueStack.add(dispatch.invoke(valueStack.removeLast(), (String) valueStack.removeLast(), popWholeStack()));
        break;
      case bridge_POP:
        encoder.sendValue(valueStack.removeLast());
        break;

      case bridge_ACKEXCEPTION:
        awaitingExceptionAck = false;
        break;
      case bridge_DESTROY:
        javaRefs.inverse().remove(decoder.readLong());
        break;

      default:
        throw new IllegalStateException(String.format("Unknown verb: 0x%02x", action));
    }
  }

  public void handleConnection() throws IOException{
    do{
      try{
        out.flush(); // After every action flush the output to keep things moving
        final int c = read();
        if(ProtocolConstants.isNoun(c)){
          valueStack.add(decoder.readValue(c));
        }else if(ProtocolConstants.isVerb(c)){
          act(c);
        }else if(c == ProtocolConstants.bridge_DEBUG){
          assert 2 == Math.pow(2, 1);
        }else if(c == bridge_CLOSE){
          break;
        }else{
          throw new Error(String.format("Unknown Action: 0x%02x", c));
        }
      }catch(Exception e){
        if(awaitingExceptionAck)
          continue; // don't spew exceptions when we are waiting for acknowledgement of an exception
        awaitingExceptionAck = true;
        valueStack.clear();
        encoder.sendException(e);
      }
    }while(true);
    out.write(bridge_CLOSE);
  }
}
