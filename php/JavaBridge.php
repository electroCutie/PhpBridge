<?php

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

require_once("Ieee754.php");

// UTF-8 server side
mb_language('uni');
mb_internal_encoding('UTF-8');

/* Protocol Constants generated from ProtocolConstants.java */
define('bridge_DEBUG', 0x00);
define('bridge_GET', 0x80);
define('bridge_ARRAY_GET', 0x87);
define('bridge_SET', 0x81);
define('bridge_ARRAY_SET', 0x88);
define('bridge_INVOKE', 0x84);
define('bridge_POP', 0x85);
define('bridge_DESTROY', 0x86);
define('bridge_ACKEXCEPTION', 0x89);
define('bridge_Z', 0xc0);
define('bridge_J', 0xc1);
define('bridge_D', 0xc2);
define('bridge_L', 0xc3);
define('bridge_A', 0xc4);
define('bridge_S', 0xc5);
define('bridge_V', 0xc6);
define('bridge_EXCEPTION', 0xc7);
define('bridge_MAP', 0xc8);
define('bridge_MAP_END', 0xc9);
define('bridge_REF', 0xca);
define('bridge_CLOSE', 0x40);
define('bridge_ITERABLE_TYPE', 0x01);
define('bridge_INDEXED_TYPE', 0x03);
define('bridge_MAP_TYPE', 0x05);
define('bridge_ARRAY_TYPE', 0x08);

/* Http Connector */

class ChunkedHttp{
  private $sock;
  private $buffer = "";
  private $remaining = -2;
  public $isClosed = false;
  private $closeHookCtr = 0;
  
  private function writeWhole($data){
    $written = 0;
    do{
      $data = substr($data, $written );
      $written = fwrite($this->sock, $data) or throwErr("failed writing data");
    }while(strlen($data) > $written);
  }

  private function connect($args){
    $errno = 0; $errStr = "";
    $this->sock = fsockopen($args["host"], $args["port"]) or throwErr("could not open socket");
    stream_set_timeout($this->sock, 600);

    $this->writeWhole(
        "PUT " . $args["servlet"] . " HTTP/1.1\r\n"
        . "Host: " . $args["host"] . ":" . $args["port"] . "\r\n"
        . "Transfer-Encoding: chunked\r\n"
        ."\r\n"); //tickle the server with some initial data
    fflush($this->sock) or throwErr("Flush Failed");
  }

  public function __construct($args){
    !is_null($args) or throwErr("No bridge arguments!");
    $this->connect($args);

    //Now read back their headers
    $r = 0;
    do{
      $char = fread($this->sock, 1);
      if("" == $char) throwErr("empty read");
      //       print($char);
      $r = ($r << 8) | ord($char);
      $r &= (1 << 32) - 1;
    }while($r != 0x0d0a0d0a);
//    printf("Begin Body...\n");
  }

  private function nextChunk(){
    (!$this->isClosed) or throwErr("Connection Closed");
    $r = 0;
    $str = "";
    //     print("Next Chunk...\n");

    if($this->remaining == 0) //Not the first chunk
    // consume and discard the "\r\n" at the end of a chunk
    (("" != fread($this->sock, 1)) && ("" != fread($this->sock, 1))) or throwErr("EOF after chunk");
    do{
      $char = fread($this->sock, 1);
      if("" === $char) throwErr("EOF in chunk header");
      $str .= $char;
      $char = ord($char);
      $r = ($r << 8) | $char;
      $r &= 0xffff;
    }while($r != 0x0d0a);
    $str = substr($str, 0, strlen($str)-2);
    if("" == $str) throwErr("End of Chunked Data");
    $this->remaining = hexdec($str);
    //     printf("Got Chunk Size: %d\n", $this->remaining);
    if(0 == $this->remaining) throwErr("unexpected end chunk");
  }

  public function read($len){
    (!$this->isClosed) or throwErr("Connection Closed");
    $str = "";
    while(strlen($str) < $len){
      if($this->remaining <= 0) $this->nextChunk();
      $char = fread($this->sock, 1);
      if(false === $char) throwErr("connection closed mid chunk");
      if("" === $char) throwErr("unexpected empty read");
      $str .= $char;
      $this->remaining--;
    }
    return $str;
  }
  
