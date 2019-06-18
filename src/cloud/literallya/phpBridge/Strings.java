package cloud.literallya.phpBridge;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

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

public class Strings{

  /**
   * Returns a function that will return the given string if it is not {@code null} and also not {@link String#isEmpty() empty}
   * or the alternative string from the supplier otherwise
   * 
   * @param alt
   *          String to return when the given string is empty
   * @return A function to guarantee the non-emptiness of a string by making a substitution
   */
  public static Function<String, String> notEmptyOr(Supplier<String> alt){
    return s -> null != s && !s.isEmpty() ? s : checkNotNull(alt.get());
  }

  /**
   * Returns a function that will return the given string if it is not {@code null} and also not {@link String#isEmpty() empty}
   * or the alternative string passed to this method
   * 
   * @param alt
   *          String to return when the given string is empty
   * @return A function to guarantee the non-emptiness of a string by making a substitution
   */
  public static Function<String, String> notEmptyOr(String alt){
    checkNotNull(alt);
    return notEmptyOr(() -> alt);
  }

  public static String notEmptyOr(String str, String alt){
    return null != str && !str.isEmpty() ? str : alt;
  }

  public static String notEmptyOr(String str, Supplier<String> alt){
    return null != str && !str.isEmpty() ? str : alt.get();
  }

  /**
   * Checks that the string is not null and not empty, throws and exception or returns the given string
   * 
   * @return the given non-null non-empty String
   * @throws NullPointerException
   *           if the string is null
   * @throws IllegalArgumentException
   *           if {@link String#isEmpty()}
   */
  public static String checkNotEmpty(String str){
    checkArgument(!checkNotNull(str).isEmpty());
    return str;
  }

  /**
   * Checks that the string is not null and not empty after being {@link String#trim() trimmed}, throws and exception or returns
   * the given string
   * 
   * @return the given non-null non-empty {@link String#trim() trimmed} String
   * @throws NullPointerException
   *           if the string is null
   * @throws IllegalArgumentException
   *           if {@link String#trim() str.trim()}.{@link String#isEmpty() isempty()}
   */
  public static String checkNotEmptyTrimmed(String str){
    checkArgument(!(str = checkNotNull(str).trim()).isEmpty());
    return str;
  }

  /**
   * Checks that the string is not null and not empty after being {@link String#trim() trimmed}
   * 
   * @return true if and only if the string is non null and not empty after trimming
   * 
   */
  public static boolean isNotEmptyTrimmed(String str){
    return null != str && !str.trim().isEmpty();
  }

  /**
   * Returns a hash that is not sensitive to the case of the characters in the string. No guarantees are made about the result
   * of this hash except that two strings which differ only in case will produce the same integer.
   * <br>
   * Specifically it should not be expected that this will produce the same hash as String's hash
   * 
   * @return a case insensitive hash of the given string or 0 if the String is null or empty
   */
  public static int caseInsensitiveHash(final String str){
    if(null == str)
      return 0;
    int h = 0;
    final int length = str.length();
    for(int idx = 0; idx < length;){
      final int cp = str.codePointAt(idx);
      idx += Character.charCount(cp);
      h = (37 * h) + Character.toLowerCase(cp);
    }
    return h;
  }

  @Deprecated
  public static Function<String, String> toLowerCase = new Function<String, String>(){
    @Override
    public String apply(String input){
      return input.toLowerCase();
    }
  };

  public static String trimCollapseWhitespace(String str){
    return str.trim().replaceAll("\\s", " ");
  }

  private static final LongMap<String> escapeSequences;
  static{
    escapeSequences = ImmutableLongMap.<String> builder()
      .put((long) '"', "&quot;")
      .put((long) '\'', "&apos;")
      .put((long) '&', "&amp;")
      .put((long) '<', "&lt;")
      .put((long) '>', "&gt;")
      .build();
  }

  public static String xmlEntities(CharSequence unescaped){
    StringBuilder sb = new StringBuilder(unescaped.length());
    final int len = unescaped.length();
    for(int idx = 0; idx < len;){
      final int cp = Character.codePointAt(unescaped, idx);
      idx += Character.charCount(cp);

      final String escaped = escapeSequences.get(cp);
      if(null != escaped)
        sb.append(escapeSequences.get(cp));
      else
        sb.appendCodePoint(cp);
    }
    return sb.toString();
  }

  private static final String suffixes[] = new String[] {
    "B", "KiB", "MiB", "GiB", "TiB", "EiB"
  };

