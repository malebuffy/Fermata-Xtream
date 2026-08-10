#!/bin/sh
set -e

APP_ID_SFX=''
DIR="$(cd "$(dirname "$0")"; pwd -P)"
DEST_DIR="$DIR/dist"
mkdir -p "$DEST_DIR"
export NO_GS=true
CLEAN='clean'

while [ "$1" != "" ]; do
    case "$1" in
        -nc)
            unset CLEAN
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
    shift
done

if [ -z "$ANDROID_SDK_ROOT" ]; then
    if [ -f "$DIR/local.properties" ]; then
        ANDROID_SDK_ROOT="$(grep sdk.dir= local.properties | cut -d = -f2)"
    fi
fi

if [ -z "$ANDROID_SDK_ROOT" ]; then
    echo 'ANDROID_SDK_ROOT environment variable is not set'
    exit 1
else
    echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
fi

CMAKE_PATH="$(find $ANDROID_SDK_ROOT/cmake/* -maxdepth 1 -type d -name bin | sort -V | tail -1)"
echo "CMAKE_PATH=$CMAKE_PATH"
export PATH=$CMAKE_PATH:$PATH

./gradlew $CLEAN releaseDist -PAPP_ID_SFX=$APP_ID_SFX -Pfree=true

echo "Built release artifacts in $DEST_DIR:"
ls -lh "$DEST_DIR"/*.aab "$DEST_DIR"/*-universal.apk 2>/dev/null || ls -lh "$DEST_DIR"
