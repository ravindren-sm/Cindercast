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
- **Order and pace:** titles with no portal age label first, newest first; five titles at a time (two requests each), about 8 titles a second on the Fire TV, so the whole catalogue takes roughly 3.5 hours; waits when the service says
  slow down (HTTP 429); stops at once and says so if the key is refused; pauses while a video plays; resumes where it left off. It runs only while the app is
  open and idle (it does not keep the screen awake, unlike the title index).
- **Use for adults (2026-11-22):** wherever a list loads (Movies and Shows from the portal or the on-device copy, and Search), a title the portal gave no score gets the TMDB score (badge "TMDB 7.4", used by the rating floor, the rating filter and the Best rated order), and no genres or age label get the TMDB ones; the portal's own values always win. The rating floor and sort read the score in SQL; the genre filter in a category reads TMDB genres where the portal gave none.
- **Use:** a Kids profile now takes the TMDB certification when the portal has none, and hides a title whose tag is flagged. Adult profiles are unchanged.
- **Remove:** Settings, "Remove key and tags" deletes the key and every tag.

## Second source: OMDb (IMDb score and Rotten Tomatoes), added 2026-11-22
- Optional, its own key (Settings, "IMDb and Rotten Tomatoes scores (optional)"), stored sealed in `files/score-key.bin`. Sends only an IMDb id and the key.
- The IMDb id comes from the first source (TMDB gives it for most titles); the tag crawl now stores it, and once every title has a row it asks for the id of
  rows made earlier (one request each). The OMDb crawl then reads, for every title with an id: the real IMDb score, the Rotten Tomatoes percentage, and the
  Rated label (which fills the age label only where TMDB had none).
- **Limits:** the free key allows about 1,000 titles a day; when the day's allowance is used up the crawl waits 30 minutes and tries again, so a free key
  works through the list over weeks and a paid key (about 100,000 a day, a few dollars a month as of writing; check omdbapi.com) finishes in a day or two.
  The scores stay on the device if the paid plan is cancelled, and the free key still covers new titles and slow refreshes.
- **Use:** the real IMDb score takes the portal's IMDb slot (badge with a star, rating floor, Best rated order, SQL join); Rotten Tomatoes shows on the movie
  page and drives the Search "Rotten Tomatoes" filter (60, 70, 80, 90 percent and above).
- Tested with a fake service only (`OmdbTest`).

## First real run (2026-11-22)
Key entered through the Settings box; the crawl started on the real service. First 300 titles (newest first, no portal label): 61 percent matched, 33 percent got a
certification (51 percent of movies, 13 percent of shows); labels were mostly Indian (U/A 16+, 16, U/A 13+) plus some TV-14, PG, R. One worker did 2 titles a second;
five workers do about 8. The crawl resumes by itself after the app restarts.

## Not done
- TMDB original language is stored but not used yet (the language filter still works from category names).
- A second source (OMDb) for titles TMDB cannot grade, and Rotten Tomatoes scores.
- Checking the crawl against the real service: the key has not been entered yet, so all tests use a fake service (`TitleTaggerTest`, `TmdbTest`). The first
  real run should be watched for the match rate (how many of the 104,000 titles get a certification), speed and any 429 answers.
- Non-English and regional titles often have no certification at TMDB; they stay unlabelled.
