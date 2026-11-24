import {
  ContinueWatchingItem,
  EpgProgramme,
  EpisodeItem,
  LiveCategory,
  LiveChannel,
  MovieItem,
  QualityBadge,
  RecentlyWatchedItem,
  SeasonItem,
  ShowItem,
  VodCategory,
} from '../types/tv';

// ---------------------------------------------------------------------------
// Live TV Categories (8 core populated categories + simulated 74-category scale)
// ---------------------------------------------------------------------------
export const LIVE_CATALOG_STATS = {
  totalChannels: 4912,
  totalCategories: 74,
  apiPageSize: 14,
};

export const VOD_CATALOG_STATS = {
  totalItems: 104280,
  totalCategories: 164,
  apiPageSize: 14,
};

export const LIVE_CATEGORIES: LiveCategory[] = [
  { id: 'cat-all', name: 'All Channels', totalChannelsInCatalog: 4912 },
  { id: 'cat-usa-local', name: 'English USA Local', totalChannelsInCatalog: 418 },
  { id: 'cat-news', name: 'English News', totalChannelsInCatalog: 194 },
  { id: 'cat-kids', name: 'Kids & Family', totalChannelsInCatalog: 162 },
  { id: 'cat-sports', name: 'Sports', totalChannelsInCatalog: 540 },
  { id: 'cat-movies', name: 'Cinema & Drama', totalChannelsInCatalog: 386 },
  { id: 'cat-music', name: 'Music & Concerts', totalChannelsInCatalog: 128 },
  { id: 'cat-hindi', name: 'Hindi', totalChannelsInCatalog: 412 },
  { id: 'cat-regional', name: 'Regional & South Asia', totalChannelsInCatalog: 344 },
  { id: 'cat-docs', name: 'Documentaries | Science', totalChannelsInCatalog: 0, isSimulatedEmpty: true },
  { id: 'cat-uk', name: 'UK | Entertainment & Freeview', totalChannelsInCatalog: 290 },
  { id: 'cat-ca', name: 'Canada | English & French', totalChannelsInCatalog: 214 },
  { id: 'cat-es', name: 'Latino | Noticias y Deportes', totalChannelsInCatalog: 465 },
  { id: 'cat-eu', name: 'Europe | Generalist HD', totalChannelsInCatalog: 520 },
  { id: 'cat-4k', name: '4K UHD | High Bitrate Live', totalChannelsInCatalog: 84 },
  { id: 'cat-radio', name: '24/7 Streams | Classic Radio', totalChannelsInCatalog: 155 },
];

interface RawChannelSeed {
  number: number;
  name: string;
  shortCode: string;
  categoryId: string;
  quality: QualityBadge;
  programme: string | null;
  next: string | null;
  desc: string;
}

