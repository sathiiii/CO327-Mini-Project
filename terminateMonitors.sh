if [ "$#" -ne 1 ]; then
    echo "Illegal number of parameters"
    echo "Usage: terminateMonitors.sh <monitor name prefix>"
	exit 1
fi
while IFS= read -r pid; do
    kill $pid
done < $1.pid
rm $1.pid