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

//Configures the bridge and connects
require_once("sampleConfig.php");

// First we need to get a hold of a "class wrapper", which is the starting point for all calls
// Use the class's fully qualified name to do so
$System = java("java.lang.System");

// You can then treat the object much like any PHP object
printf("The current time in millis since the epoch is %d\n",
  $System->currentTimeMillis());


// Making a new object uses the new keyword as though it were a method call

$someSet = java("java.util.LinkedHashSet")->new();

$someSet->add("Testing");
$someSet->add("Testing 123");
$someSet->add("Testing");

printf("The number of unique strings added was %d\n",
  $someSet->size());

$someSetCopy = java("java.util.LinkedHashSet")->new($someSet);

$someSetCopy->add("I'm new");

printf("The number of unique strings in the new set is %d\n",
  $someSetCopy->size());


//Iteration works like you might expect

print("The contents of the set are:\n");
foreach($someSetCopy as $str)
	print($str . "\n");

print("\n");

//Maps work too

$phpMap = array("a" => "A", "b" => "B", "c" => "C");

$javaMap = java("java.util.HashMap")->new();
$javaMap->putAll($phpMap);

print("The contents of the map are:\n");
foreach($javaMap as $key => $val)
	printf("%s => %s\n", $key, $val);
print("\n");

// No messy unwrapping primitive values, primitives are returned as their natural php values
// Such as this boolean

printf("Is the map empty? %s\n", ($javaMap->isEmpty() ? "yes" : "no" ));


// What if you want to get the Class *object* from something?
// use the class field of the wrapper

printf("The simplename for String is %s\n",
  java("java.lang.String")->class->getSimpleName());

print("\n");

// Happy coding!