const CHANNEL_SEEDS: RawChannelSeed[] = [
  // English USA Local (8 channels)
  { number: 101, name: 'KTV4 Metro Pacific', shortCode: 'K4', categoryId: 'cat-usa-local', quality: 'HD', programme: 'Midday Metro News at Noon', next: 'Valley Weather & Traffic', desc: 'Regional headlines, city council updates, and live bay traffic coverage.' },
  { number: 102, name: 'WNY8 Hudson Local', shortCode: 'W8', categoryId: 'cat-usa-local', quality: 'HD', programme: 'Tri-State Noon Desk', next: 'Afternoon Living Magazine', desc: 'Live breaking reports from across the metropolitan corridor.' },
  { number: 103, name: 'KLA9 Harbor Broadcast', shortCode: 'K9', categoryId: 'cat-usa-local', quality: '4K', programme: 'Coastal Forecast Special', next: 'Civic Forum Live', desc: 'Extended marine layer outlook and regional weather radar analysis.' },
  { number: 104, name: 'WCH5 Lakeshore TV', shortCode: 'W5', categoryId: 'cat-usa-local', quality: 'HD', programme: 'Great Lakes Midday Report', next: 'Midwest Kitchens', desc: 'Local journalism, commuter rail alerts, and community spotlights.' },
  { number: 105, name: 'KTX7 Lone Star Civic', shortCode: 'K7', categoryId: 'cat-usa-local', quality: 'HD', programme: null, next: 'Heartland Afternoon', desc: 'Regional public broadcasting and civic affairs programming.' },
  { number: 106, name: 'WFL3 Suncoast Local', shortCode: 'W3', categoryId: 'cat-usa-local', quality: 'HD', programme: 'Gulf Coast Watch', next: 'Bayfront Homes', desc: 'Tropical weather desk and local coastal reports.' },
  { number: 107, name: 'KWA6 Cascade Public', shortCode: 'K6', categoryId: 'cat-usa-local', quality: 'HD', programme: 'Pacific Northwest Trails', next: 'Puget Sound Evening Prep', desc: 'Documentary series exploring mountain valleys and coastal ferries.' },
  { number: 108, name: 'WMA2 Beacon Local', shortCode: 'W2', categoryId: 'cat-usa-local', quality: 'SD', programme: null, next: null, desc: 'Local metropolitan news, town halls, and regional arts.' },

  // English News (8 channels)
  { number: 201, name: 'Global Desk 24', shortCode: 'GD', categoryId: 'cat-news', quality: '4K', programme: 'World Meridian Live', next: 'Global Markets Close', desc: 'International correspondents report on diplomacy, energy, and trade.' },
  { number: 202, name: 'Meridian Business News', shortCode: 'MB', categoryId: 'cat-news', quality: 'HD', programme: 'Midday Market Pulse', next: 'Tech & Semiconductor Brief', desc: 'Real-time equities analysis, bond yields, and quarterly earnings briefings.' },
  { number: 203, name: 'Civic Wire News', shortCode: 'CW', categoryId: 'cat-news', quality: 'HD', programme: 'Capitol Briefing Hour', next: 'Policy Roundtable', desc: 'Unfiltered legislative coverage and non-partisan policy interviews.' },
  { number: 204, name: 'Atlantic News Network', shortCode: 'AN', categoryId: 'cat-news', quality: 'HD', programme: 'London to New York Live', next: 'European Evening Ledger', desc: 'Cross-Atlantic news desk connecting London, Brussels, and Washington.' },
  { number: 205, name: 'WeatherSphere HD', shortCode: 'WS', categoryId: 'cat-news', quality: 'HD', programme: 'Continental Radar Live', next: 'Storm Trackers Archive', desc: 'Continuous radar loops, jet stream forecasts, and severe alerts.' },
  { number: 206, name: 'Pacific Wire 24/7', shortCode: 'PW', categoryId: 'cat-news', quality: 'HD', programme: 'Asia-Pacific Nightly', next: 'Tokyo & Singapore Open', desc: 'Overnight coverage from across the Pacific Rim and Oceania.' },
  { number: 207, name: 'Headline Express', shortCode: 'HX', categoryId: 'cat-news', quality: 'HD', programme: 'Top Stories in 15', next: 'Science & Climate Desk', desc: 'Fast-paced news summaries refreshed every quarter hour.' },
  { number: 208, name: 'Public Parliament C-1', shortCode: 'PP', categoryId: 'cat-news', quality: 'SD', programme: null, next: 'Committee Hearings', desc: 'Gavel-to-gavel public legislative sessions without commentary.' },

  // Kids & Family (7 channels)
  { number: 301, name: 'Pebble Junior TV', shortCode: 'PJ', categoryId: 'cat-kids', quality: 'HD', programme: 'Pip & Otto Build a Bridge', next: 'Sunny Meadow Singalong', desc: 'Gentle animated stories teaching curiosity, colors, and teamwork.' },
  { number: 302, name: 'Orbit Toons', shortCode: 'OT', categoryId: 'cat-kids', quality: 'HD', programme: 'Captain Sprocket: Moon Race', next: 'Robo-Pup Adventures', desc: 'High-energy sci-fi cartoon adventures for older kids.' },
  { number: 303, name: 'WonderLab Kids', shortCode: 'WL', categoryId: 'cat-kids', quality: '4K', programme: 'Kitchen Chemistry Live', next: 'Backyard Dinosaurs', desc: 'Hands-on science experiments and nature discoveries.' },
  { number: 304, name: 'Storybook Channel', shortCode: 'SB', categoryId: 'cat-kids', quality: 'HD', programme: 'The Clockwork Fox', next: 'Lantern Tales', desc: 'Classic illustrated fables adapted into cozy animated shorts.' },
  { number: 305, name: 'Pixel Playground', shortCode: 'PX', categoryId: 'cat-kids', quality: 'HD', programme: 'Block Builders Challenge', next: 'Origami Masters', desc: 'Creative crafting, puzzle challenges, and friendly competitions.' },
  { number: 306, name: 'Tiny Tracks Nursery', shortCode: 'TT', categoryId: 'cat-kids', quality: 'HD', programme: null, next: 'Quiet Cloud Time', desc: 'Calm rhymes and lullabies for preschool viewers.' },
  { number: 307, name: 'Family Hearth Cinema', shortCode: 'FH', categoryId: 'cat-kids', quality: 'HD', programme: 'The Paper Kite Mystery', next: 'Summer at Willow Creek', desc: 'All-ages matinee films and weekend family adventures.' },

  // Sports (8 channels)
  { number: 401, name: 'Arena Sports 1 UHD', shortCode: 'A1', categoryId: 'cat-sports', quality: '4K', programme: 'Premier Pitch: North vs. City', next: 'Post-Match Tactical Desk', desc: 'Live top-flight football broadcast in 4K 60fps with stadium audio.' },
  { number: 402, name: 'Arena Sports 2', shortCode: 'A2', categoryId: 'cat-sports', quality: 'HD', programme: 'Grand Clay Open: Quarterfinals', next: 'Court Side Recap', desc: 'Live championship tennis from center court.' },
  { number: 403, name: 'Gridiron Network', shortCode: 'GN', categoryId: 'cat-sports', quality: 'HD', programme: 'Playbook Breakdown Live', next: 'Sunday Preview Show', desc: 'Film room tactics, injury updates, and training camp reports.' },
  { number: 404, name: 'Apex Motorsport TV', shortCode: 'AM', categoryId: 'cat-sports', quality: '4K', programme: 'Circuit Qualifying Session', next: 'Paddock Telemetry Hour', desc: 'Trackside cameras and live lap-time comparisons from qualifying.' },
  { number: 405, name: 'Cricket Oval HD', shortCode: 'CO', categoryId: 'cat-sports', quality: 'HD', programme: 'Test Series Day 3: Session 2', next: 'Stumps Analysis', desc: 'Ball-by-ball international cricket coverage with pitch tracker.' },
  { number: 406, name: 'Hardwood Hoops TV', shortCode: 'HH', categoryId: 'cat-sports', quality: 'HD', programme: 'Coast-to-Coast Shootaround', next: 'Classic Finals 2018', desc: 'Basketball highlights, player interviews, and league standings.' },
  { number: 407, name: 'Alpine & Outdoor Sports', shortCode: 'AO', categoryId: 'cat-sports', quality: 'HD', programme: null, next: 'Downhill Cup Highlights', desc: 'Cycling tours, mountain climbing, sailing, and winter sports.' },
  { number: 408, name: 'Combat Ring HD', shortCode: 'CR', categoryId: 'cat-sports', quality: 'HD', programme: 'Championship Weigh-In Live', next: 'Classic Bouts Anthology', desc: 'Boxing and grappling cards, fighter profiles, and live weigh-ins.' },

  // Cinema & Drama (8 channels)
  { number: 501, name: 'SilverScreen Premier', shortCode: 'SP', categoryId: 'cat-movies', quality: '4K', programme: 'The Glass Meridian (2024)', next: 'Harbor of Echoes (2023)', desc: 'Uninterrupted contemporary cinema curated in high bitrate.' },
  { number: 502, name: 'Noir & Classics TV', shortCode: 'NC', categoryId: 'cat-movies', quality: 'HD', programme: 'Shadow on Pier 9 (1958)', next: 'Midnight Expressman (1964)', desc: 'Restored monochrome thrillers and golden-age studio classics.' },
  { number: 503, name: 'Action Vault HD', shortCode: 'AV', categoryId: 'cat-movies', quality: 'HD', programme: 'Iron Convoy (2022)', next: 'Zero Altitude (2021)', desc: 'Non-stop heist, espionage, and adrenaline blockbusters.' },
  { number: 504, name: 'Indie Frame Cinema', shortCode: 'IF', categoryId: 'cat-movies', quality: 'HD', programme: 'Winter in Lisbon (2025)', next: 'Director Q&A: Clara Vance', desc: 'Festival selections and international independent filmmaking.' },
  { number: 505, name: 'Sci-Fi Horizon', shortCode: 'SH', categoryId: 'cat-movies', quality: '4K', programme: 'Orbital Drift (2024)', next: 'The Seventh Signal (2020)', desc: 'Speculative fiction, space epics, and visionary world-building.' },
  { number: 506, name: 'Prestige Drama One', shortCode: 'PD', categoryId: 'cat-movies', quality: 'HD', programme: 'The Harbor Syndicate S2E4', next: 'The Harbor Syndicate S2E5', desc: 'Back-to-back acclaimed drama series marathons.' },
  { number: 507, name: 'Comedy Centrality', shortCode: 'CC', categoryId: 'cat-movies', quality: 'HD', programme: null, next: 'Late Night Standup Set', desc: 'Sitcom reruns, sketch showcases, and modern comedies.' },
  { number: 508, name: 'World Cinema Mosaic', shortCode: 'WM', categoryId: 'cat-movies', quality: 'HD', programme: 'Autumn Train to Kyoto (2023)', next: 'Copenhagen Rain (2022)', desc: 'Subtitled world cinema from Europe, East Asia, and Latin America.' },

  // Music & Concerts (6 channels)
  { number: 601, name: 'Acoustic Sessions 4K', shortCode: 'AS', categoryId: 'cat-music', quality: '4K', programme: 'Live at Cedar Hall: Trio Nova', next: 'Unplugged Studio Cuts', desc: 'Intimate live studio recordings mixed in lossless stereo.' },
  { number: 602, name: 'Pulse Hits Video', shortCode: 'PH', categoryId: 'cat-music', quality: 'HD', programme: 'Global Top 40 Countdown', next: 'Afternoon Pop Mix', desc: 'Continuous curated music videos and artist spotlights.' },
  { number: 603, name: 'Vinyl Jazz Lounge', shortCode: 'VJ', categoryId: 'cat-music', quality: 'HD', programme: 'Blue Hour Quartet Sessions', next: 'Tokyo Jazz Club Archive', desc: 'Smooth modern jazz, bebop legends, and live club sets.' },
  { number: 604, name: 'Symphony Hall HD', shortCode: 'SY', categoryId: 'cat-music', quality: 'HD', programme: 'Vienna Chamber Evening', next: 'Piano Concertos No. 4', desc: 'Orchestral performances, opera recitals, and conductor profiles.' },
  { number: 605, name: 'Retro Beats 80s & 90s', shortCode: 'RB', categoryId: 'cat-music', quality: 'SD', programme: null, next: 'Synthwave Rewind', desc: 'Classic music videos and concert footage from the analog era.' },
  { number: 606, name: 'Indie Soundstage', shortCode: 'IS', categoryId: 'cat-music', quality: 'HD', programme: 'Festival Live: North Stage', next: 'Bedroom Pop Curated', desc: 'Alternative rock, folk, and emerging independent bands.' },

  // Hindi (8 channels)
  { number: 701, name: 'Bharat Prime HD', shortCode: 'BP', categoryId: 'cat-hindi', quality: 'HD', programme: 'Aangan Ki Kahani', next: 'Shaam Ka Safar', desc: 'Flagship Hindi family dramas and primetime entertainment.' },
  { number: 702, name: 'Samachar 24 Hindi', shortCode: 'S2', categoryId: 'cat-hindi', quality: 'HD', programme: 'Dopahar Express Live', next: 'Desh Videsh Bulletin', desc: '24-hour Hindi national news, debates, and live ground reports.' },
  { number: 703, name: 'Sangeet Gold HD', shortCode: 'SG', categoryId: 'cat-hindi', quality: 'HD', programme: 'Evergreen Melodies 90s', next: 'Sufi & Acoustic Evenings', desc: 'Hindi film soundtracks, indie pop, and classic playback hits.' },
  { number: 704, name: 'Rangmanch Cinema', shortCode: 'RC', categoryId: 'cat-hindi', quality: '4K', programme: 'Safar-e-Dilli (2024)', next: 'Chandni Chowk Express (2022)', desc: 'Blockbuster Hindi feature films with crisp audio mastering.' },
  { number: 705, name: 'Khel Bharat Hindi', shortCode: 'KB', categoryId: 'cat-hindi', quality: 'HD', programme: 'Cricket Charcha Live', next: 'Kabaddi League Highlights', desc: 'Hindi-commentary live sports and match-day studio panels.' },
  { number: 706, name: 'Masala Action TV', shortCode: 'MA', categoryId: 'cat-hindi', quality: 'HD', programme: 'Veer Yodha (Hindi Dub)', next: 'Toofan Junction', desc: 'Action cinema and Hindi-dubbed southern blockbusters.' },
  { number: 707, name: 'Zindagi Classics', shortCode: 'ZC', categoryId: 'cat-hindi', quality: 'HD', programme: null, next: 'Purane Zamane Ke Geet', desc: 'Nostalgic serials, literary dramas, and vintage cinema.' },
  { number: 708, name: 'Rasoi & Travel India', shortCode: 'RT', categoryId: 'cat-hindi', quality: 'HD', programme: 'Jaipur Spice Trail', next: 'Himalayan Homestays', desc: 'Regional Indian cuisine, heritage architecture, and travelogues.' },

  // Regional & South Asia (7 channels)
  { number: 801, name: 'Kaveri Tamil HD', shortCode: 'KT', categoryId: 'cat-regional', quality: 'HD', programme: 'Chennai Midday Matinee', next: 'Madurai Melodies', desc: 'Tamil general entertainment, cinema, and cultural specials.' },
  { number: 802, name: 'Deccan Telugu Plus', shortCode: 'DT', categoryId: 'cat-regional', quality: '4K', programme: 'Hyderabad Cinema Showcase', next: 'Telugu News @ 2', desc: 'Telugu blockbusters, talk shows, and regional news.' },
  { number: 803, name: 'Malabar Vision HD', shortCode: 'MV', categoryId: 'cat-regional', quality: 'HD', programme: 'Backwater Chronicles', next: 'Kochi Evening News', desc: 'Malayalam cinema, investigative journalism, and arts.' },
  { number: 804, name: 'Sahyadri Marathi', shortCode: 'SM', categoryId: 'cat-regional', quality: 'HD', programme: 'Pune Theatre Classics', next: 'Maharashtra Live', desc: 'Marathi stage dramas, family shows, and regional bulletins.' },
  { number: 805, name: 'Punjab Virasat HD', shortCode: 'PV', categoryId: 'cat-regional', quality: 'HD', programme: 'Amritsar Folk Sessions', next: 'Punjabi Cinema Hour', desc: 'Punjabi music, comedy showcases, and diaspora news.' },
  { number: 806, name: 'Bengal Sonar TV', shortCode: 'BS', categoryId: 'cat-regional', quality: 'HD', programme: null, next: 'Kolkata Film Retrospective', desc: 'Bengali cinema, literature debates, and classical music.' },
  { number: 807, name: 'Narmada Gujarati', shortCode: 'NG', categoryId: 'cat-regional', quality: 'SD', programme: 'Ahmedabad Business Desk', next: 'Garba & Folk Nights', desc: 'Gujarati news, community events, and evening dramas.' },
];

