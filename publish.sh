#!/bin/bash

# Script to update version in gradle.properties and README.md, then publish to Maven Central

# Exit on error
set -euo pipefail

# Check if version parameter is provided
if [ -z "${1:-}" ]; then
    echo "❌ Error: Version parameter is required"
    echo "Usage: ./publish.sh <version>"
    echo "Example: ./publish.sh 1.0.0"
    exit 1
fi

# Check if on master branch
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$CURRENT_BRANCH" != "master" ]; then
    echo "❌ Error: Must be on 'master' branch to publish. Current branch: $CURRENT_BRANCH"
    exit 1
fi

VERSION="$1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_PROPERTIES="${SCRIPT_DIR}/gradle.properties"
README="${SCRIPT_DIR}/README.md"

# Update version in gradle.properties
if [ ! -f "$GRADLE_PROPERTIES" ]; then
    echo "❌ Error: gradle.properties not found at $GRADLE_PROPERTIES"
    exit 1
fi

sed -i '' "s/VERSION_NAME=.*/VERSION_NAME=${VERSION}/" "$GRADLE_PROPERTIES"

# Update version in README.md
if [ ! -f "$README" ]; then
    echo "❌ Error: README.md not found at $README"
    exit 1
fi

sed -i '' "s/me\.sergeich:remoter-annotations:[^']*/me.sergeich:remoter-annotations:${VERSION}/g" "$README"
sed -i '' "s/me\.sergeich:remoter:[^']*/me.sergeich:remoter:${VERSION}/g" "$README"
sed -i '' "s/me\.sergeich:remoter-builder:[^']*/me.sergeich:remoter-builder:${VERSION}/g" "$README"

cd "$SCRIPT_DIR"
./gradlew :remoter:clean :remoter-annotations:clean :remoter-builder:clean \
          :remoter:build :remoter-annotations:build :remoter-builder:build \
          :remoter:publishToMavenCentral :remoter-annotations:publishToMavenCentral :remoter-builder:publishToMavenCentral

# Create a commit with the message "Bump version."
git add "$GRADLE_PROPERTIES" "$README"
git commit -m "Bump version to $VERSION"

# Create a tag for the version
git tag -a "v$VERSION" -m "Bump version to $VERSION"
git push --follow-tags origin master
