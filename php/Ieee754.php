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

function float754Decode($binaryData) {
  // binaryBlob is a binary string of IEEE 754 double-precision binary floating-point
  $mantissa = $binaryData & ((1 << 52) - 1);
  $exp = ($binaryData >> 52) & ((1 << 11) - 1);
  $mag = pow( 2, $exp - (1023 + 52));
  $sign = (($binaryData >> 63) & 1 ) ? -1.0 : 1.0;
  
  
  if (0 == $exp) {
    if (0 == $mantissa) {
      if (1 == $sign)
        return - 0;
      else
        return 0;
    }else{
      // subnormal number
      return 1.0 * ($mantissa) * pow ( 2, -1023 ) * pow(2,-51) * $sign;
    }
  }
  
  if (2047 == $exp) {
    if (0 == $mantissa)
      return $sign * INF;
    return NAN;
  }
    
  $offsetMaintissa = 1.0 * ((1 << 52) | $mantissa);
  return 1.0 * $offsetMaintissa * $mag * $sign;
}
 
define('MIN_INT_MANTISSA', (float) (1 << 52));
define('MAX_INT_MANTISSA', (float) (1 << 53));
define('MIN_NORMAL_NUMBER', pow(2.0, -1022));

function float754Encode($n) {
  $original = $n;
  $sign = 0;
  $exp = 1023 + 52;
  $binaryData = 0;
  
  $sign = ($n < 0.0) ? 1 : 0;
  $n = abs($n);
  
  if(is_infinite($n))
    return 0x7ff0000000000000 | ($sign << 63);
  if(is_nan($n))
    return 0x7fffffffffffffff;
  if(0.0 == $n)
    return 0x7ff0000000000000 | ($sign << 63);
  
  if( $n < MIN_NORMAL_NUMBER){
  	$exp = 0;
  	//scaling must be done in two steps to avoid overflow to infinity
    $n = $n * pow(2.0, 1022.0);
    $n = $n * pow(2.0, 52.0);
  }else{
    while($n < MIN_INT_MANTISSA){
      $n = $n * 2.0;
      $exp--;
    }
    while($n >= MAX_INT_MANTISSA){
      $n = $n / 2.0;
      $exp++;
    }
  }
  
  $n = (int) $n;
  $n = ((1 << 52)-1) & $n; //mask upper 12 bits
  ($exp == (((1 << 11) - 1) & $exp)) or die("exponent overflow, should be impossible");
  
  $binaryData =  $sign << 63 | ($exp << 52)  | $n;
  
  return $binaryData;
}
 
