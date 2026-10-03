# Using Burton GroupMe

Burton GroupMe is an account client. Sign in with **Connect with GroupMe**. The token is stored on the phone. Messages go through GroupMe’s HTTPS v3 API.

## Connect

1. Tap **Connect with GroupMe** and allow the Burton GroupMe application in the browser.
2. GroupMe redirects to an HTTPS page; that page opens the app. The access token stays on the phone (DataStore).

The GroupMe application is registered at [dev.groupme.com](https://dev.groupme.com/). GroupMe requires an **HTTPS** callback, so register:

`https://burton-workspaces.github.io/burton-groupme/oauth/`

That Pages hop (`web/oauth/index.html`) opens `burtongroupme://oauth` with the token. Someone has to create the GroupMe application once, then put the public Client ID in `groupme/client-id.txt`. After that, every phone uses the same application.

Sign out from Settings. That deletes the token from the phone.

### Use a token (optional)

On Connect, **Use a token** pastes an access token from an application you created at [dev.groupme.com](https://dev.groupme.com/). Prefer Connect with GroupMe when the Client ID is set.

## Screens

### Connect

Shown when no token is stored. **Connect with GroupMe** opens GroupMe’s authorize page. Failed login stays on this screen with an error and retry. **Use a token** is a debug fallback.

### Home

Lists groups, then direct messages. Unread counts show in sand when GroupMe provides them. Tap a row to open it.

Settings (gear) holds the account, sign out, and the app version.

### Group / DM

Newest messages at the bottom. Tap a message to like or unlike it (heart). The compose field at the bottom sends with **Send**.

### Search

Local search over group names, DM names, last-message previews, and messages already loaded into memory. Tap a hit to open that conversation.

## Permissions

| Android | Permission | Why |
| --- | --- |
| All | Internet | GroupMe v3 API |

No location or nearby-devices permission. Debug builds use application id `com.burton.groupme.debug` and can sit next to a signed install.
