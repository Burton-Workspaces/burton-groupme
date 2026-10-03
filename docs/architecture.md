# Architecture

The app is a single Gradle module (`:app`), Kotlin, Jetpack Compose, Hilt, OkHttp, Coil, and DataStore. UI collects `GroupMeRepository` state.

```
ui/          Compose screens and ViewModels (Hilt)
domain/      Conversation, GroupMeMessage, Account, MessageText
data/
  groupme    GroupMeApi, OAuth URL, access token
  parse      TinyJson + GroupMeCodec
  repository GroupMeRepository, LocalPrefs (DataStore)
di/          OkHttp, Coil ImageLoader
```

## Auth

Connect with GroupMe opens `https://oauth.groupme.com/oauth/authorize?client_id=…` in a Custom Tab. GroupMe requires an HTTPS callback, so it returns to `https://burton-workspaces.github.io/burton-groupme/oauth/?access_token=…`. That static page (`web/oauth/index.html`) hops to `burtongroupme://oauth` (or the same HTTPS URL if Android delivers it to the activity). There is no client secret in the APK. GroupMe access tokens do not rotate in this client. Tokens live in DataStore (`burton_groupme`) as `user_token`. Paste-token sign-in stores the same field.

`users/me` fills the account snapshot. Screens never see the token string after sign-in; the repository holds it in memory and DataStore.

## API

`GroupMeApi` GETs and POSTs JSON to `https://api.groupme.com/v3/{path}` with `X-Access-Token`. Responses are TinyJson maps. GroupMeCodec maps those onto domain models. HTTP 304 (no new messages) is treated as an empty success.

| Path | Use |
| --- | --- |
| `users/me` | Signed-in account |
| `groups` | Home groups (paged) |
| `chats` | Direct messages |
| `groups/{id}/messages` | Group history (`before_id` for older) |
| `direct_messages` | DM history (`other_user_id`) |
| `groups/{id}/messages` POST | Send to a group (`source_guid`) |
| `direct_messages` POST | Send a DM |
| `messages/{conversation_id}/{id}/like` / `unlike` | Heart on a message |
| `groups/{id}/members/{membership_id}/remove` POST | Leave a group (self). Falls back to `groups/{id}/leave` if membership id is missing. The creator cannot leave. |

Pagination uses `page` / `per_page` with a page cap.

## Polling

`GroupMeRepository.start()` hydrates token → account → groups → chats, then polls the list about every 4 seconds. An open conversation polls history about every 3 seconds. There is no GroupMe push / Faye in this build.

## UI shell

`MainActivity` hosts a `NavHost`. **Home** and **Search** are bottom tabs. Group/DM is a stacked route and hides the tab bar. Settings is a full-screen modal. Sign-in is a gate when no token is stored.

Search is local: conversation names plus messages already loaded into memory. GroupMe has no workspace-wide message search API comparable to Slack.

Theme tokens match Burton Sonos: black surfaces, ivory text, sand accent, danger `#C45C4A`.
