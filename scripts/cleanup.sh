#!/bin/bash
echo "🧹 Cleaning up Gradle environment..."
./gradlew --stop
./gradlew clean
rm -rf .gradle/8.7/*
rm -rf .gradle/buildOutputCleanup
echo " Cleanup complete!"
