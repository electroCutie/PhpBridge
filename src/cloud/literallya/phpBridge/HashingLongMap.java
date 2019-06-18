package cloud.literallya.phpBridge;

import static cloud.literallya.phpBridge.LongHashing.TERM_FLAG;
import static cloud.literallya.phpBridge.LongHashing.flag;
import static cloud.literallya.phpBridge.LongHashing.getIdx;
import static cloud.literallya.phpBridge.LongHashing.idx;
import static cloud.literallya.phpBridge.LongHashing.incIdx;
import static cloud.literallya.phpBridge.LongHashing.isEmptySlot;
import static cloud.literallya.phpBridge.LongHashing.isTerminal;
import static cloud.literallya.phpBridge.LongHashing.key;
import static cloud.literallya.phpBridge.LongHashing.nextIdx;
import static cloud.literallya.phpBridge.LongHashing.prevIdx;
import static cloud.literallya.phpBridge.LongHashing.setKey;
import static cloud.literallya.phpBridge.LongHashing.setNextIdx;
import static cloud.literallya.phpBridge.LongHashing.setPrevIdx;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.base.Objects;
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

//TODO: make the iterators returned list iterators

@SuppressWarnings({ "unchecked", "rawtypes" })
public class HashingLongMap<V> extends AbstractLongMap<V>{

  protected long[] index;
  protected Object[] values;

  private int mutation = 0;
  private int terminalSlots;
  private int thresholdTerminal, thresholdSize;
  final private float loadFactor;

  private int size;
  protected int firstIdx = -1;
  protected int lastIdx = -1;
  private int idxMask;

  public HashingLongMap(){
    loadFactor = MAX_LOAD_FACTOR;
    makeTables(INITIAL_SIZE);
  }

  private void cloneOther(HashingLongMap b){
    int idx = b.firstIdx;
    int prev = -1;
    makeTables(b.values.length);
    lastIdx = firstIdx = findSlot(key(idx, b.index));

    do{
      final long key = key(idx, b.index);
      place(key, findSlot(key), (V) b.values[idx]);
      prev = idx;
      idx = nextIdx(idx, b.index);
    }while(prev != idx);
  }

  public HashingLongMap(LongMap<V> basis){
    loadFactor = MAX_LOAD_FACTOR;
    makeTables(closedTableSize(basis.size()));
    if(basis instanceof HashingLongMap)
      cloneOther((HashingLongMap) basis);
    else
      putAll(basis);

  }

  static final float MAX_LOAD_FACTOR = 0.7f;
  static final int INITIAL_SIZE = 8;
  static final int MAX_TABLE_SIZE = Ints.MAX_POWER_OF_TWO;

  int closedTableSize(int expectedEntries){
    // Get the recommended table size.
    // Round down to the nearest power of 2.
    expectedEntries = Math.max(expectedEntries, 2);
    int tableSize = Integer.highestOneBit(expectedEntries);

    // then double the size until we are better than the max load factor
    while(expectedEntries > (int) (loadFactor * tableSize)){
      tableSize <<= 1;
      // TODO: multi table version to handle massive tables
      if(tableSize < 0)
        throw new RuntimeException("Lacking huge table support");
    }

    return tableSize;
  }

  private void makeTables(final int tableSize){
    assert 1 == Integer.bitCount(tableSize);

    values = new Object[tableSize];
    index = new long[tableSize << 1];

    idxMask = tableSize - 1;
    terminalSlots = tableSize;
    thresholdSize = (int) (loadFactor * tableSize);
    thresholdTerminal = tableSize - thresholdSize;

    firstIdx = lastIdx = -1;
    size = 0;
  }

  private boolean rehashing = false;

  private void rehashImpl(final int targetSize, final Object oldV[], final long oldI[]){
    assert 0 != size;
    final int oldSize = size;

    int idx = firstIdx;
    int prev = -1;
    makeTables(closedTableSize(targetSize));
    lastIdx = firstIdx = findSlot(key(idx, oldI));

    do{
      final long key = key(idx, oldI);
      place(key, findSlot(key), (V) oldV[idx]);
      prev = idx;
      idx = nextIdx(idx, oldI);
    }while(prev != idx);

    assert oldSize == size;
  }

  private void rehash(final int targetSize){
    assert !rehashing;
    rehashing = true;
    rehashImpl(targetSize, values, index);
    rehashing = false;
  }

  private void rehashIfNeeded(){
    if(rehashing)
      return; // Don't recursively rehash, their hashCode must be BAD

    // REMINDER: re-hash based on non-terminal slots not on the size
    // Permit a very full table if it is mostly terminal slots
    if(size >= thresholdSize)
      rehash(size << 1); // grow the table
    else if(terminalSlots <= thresholdTerminal)
      rehash(size); // clean out garbage
  }

  @Override
  public V get(long key){
    final int idx = getIdx(key, index, idxMask);
    if(idx >= 0)
      return (V) values[idx];
    return null;
  }

  private int findSlot(final long key){
    int idx = idx(key, idxMask);
    // Loop till we find an empty slot...
    for(; !isEmptySlot(idx, index); idx = incIdx(idx, idxMask)){
      // ...Or till we find our own key
      if(key(idx, index) == key)
        return idx; // clobbering an old value

      // Clear terminal flags as we go
      if(isTerminal(idx, index))
        terminalSlots--; // once a slot is non-terminal it can only become terminal again by a re-hash
      flag(idx, index, TERM_FLAG, false);
    }

    return idx; // found an empty slot
  }