export const LIVE_CHANNELS: LiveChannel[] = CHANNEL_SEEDS.map((seed, idx) => ({
  id: `ch-${seed.number}`,
  number: seed.number,
  name: seed.name,
  shortCode: seed.shortCode,
  categoryId: seed.categoryId,
  quality: seed.quality,
  streamInfo:
    seed.quality === '4K'
      ? '2160p · HEVC 60fps · Dolby 5.1'
      : seed.quality === 'HD'
        ? '1080p · H.264 50fps · Stereo'
        : '576p · H.264 · Stereo',
  artSeed: idx + 1,
  currentProgramme: seed.programme,
  nextProgramme: seed.next,
  programmeProgress: seed.programme ? 22 + ((idx * 17) % 68) : 0,
  programmeTimeRange: seed.programme ? '12:00 – 13:00' : 'Schedule unavailable',
  programmeDescription: seed.desc,
}));

// ---------------------------------------------------------------------------
// EPG Schedule Grid (16 half-hour slots: 11:00 to 19:00, "Now" = slot 2.5 -> 12:15)
// ---------------------------------------------------------------------------
export const EPG_SLOTS = [
  '11:00', '11:30', '12:00', '12:30', '13:00', '13:30',
  '14:00', '14:30', '15:00', '15:30', '16:00', '16:30',
  '17:00', '17:30', '18:00', '18:30',
];
export const EPG_NOW_SLOT_FLOAT = 2.5; // 12:15 PM

