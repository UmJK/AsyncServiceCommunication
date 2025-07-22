#!/bin/bash
echo "NUCLEAR CLEANUP - Removing ALL problematic files..."

# Remove ALL potentially problematic directories and files
rm -rf src/main/kotlin/com/chargepoint/asynccharging/database/ 2>/dev/null || true
rm -rf src/main/kotlin/com/chargepoint/asynccharging/config/ 2>/dev/null || true
rm -rf src/main/kotlin/com/chargepoint/asynccharging/models/ 2>/dev/null || true
rm -rf src/main/kotlin/com/chargepoint/asynccharging/controllers/ 2>/dev/null || true
rm -rf src/main/kotlin/com/chargepoint/asynccharging/services/ 2>/dev/null || true
rm -rf src/main/kotlin/com/chargepoint/asynccharging/queue/ 2>/dev/null || true
rm -rf src/main/kotlin/com/chargepoint/asynccharging/plugins/ 2>/dev/null || true
rm -rf src/main/kotlin/com/chargepoint/asynccharging/monitoring/ 2>/dev/null || true
rm -rf src/main/kotlin/com/chargepoint/asynccharging/exceptions/ 2>/dev/null || true
rm -rf src/main/kotlin/com/chargepoint/asynccharging/utils/ 2>/dev/null || true

# Remove any malformed files
find src/ -name "*.kt" 2>/dev/null | xargs rm -f || true

echo " Complete cleanup done!"

# Recreate directory structure
echo "Creating clean directory structure..."
mkdir -p src/main/kotlin/com/chargepoint/asynccharging/{config,exceptions,monitoring}
mkdir -p src/main/kotlin/com/chargepoint/asynccharging/models/{requests,responses,decisions,callbacks,enums}
mkdir -p src/main/kotlin/com/chargepoint/asynccharging/{services,controllers,queue,utils,plugins}

echo "Directory structure created!"
