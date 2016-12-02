#!/bin/sh

# wait for a VNC connection
until [ `lsof -i | grep "5901->" | wc -l` = 1 ]
do
    sleep 1
done

# wait until the connection disappears
until [ `lsof -i | grep "5901->" | wc -l` = 0 ]
do
    sleep 60
done

sudo poweroff