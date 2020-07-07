#!/bin/bash -e
#
# Script to test all the endpoints.  Probably best employed by cutting and pasting pieces as needed.
#
# export SERVER=165.134.241.141
export SERVER=localhost:8080

# Add new user
curl -i -X POST --cookie tradamus.txt -d '{"mail":"eric.smith@utoronto.ca","password":"bar","name":"Eric Smith (Toronto)"}' -H "Content-Type: application/json"  http://$SERVER/Tradamus/users

# Invite new user ###
curl -i -X POST --cookie tradamus.txt -d '{ "mail":"ericjmsmith@gmail.com", "name":"Eric Smith (GMail)", "edition":1 }' -H "Content-Type: application/json"  http://$SERVER/Tradamus/users

# Get user details
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/user/2

# Get user details by email ###
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/user?email=eric.smith@utoronto.ca

# Modify user details
curl -i -X PUT --cookie tradamus.txt -H "Content-Type: application/json" -d '{"password":"foo"}' http://$SERVER/Tradamus/user/2

# Reset user password
curl -i -X PUT http://$SERVER/Tradamus/user?reset=ericsmith@slu.edu

# Resend confirmation email
curl -i -X PUT http://$SERVER/Tradamus/user?resend=ericsmith@slu.edu

# Login
curl -i -X POST -H "Content-Type: application/json" --cookie-jar tradamus.txt -d '{"mail":"ericsmith@slu.edu","password":"foo"}' http://$SERVER/Tradamus/login

# Log out of Tradamus
curl -i -X POST --cookie-jar tradamus.txt http://$SERVER/Tradamus/login

# Check login status ###
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/login

# Add new edition
curl -i -X POST --cookie tradamus.txt -d '{"title":"Petrus Plaoul 1"}' -H 'Content-Type: application/json' http://$SERVER/Tradamus/editions

# Import JSON edition
curl -i -X POST --cookie tradamus.txt -d '{"title":"Petrus Plaoul 1"}' -H 'Content-Type: application/json' http://$SERVER/Tradamus/editions

# List editions
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/editions

# Get edition details (includes metadata and permissions)
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/edition/1

# Set edition details
curl -i -X PUT --cookie tradamus.txt -d '{"title":"Retitled"}' -H 'Content-Type: application/json' http://$SERVER/Tradamus/edition/1

# Add edition metadatum
curl -i -X POST --cookie tradamus.txt -d '{"type":"colour","content":"turquoise"}' -H 'Content-Type: application/json' http://$SERVER/Tradamus/edition/1/metadata

# Set edition metadata
curl -i -X PUT --cookie tradamus.txt -d '[{"type":"colour","content":"turquoise"}]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/edition/1/metadata

# Get edition metadata (removed)
# curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/edition/1/metadata

# Approve edition metadata
curl -i -X PUT --cookie tradamus.txt -d '[1000]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/edition/1/approval

# Get edition annotations recursively (not properly implemented)
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/edition/1/annotations

# Add edition outline
curl -i -X POST --cookie tradamus.txt -d @outline.json -H 'Content-Type: application/json' http://$SERVER/Tradamus/edition/1/outlines

# Set edition decisions (removed)
# curl -i -X PUT --cookie tradamus.txt -d @decisions-edited.json -H 'Content-Type: application/json' http://$SERVER/Tradamus/edition/1/decisions

# Add edition permissions (not implemented)
# curl -i -X POST --cookie tradamus.txt -d '[{"role":"VIEWER","user":3}]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/edition/1/permissions

# Set edition permissions
curl -i -X PUT --cookie tradamus.txt -d '[{"role":"VIEWER","user":3}]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/edition/1/permissions

# Export an edition to JSON
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/edition/1?format=json

# Delete an edition
curl -i -X DELETE --cookie tradamus.txt http://$SERVER/Tradamus/edition/1

### Create a new witness using JSON ###

# Import JSON-LD witness
curl -i -X POST --cookie tradamus.txt -d @2436.jsonld -H "Content-Type: application/ld+json" http://$SERVER/Tradamus/edition/2/witnesses
curl -i -X POST --cookie tradamus.txt http://$SERVER/Tradamus/edition/4/witnesses?src=http://$SERVER/T-PEN/project/2414?user=ericsmith@slu.edu

