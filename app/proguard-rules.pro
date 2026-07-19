# App-specific R8 rules belong here. Library consumer rules cover the current
# Room, WorkManager, Google API client, and libphonenumber dependencies.

# Apache HTTP exposes optional desktop authentication/LDAP paths that are not
# available or used by the Android transport selected in GmailServiceFactory.
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