const GENERIC_EPG_TITLES: Record<string, string[]> = {
  'cat-usa-local': ['Morning Commute Watch', 'Midday Metro News', 'Regional Weather Live', 'Community Focus', 'Afternoon Desk', 'Evening News First Look', 'City Council Recap'],
  'cat-news': ['World Meridian Live', 'Global Markets Brief', 'Capitol Briefing', 'Diplomatic Hour', 'Climate & Energy Desk', 'Evening World Edition', 'Special Investigation'],
  'cat-kids': ['Pip & Otto Adventures', 'Starlight Scouts', 'WonderLab Experiments', 'Clockwork Fox Tales', 'Origami Club', 'Cloud Island Stories', 'Family Matinee'],
  'cat-sports': ['Live Match Coverage', 'Tactical Film Room', 'Championship Replay', 'Trackside Telemetry', 'Court Side Live', 'Scoreboard Tonight', 'Highlight Vault'],
  'cat-movies': ['The Glass Meridian', 'Harbor of Echoes', 'Shadow on Pier 9', 'Winter in Lisbon', 'Orbital Drift', 'Midnight Expressman', 'Autumn Train to Kyoto'],
  'cat-music': ['Live at Cedar Hall', 'Global Top 40', 'Blue Hour Jazz', 'Orchestra Evening', 'Acoustic Unplugged', 'Festival Stage Live', 'Late Night Vinyl'],
  'cat-hindi': ['Aangan Ki Kahani', 'Dopahar Express', 'Evergreen Melodies', 'Safar-e-Dilli', 'Cricket Charcha', 'Shaam Ka Safar', 'Jaipur Spice Trail'],
  'cat-regional': ['Chennai Matinee', 'Hyderabad Showcase', 'Backwater Chronicles', 'Pune Stage Classics', 'Folk Sessions Live', 'Evening Regional News', 'Cultural Mosaic'],
};

export const EPG_PROGRAMMES_BY_CHANNEL: Record<string, EpgProgramme[]> = {};

LIVE_CHANNELS.forEach((ch, chIdx) => {
  const pool = GENERIC_EPG_TITLES[ch.categoryId] ?? GENERIC_EPG_TITLES['cat-news'];
  const items: EpgProgramme[] = [];
  let cursor = 0;

  while (cursor < EPG_SLOTS.length) {
    const hash = (chIdx * 31 + cursor * 17) % 100;
    // Slot 2 (12:00) aligns with channel's currentProgramme
    if (cursor === 2) {
      const dur = ch.currentProgramme ? (hash % 2 === 0 ? 2 : 3) : 2;
      const clampedDur = Math.min(dur, EPG_SLOTS.length - cursor);
      items.push({
        id: `${ch.id}-epg-${cursor}`,
        channelId: ch.id,
        title: ch.currentProgramme,
        description: ch.currentProgramme ? ch.programmeDescription : 'No programme guide data provided by portal for this time window.',
        startSlot: cursor,
        durationSlots: clampedDur,
        isLive: true,
      });
      cursor += clampedDur;
      continue;
    }

    // ~34% intentional "No information" missing EPG cells
    const isMissing = hash < 34 || (ch.currentProgramme === null && hash < 60);
    const dur = isMissing ? (hash % 2 === 0 ? 1 : 2) : (hash % 3 === 0 ? 3 : hash % 2 === 0 ? 2 : 1);
    const clampedDur = Math.min(dur, EPG_SLOTS.length - cursor);

    // Ensure slot 2 is never jumped over
    const safeDur = cursor < 2 && cursor + clampedDur > 2 ? 2 - cursor : clampedDur;

    const title = isMissing ? null : pool[(chIdx + cursor) % pool.length];
    items.push({
      id: `${ch.id}-epg-${cursor}`,
      channelId: ch.id,
      title,
      description: title
        ? `${ch.name} broadcast presentation featuring scheduled coverage and studio commentary.`
        : 'Guide metadata was not supplied by the upstream EPG source.',
      startSlot: cursor,
      durationSlots: safeDur,
    });
    cursor += safeDur;
  }

  EPG_PROGRAMMES_BY_CHANNEL[ch.id] = items;
});

// ---------------------------------------------------------------------------
// VOD Categories (includes long " | " provider-style categories)
// ---------------------------------------------------------------------------
export const MOVIE_CATEGORIES: VodCategory[] = [
  { id: 'mcat-all', name: 'All Movies', totalItemsInCatalog: 68420 },
  { id: 'mcat-new-4k', name: '4K ULTRA HD | RECENT RELEASES', totalItemsInCatalog: 2140 },
  { id: 'mcat-hindi-south', name: 'HINDI DUB | SOUTH MOVIES', totalItemsInCatalog: 4890 },
  { id: 'mcat-action', name: 'ACTION | CRIME & ESPIONAGE', totalItemsInCatalog: 8910 },
  { id: 'mcat-scifi', name: 'ENGLISH | SCI-FI & MYSTERY', totalItemsInCatalog: 5340 },
  { id: 'mcat-drama', name: 'FESTIVAL | PRESTIGE DRAMA', totalItemsInCatalog: 7620 },
  { id: 'mcat-hindi-orig', name: 'BOLLYWOOD | HINDI CINEMA 2020-2026', totalItemsInCatalog: 6410 },
  { id: 'mcat-family', name: 'ANIMATION | FAMILY & KIDS', totalItemsInCatalog: 4120 },
  { id: 'mcat-classic', name: 'CLASSIC CINEMA | RESTORED 1950-1999', totalItemsInCatalog: 9850 },
  { id: 'mcat-world', name: 'WORLD CINEMA | SUBTITLED MASTERWORKS', totalItemsInCatalog: 5980 },
  { id: 'mcat-doc', name: 'DOCUMENTARY | NATURE & HISTORY', totalItemsInCatalog: 3840 },
  { id: 'mcat-comedy', name: 'COMEDY | FEEL-GOOD COLLECTION', totalItemsInCatalog: 6180 },
  { id: 'mcat-empty', name: '3D ARCHIVE | LEGACY STREAMS', totalItemsInCatalog: 0 },
  { id: 'mcat-tamil', name: 'TAMIL | NEW & CLASSIC RELEASES', totalItemsInCatalog: 3140 },
];

