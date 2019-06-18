package cloud.literallya.phpBridge;

import java.util.Map;
import java.util.Set;

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

public interface LongMap<V> extends Map<Long, V>{
  public V get(long key);

  public V put(long key, V value);

  public void putAll(LongMap<? extends V> m);

  public V remove(long key);

  public boolean containsKey(long key);

  public Set<Entry<V>> longEntrySet();

  public LongSet longKeySet();

  public static interface Entry<V> extends Map.Entry<Long, V>{
    public long longKey();
  }

}
