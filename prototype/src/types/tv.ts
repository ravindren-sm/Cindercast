export type RouteId =
  | 'home'
  | 'live-tv'
  | 'live-player'
  | 'tv-guide'
  | 'movies'
  | 'movie-detail'
  | 'shows'
  | 'show-detail'
  | 'vod-player'
  | 'search'
  | 'favorites'
  | 'settings'
  | 'setup'
  | 'states'
  | 'design-notes';

export type QualityBadge = '4K' | 'HD' | 'SD';

export interface RouteEntry {
  route: RouteId;
  params?: {
    channelId?: string;
    movieId?: string;
    showId?: string;
    seasonNumber?: number;
    episodeNumber?: number;
    vodType?: 'movie' | 'episode';
    startTimeSec?: number;
    simulateError?: boolean;
  };
  savedFocusId?: string;
}

export interface LiveCategory {
  id: string;
  name: string;
  totalChannelsInCatalog: number; // reflects real ~4,900 channel scale
  isSimulatedEmpty?: boolean;
}

export interface EpgProgramme {
  id: string;
  channelId: string;
  title: string | null; // null renders graceful "No information"
  description: string;
  startSlot: number; // 0 = 11:00, each slot = 30 min
  durationSlots: number; // 1 = 30m, 2 = 60m, 3 = 90m, 4 = 120m
  category?: string;
  isLive?: boolean;
}

export interface LiveChannel {
  id: string;
  number: number;
  name: string;
  shortCode: string;
  categoryId: string;
  quality: QualityBadge;
  streamInfo: string; // e.g. "1080p · H.264 · Stereo"
  artSeed: number;
  currentProgramme: string | null;
  nextProgramme: string | null;
  programmeProgress: number; // 0..100
  programmeTimeRange: string;
  programmeDescription: string;
}

export interface VodCategory {
  id: string;
  name: string; // includes " | " real-world provider naming
  totalItemsInCatalog: number;
}

export interface MovieItem {
  id: string;
  title: string;
  originalTitle?: string;
  year: number;
  ageRating: string;
  imdbRating: number;
  genres: string[];
  durationMinutes: number;
  description: string;
  director: string;
  cast: string[];
  categoryId: string;
  quality: QualityBadge;
  isNew?: boolean;
  addedOrder: number;
  artSeed: number;
  resumePercent?: number;
  streamInfo: string;
}

export interface EpisodeItem {
  id: string;
  seasonNumber: number;
  episodeNumber: number;
  title: string;
  durationMinutes: number;
  description: string;
  watched: boolean;
  resumePercent?: number;
  artSeed: number;
}

export interface SeasonItem {
  seasonNumber: number;
  year: number;
  episodes: EpisodeItem[];
}

export interface ShowItem {
  id: string;
  title: string;
  originalTitle?: string;
  year: number;
  ageRating: string;
  imdbRating: number;
  genres: string[];
  description: string;
  creator: string;
  cast: string[];
  categoryId: string;
  quality: QualityBadge;
  isNew?: boolean;
  addedOrder: number;
  artSeed: number;
  seasons: SeasonItem[];
  streamInfo: string;
}

export interface ContinueWatchingItem {
  id: string;
  type: 'movie' | 'episode';
  itemId: string;
  title: string;
  subtitle: string;
  progressPercent: number;
  remainingLabel: string;
  artSeed: number;
  seasonNumber?: number;
  episodeNumber?: number;
}

export interface RecentlyWatchedItem {
  id: string;
  type: 'movie' | 'show';
  itemId: string;
  title: string;
  subtitle: string;
  progressPercent: number; // 1..94 = partial resume bar; >=95 = Watched
  remainingLabel: string;
  watchedAgoLabel: string;
  quality: QualityBadge;
  ageRating: string;
  imdbRating: number;
  artSeed: number;
  durationMinutes: number;
  seasonNumber?: number;
  episodeNumber?: number;
}

export interface ExternalRatings {
  imdbScore: number;
  imdbVotes: string;
  rtCritics: number | null; // null = no Rotten Tomatoes score available (graceful fallback)
  rtAudience: number | null;
  source: 'OMDb / MDBList';
}

export interface AppSettings {
  bufferSize: 'small' | 'balanced' | 'large';
  preferredAudio: 'English' | 'Hindi' | 'Spanish' | 'Original';
  hardwareDecoder: boolean;
  parentalEnabled: boolean;
  parentalPin: string;
  themeVariant: 'dark-default' | 'high-contrast';
  showChannelNumbers: boolean;
  showExternalRatings: boolean;
  clockFormat: '12h' | '24h';
  portalUrlMasked: string;
  macAddressMasked: string;
}

export type SortOption = 'recent' | 'year' | 'rating';
