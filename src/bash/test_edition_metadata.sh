#!/bin/bash -e
#
# Script to test the witness metadata endpoints.
#
# export SERVER=165.134.241.141
export SERVER=localhost:8084
curl -f -X POST --cookie-jar cookies.txt -H "Content-Type: application/json" -d '{"mail":"ericsmith@slu.edu","password":"foo"}' http://$SERVER/Tradamus/login
curl -f -X GET --cookie cookies.txt http://$SERVER/Tradamus/edition/1
curl -f -X PUT --cookie cookies.txt  -H "Content-Type: application/json" -d '[{"type":"colour","content":"blue"}]' http://$SERVER/Tradamus/edition/1/metadata
curl -f -X GET --cookie cookies.txt http://$SERVER/Tradamus/edition/1
curl -f -X PUT --cookie cookies.txt  -H "Content-Type: application/json" -d '[{"type":"colour","content":"red"}]' http://$SERVER/Tradamus/edition/1/metadata
curl -f -X GET --cookie cookies.txt http://$SERVER/Tradamus/edition/1
curl -f -X PUT --cookie cookies.txt  -H "Content-Type: application/json" -d '[{"type":"flavour","content":"cherry"}]' http://$SERVER/Tradamus/edition/1/metadata
curl -f -X GET --cookie cookies.txt http://$SERVER/Tradamus/edition/1
curl -f -X PUT --cookie cookies.txt  -H "Content-Type: application/json" -d '[{"type":"flavour","content":"cherry"},{"type":"colour","content":"black"}]' http://$SERVER/Tradamus/edition/1/metadata
curl -f -X GET --cookie cookies.txt http://$SERVER/Tradamus/edition/1
