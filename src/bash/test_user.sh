#!/bin/bash -e
#
# Script to test the witness metadata endpoints.
#
# export SERVER=165.134.241.141
export SERVER=localhost:8084
curl -f -X POST --cookie-jar cookies.txt -H "Content-Type: application/json" -d '{"mail":"ericsmith@slu.edu","password":"foo"}' http://$SERVER/Tradamus/login
echo
curl -f -X GET --cookie cookies.txt http://$SERVER/Tradamus/user/1
echo
curl -f -X GET --cookie cookies.txt http://$SERVER/Tradamus/user?mail=ericsmith@slu.edu
echo