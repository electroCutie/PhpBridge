package cloud.literallya.phpBridge;

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

public class LongHashing{

  static final long TERM_FLAG = 1l << 0;
  static final long EMPTY_FLAG = 1l << 1;

  static final long PREV_MASK = (1l << 33) - 1 & ~3;
  static final int PREV_ROT = Long.numberOfTrailingZeros(PREV_MASK);
  static final long NEXTIDX_MASK = (-1l) & ~((1l << 33) - 1);
  static final int NEXTIDX_ROT = Long.numberOfTrailingZeros(NEXTIDX_MASK);

  static{
    assert 31 == Long.bitCount(PREV_MASK);
    assert 31 == Long.bitCount(NEXTIDX_MASK);

    assert Long.bitCount(TERM_FLAG)
      + Long.bitCount(EMPTY_FLAG)
      + Long.bitCount(PREV_MASK)
      + Long.bitCount(NEXTIDX_MASK)
      == Long.bitCount(
        TERM_FLAG
          ^ EMPTY_FLAG
          ^ PREV_MASK
          ^ NEXTIDX_MASK) : "Flags and masks should be orthagonal";
  }

  // murmur-3 constants
  static final int C1 = 0xcc9e2d51;
  static final int C2 = 0x1b873593;

  static int hash(long key){
    int a = (int) key, b = (int) (key >> 32);
    a = C2 * Integer.rotateLeft(a * C1, 15);
    b = C2 * Integer.rotateRight(b * C1, 15);
    return a ^ b;
  }

  static int decIdx(int idx, int idxMask){
    return (idx - 1) & idxMask;
  }

  static int incIdx(int idx, int idxMask){
    return (idx + 1) & idxMask;
  }

  static int idx(long key, int idxMask){
    return hash(key) & idxMask;
  }

  static long key(int idx, long index[]){
    return index[idx << 1];
  }

  static long meta(int idx, long index[]){
    return index[(idx << 1) + 1];
  }

  static void flag(int idx, long index[], long flag, boolean setOrClear){
    idx = (idx << 1) + 1;
    if(setOrClear)
      index[idx] &= ~flag;
    else
      index[idx] |= flag;
  }

  static void setValIdx(int idx, long index[], int valIdx){
    setPrevIdx(idx, index, valIdx);
  }

  static void setPrevIdx(int idx, long index[], int prevIdx){
    idx = (idx << 1) + 1;
    long l = prevIdx;
    l <<= PREV_ROT;
    l &= PREV_MASK;
    index[idx] = (index[idx] & ~PREV_MASK) | l;
  }

  static int prevIdx(int idx, long index[]){
    return (int) ((meta(idx, index) & PREV_MASK) >> PREV_ROT);
  }

  static void setNextIdx(int idx, long index[], int nextIdx){
    idx = (idx << 1) + 1;
    long l = nextIdx;
    l <<= NEXTIDX_ROT;
    l &= NEXTIDX_MASK;
    index[idx] = (index[idx] & ~NEXTIDX_MASK) | l;
  }

  static int nextIdx(int idx, long index[]){
    long meta = meta(idx, index);
    return (int) ((meta & NEXTIDX_MASK) >> NEXTIDX_ROT);
  }

  static void setKey(int idx, long index[], long key){
    index[(idx << 1)] = key;
  }

  static boolean isTerminal(int idx, long index[]){
    return 0 == (meta(idx, index) & TERM_FLAG);
  }

  static boolean isEmptySlot(int idx, long index[]){
    return 0 == (meta(idx, index) & EMPTY_FLAG);
  }

  // Open addressing needs a relatively small load factor to achieve good performance
  static final double MAX_LOAD_FACTOR = 0.7d;
  static final int MAX_TABLE_SIZE = Ints.MAX_POWER_OF_TWO;

  static int closedTableSize(int expectedEntries, double loadFactor){
    // Get the recommended table size.
    // Round down to the nearest power of 2.
    expectedEntries = Math.max(expectedEntries, 2);
    int tableSize = Integer.highestOneBit(expectedEntries);

    // then double the size until we are better than the max load factor
    while(expectedEntries > (int) (loadFactor * tableSize)){
      tableSize <<= 1;
      if(tableSize < 0)
        return MAX_TABLE_SIZE;
    }

    return tableSize;
  }

  static int getIdx(long key, long index[], int idxMask){
    int idx = idx(key, idxMask);
    boolean matchKey, isTerminal;
    do{
      matchKey = key(idx, index) == key;
      isTerminal = isTerminal(idx, index);
      if(matchKey || isTerminal)
        break;
      idx = incIdx(idx, idxMask);
    }while(true);

    if((!matchKey && isTerminal) || isEmptySlot(idx, index))
      return -1;
    return idx;
  }

}
