#!/bin/bash
# -----------------------------------------------------------------------------
# Restart T24 / TAFJ (JBoss EAP) on the server - run by "mvn clean deploy".
# Same steps as done by hand:
#   find the JBoss java process -> kill -15 -> wait until it is gone
#   cd <jboss>/bin ; nohup ./standalone.sh -c Transact.xml > /tmp/tafj-startup.log 2>&1 &
#   follow the log until JBoss says "started" (WFLYSRV0025) or "started with errors" (WFLYSRV0026)
# Arguments: <jboss bin dir> <config xml> <startup log> <stop wait s> <start wait s>
# Never uses kill -9: if JBoss does not stop, the script stops and says so.
# -----------------------------------------------------------------------------
BIN="${1:-/srv/Temenos/3rdParty/AS/jboss-eap-8.0/bin}"
CFG="${2:-Transact.xml}"
LOG="${3:-/tmp/tafj-startup.log}"
STOP_WAIT="${4:-180}"
START_WAIT="${5:-900}"

SERVER_LOG="${BIN}/../standalone/log/server.log"

jboss_pids() { pgrep -f "jboss-modules.jar.*${CFG}" ; }
# lines written since this start: console log + JBoss server.log (only lines after the start)
new_lines() { cat "${LOG}" 2>/dev/null; tail -n +"$((SERVER_LOG_START + 1))" "${SERVER_LOG}" 2>/dev/null; }
# last non-empty line, colours removed
last_line() { new_lines | sed 's/\x1b\[[0-9;]*m//g' | grep -v '^[[:space:]]*$' | tail -1 | cut -c1-110; }

echo "=== T24 restart: ${BIN} -c ${CFG}"

# 1. stop
PIDS="$(jboss_pids)"
if [ -z "${PIDS}" ]; then
    echo "JBoss (${CFG}) is not running - starting it"
else
    echo "Stopping JBoss pid(s): ${PIDS}"
    ps -o pid,stat,etime,cmd -p ${PIDS} | cut -c1-150
    kill -15 ${PIDS}
    waited=0
    while [ -n "$(jboss_pids)" ] && [ "${waited}" -lt "${STOP_WAIT}" ]; do
        sleep 5; waited=$((waited + 5))
        [ $((waited % 30)) -eq 0 ] && echo "  still stopping... ${waited}s"
    done
    if [ -n "$(jboss_pids)" ]; then
        echo "ERROR: JBoss did not stop within ${STOP_WAIT}s - NOT restarted, check the server by hand:"
        ps -o pid,stat,etime,cmd -p $(jboss_pids) | cut -c1-150
        exit 1
    fi
    echo "Stopped after ${waited}s"
fi

# 2. start
SERVER_LOG_START=$(wc -l < "${SERVER_LOG}" 2>/dev/null || echo 0)
cd "${BIN}" || { echo "ERROR: folder ${BIN} not found"; exit 1; }
nohup setsid ./standalone.sh -c "${CFG}" > "${LOG}" 2>&1 < /dev/null &
echo "Starting JBoss - log ${LOG} (also watching ${SERVER_LOG})"

# 3. wait for "started"
waited=0
while [ "${waited}" -lt "${START_WAIT}" ]; do
    sleep 10; waited=$((waited + 10))
    if new_lines | grep -q "WFLYSRV0025"; then
        echo "STARTED after ${waited}s:"; new_lines | sed "s/\x1b\[[0-9;]*m//g" | grep "WFLYSRV0025" | tail -1 | cut -c1-200
        echo "New JBoss pid: $(jboss_pids)"
        exit 0
    fi
    if new_lines | grep -q "WFLYSRV0026"; then
        echo "STARTED WITH ERRORS after ${waited}s - check ${LOG} / ${SERVER_LOG}:"
        new_lines | grep -E "WFLYSRV0026|ERROR" | tail -15 | cut -c1-200
        exit 2
    fi
    if [ -z "$(jboss_pids)" ] && [ "${waited}" -ge 60 ]; then
        echo "ERROR: JBoss process ended during start-up - last lines:"; new_lines | tail -30 | cut -c1-200
        exit 1
    fi
    [ $((waited % 60)) -eq 0 ] && echo "  starting... ${waited}s pid=$(jboss_pids | tr '\n' ' ')| $(last_line)"
done
echo "ERROR: no 'started' message within ${START_WAIT}s - JBoss may still be starting, check ${LOG} / ${SERVER_LOG}"
new_lines | tail -20 | cut -c1-200
exit 1