# Import XML witness
export PETRUS=/Users/zig/Documents/Petrus\ Plaoul
curl -i -X POST -H "Content-Type: text/xml" --cookie tradamus.txt -d @"$PETRUS"/prollecture1/reimstranscript_prollecture1.xml http://$SERVER/Tradamus/edition/1/witnesses

# Import JSON witness
curl -i -X POST --cookie tradamus.txt -d @2436.jsonld -H "Content-Type: application/json" http://$SERVER/Tradamus/edition/2/witnesses

# Import plain text witness
curl -i -X POST --cookie tradamus.txt -d @SuperPsalterium.txt -H "Content-Type: text/plain" http://$SERVER/Tradamus/edition/3/witnesses?lineBreak=%0a\&pageBreak=%0c\&title=Super%20Psalterium\&siglum=SP

# Get witness details
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/witness/1

# Set witness details
curl -i -X PUT --cookie tradamus.txt -H "Content-Type: application/json" -d '{"title":"Retitled Witness"}' http://$SERVER/Tradamus/witness/1

# Update witness from T-PEN
curl -i -X PUT --cookie tradamus.txt http://$SERVER/Tradamus/witness/1\&src=http://$SERVER/T-PEN/project/2415?user=ericsmith@slu.edu

# Add witness metadatum
curl -i -X POST --cookie tradamus.txt -d '{"type":"colour","content":"turquoise"}' -H 'Content-Type: application/json' http://$SERVER/Tradamus/witness/1/metadata

# Get witness metadata (removed)
# curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/witness/1/metadata

# Set witness metadata
curl -i -X PUT --cookie tradamus.txt -d '{"type":"colour","content":"turquoise"}' -H 'Content-Type: application/json' http://$SERVER/Tradamus/witness/1/metadata

# Approve witness metadata
curl -i -X PUT --cookie tradamus.txt -d '[1000]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/witness/1/approval

# Get witness annotations recursively
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/witness/1/annotations

# Export witness to JSON
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/witness/1?format=json

# Get all witness annotations
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/witness/1/annotations

# Delete a witness
curl -i -X DELETE --cookie tradamus.txt http://$SERVER/Tradamus/witness/1


# Get transcription details
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/transcription/1

# Add transcription annotation
curl -i -X POST -H "Content-Type: application/json" --cookie tradamus.txt -d '{"endOffset":0,"endPage":1002,"startOffset":0,"startPage":1000,"content":"Content","type":"type"}' http://$SERVER/Tradamus/transcription/1/annotations

# Set transcription permissions
curl -i -X PUT --cookie tradamus.txt -d '[{"role":"VIEWER","user":3}]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/transcription/1/permissions


# Get page details
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/page/1000

### Set page details ###

# Add page annotation
curl -i -X POST -H "Content-Type: application/json" --cookie tradamus.txt -d '{"type":"test"}' http://$SERVER/Tradamus/page/1000/annotations

### Get page annotations ###
### Set page annotations ###

# Get page lines
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/page/1000/lines

### Set page lines ###


# Get manifest details
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/manifest/1

# Add manifest annotation
curl -i -X POST -H "Content-Type: application/json" --cookie tradamus.txt -d '{"canvas":"canvas/1002#xywh=0,0,100,100","content":"Content","type":"type"}' http://$SERVER/Tradamus/manifest/1/annotations

# Set manifest permissions
curl -i -X PUT --cookie tradamus.txt -d '[{"role":"CONTRIBUTOR","user":3}]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/manifest/1/permissions


# Get canvas details
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/canvas/9

### Set canvas details ###

# Add canvas annotation
curl -i -X POST -H "Content-Type: application/json" --cookie tradamus.txt -d '{"type":"test"}' http://$SERVER/Tradamus/canvas/1000/annotations

# Get canvas annotations
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/canvas/1000/annotations

### Set canvas annotations ###

# Get canvas lines
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/canvas/1000/lines

### Set canvas lines ###

# Set canvas images
curl -i -X PUT --cookie tradamus.txt -d '[{{"id":2,"index":0,"uri":"http://localhost:8080/TPEN/imageResize?folioNum=12841944&height=1000&user=ericsmith@slu.edu","format":"JPEG","width":1472,"height":2000,"canvas":1001}]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/canvas/1000/images


