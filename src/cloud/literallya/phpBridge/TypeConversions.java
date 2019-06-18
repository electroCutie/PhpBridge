package cloud.literallya.phpBridge;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UnknownFormatConversionException;
import java.util.regex.Pattern;

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

class TypeConversions{

  private TypeConversions(){
    assert false : "static only";
  }

  public static Number asNumber(Object o){
    if(o instanceof String)
      return new BigDecimal((String) o);
    else if(o instanceof Number)
      return (Number) o;

    throw new UnknownFormatConversionException("Values is neither a numbe rnor a string");
  }

  public static long aslong(Object o){
    if(o instanceof String){
      return Long.valueOf((String) o);
    }else if(o instanceof Number){
      return ((Number) o).longValue();
    }

    throw new UnknownFormatConversionException("Value is neither a number nor a string");
  }

  public static Long asLong(Object o){
    if(o instanceof String){
      return Long.valueOf((String) o);
    }else if(o instanceof Number){
      return Long.valueOf(((Number) o).longValue());
    }

    throw new UnknownFormatConversionException("Value is neither a number nor a string");
  }

  public static int asint(Object o){
    if(o instanceof String){
      return Integer.valueOf((String) o);
    }else if(o instanceof Number){
      return ((Number) o).intValue();
    }

    throw new UnknownFormatConversionException("Value is neither a number nor a string");
  }

  public static Integer asInt(Object o){
    if(o instanceof String){
      return Integer.valueOf((String) o);
    }else if(o instanceof Number){
      return ((Number) o).intValue();
    }

    throw new UnknownFormatConversionException("Value is neither a number nor a string");
  }

  public static Double asDouble(Object o){
    if(o instanceof String){
      return Double.valueOf((String) o);
    }else if(o instanceof Number){
      return ((Number) o).doubleValue();
    }

    throw new UnknownFormatConversionException("Value is neither a number nor a string");
  }

  public static double asdouble(Object o){
    if(o instanceof String){
      return Double.parseDouble((String) o);
    }else if(o instanceof Number){
      return ((Number) o).doubleValue();
    }

    throw new UnknownFormatConversionException("Value is neither a number nor a string");
  }

  private static final Pattern truePattern = Pattern.compile("^(t|y|T|Y)");
  private static final Pattern falsePattern = Pattern.compile("^(f|n|F|N|$)");

  public static Boolean asBoolean(Object o){
    if(null == o)
      return null;
    if(o instanceof Boolean)
      return((Boolean) o);
    if(o instanceof Number)
      return Boolean.valueOf(((Number) o).doubleValue() != 0);
    if(o instanceof String){
      if(truePattern.matcher((String) o).find())
        return Boolean.TRUE;
      else if(falsePattern.matcher((String) o).find())
        return Boolean.FALSE;
      throw new UnknownFormatConversionException("String cannot be parsed into a bool, \"" + o + "\"");
    }
    throw new UnknownFormatConversionException("Type not convertable to a boolean: " + o.getClass().getCanonicalName());
  }

  public static boolean asboolean(Object o){
    if(null == o)
      return false;
    if(o instanceof Boolean)
      return ((Boolean) o).booleanValue();
    if(o instanceof Number)
      return ((Number) o).doubleValue() != 0;
    if(o instanceof String){
      if(truePattern.matcher((String) o).find())
        return true;
      else if(falsePattern.matcher((String) o).find())
        return false;
      throw new UnknownFormatConversionException("String cannot be parsed into a bool, \"" + o + "\"");
    }
    throw new UnknownFormatConversionException("Type not convertable to a boolean: " + o.getClass().getCanonicalName());
  }

  public static String asString(Object o){
    return (String) o;
  }

  public static BigDecimal asBigDecimal(Object o){
    if(o instanceof String)
      return new BigDecimal((String) o);
    if(o instanceof Number)
      return new BigDecimal(((Number) o).doubleValue());
    throw new UnknownFormatConversionException("Type not convertable to a BigDecimal: " + o.getClass().getCanonicalName());
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> asMap(Object o){
    return (Map<String, Object>) o;
  }

  @SuppressWarnings("unchecked")
  public static List<Object> asList(Object o){
    return (List<Object>) o;
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> asListOfMaps(Object o){
    return (List<Map<String, Object>>) o;
  }

  @SuppressWarnings("unchecked")
  public static List<String> asListOfStrings(Object o){
    return (List<String>) o;
  }

  /**
   * Takes and enum class and the name of one of the members (must be castable to a string but accepts object) and returns the
   * member of the enum with that name.
   * 
   * @return the member of the enum with the given name
   * @see Enum#valueOf(Class, String)
   */
  public static <E extends Enum<E>> E asEnum(Class<E> enumClass, Object name){
    return Enum.valueOf(enumClass, asString(name));
  }

}