  function flush(){
    $b = $this->buffer;
    $len = strlen($b);
    if(0 == $len) return;
    $this->buffer = "";
    $this->writeWhole( dechex($len) . "\r\n$b\r\n");
    fflush($this->sock) or throwErr("flush failed");
  }

  function write($chunk){
    (!$this->isClosed) or throwErr("Connection Closed");
    $this->buffer .= $chunk;
  }
  
  function addCloseHook(){
  	$this->closeHookCtr++;
  }
  
  function closeImpl(){
  	if($this->isClosed) return;
  	$this->write(chr(bridge_CLOSE));
  	$this->write("0\r\n\r\n");
  	$this->flush();
  	$this->isClosed = true;
  	fread($this->sock, 1); //Far side will send it's close byte
  	fclose($this->sock);
  }
  
  function __destruct(){
  	$this->closeImpl();
  	//if($this->closeHookCtr > 0) throwErr("Not all close hooks ran!");
  }
  
  function close(){
    if((--$this->closeHookCtr) > 0) return; //Still outstanding close hooks
    $this->closeImpl();
  }
}

/**
 * Basis for all the other java objects for easy detections
 */
class JavaBase{}

function getJavaConnection(){
	global $JAVA_CONNECTION;
	if(null != $JAVA_CONNECTION && !$JAVA_CONNECTION->isClosed()) return $JAVA_CONNECTION;
  global $JAVA_BRIDGE_ARGS;
	$JAVA_CONNECTION = new LowLevelConnection(new ChunkedHttp($JAVA_BRIDGE_ARGS));
	$JAVA_CONNECTION->handshake();
	return $JAVA_CONNECTION;
}

function throwErr($msg){
  throw new Exception($msg);
}

class LowLevelConnection{
  private $http;
  private $session;
  
  private $refNum = 1;
  private $mapDepth = 0;
  private $refs = array();
  
  private $javaRefCounts = array();
  
  public function __construct(ChunkedHttp $http){
    $this->http = $http;
    register_shutdown_function($this->getShutdownHook());
  }
  
  public function getShutdownHook(){
  	$http = $this->http;
  	$isDone = 0;
  	$this->http->addCloseHook();
  	return function() use ($http, $isDone){
  		if(0 == ($isDone++)) $http->close();
  	};
  }
  
  public function debug(){
    $this->http->write(chr(bridge_DEBUG));
    $this->http->flush();
  }
  
  private function closeDie(){
    $this->http->closeImpl();
    throwErr("Connection closed unexpectedly!");
  }
  
  function isClosed(){
  	return $this->http->isClosed;
  }
  
  public function addRef($id){
    if(isset($this->javaRefCounts[$id]))
      $this->javaRefCounts[$id]++;
    else
      $this->javaRefCounts[$id] = 1;
  }
  
  public function clearRef($id){
    if($this->http->isClosed) return; //Don't care at this point
    if(!isset($this->javaRefCounts[$id])){
      syslog(LOG_WARNING, sprintf("Undefined ref-ID: $id"));
      return;
      //throwErr(sprintf("Undefined ID: %d '%s'", $id, $this->javaRefCounts[$id]));
    }
    //Once the last ref here is cleared get rid of it on the Java side
    if((--$this->javaRefCounts[$id]) > 0) return;
    
    unset($this->javaRefCounts[$id]);
    $this->writeLongImpl(bridge_DESTROY, $id);
  }
  
  private function writePrimImpl($type, $val){
    $this->http->write(chr($type));
    $this->http->write($val);
    return $this;
  }
  