# Get an image
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/image/1


# Get annotation details
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/annotation/1

# Set annotation details
curl -i -X PUT --cookie tradamus.txt -H "Content-Type: application/json" -d '{"content":"New content"}' http://$SERVER/Tradamus/annotation/1

# Add sub-annotation
curl -i -X POST --cookie tradamus.txt -d '{"type":"colour","content":"turquoise"}' -H 'Content-Type: application/json' http://$SERVER/Tradamus/annotation/1/annotations

# Get sub-annotations
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/annotation/1/annotations

# Set annotation sub-annotations
curl -i -X PUT --cookie tradamus.txt -d '[{"type":"colour","content":"turquoise"}]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/annotation/1/annotations

# Approve a single annotation
curl -i -X PUT --cookie tradamus.txt http://$SERVER/Tradamus/annotation/1/approval

# Delete annotation
curl -i -X DELETE --cookie tradamus.txt http://$SERVER/Tradamus/annotation/1


# Get outline details
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/outline/1

# Set outline details
curl -i -X PUT --cookie tradamus.txt -H "Content-Type: application/json" -d @outline-edited.json http://$SERVER/Tradamus/outline/1

# Approve outline decisions
curl -i -X PUT --cookie tradamus.txt -d '[1000]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/outline/1/approval

# Add outline annotation
curl -i -X POST -H "Content-Type: application/json" --cookie tradamus.txt -d '{"type":"test","content":"Test Content"}' http://$SERVER/Tradamus/outline/1/annotations

# Get outline annotations
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/outline/1/annotations

# Set outline annotations
curl -i -X PUT --cookie tradamus.txt -d '[{"type":"colour","content":"turquoise"}]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/outline/1/annotations?merge=true
curl -i -X PUT --cookie tradamus.txt -d '[{"type":"colour","content":"turquoise"}]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/outline/1/annotations?merge=false

# Collation of full edition
curl -i -X POST --cookie tradamus.txt http://$SERVER/Tradamus/collation/1 > collation.json

# Collation of annotations
curl -i -X POST --cookie tradamus.txt -H "Content-Type: application/json" -d '[{"startPage":1000, "startOffset":41, "endPage":1000, "endOffset":477},{"startPage":2000, "startOffset":44, "endPage":2000, "endOffset":460}]' http://$SERVER/Tradamus/collation > collation.json

# Get activities
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/activity
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/activity?user=2
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/activity?table=annotation\&id=85116

# Get config
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/config

# 9. T-PEN proxy
curl -i -X POST --cookie tradamus.txt --cookie-jar tradamus.txt -H "Content-Type: application/json" -d '{"mail":"ericsmith@slu.edu","password":"foo"}' http://$SERVER/Tradamus/tpen/login
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/tpen/projects?user=ericsmith@slu.edu
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/tpen/project/2414?user=ericsmith@slu.edu
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/tpen?config

# Create a publication.
curl -i -X POST --cookie tradamus.txt -d '{"title":"Petrus Plaoul PDF", "type":"PDF", "edition":1}' -H 'Content-Type: application/json' http://$SERVER/Tradamus/publications

# List all publications
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/publications

# Get publication details
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/publication/1

# Export publication to JSON
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/publication/1?format=json

# Delete a publication
curl -i -X DELETE --cookie tradamus.txt http://$SERVER/Tradamus/publication/1

# Set publication permissions
curl -i -X PUT --cookie tradamus.txt -d -d '[{"role":"VIEWER","user":3}]' -H 'Content-Type: application/json' http://$SERVER/Tradamus/publication/1/permissions

# Add section to publication
curl -i -X POST --cookie tradamus.txt -d '{"title":"Chapter 1", "type":"TEXT"}' -H 'Content-Type: application/json' http://$SERVER/Tradamus/publication/1/sections

# Get section details (includes layout and decoration rules)
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/section/1

# Set section details (includes layout and decoration rules)
curl -i -X PUT --cookie tradamus.txt -d @toc.json -H "Content-Type: application/json" http://$SERVER/Tradamus/section/1

# Delete a section
curl -i -X DELETE --cookie tradamus.txt http://$SERVER/Tradamus/section/1

# Retrieve the results of a deferred collation
curl -i -X GET --cookie tradamus.txt http://$SERVER/Tradamus/deliverable/1
