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

jboss_pids() { pgrep -f "jboss-modules.jar.*${CFG}" ; }

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
cd "${BIN}" || { echo "ERROR: folder ${BIN} not found"; exit 1; }
nohup setsid ./standalone.sh -c "${CFG}" > "${LOG}" 2>&1 < /dev/null &
echo "Starting JBoss - log ${LOG}"

# 3. wait for "started"
waited=0
while [ "${waited}" -lt "${START_WAIT}" ]; do
    sleep 10; waited=$((waited + 10))
    if grep -q "WFLYSRV0025" "${LOG}" 2>/dev/null; then
        echo "STARTED after ${waited}s:"; grep "WFLYSRV0025" "${LOG}" | tail -1 | cut -c1-200
        exit 0
    fi
    if grep -q "WFLYSRV0026" "${LOG}" 2>/dev/null; then
        echo "STARTED WITH ERRORS after ${waited}s - check ${LOG}:"
        grep -E "WFLYSRV0026|ERROR" "${LOG}" | tail -15 | cut -c1-200
        exit 2
    fi
    if [ -z "$(jboss_pids)" ] && [ "${waited}" -ge 60 ]; then
        echo "ERROR: JBoss process ended during start-up - last lines of ${LOG}:"; tail -30 "${LOG}" | cut -c1-200
        exit 1
    fi
    [ $((waited % 60)) -eq 0 ] && echo "  starting... ${waited}s ($(tail -1 "${LOG}" 2>/dev/null | cut -c1-100))"
done
echo "ERROR: no 'started' message within ${START_WAIT}s - JBoss may still be starting, check ${LOG}"
tail -20 "${LOG}" | cut -c1-200
exit 1