  function writeNull(){
    $this->http->write(chr(bridge_V));
    return $this;
  }
  function writeBool($val){
    return $this->writePrimImpl(bridge_Z, ((!!$val) ? "\x01" : "\x00"));}
  function writeLongImpl($type, $l){
    if(!is_int($l)) throw new Exception($l . " is not an integer type");
    return $this->writePrimImpl($type, pack("NN", ($l >> 32) & ((1 << 32) - 1), $l & ((1 << 32) - 1)));
  }
  function writeLong($l){ return  $this->writeLongImpl(bridge_J, $l); }
  function writeDouble($val){ return $this->writeLongImpl(bridge_D, float754Encode($val)); }
  
  function writeStringLiteral($str){
  	$str = mb_convert_encoding($str, "UTF-8");
    $this->http->write(pack("N", strlen($str)));
    $this->http->write($str);
    return $this;
  }
  
  function writeString($str){
    $this->http->write(chr(bridge_S));
    return $this->writeStringLiteral($str);
  }
  
  function writeClass($clazz){
    if(!is_string($clazz)) throw new Exception("Class name must be a string");
    if(strlen($clazz) == 0) throw new Exception("Class name must not be empty");
    $this->http->write(chr(bridge_L));
    return $this->writeStringLiteral($clazz);
  }

  private function getMapId($map){
    $len = count($this->refs);
    $i = 0;
    for(; $i < $len; $i++){
      $m = $this->refs[$i];
      if($m === $map) //Identity
        return $i;
    }
    
    $this->refs[$i] = $map;
    return (-$i) - 1;
  }
  
  private function writeMapImpl($map, $refnum){
    $this->mapDepth++;
    $this->writeLongImpl(bridge_MAP, $refnum);
    foreach($map as $key => $value)
      $this->writeValue($key)->writeValue($value);
    $this->http->write(chr(bridge_MAP_END));
    
    if(0 == (--$this->mapDepth))
      $this->refs = array(); // refs are only valid within the single map structure
  }
  
  function writeMap($map){
    if(!is_array($map)) throw new Exception("Object not a map reference");
    $id = $this->getMapId($map);
    
    if($id > -1) //Already seen this map
      return $this->writeLongImpl(bridge_REF, $id);
    return $this->writeMapImpl($map, -($id + 1));
  }
  
  function writeJavaRef(JavaObject $o){
    if(!($o instanceof JavaObject)) throwErr("Object not a java reference");
    return $this->writeLongImpl(bridge_A, $o->__id);
  }
  
  function writeInvoke(){
    $this->http->write(chr(bridge_INVOKE));
    return $this;
  }
  
  function writeGet(){
    $this->http->write(chr(bridge_GET));
    return $this;
  }
  
  function writeArrayGet(){
    $this->http->write(chr(bridge_ARRAY_GET));
    return $this;
  }
  
  function writeArraySet(){
    $this->http->write(chr(bridge_ARRAY_SET));
    return $this;
  }
  
  function writeSet(){
    $this->http->write(chr(bridge_SET));
    return $this;
  }
  
  function writePop(){
    $this->http->write(chr(bridge_POP));
    return $this;
  }
  
  function ackException(){
  	$this->http->write(chr(bridge_ACKEXCEPTION));
  	return this;
  }
  
  function writeValue($v){
    switch(gettype($v)){
      case "boolean": return $this->writeBool(true === $v);
      case "integer": return $this->writeLong($v);
      case "double": return $this->writeDouble($v);
      case "string": return $this->writeString($v);
      case "array": return $this->writeMap($v);
      case "NULL": return $this->writeNull();
      
      case "object":
        if($v instanceof JavaObject) return $this->writeJavaRef($v);
      case "resource":
      case "unknown type":
      default:
        throw new Exception("Cannot encode general objects or resources");
    }
  }
  
  /*
   * Recv
   */
  
  private function recvInt(){
    return unpack("N", $this->http->read(4))[1] & 0xffffffff;
  }
  
  private function recvLong(){
    $a = $this->recvInt();
    $b = $this->recvInt();
    return ($a << 32) | $b;
  }
  
  private function recvStringLiteral(){
    $length = $this->recvInt();
    return $this->http->read($length);
  }
  
  private function recvString(){
    $id = $this->recvLong();
    $literal = $this->recvStringLiteral();
    return new JavaString($id, $literal);
  }
  