  /**
   * Takes a number of bytes and converts it into a human readable string with 2 decimals of precision. e.g. 2048 -> 2.00 KiB
   * 
   * @param bytes
   *          raw byte count
   * @return Human readable String
   */
  public static String toHumanSize(long bytes){
    return toHumanSize(bytes, 2);
  }

  /**
   * Takes a number of bytes and converts it into a human readable string with the given precision. e.g. 2048, 3 -> 2.000 KiB
   * 
   * @param bytes
   *          raw byte count
   * @param precision
   *          number of decimal places to include
   * @return Human readable String
   */
  public static String toHumanSize(long bytes, int precision){
    Preconditions.checkArgument(precision > 0);
    double b = (double) bytes;
    int suffix;
    for(suffix = 0; suffix < suffixes.length - 1 && b > 1024; suffix++)
      b /= 1024.0;

    return String.format("%." + precision + "f\u202f%s", b, suffixes[suffix]);
  }

  private static final Joiner thinSpaceJoiner = Joiner.on("\u202f");

  /**
   * @param eta
   *          a time interval in seconds
   * @return [[[days] hours] minutes] seconds remaining in the format 01d 02h 03m 12s
   */
  public static String toHumanTime(long eta){
    // Want the addFirst method of the linked list
    final LinkedList<String> times = new LinkedList<>();
    do{
      // sec
      times.addFirst(String.format("%02ds", eta % 60));
      if(eta < 60)
        break;

      // min
      eta /= 60;
      times.addFirst(String.format("%02dm", eta % 60));
      if(eta < 60)
        break;

      // hours
      eta /= 60;
      times.addFirst(String.format("%02dh", eta % 24));
      if(eta < 24)
        break;

      eta /= 24;
      times.addFirst(String.format("%02dd", eta));
    }while(false);

    return thinSpaceJoiner.join(times);
  }

  /**
   * For debugging SOAP calls or XML glitches
   * 
   * @param doc
   *          some DOM
   * @return pretty printed string
   */
  public static String domToString(Document doc){
    try{
      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      // initialize StreamResult with File object to save to file
      StreamResult result = new StreamResult(new StringWriter());
      DOMSource source = new DOMSource(doc);
      transformer.transform(source, result);
      return result.getWriter().toString();
    }catch(TransformerException e){
      throw new RuntimeException(e);
    }
  }

  private static final int[] utfCodepointMaximums = {
    0x80, 0x800, 0x10000, 0x11000 // As of 2003 UTF-8 is restricted to a max codepoint of of 0x10ffff
    // 0x200000, 0x4000000, 0x80000000
  };

  private static int utfBytesForCodepoint(int cp){
    int idx = Arrays.binarySearch(utfCodepointMaximums, cp);
    return (idx < 0 ? -(idx + 1) : idx) + 1;
  }

  public static int utf8Length(CharSequence sequence){
    return sequence.codePoints().parallel()
      .map(Strings::utfBytesForCodepoint)
      .reduce(0, (a, b) -> a + b);
  }

  public static IntConsumer utf8CodepointWriter(IntConsumer byteWriter){
    return cp -> {
      checkArgument(cp < 0x11000);
      checkArgument(cp > -1);

      if(cp < 0x80){
        byteWriter.accept(cp & 0x7F);
      }else if(cp < 0x0800){
        byteWriter.accept(((cp >>> 6) & 0x1F) | 0xC0);
        byteWriter.accept((cp & 0x3F) | 0x80);
      }else if(cp < 0x010000){
        byteWriter.accept(((cp >>> 12) & 0x0F) | 0xE0);
        byteWriter.accept(((cp >>> 6) & 0x3F) | 0x80);
        byteWriter.accept(((cp >>> 0) & 0x3F) | 0x80);
      }else if(cp < 0x110000){
        byteWriter.accept(((cp >>> 18) & 0x07) | 0xF0);
        byteWriter.accept(((cp >>> 12) & 0x3F) | 0x80);
        byteWriter.accept(((cp >>> 6) & 0x3F) | 0x80);
        byteWriter.accept((cp & 0x3F) | 0x80);
      }
    };
  }

  public static boolean containsAlphaNum(String s){
    return s.codePoints().anyMatch(c -> Character.isDigit(c) || Character.isAlphabetic(c));
  }

  public static boolean notContainsAlphaNum(String s){
    return !containsAlphaNum(s);
  }
}
