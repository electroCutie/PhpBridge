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

// This is the global configuration variable for the bridge
// It is only used once and so can't be changed after the connection is made (when the file is included, as below)
global $JAVA_BRIDGE_ARGS;
$JAVA_BRIDGE_ARGS = array(
		 // The host that the Java server is running on
		"host" => "127.0.0.1",
		 // The port that the Java server is listening on
		"port" => 8080,
		 // The path to the bridge's servlet
		"servlet" => "/backend/PhpBridge"
);

// Only include the JavaBridge file AFTER the config is set
require_once("JavaBridge.php");