  private function recvJavaRef(){
    $flags = $this->recvInt();
    $id = $this->recvLong();
    switch($flags){
      case 0: //Vanilla
        return new JavaObject($id);
      case bridge_ITERABLE_TYPE:
        return new JavaIterable($id);
      case bridge_INDEXED_TYPE:
        return new JavaList($id);
      case bridge_MAP_TYPE:
        return new JavaMap($id);
      case bridge_ARRAY_TYPE:
        return new JavaArray($id);
      default:
        throw new Exception("Unknown type: " . $flags);
    }
  }
  
  private function recv(){
    $this->http->flush(); //Ensure that all out requests are flushed before trying to read
    $type = ord($this->http->read(1));
    switch($type){
      case bridge_V:
        return null;
      case bridge_Z:
        return 1 == ord($this->http->read(1));
      case bridge_D:
        return float754Decode($this->recvLong());
      case bridge_J:
        return $this->recvLong();
      case bridge_S:
        return $this->recvString();
      case bridge_A:
        return $this->recvJavaRef();
      case bridge_EXCEPTION:
      	$this->ackException();
        throw new Exception($this->recvStringLiteral());
      default:
      throw new Exception(sprintf("Unsupported Operation 0x%02x", $type));
    }
  }
  
  /*
   * Compund functions
   */
  
  public function get($o, $key){
    return $this
      ->writeString($key)
      ->writeJavaRef($o)
      ->writeGet() //places the value on the stack
      ->writePop() //Will cause Java to write the value out to us
      ->recv();
  }
  
  public function set($o, $key, $value){
    $this
      ->writeValue($value)
      ->writeString($key)
      ->writeJavaRef($o)
      ->writeSet();
  }
  
  public function invoke($o, $method, $args){
    foreach($args as $v)
      $this->writeValue($v);
    return $this
      ->writeString($method)
      ->writeJavaRef($o)
      ->writeInvoke()
      ->writePop()
      ->recv(); //even if the invocation returns void Java will still give us back a null, since we can't know that in general
  }
  
  public function getClazz($clazz){
    return $this->writeClass($clazz)
      ->writePop()
      ->recv();
  }
  
  public function getArrayIndex($arr, $idx){
    return $this
      ->writeLong($idx)
      ->writeValue($arr)
      ->writeArrayGet()
      ->writePop()
      ->recv();
  }
  
  public function setArrayIndex($arr, $idx, $v){
    $this
      ->writeValue($v)
      ->writeLong($idx)
      ->writeValue($arr)
      ->writeArraySet();
  }
  
  public function handshake(){
    //Other side sends a little magic to get the connection flowing
    $this->session = $this->recv();
  }
}


/* Protocol Section */

class JavaObject extends JavaBase{
  public $__id;
  public $__conn;
  
  public function  __construct($id){
    is_int($id) or throwErr("Invalid reference id");
    $this->__id = $id;
    $this->__conn = getJavaConnection();
    $this->__conn->addRef($id);
  }
  
  public function __destruct(){
    $this->__conn->clearRef($this->__id);
  }
  
  public function __get($key){
    return $this->__conn->get($this, $key);
  }
  
  public function __set($key, $value){
    $this->__conn->set($this, $key, $value);
  }
  
  public function __call($method, $args){
    return $this->__conn->invoke($this, $method, $args);
  }
  
  public function __toString(){
    return $this->__call("toString", []);
  }
}

class JavaString extends JavaObject{
  private $stringLiteral;
  
  public function __construct($id, $literal){
    parent::__construct($id);
    $this->stringLiteral = $literal;
  }
  
  public function __toString(){
    return mb_convert_encoding ( $this->stringLiteral, mb_internal_encoding(), "UTF-8");
  }
}

class Marker{}

/**
 * General purpose Iterator
 * Because of rewind it is actually a wrapper around Iterable and it's iterators
 */


/**
 * Specialized iterator for lists (and arrays) where the key is a monatonic integer from zero to size - 1
 */
class JavaIndexedIterator extends JavaBase implements Iterator{
  protected $current = null;
  protected $index = -1;

