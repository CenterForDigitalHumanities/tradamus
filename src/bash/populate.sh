#!/bin/bash -e
#
# Script to populate the initial database.  Should be invoked after populate.sql has been run to
# create the initial users.
#
# export SERVER=165.134.241.141
export SERVER=localhost:8084
export PETRUS=/Users/zig/Documents/Petrus\ Plaoul
curl -f -v -X POST -H "Content-Type: application/json" --cookie-jar cookies.txt -d '{"mail":"ericsmith@slu.edu","password":"foo"}' http://$SERVER/Tradamus/login
curl -f -v -X POST -H "Content-Type: application/json" --cookie cookies.txt -d '{"title":"Petrus Plaoul 1"}' http://$SERVER/Tradamus/editions
curl -f -v -X POST -H "Content-Type: text/xml" --cookie cookies.txt -d @"$PETRUS"/prollecture1/reimstranscript_prollecture1.xml http://$SERVER/Tradamus/witnesses?edition=1
