#!/bin/bash
echo "🧪 Running tests with coverage..."
./gradlew clean test jacocoTestReport
echo ""
echo "📊 Reports available at:"
echo "  - Test report: build/reports/tests/test/index.html"
echo "  - Coverage report: build/reports/jacoco/test/html/index.html"
