#
# Script to populate the initial database.  Normal usage is to run this SQL script before running
# the populate.sh script, which will use the endpoints to do more elaborate initialisations.
#
# Author: Eric Smith 2015-07-02
#

# Our only initial config is the database version.
INSERT INTO `config` (`setting`, `value`) VALUES ('dbVersion', '13');

# Populate the messages table.
INSERT INTO `messages` (`id`, `english`) VALUES
	('COLLATION_COMPLETE', '<!DOCTYPE html>\n<html>\n<head>\n<title>Collation Complete</title>\n</head>\n<body>\nThe collation for <a href=\"%1$s\">%1$s</a> has been completed.<br>\nThe results can be found at <a href=\"%2$s/Tradamus/deliverable/%3$d\">%2$s/Tradamus/deliverable/%3$d</a>.\n</body>\n</html>\n'),
	('CONFIRMATION_SUCCESSFUL', '<!DOCTYPE html>\n<html>\n<head>\n<title>Confirmation Successful</title>\n</head>\n<body>\nAccount confirmed for user %s\n</body>\n</html>\n\n'),
	('DEFERRED_COLLATION', '<!DOCTYPE html>\n<html>\n<head>\n<title>Collation Request Submitted</title>\n</head>\n<body>\nThe collation request for %1$s has been submitted.<br> When the process is complete, the results will be found at <a href=\"%2$s/Tradamus/deliverable/%3$d\">%2$s/Tradamus/deliverable/%3$d</a>.\n</body>\n</html>\n'),
	('DEFERRED_PDF', '<!DOCTYPE html>\n<html>\n<head>\n<title>PDF Request Submitted</title>\n</head>\n<body>\nThe PDF for %1$s is being generated.<br> When the process is complete, the results will be found at <a href=\"%2$s/Tradamus/deliverable/%3$d\">%2$s/Tradamus/deliverable/%3$d</a>.\n</body>\n</html>\n'),
	('INVITATION', '<!DOCTYPE html>\n<html>\n<head>\n<title>Tradamus Invitation</title>\n</head>\n<body>%1$s has invited you to join Tradamus with access to the %2$s \"%3$s\".  Your user name is your email address and your login password is \"%4$s\" (no quotes).  To confirm this invitation, please visit <a href=\"%5$s://%6$s:%7$d/Tradamus/users?mail=%8$s&confirmation=%9$s\">%5$s://%6$s:%7$d/Tradamus/users?mail=%8$s&confirmation=%9$s</a>.\n</body>\n</html>\n'),
	('REVIEW_INVITATION', '<!DOCTYPE html>\n<html>\n<head>\n<title>Tradamus Review Invitation</title>\n</head>\n<body>%1$s has invited you to join Tradamus as a reviewer on publication \"%2$s\".  To review this publication go to <a href=\"%3$s://%4$s:%5$d/Tradamus/publication/%6$d/review?key=%7$s\">%3$s://%4$s:%5$d/Tradamus/publication/%6$d/review?key=%7$s</a>\n</body>\n</html>\n');

# Our only initial repositories are our T-PEN instances.
INSERT INTO `repositories` (`prefix`, `type`) VALUES
   ('http://t-pen.org/TPEN/', 'TPEN'),
   ('http://localhost:8080/T-PEN/', 'TPEN');

INSERT INTO `users` (`mail`, `name`, `hash`, `disabled`) VALUES ('none', 'Public User', '', 1);
UPDATE `users` SET id = 0 WHERE id = last_insert_id();

# Create initial user, with password "foo".
INSERT INTO users (mail, name, hash, last_login) VALUES ('ericsmith@slu.edu', 'Eric Smith', 'r/reWkpDIuLKw6hBseMXECadXuC68lv9IJ6U41t3Ams=', NOW());

INSERT INTO `misspellings` (`dictionary`, `correct`, `incorrect`) VALUES
	('lat','$1$2','([aeiouy])h([aeiouy])'),
	('lat','$1$1','([bcdfghklmnprstvxz])\\1'),
	('lat','$1','([bcdfghklmnprstvxz])\\1+'),
	('lat','h$1','^([aeiouy])'),
	('lat','','^h'),
	('lat','v','b'),
	('lat','ch$1','c([ao])'),
	('lat','qu$1','c([aeiouy])'),
	('lat','s$1','c([ei])'),
	('lat','c$1','ch([ao])'),
	('lat','ti$1','ci([aeiouy])'),
	('lat','t','ct'),
	('lat','ae','e'),
	('lat','oe','e'),
	('lat','ph','f'),
	('lat','jer','hier'),
	('lat','y','i'),
	('lat','i','j'),
	('lat','jesus','jhesus'),
	('lat','c$1','k([ao])'),
	('lat','m','mp'),
	('lat','gn','ngn'),
	('lat','gn','nn'),
	('lat','f','ph'),
	('lat','c','qu'),
	('lat','quu','qu'),
	('lat','x','s'),
	('lat','c$1','s([ei])'),
	('lat','sc$1','s([ei])'),
	('lat','c$1','sc([ei])'),
	('lat','sc$1','ss([ei])'),
	('lat','xst','st'),
	('lat','$1ct','([^c])t'),
	('lat','th','t'),
	('lat','d','t$'),
	('lat','ci$1','ti([aeiouy])'),
	('lat','v','u'),
	('lat','b','v'),
	('lat','u','v'),
	('lat','vu','w'),
	('lat','s','x'),
	('lat','x','xc'),
	('lat','st','xst'),
	('lat','x','xst'),
	('lat','xt','xst'),
	('lat','xst','xt'),
	('lat','i','y'),
	('lat','di','z');