  protected $iterable;
  protected $backingIterator;

  function __construct($iterable){ //Maps must pass in their entry sets, not themselves
    $this->iterable = $iterable;
    $this->backingIterator = $iterable->iterator();

    // PHP does the reverse of Java, it advances THEN checks if that is a valid position
    // So tee-up the first value or get the end position constant set
    $this->next();
  }

  public function current(){
    $e = $this->current;
    return (($e instanceof Marker) ? null : $e);
  }

  public function key(){
    return $this->index;
  }

  public function next(){
    if($this->backingIterator->hasNext())
      $this->current = $this->backingIterator->next();
    else
      $this->current = new Marker();
    $this->index++;
  }

  public function rewind(){
    $this->backingIterator = $this->iterable->iterator();
    $this->next();
  }

  public function valid(){
    return !($this->current instanceof Marker);
  }
}

class JavaIterable extends JavaObject implements IteratorAggregate{
  public function getIterator(){
    return new JavaIndexedIterator($this);
  }
}

class JavaList extends JavaIterable implements ArrayAccess{
  public function offsetExists($key){
    return is_int($key) && $key > -1 && $this->size() < $key;
  }
  
  public function offsetGet($offset){
    return $this->get($offset);
  }
  
  public function offsetSet($offset, $value){
    //TODO: write a utility to ensure capacity of the list before setting
    if($offset > $this->size()) throwErr("Need ensure capacity!");
    $this->set($offset, $value);
  }
  
  public function offsetUnset($offset){
    if($offset > $this->size()) return; //Can't be in the list
    //TODO: The semantics of unset aren't clear. This shouldn't SHIFT the list... should it?
    return $this->set($offset, null);
  }
}

class JavaArrayIterator implements Iterator{
  protected $index = 0;
  protected $arr;

  function __construct($arr){ //Maps must pass in their entry sets, not themselves
    $this->arr = $arr;
  }

  public function current(){
    return $this->arr->offsetGet($this->index);
  }

  public function key(){
    return $this->index;
  }

  public function next(){
    $this->index++;
  }

  public function rewind(){
    $this->index = 0;
  }

  public function valid(){
    return $this->index < $this->arr->length;
  }
}

class JavaArray extends JavaIterable implements ArrayAccess{
  public function getIterator(){
    return new JavaArrayIterator($this);
  }
  
  public function offsetExists($key){
    return is_int($key) && $key > -1 && $this->length < $key;
  }

  public function offsetGet($idx){
    return $this->__conn->getArrayIndex($this, $idx);
  }

  public function offsetSet($idx, $value){
    $this->__conn->setArrayIndex($this, $idx, $value);
  }

  public function offsetUnset($offset){
    $this->__conn->setArrayIndex($this, $idx, null);
  }
}

class JavaMapIterator extends JavaIndexedIterator implements Iterator{
  public function current(){
    $e = $this->current;
    return (($e instanceof Marker) ? null : $e->getValue());
  }

  public function key(){
    $e = $this->current;
    return (($e instanceof Marker) ? null : $e->getKey());
  }

  public function rewind(){
    $this->backingIterator = $this->iterable->iterator();
    $this->next();
  }

  public function valid(){
    return !($this->current instanceof Marker);
  }
}

class JavaMap extends JavaIterable implements ArrayAccess{
  public function getIterator(){
    return new JavaMapIterator($this->entrySet());
  }
  
  public function offsetExists($key){
    return $this->containsKey($key);
  }
  
  public function offsetGet($key){
    return $this->get($key);
  }
  
  public function offsetSet($key, $value){
    return $this->put($key, $value);
  }
  
  public function offsetUnset($key){
    return $this->remove($key);
  }
  
  public function __unwrap(){
    $arr = array();
    foreach($this as $k => $v){
      if($k instanceof JavaString)
        $arr[$k->__toString()] = $v;
      else
        $arr[$k] = $v;
    }
    return $arr;
  }
}

/*
 * Bridge functions
 */

function java($clazz){
  return getJavaConnection()->getClazz($clazz);
}

