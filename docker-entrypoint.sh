#!/bin/sh
set -eu

export DISPLAY=:99

echo "Starte virtuelles X-Display ${DISPLAY}"
Xvfb "${DISPLAY}" -screen 0 1280x1024x24 -nolisten tcp -ac &
xvfb_pid=$!

attempt=0
while [ ! -S /tmp/.X11-unix/X99 ]; do
    if ! kill -0 "${xvfb_pid}" 2>/dev/null; then
        wait "${xvfb_pid}"
        exit $?
    fi
    attempt=$((attempt + 1))
    if [ "${attempt}" -ge 100 ]; then
        echo "Virtuelles X-Display wurde nicht rechtzeitig bereit" >&2
        exit 1
    fi
    sleep 0.1
done

echo "Virtuelles X-Display ist bereit, starte BG Offers"
exec java -jar /app/app.jar