  private void loopFirst(){
    setPrevIdx(firstIdx, index, firstIdx);
  }

  private void loopLast(){
    setNextIdx(lastIdx, index, lastIdx);
  }

  private Object place(long key, int idx, V value){
    // Set the next index of the last entry to us
    setNextIdx(lastIdx, index, idx);
    // set our previous index to the former last
    setPrevIdx(idx, index, lastIdx);
    // make ourselves the last entry
    lastIdx = idx;
    loopLast();

    if(isEmptySlot(idx, index))
      size++; // taking an empty slot grows the map
    // mark the slot as taken
    flag(idx, index, LongHashing.EMPTY_FLAG, false);

    setKey(idx, index, key);
    final Object old = values[idx];
    values[idx] = value;

    rehashIfNeeded();

    return old;
  }

  private Object placeFirst(long key, int idx, V value){
    firstIdx = idx;
    lastIdx = idx;
    return place(key, idx, value);
  }

  private Object replace(long key, int idx, V value){
    Object o = values[idx];
    values[idx] = value;
    return o;
  }

  @Override
  public V put(long key, V value){
    mutation++;
    final int idx = findSlot(key);
    if(0 == size)
      return (V) placeFirst(key, idx, value); // First entry
    if(!isEmptySlot(idx, index))
      return (V) replace(key, idx, value); // Replace an entry
    return (V) place(key, idx, value); // place an entry in an empty slot
  }

  private Object removeImpl(final int idx){
    mutation++;
    size--;
    final Object old = values[idx];
    values[idx] = null;
    flag(idx, index, LongHashing.EMPTY_FLAG, true);

    final boolean isFirst = idx == firstIdx, isLast = idx == lastIdx;

    if(isFirst && isLast){
      // We are emptying the map
      assert 0 == size;
      lastIdx = firstIdx = -1;
      return old;
    }

    // Make the next's previous our previous and vice versa
    final int prevIdx = prevIdx(idx, index);
    final int nextIdx = nextIdx(idx, index);
    setNextIdx(prevIdx, index, nextIdx); // prev -> next
    setPrevIdx(nextIdx, index, prevIdx); // prev <- next

    if(idx == firstIdx){
      firstIdx = nextIdx;
      loopFirst();
    }else if(idx == lastIdx){
      lastIdx = prevIdx;
      loopLast();
    }

    return old;
  }

  @Override
  public V remove(long key){
    final int idx = getIdx(key, index, idxMask);
    if(idx < 0)
      return null;
    return (V) removeImpl(idx);
  }

  @Override
  public void clear(){
    mutation++;
    makeTables(8);
  }

  @Override
  public boolean containsKey(long key){
    int idx = getIdx(key, index, idxMask);
    return idx >= 0;
  }

  @Override
  public boolean containsValue(Object value){
    final int len = values.length;
    for(int i = 0; i < len; i++){
      if(!isEmptySlot(i, index) && Objects.equal(value, values[i]))
        return true;
    }
    return false;
  }

  //

  @Override
  public int size(){
    return size;
  }

  //

  abstract class AbsItr{
    private int prev = -1;
    private boolean isLast = size == 0;
    private int idx = firstIdx;
    private int expectedChange = mutation;

    protected void checkMod(){
      if(expectedChange != mutation)
        throw new ConcurrentModificationException();
    }

    protected void updateMod(){
      expectedChange = mutation;
    }

    public boolean hasNext(){
      checkMod();
      return !isLast;
    }

    protected int advanceIdx(){
      checkMod();

      if(isLast)
        throw new NoSuchElementException();

      prev = idx;
      idx = nextIdx(idx, index);

      isLast = idx == prev;

      return prev;
    }

    public void remove(){
      checkMod();
      if(prev < 0)
        throw new NoSuchElementException();
      removeImpl(prev);
      updateMod();
      prev = -1;
    }
  }

  protected class Entry extends AbstractLongEntry<V> implements LongMap.Entry<V>{
    private final int idx;
    private final AbsItr itr;

    Entry(int idx, AbsItr itr){
      this.idx = idx;
      this.itr = itr;
    }

    @Override
    public long longKey(){
      return key(idx, index);
    }

    @Override
    public V getValue(){
      return (V) values[idx];
    }

    @Override
    public V setValue(V value){
      itr.checkMod();
      mutation++;
      final Object o = values[idx];
      values[idx] = value;
      itr.updateMod();
      return (V) o;
    }

  }

  class EntryItr extends AbsItr implements Iterator<LongMap.Entry<V>>{
    @Override
    public LongMap.Entry<V> next(){
      return new Entry(advanceIdx(), this);
    }
  }

  @Override
  protected Iterator<LongMap.Entry<V>> entryIterator(){
    return new EntryItr();
  }

  class KeyItr extends AbsItr implements LongIterator{
    @Override
    public long nextLong(){
      return key(advanceIdx(), index);
    }
  }

  @Override
  protected LongIterator keyIterator(){
    return new KeyItr();
  }

}
