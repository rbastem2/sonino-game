#!/bin/sh
if [ -n "$JAVA_HOME" ] ; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi
exec "$JAVACMD" -jar gradle/wrapper/gradle-wrapper.jar "$@"
