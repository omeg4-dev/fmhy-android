# Difficult sites

Sites that have broken the in-app browser before. Check them after browser changes (BUG-01, QA-01).

| Site | What to test | Expected |
| ---- | ------------ | -------- |
| streamed.pk | Open a live-events page and scroll it quickly up and down; flick and tap to stop; open a stream. | No "This site isn't loading" screen; the bar hides on scroll down, shows on scroll up and never flicks after lifting the finger. |
| virgil.samidy.com | Tap the page's "Games", "Software" and "Android" buttons several times in a row. | The page changes category; no rescue screen appears or flashes. |
| chat.z.ai | Open the start page; type a message and send it (signed out and signed in). | The page fills the screen with the task cards and the "Send a Message" box; no white page. BUG-01: it collapsed when the WebView reported `100vh` as 0. |
| chat.qwen.ai | Open the start page, dismiss the cookie notice, log in and send a message. | Header, "How can I help you?" and the input are visible at once; the chat answers. BUG-01: the layout uses `100dvh` and stayed white. A DNS ad blocker (e.g. Mullvad adblock DNS) also blocks Alibaba's fingerprinting hosts, outside the app. |
| animepahe.pw (video player) | Open https://animepahe.pw/, open any episode, tap "Click to load", play, go full screen. | The Cloudflare check passes by itself (a few 403s then the page); the Kwik player loads and plays; no failed-load message over the check. |