export const SHOW_CATEGORIES: VodCategory[] = [
  { id: 'scat-all', name: 'All Shows', totalItemsInCatalog: 35860 },
  { id: 'scat-drama', name: 'ENGLISH | PRESTIGE DRAMA SERIES', totalItemsInCatalog: 6420 },
  { id: 'scat-scifi', name: 'SCI-FI | SPECULATIVE & THRILLER', totalItemsInCatalog: 4180 },
  { id: 'scat-crime', name: 'NORDIC & UK | DETECTIVE MYSTERIES', totalItemsInCatalog: 3910 },
  { id: 'scat-hindi', name: 'HINDI WEB | ORIGINAL SERIES', totalItemsInCatalog: 4850 },
  { id: 'scat-doc', name: 'DOCUMENTARY | LIMITED DOCUSERIES', totalItemsInCatalog: 5120 },
  { id: 'scat-comedy', name: 'COMEDY | HALF-HOUR FAVORITES', totalItemsInCatalog: 4790 },
  { id: 'scat-kids', name: 'KIDS & FAMILY | ANIMATED SERIES', totalItemsInCatalog: 3440 },
  { id: 'scat-dub', name: 'HINDI DUB | INTERNATIONAL SERIES', totalItemsInCatalog: 3150 },
];

// ---------------------------------------------------------------------------
// 120 Believable Fictional Movies
// ---------------------------------------------------------------------------
const MOVIE_ADJECTIVES = [
  'Glass', 'Silent', 'Northern', 'Crimson', 'Last', 'Velvet', 'Iron', 'Midnight',
  'Winter', 'Golden', 'Hidden', 'Sunken', 'Distant', 'Broken', 'Amber', 'Cobalt',
  'Parallel', 'Second', 'Forgotten', 'Hollow', 'Paper', 'Electric', 'Copper', 'Quiet',
];

const MOVIE_NOUNS = [
  'Meridian', 'Harbor', 'Signal', 'Convoy', 'Orchard', 'Protocol', 'Lantern', 'Ferry',
  'Horizon', 'Archive', 'Compass', 'Canyon', 'Station', 'Monsoon', 'Cipher', 'Observatory',
  'Express', 'Citadel', 'Bridge', 'Solstice', 'Voyage', 'Passage', 'Gallery', 'Frontier',
];

const MOVIE_DIRECTORS = [
  'Elena Rostova', 'Marcus Vance', 'Priya Deshmukh', 'Kenji Takahashi',
  'Soren Lindqvist', 'Camille Laurent', 'David Okafor', 'Rohan Kulkarni',
  'Hannah Gallagher', 'Mateo Morales', 'Aisha Rahman', 'Tomasz Nowak',
];

const MOVIE_ACTORS = [
  'Clara Sterling', 'Vikram Nair', 'Julian Mercer', 'Nadia Al-Mansoor',
  'Henrik Solberg', 'Meera Menon', 'Owen Callahan', 'Mei-Ling Zhou',
  'Gabriel Costa', 'Fiona Brooks', 'Arjun Rao', 'Beatrice Moreau',
  'Daniel Cho', 'Tessa Lind', 'Karim Haddad', 'Ananya Verma',
];

const MOVIE_GENRE_SETS: Record<string, string[]> = {
  'mcat-new-4k': ['Thriller', 'Drama', 'Mystery'],
  'mcat-hindi-south': ['Action', 'Drama', 'Adventure'],
  'mcat-action': ['Action', 'Crime', 'Espionage'],
  'mcat-scifi': ['Sci-Fi', 'Mystery', 'Thriller'],
  'mcat-drama': ['Drama', 'Biography'],
  'mcat-hindi-orig': ['Drama', 'Romance', 'Music'],
  'mcat-family': ['Animation', 'Family', 'Adventure'],
  'mcat-classic': ['Noir', 'Classic', 'Mystery'],
  'mcat-world': ['World Cinema', 'Drama'],
  'mcat-doc': ['Documentary', 'History'],
  'mcat-comedy': ['Comedy', 'Slice of Life'],
  'mcat-tamil': ['Action', 'Drama', 'Thriller'],
};

const POPULATED_MOVIE_CATS = MOVIE_CATEGORIES.filter(
  (c) => c.id !== 'mcat-all' && c.id !== 'mcat-empty'
);

const CURATED_MOVIE_TITLES: Array<{
  title: string;
  originalTitle?: string;
  cat: string;
  year: number;
  rating: number;
  age: string;
  duration: number;
  quality: QualityBadge;
  isNew?: boolean;
  resumePercent?: number;
  desc: string;
}> = [
  {
    title: 'The Glass Meridian',
    cat: 'mcat-new-4k',
    year: 2026,
    rating: 8.4,
    age: 'PG-13',
    duration: 134,
    quality: '4K',
    isNew: true,
    resumePercent: 42,
    desc: 'A hydrographer stationed in the North Atlantic intercepts an encrypted sonar pulse that points to an uncharted trench—and a fifty-year cover-up.',
  },
  {
    title: 'Monsoon Over Deccan',
    originalTitle: 'दक्कन का मानसून',
    cat: 'mcat-hindi-south',
    year: 2025,
    rating: 8.6,
    age: 'U/A 13+',
    duration: 152,
    quality: '4K',
    isNew: true,
    desc: 'When a freight corridor threatens an ancient railway town, a retired locomotive engineer unites two rival families before the final monsoon bridge closes.',
  },
  {
    title: 'Harbor of Echoes',
    cat: 'mcat-drama',
    year: 2025,
    rating: 8.1,
    age: 'R',
    duration: 121,
    quality: '4K',
    isNew: true,
    resumePercent: 68,
    desc: 'Over three foggy nights in a shrinking shipbuilding port, a salvage diver pieces together the disappearance of his sister’s research vessel.',
  },
  {
    title: 'Orbital Drift',
    cat: 'mcat-scifi',
    year: 2026,
    rating: 7.9,
    age: 'PG-13',
    duration: 141,
    quality: '4K',
    isNew: true,
    desc: 'After a solar flare silences mission control, two telemetry specialists aboard an automated relay station must navigate home using analog star charts.',
  },
  {
    title: 'Iron Convoy',
    cat: 'mcat-action',
    year: 2024,
    rating: 7.6,
    age: 'R',
    duration: 118,
    quality: 'HD',
    desc: 'An armored customs courier crosses a winter mountain pass while pursued by mercenaries determined to recover a mislabeled diplomatic crate.',
  },
  {
    title: 'Autumn Train to Kyoto',
    originalTitle: '京都への秋の列車',
    cat: 'mcat-world',
    year: 2024,
    rating: 8.5,
    age: 'PG',
    duration: 112,
    quality: '4K',
    isNew: true,
    desc: 'Four strangers sharing a sleeper car bound for Kyoto discover their late grandfather’s watchmaker ledger links their lives across decades.',
  },
  {
    title: 'Safar-e-Dilli',
    originalTitle: 'सफ़र-ए-दिल्ली',
    cat: 'mcat-hindi-orig',
    year: 2025,
    rating: 8.3,
    age: 'U/A 13+',
    duration: 146,
    quality: '4K',
    isNew: true,
    desc: 'A young radio archivist restores forgotten 1970s cassette tapes across Old Delhi, uncovering an unfinished musical collaboration.',
  },
  {
    title: 'The Paper Kite Mystery',
    cat: 'mcat-family',
    year: 2025,
    rating: 7.8,
    age: 'PG',
    duration: 98,
    quality: 'HD',
    isNew: true,
    desc: 'Two siblings follow a hand-painted kite across rooftop gardens to find the secret workshop of a legendary toy maker.',
  },
];

