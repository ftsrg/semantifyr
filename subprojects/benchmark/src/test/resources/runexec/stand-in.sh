#!/bin/sh
if [ "$1" = "--version" ]; then
    echo "runexec 3.35"
    exit 0
fi
while [ "$1" != "--" ]; do
    shift
done
shift
"$@"
returnvalue=$?
echo "starttime=2026-11-18T00:00:00.000000+01:00"
echo "returnvalue=$returnvalue"
echo "walltime=0.1s"
echo "cputime=0.1s"
echo "memory=1048576B"
