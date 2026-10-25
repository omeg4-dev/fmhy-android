# Difficult sites

Sites that have broken the in-app browser before. Check them after browser changes (BUG-01, QA-01).

| Site | What to test | Expected |
| ---- | ------------ | -------- |
| streamed.pk | Open a live-events page and scroll it quickly up and down; flick and tap to stop; open a stream. | No "This site isn't loading" screen; the bar hides on scroll down, shows on scroll up and never flicks after lifting the finger. |
| virgil.samidy.com | Tap the page's "Games", "Software" and "Android" buttons several times in a row. | The page changes category; no rescue screen appears or flashes. |