export const MOVIES: MovieItem[] = Array.from({ length: 120 }, (_, i) => {
  const curated = CURATED_MOVIE_TITLES[i];
  if (curated) {
    const genres = MOVIE_GENRE_SETS[curated.cat] ?? ['Drama'];
    return {
      id: `mov-${i + 1}`,
      title: curated.title,
      originalTitle: curated.originalTitle,
      year: curated.year,
      ageRating: curated.age,
      imdbRating: curated.rating,
      genres,
      durationMinutes: curated.duration,
      description: curated.desc,
      director: MOVIE_DIRECTORS[i % MOVIE_DIRECTORS.length],
      cast: [
        MOVIE_ACTORS[i % MOVIE_ACTORS.length],
        MOVIE_ACTORS[(i + 3) % MOVIE_ACTORS.length],
        MOVIE_ACTORS[(i + 7) % MOVIE_ACTORS.length],
        MOVIE_ACTORS[(i + 11) % MOVIE_ACTORS.length],
      ],
      categoryId: curated.cat,
      quality: curated.quality,
      isNew: curated.isNew,
      addedOrder: 120 - i,
      artSeed: i + 10,
      resumePercent: curated.resumePercent,
      streamInfo: curated.quality === '4K' ? '2160p · HEVC · Dolby 5.1' : '1080p · H.264 · Stereo',
    };
  }

  const catObj = POPULATED_MOVIE_CATS[i % POPULATED_MOVIE_CATS.length];
  const adj = MOVIE_ADJECTIVES[i % MOVIE_ADJECTIVES.length];
  const noun = MOVIE_NOUNS[(Math.floor(i / 5) + i * 3) % MOVIE_NOUNS.length];
  const suffix = i >= 72 ? ` II` : '';
  const year = catObj.id === 'mcat-classic' ? 1962 + (i % 34) : 2016 + (i % 11);
  const rating = Number((6.4 + ((i * 7) % 25) / 10).toFixed(1));
  const quality: QualityBadge = i % 3 === 0 ? '4K' : i % 9 === 0 ? 'SD' : 'HD';
  const ages = ['PG', 'PG-13', 'R', 'U/A 13+', 'TV-14'];
  const genres = MOVIE_GENRE_SETS[catObj.id] ?? ['Drama', 'Thriller'];

  return {
    id: `mov-${i + 1}`,
    title: `${i % 4 === 0 ? 'The ' : ''}${adj} ${noun}${suffix}`,
    originalTitle:
      catObj.id === 'mcat-world' || catObj.id === 'mcat-hindi-south'
        ? `${adj} ${noun} (Original Cut)`
        : undefined,
    year,
    ageRating: ages[i % ages.length],
    imdbRating: Math.min(9.1, rating),
    genres,
    durationMinutes: 94 + ((i * 11) % 68),
    description: `Set against the backdrop of ${year}, an unlikely alliance forms when a routine assignment uncovers hidden loyalties, forcing every witness to choose what to protect.`,
    director: MOVIE_DIRECTORS[i % MOVIE_DIRECTORS.length],
    cast: [
      MOVIE_ACTORS[i % MOVIE_ACTORS.length],
      MOVIE_ACTORS[(i + 5) % MOVIE_ACTORS.length],
      MOVIE_ACTORS[(i + 9) % MOVIE_ACTORS.length],
    ],
    categoryId: catObj.id,
    quality,
    isNew: year >= 2025 && i < 36,
    addedOrder: 120 - i,
    artSeed: i + 10,
    streamInfo: quality === '4K' ? '2160p · HEVC · 5.1' : '1080p · H.264 · Stereo',
  };
});

