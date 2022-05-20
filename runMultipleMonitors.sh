javac VitalMonitor.java
if [ "$#" -ne 2 ]; then
    echo "Illegal number of parameters"
	echo "Usage: runMultipleMonitors.sh <starting port> <monitor name prefix>"
	exit 1
fi
startingPort=$1
for i in $(seq 5)
do
	j=$((i + startingPort));
	echo -e "Starting Vital Monitor: $2_$i at port: $j\n"
	java VitalMonitor "$2_$i" "$j" &
	echo $! >> $2.pid
done
