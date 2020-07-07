#
# Script to exercise the T-PEN import/export functionality.
#

# Log in to Tradamus
curl -i -X POST --cookie-jar tradamus.txt -H "Content-Type: application/json" -d '{"mail":"ericsmith@slu.edu","password":"foo"}' http://$SERVER/Tradamus/login

# Log in to T-PEN
curl -i -X POST --cookie tradamus.txt --cookie-jar tradamus.txt -H "Content-Type: application/json" -d '{"mail":"ericsmith@slu.edu","password":"foo"}' http://$SERVER/Tradamus/tpen/login

# List projects
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/tpen/projects

# List projects
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/tpen/project/2443 > 2443.jsonld

# Log out of T-PEN
curl -i -X POST --cookie tradamus.txt --cookie-jar tradamus.txt http://$SERVER/Tradamus/tpen/login