// ---------------------------------------------------------------------------
// 30 Believable Fictional Shows (1-4 seasons, 6-10 episodes each)
// ---------------------------------------------------------------------------
const SHOW_SEEDS: Array<{
  title: string;
  cat: string;
  year: number;
  seasonsCount: number;
  rating: number;
  age: string;
  genres: string[];
  desc: string;
}> = [
  { title: 'The Harbor Syndicate', cat: 'scat-drama', year: 2025, seasonsCount: 3, rating: 8.7, age: 'TV-MA', genres: ['Crime', 'Drama'], desc: 'A maritime customs auditor and a dockworkers union steward untangle a decade-long smuggling ledger.' },
  { title: 'Station Nine', cat: 'scat-scifi', year: 2026, seasonsCount: 2, rating: 8.5, age: 'TV-14', genres: ['Sci-Fi', 'Mystery'], desc: 'Engineers staffing a deep-sea geothermal relay detect rhythmic signals arriving from beneath the basalt floor.' },
  { title: 'Fjordline Detective', cat: 'scat-crime', year: 2024, seasonsCount: 4, rating: 8.4, age: 'TV-MA', genres: ['Mystery', 'Crime'], desc: 'Inspector Astrid Holm investigates cold cases along Norway’s winter ferry routes.' },
  { title: 'Dilli Darbar', cat: 'scat-hindi', year: 2025, seasonsCount: 2, rating: 8.6, age: 'U/A 16+', genres: ['Drama', 'Thriller'], desc: 'Inside a family-run heritage newspaper navigating digital disruption and municipal intrigue.' },
  { title: 'Signal & Static', cat: 'scat-scifi', year: 2025, seasonsCount: 2, rating: 8.2, age: 'TV-14', genres: ['Thriller', 'Sci-Fi'], desc: 'An amateur shortwave operator hears broadcasts from twenty-four hours in the future.' },
  { title: 'The Cartographer’s Atlas', cat: 'scat-doc', year: 2026, seasonsCount: 1, rating: 8.8, age: 'TV-PG', genres: ['Documentary', 'History'], desc: 'How hand-drawn sea charts and satellite altimetry remapped our planet.' },
  { title: 'Corner Bistro 4B', cat: 'scat-comedy', year: 2024, seasonsCount: 4, rating: 8.1, age: 'TV-PG', genres: ['Comedy', 'Slice of Life'], desc: 'Three mismatched neighbors inherit a tiny late-night noodle counter and refuse to agree on the menu.' },
  { title: 'Pip & Otto Workshop', cat: 'scat-kids', year: 2025, seasonsCount: 3, rating: 8.0, age: 'TV-Y', genres: ['Kids', 'Animation'], desc: 'Two inventive otters build clever contraptions to help woodland friends solve everyday puzzles.' },
  { title: 'Whiteout Protocol', cat: 'scat-crime', year: 2025, seasonsCount: 2, rating: 8.3, age: 'TV-MA', genres: ['Crime', 'Thriller'], desc: 'An Arctic weather outpost goes dark mid-winter, leaving one investigator with six unreliable logs.' },
  { title: 'Kaveri Chronicles', cat: 'scat-dub', year: 2024, seasonsCount: 3, rating: 8.2, age: 'U/A 13+', genres: ['Historical', 'Drama'], desc: 'Multi-generational riverfront saga spanning trade guilds, music houses, and monsoon floods.' },
  { title: 'Meridian Air Traffic', cat: 'scat-drama', year: 2023, seasonsCount: 3, rating: 7.9, age: 'TV-14', genres: ['Drama'], desc: 'High-stakes overnight shifts inside an international radar center during peak winter storms.' },
  { title: 'Vault of Mirrors', cat: 'scat-scifi', year: 2025, seasonsCount: 1, rating: 8.0, age: 'TV-14', genres: ['Mystery', 'Sci-Fi'], desc: 'An art restorer discovers micro-engraved coordinates hidden inside Renaissance silverwork.' },
  { title: 'Mumbai Night Shift', cat: 'scat-hindi', year: 2026, seasonsCount: 2, rating: 8.4, age: 'U/A 16+', genres: ['Drama', 'Medical'], desc: 'Emergency paramedics race the clock across the city between midnight and dawn.' },
  { title: 'Greenhouse Earth', cat: 'scat-doc', year: 2024, seasonsCount: 2, rating: 8.7, age: 'TV-G', genres: ['Documentary', 'Nature'], desc: 'Time-lapse botanical expeditions across cloud forests, deserts, and alpine meadows.' },
  { title: 'Suburban Choir', cat: 'scat-comedy', year: 2023, seasonsCount: 3, rating: 7.8, age: 'TV-PG', genres: ['Comedy', 'Music'], desc: 'A fiercely competitive town choir prepares for regional finals despite zero budget.' },
  { title: 'Clockwork Harbor', cat: 'scat-kids', year: 2024, seasonsCount: 2, rating: 7.9, age: 'TV-Y7', genres: ['Animation', 'Adventure'], desc: 'Young apprentices sail miniature wind-up boats to deliver mail across floating islands.' },
  { title: 'The Amber Ledger', cat: 'scat-crime', year: 2022, seasonsCount: 4, rating: 8.5, age: 'TV-MA', genres: ['Crime', 'Mystery'], desc: 'Forensic accountants in Zurich track vanished museum endowments across five countries.' },
  { title: 'Redline Rail', cat: 'scat-drama', year: 2025, seasonsCount: 1, rating: 8.1, age: 'TV-14', genres: ['Drama', 'Thriller'], desc: 'Behind the construction of a trans-alpine high-speed tunnel when funding collapses overnight.' },
  { title: 'Solaris Yard', cat: 'scat-scifi', year: 2023, seasonsCount: 3, rating: 7.7, age: 'TV-14', genres: ['Sci-Fi', 'Adventure'], desc: 'Orbital debris salvagers find an intact probe launched before their colony existed.' },
  { title: 'Jaipur Nights', cat: 'scat-hindi', year: 2024, seasonsCount: 2, rating: 8.0, age: 'U/A 13+', genres: ['Drama', 'Mystery'], desc: 'A jewelry appraiser uncovers a century-old royal heist during a grand wedding season.' },
  { title: 'Belfast Cold Case', cat: 'scat-crime', year: 2023, seasonsCount: 3, rating: 8.3, age: 'TV-MA', genres: ['Crime', 'Drama'], desc: 'Retired detectives and a rookie archivist reopen unsolved maritime cases.' },
  { title: 'Blueprints of Tomorrow', cat: 'scat-doc', year: 2025, seasonsCount: 2, rating: 8.2, age: 'TV-G', genres: ['Documentary', 'Engineering'], desc: 'Inside ambitious bridges, tidal turbines, and underground labs around the globe.' },
  { title: 'Desk 14', cat: 'scat-comedy', year: 2025, seasonsCount: 2, rating: 7.9, age: 'TV-PG', genres: ['Comedy', 'Workplace'], desc: 'A municipal lost-and-found office where the strangest city mysteries end up on shelf C.' },
  { title: 'Starlight Academy', cat: 'scat-kids', year: 2023, seasonsCount: 4, rating: 8.1, age: 'TV-Y7', genres: ['Kids', 'Sci-Fi'], desc: 'Junior astronomers decode comet trails from a hilltop observatory school.' },
  { title: 'Vanguard Protocol', cat: 'scat-dub', year: 2025, seasonsCount: 2, rating: 8.0, age: 'TV-14', genres: ['Action', 'Thriller'], desc: 'Cyber-response specialists defend regional power grids during an unprecedented blackout.' },
  { title: 'The Violinist’s Estate', cat: 'scat-drama', year: 2022, seasonsCount: 2, rating: 8.4, age: 'TV-14', genres: ['Drama', 'Music'], desc: 'Three estranged conservatory siblings reunite to catalog a priceless instrument collection.' },
  { title: 'Parallax Zero', cat: 'scat-scifi', year: 2026, seasonsCount: 1, rating: 8.3, age: 'TV-14', genres: ['Sci-Fi', 'Drama'], desc: 'When lunar communications lag by twelve seconds, two physicists suspect the delay is artificial.' },
  { title: 'Coastal Kitchen Diaries', cat: 'scat-doc', year: 2024, seasonsCount: 3, rating: 8.5, age: 'TV-G', genres: ['Documentary', 'Food'], desc: 'Chefs and fishermen share generational recipes from twelve working harbors.' },
  { title: 'Flatmates of Sector 9', cat: 'scat-comedy', year: 2024, seasonsCount: 2, rating: 7.6, age: 'U/A 13+', genres: ['Comedy'], desc: 'Four software interns and a retired cricket umpire share an eccentric bungalow.' },
  { title: 'Iron Horizon', cat: 'scat-dub', year: 2023, seasonsCount: 3, rating: 8.1, age: 'TV-MA', genres: ['Action', 'Crime'], desc: 'Border logistics inspectors confront a shadowy cargo network operating across mountain highways.' },
];

const EPISODE_TITLE_PARTS = [
  'Low Tide', 'The Manifest', 'Static on Channel 4', 'Night Watch', 'Cold Bearings',
  'Paper Trail', 'Glass Horizon', 'Overtime', 'Second Signal', 'Final Approach',
];

