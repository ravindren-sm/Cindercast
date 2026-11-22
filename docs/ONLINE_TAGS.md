# Online tags (optional): age certification, genres and keywords from themoviedb.org

Added 2026-11-22. The portal gives no age label for 71 percent of its titles, so a Kids profile sees little. An optional crawl asks the free
themoviedb.org (TMDB) service about each title and keeps what it says on the device.

## What it does
- **Opt-in, with the viewer's own key.** Settings, "Online tags (optional)": paste a free TMDB key (the v3 key or the long v4 read token). It is stored
  sealed (AES-GCM, Android Keystore) in `files/tag-key.bin`, never in the clear, never logged. Nothing is sent until a key is saved and Start is pressed.
- **What is sent:** the title text (tags like [Eng Sub] and quality words removed) and the year, one title at a time, plus the key. No portal address,
  MAC, profile, viewing history or device identity.
- **How it matches:** `TmdbMatcher` searches movies or shows by cleaned name, accepts only an exact name (or original name) whose year agrees (one year
  out is allowed; a different year is rejected); with no year it needs some standing (10 or more votes) or to be the only match. Doubtful titles are
  recorded as "looked up, no match" and not asked again.
- **What is kept** (table `title_tags`, database version 6): TMDB id, the certification label and its minimum age (the US label when known, otherwise the
  strictest of the other countries), genres, original language, rating, up to 30 keywords, and a flag when a genre or keyword is on the Kids word list.
- **Order and pace:** titles with no portal age label first, newest first; about 8 titles a second at most (two requests each); waits when the service says
  slow down (HTTP 429); stops at once and says so if the key is refused; pauses while a video plays; resumes where it left off. It runs only while the app is
  open and idle (it does not keep the screen awake, unlike the title index).
- **Use:** a Kids profile now takes the TMDB certification when the portal has none, and hides a title whose tag is flagged. Adult profiles are unchanged.
- **Remove:** Settings, "Remove key and tags" deletes the key and every tag.

## Not done
- Using the tags for search filters and genres in lists for adults (genres, language and rating from TMDB could fill gaps in the portal's own fields).
- A second source (OMDb) for titles TMDB cannot grade, and Rotten Tomatoes scores.
- Checking the crawl against the real service: the key has not been entered yet, so all tests use a fake service (`TitleTaggerTest`, `TmdbTest`). The first
  real run should be watched for the match rate (how many of the 104,000 titles get a certification), speed and any 429 answers.
- Non-English and regional titles often have no certification at TMDB; they stay unlabelled.
