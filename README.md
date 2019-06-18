# PhpBridge
A library for calling Java code running on an app server from PHP that works today

Rquires Java 8 and PHP 5, tested with PHP 5.6.4

Also requires the [Google Guava] (https://github.com/google/guava) library

You can download a guava jar [here] (https://code.google.com/p/guava-libraries/)


Use examples can be found in [sampleUse.php] (php/sampleUse.php)

A configuration example is in [sampleConfig.php] (php/sampleConfig.php)

A sample servlet that the PHP side talks to is in [PhpBridge.java] (src/cloud/literallya/phpBridge/PhpBridge.java)

There is as yet no security built in, but it could be very simply added. For now a good firewall is reccomended.