export const SHOWS: ShowItem[] = SHOW_SEEDS.map((seed, sIdx) => {
  const seasons: SeasonItem[] = Array.from({ length: seed.seasonsCount }, (_, seIdx) => {
    const seasonNumber = seIdx + 1;
    const epCount = 6 + ((sIdx + seIdx * 2) % 5); // 6 to 10 episodes
    const episodes: EpisodeItem[] = Array.from({ length: epCount }, (_, eIdx) => {
      const epNum = eIdx + 1;
      const isWatched = sIdx < 4 && seasonNumber === 1 && epNum <= 2;
      const isResuming = sIdx === 0 && seasonNumber === 1 && epNum === 3;
      return {
        id: `show-${sIdx + 1}-s${seasonNumber}-e${epNum}`,
        seasonNumber,
        episodeNumber: epNum,
        title: EPISODE_TITLE_PARTS[(eIdx + seIdx * 3 + sIdx) % EPISODE_TITLE_PARTS.length],
        durationMinutes: 42 + ((eIdx * 7 + sIdx) % 16),
        description: `Episode ${epNum} of Season ${seasonNumber}: unexpected findings shift priorities as the team follows a fragile new lead before sunrise.`,
        watched: isWatched,
        resumePercent: isResuming ? 56 : undefined,
        artSeed: sIdx * 20 + seasonNumber * 5 + epNum,
      };
    });
    return {
      seasonNumber,
      year: seed.year - (seed.seasonsCount - seasonNumber),
      episodes,
    };
  });

  return {
    id: `show-${sIdx + 1}`,
    title: seed.title,
    year: seed.year,
    ageRating: seed.age,
    imdbRating: seed.rating,
    genres: seed.genres,
    description: seed.desc,
    creator: MOVIE_DIRECTORS[(sIdx + 2) % MOVIE_DIRECTORS.length],
    cast: [
      MOVIE_ACTORS[(sIdx + 1) % MOVIE_ACTORS.length],
      MOVIE_ACTORS[(sIdx + 4) % MOVIE_ACTORS.length],
      MOVIE_ACTORS[(sIdx + 8) % MOVIE_ACTORS.length],
    ],
    categoryId: seed.cat,
    quality: sIdx % 2 === 0 ? '4K' : 'HD',
    isNew: seed.year >= 2025,
    addedOrder: 30 - sIdx,
    artSeed: sIdx + 200,
    seasons,
    streamInfo: sIdx % 2 === 0 ? '2160p · HEVC · Dolby 5.1' : '1080p · H.264 · Stereo',
  };
});

// ---------------------------------------------------------------------------
// Initial Home Continue Watching & Favorites
// ---------------------------------------------------------------------------
export const INITIAL_CONTINUE_WATCHING: ContinueWatchingItem[] = [
  {
    id: 'cw-1',
    type: 'episode',
    itemId: 'show-1',
    title: 'The Harbor Syndicate',
    subtitle: 'S1 E3 · Static on Channel 4',
    progressPercent: 56,
    remainingLabel: '21m left',
    artSeed: 201,
    seasonNumber: 1,
    episodeNumber: 3,
  },
  {
    id: 'cw-2',
    type: 'movie',
    itemId: 'mov-1',
    title: 'The Glass Meridian',
    subtitle: 'Movie · 4K UHD',
    progressPercent: 42,
    remainingLabel: '1h 18m left',
    artSeed: 10,
  },
  {
    id: 'cw-3',
    type: 'movie',
    itemId: 'mov-3',
    title: 'Harbor of Echoes',
    subtitle: 'Movie · 4K UHD',
    progressPercent: 68,
    remainingLabel: '38m left',
    artSeed: 12,
  },
  {
    id: 'cw-4',
    type: 'episode',
    itemId: 'show-2',
    title: 'Station Nine',
    subtitle: 'S1 E2 · The Manifest',
    progressPercent: 28,
    remainingLabel: '35m left',
    artSeed: 202,
    seasonNumber: 1,
    episodeNumber: 2,
  },
  {
    id: 'cw-5',
    type: 'episode',
    itemId: 'show-4',
    title: 'Dilli Darbar',
    subtitle: 'S1 E1 · Low Tide',
    progressPercent: 81,
    remainingLabel: '9m left',
    artSeed: 204,
    seasonNumber: 1,
    episodeNumber: 1,
  },
];

export const INITIAL_RECENTLY_WATCHED: RecentlyWatchedItem[] = [
  {
    id: 'rw-mov-1',
    type: 'movie',
    itemId: 'mov-1',
    title: 'The Glass Meridian',
    subtitle: '2026 · Thriller / Drama',
    progressPercent: 42,
    remainingLabel: '1h 18m left',
    watchedAgoLabel: '25m ago',
    quality: '4K',
    ageRating: 'PG-13',
    imdbRating: 8.4,
    artSeed: 10,
    durationMinutes: 134,
  },
  {
    id: 'rw-show-1',
    type: 'show',
    itemId: 'show-1',
    title: 'The Harbor Syndicate',
    subtitle: 'S1 E3 · Static on Channel 4',
    progressPercent: 56,
    remainingLabel: '21m left',
    watchedAgoLabel: '2h ago',
    quality: '4K',
    ageRating: 'TV-MA',
    imdbRating: 8.7,
    artSeed: 200,
    durationMinutes: 48,
    seasonNumber: 1,
    episodeNumber: 3,
  },
  {
    id: 'rw-mov-6',
    type: 'movie',
    itemId: 'mov-6',
    title: 'Autumn Train to Kyoto',
    subtitle: '2024 · World Cinema / Drama',
    progressPercent: 100,
    remainingLabel: 'Watched',
    watchedAgoLabel: 'Yesterday',
    quality: '4K',
    ageRating: 'PG',
    imdbRating: 8.5,
    artSeed: 15,
    durationMinutes: 112,
  },
  {
    id: 'rw-show-2',
    type: 'show',
    itemId: 'show-2',
    title: 'Station Nine',
    subtitle: 'S1 E2 · The Manifest',
    progressPercent: 28,
    remainingLabel: '35m left',
    watchedAgoLabel: 'Yesterday',
    quality: 'HD',
    ageRating: 'TV-14',
    imdbRating: 8.5,
    artSeed: 201,
    durationMinutes: 49,
    seasonNumber: 1,
    episodeNumber: 2,
  },
  {
    id: 'rw-mov-3',
    type: 'movie',
    itemId: 'mov-3',
    title: 'Harbor of Echoes',
    subtitle: '2025 · Drama / Biography',
    progressPercent: 68,
    remainingLabel: '38m left',
    watchedAgoLabel: '2 days ago',
    quality: '4K',
    ageRating: 'R',
    imdbRating: 8.1,
    artSeed: 12,
    durationMinutes: 121,
  },
  {
    id: 'rw-show-6',
    type: 'show',
    itemId: 'show-6',
    title: 'The Cartographer’s Atlas',
    subtitle: 'S1 E4 · Night Watch',
    progressPercent: 100,
    remainingLabel: 'Watched',
    watchedAgoLabel: '3 days ago',
    quality: 'HD',
    ageRating: 'TV-PG',
    imdbRating: 8.8,
    artSeed: 205,
    durationMinutes: 46,
    seasonNumber: 1,
    episodeNumber: 4,
  },
];

export const INITIAL_FAVORITE_CHANNELS = ['ch-101', 'ch-201', 'ch-401', 'ch-501', 'ch-701', 'ch-704'];
export const INITIAL_FAVORITE_MOVIES = ['mov-1', 'mov-2', 'mov-6', 'mov-7'];
export const INITIAL_FAVORITE_SHOWS = ['show-1', 'show-2', 'show-4'];
