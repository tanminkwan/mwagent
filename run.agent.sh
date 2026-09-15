#!/bin/bash
export WORK_DIR=$(cd $(dirname $0); pwd)
export JAVA_HOME=/home/hennry/projects/mwmanger/temp_jdk/jdk8u482-b08
export PATH=$JAVA_HOME/bin:$PATH
export USER=`whoami`
export HOSTNAME=`hostname`
export WEBTOBDIR=/home/hennry

LOG_FILE="$WORK_DIR/run_reboot.log"

{
    echo "[$(date)] --- Reboot Script Started ---"
    echo "[$(date)] WORK_DIR: $WORK_DIR"
    cd $WORK_DIR
    
    echo "[$(date)] Waiting for parent agent to finish..."
    sleep 3
    
    echo "[$(date)] Killing old agent..."
    # Only kill the java process of the agent, not the script itself
    ps -ef | grep "Dname=mwagent.$USER" | grep java | grep -v grep | awk '{print $2}' | xargs kill -9 2>/dev/null
    
    echo "[$(date)] Starting new agent..."
    sleep 1
    nohup $JAVA_HOME/bin/java -cp $WORK_DIR/lib/*:$WORK_DIR/build/mwagent.jar -Dname=mwagent.$USER -Xms128m -Xmx128m mwagent.MwAgent >> nohup.out 2>&1 &
    
    echo "[$(date)] New agent PID: $!"
    echo "[$(date)] --- Reboot Script Finished ---"
} >> "$LOG_FILE" 2>&1 &
