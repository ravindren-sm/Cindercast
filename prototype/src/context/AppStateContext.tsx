import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import {
  INITIAL_CONTINUE_WATCHING,
  INITIAL_FAVORITE_CHANNELS,
  INITIAL_FAVORITE_MOVIES,
  INITIAL_FAVORITE_SHOWS,
  INITIAL_RECENTLY_WATCHED,
  LIVE_CHANNELS,
} from '../data/sampleData';
import {
  AppSettings,
  ContinueWatchingItem,
  RecentlyWatchedItem,
  RouteEntry,
  RouteId,
} from '../types/tv';
import { useTvFocus } from './TvFocusContext';

export interface PinRequest {
  mode: 'verify' | 'set';
  title: string;
  subtitle?: string;
  hintText?: string;
  onSuccess: (pin: string) => void;
}

const RESTRICTED_RATINGS = new Set(['R', 'TV-MA', 'U/A 16+', 'NC-17']);
const RESTRICTED_CHANNEL_IDS = new Set(['ch-408', 'ch-503']);

interface AppStateContextValue {
  currentEntry: RouteEntry;
  historyStack: RouteEntry[];
  navigate: (route: RouteId, params?: RouteEntry['params'], replace?: boolean) => void;
  goBack: () => boolean;
  resetToTopRoute: (route: RouteId) => void;

  // Favorites
  favoriteChannels: string[];
  favoriteMovies: string[];
  favoriteShows: string[];
  toggleFavoriteChannel: (id: string) => void;
  toggleFavoriteMovie: (id: string) => void;
  toggleFavoriteShow: (id: string) => void;

  // Continue watching, recently watched & active channel
  continueWatching: ContinueWatchingItem[];
  recentlyWatched: RecentlyWatchedItem[];
  recordWatchProgress: (entry: Omit<RecentlyWatchedItem, 'id' | 'watchedAgoLabel'>) => void;
  activeChannelId: string;
  setActiveChannelId: (id: string) => void;
  stepChannel: (delta: 1 | -1) => string;

  // Settings & System State
  settings: AppSettings;
  updateSettings: (patch: Partial<AppSettings>) => void;
  sessionReconnecting: boolean;
  setSessionReconnecting: (v: boolean) => void;

  // Parental PIN Gate
  pinRequest: PinRequest | null;
  openPinModal: (req: PinRequest) => void;
  closePinModal: () => void;
  unlockedIds: string[];
  relockAll: () => void;
  isContentLocked: (itemId: string, ageRating?: string) => boolean;
  guardRestrictedAccess: (opts: {
    itemId: string;
    title: string;
    ageRating?: string;
    onAllow: () => void;
  }) => void;

  // Global Menu Overlay ('m' key)
  menuOpen: boolean;
  setMenuOpen: (v: boolean) => void;

  // Transient feedback toast (non-blocking)
  toastMessage: string | null;
  showToast: (msg: string) => void;

  // Focus restore signal for screens mounting after Back
  pendingRestoreFocusId: string | null;
  consumePendingRestoreFocus: () => string | null;
}

const AppStateContext = createContext<AppStateContextValue | null>(null);

const DEFAULT_SETTINGS: AppSettings = {
  bufferSize: 'balanced',
  preferredAudio: 'English',
  hardwareDecoder: true,
  parentalEnabled: true,
  parentalPin: '2468',
  themeVariant: 'dark-default',
  showChannelNumbers: true,
  showExternalRatings: true,
  clockFormat: '12h',
  portalUrlMasked: 'http://*****.***/stalker_portal/c/',
  macAddressMasked: '00:1A:79:**:**:**',
};

export function AppStateProvider({ children }: { children: React.ReactNode }) {
  const { focusedId, setFocus, registerKeyInterceptor } = useTvFocus();
  const focusedIdRef = useRef(focusedId);
  focusedIdRef.current = focusedId;

  const [historyStack, setHistoryStack] = useState<RouteEntry[]>([{ route: 'home' }]);
  const [favoriteChannels, setFavoriteChannels] = useState<string[]>(INITIAL_FAVORITE_CHANNELS);
  const [favoriteMovies, setFavoriteMovies] = useState<string[]>(INITIAL_FAVORITE_MOVIES);
  const [favoriteShows, setFavoriteShows] = useState<string[]>(INITIAL_FAVORITE_SHOWS);
  const [continueWatching, setContinueWatching] = useState<ContinueWatchingItem[]>(
    INITIAL_CONTINUE_WATCHING
  );
  const [recentlyWatched, setRecentlyWatched] = useState<RecentlyWatchedItem[]>(
    INITIAL_RECENTLY_WATCHED
  );
  const [activeChannelId, setActiveChannelId] = useState<string>(LIVE_CHANNELS[0].id);
  const [settings, setSettings] = useState<AppSettings>(DEFAULT_SETTINGS);
  const [sessionReconnecting, setSessionReconnecting] = useState<boolean>(false);
  const [menuOpen, setMenuOpen] = useState<boolean>(false);
  const [pinRequest, setPinRequest] = useState<PinRequest | null>(null);
  const [unlockedIds, setUnlockedIds] = useState<string[]>([]);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const pendingRestoreFocusRef = useRef<string | null>(null);
  const toastTimerRef = useRef<number | null>(null);

  const showToast = useCallback((msg: string) => {
    setToastMessage(msg);
    if (toastTimerRef.current) window.clearTimeout(toastTimerRef.current);
    toastTimerRef.current = window.setTimeout(() => {
      setToastMessage(null);
    }, 2600);
  }, []);

  const openPinModal = useCallback((req: PinRequest) => {
    setPinRequest(req);
  }, []);

  const closePinModal = useCallback(() => {
    setPinRequest(null);
  }, []);

  const relockAll = useCallback(() => {
    setUnlockedIds([]);
    showToast('All restricted titles re-locked');
  }, [showToast]);

  const isContentLocked = useCallback(
    (itemId: string, ageRating?: string) => {
      if (!settings.parentalEnabled) return false;
      if (unlockedIds.includes(itemId)) return false;
      if (RESTRICTED_CHANNEL_IDS.has(itemId)) return true;
      return Boolean(ageRating && RESTRICTED_RATINGS.has(ageRating));
    },
    [settings.parentalEnabled, unlockedIds]
  );

  const guardRestrictedAccess = useCallback(
    ({
      itemId,
      title,
      ageRating,
      onAllow,
    }: {
      itemId: string;
      title: string;
      ageRating?: string;
      onAllow: () => void;
    }) => {
      if (!isContentLocked(itemId, ageRating)) {
        onAllow();
        return;
      }
      setPinRequest({
        mode: 'verify',
        title: 'Restricted Content',
        subtitle: `Enter your 4-digit parental PIN to access "${title}"${ageRating ? ` (${ageRating})` : ''}.`,
        onSuccess: () => {
          setUnlockedIds((prev) => (prev.includes(itemId) ? prev : [...prev, itemId]));
          setPinRequest(null);
          showToast(`Unlocked "${title}"`);
          onAllow();
        },
      });
    },
    [isContentLocked, showToast]
  );

  const currentEntry = historyStack[historyStack.length - 1];

  const navigate = useCallback(
    (route: RouteId, params?: RouteEntry['params'], replace = false) => {
      pendingRestoreFocusRef.current = null;
      setHistoryStack((prev) => {
        if (replace) {
          const next = [...prev];
          next[next.length - 1] = { route, params };
          return next;
        }
        // Save the currently focused ID onto the top stack frame so Back restores it
        const updated = [...prev];
        updated[updated.length - 1] = {
          ...updated[updated.length - 1],
          savedFocusId: focusedIdRef.current || updated[updated.length - 1].savedFocusId,
        };
        updated.push({ route, params });
        return updated;
      });
    },
    []
  );

  const resetToTopRoute = useCallback((route: RouteId) => {
    pendingRestoreFocusRef.current = null;
    setHistoryStack((prev) => {
      const current = prev[prev.length - 1];
      if (current.route === route) return prev;
      // Preserve home as root if navigating to a primary tab so Back returns to Home
      if (route === 'home') {
        return [{ route: 'home' }];
      }
      return [{ route: 'home', savedFocusId: `nav-${route}` }, { route }];
    });
  }, []);

  const goBack = useCallback((): boolean => {
    if (historyStack.length <= 1) {
      // On Home root, move focus up to Top Nav or open menu
      if (focusedIdRef.current && !focusedIdRef.current.startsWith('nav-')) {
        setFocus('nav-home');
        return true;
      }
      setMenuOpen(true);
      return true;
    }

    const previousFrame = historyStack[historyStack.length - 2];
    pendingRestoreFocusRef.current = previousFrame.savedFocusId ?? null;
    setHistoryStack((prev) => prev.slice(0, -1));
    return true;
  }, [historyStack, setFocus]);

  const consumePendingRestoreFocus = useCallback(() => {
    const val = pendingRestoreFocusRef.current;
    pendingRestoreFocusRef.current = null;
    return val;
  }, []);

  const toggleFavoriteChannel = useCallback(
    (id: string) => {
      setFavoriteChannels((prev) => {
        const exists = prev.includes(id);
        showToast(exists ? 'Removed channel from Favorites' : 'Added channel to Favorites');
        return exists ? prev.filter((x) => x !== id) : [id, ...prev];
      });
    },
    [showToast]
  );

  const toggleFavoriteMovie = useCallback(
    (id: string) => {
      setFavoriteMovies((prev) => {
        const exists = prev.includes(id);
        showToast(exists ? 'Removed movie from Favorites' : 'Added movie to Favorites');
        return exists ? prev.filter((x) => x !== id) : [id, ...prev];
      });
    },
    [showToast]
  );

  const toggleFavoriteShow = useCallback(
    (id: string) => {
      setFavoriteShows((prev) => {
        const exists = prev.includes(id);
        showToast(exists ? 'Removed show from Favorites' : 'Added show to Favorites');
        return exists ? prev.filter((x) => x !== id) : [id, ...prev];
      });
    },
    [showToast]
  );

  const stepChannel = useCallback(
    (delta: 1 | -1): string => {
      const idx = LIVE_CHANNELS.findIndex((c) => c.id === activeChannelId);
      const nextIdx = (idx + delta + LIVE_CHANNELS.length) % LIVE_CHANNELS.length;
      const nextCh = LIVE_CHANNELS[nextIdx];
      setActiveChannelId(nextCh.id);
      return nextCh.id;
    },
    [activeChannelId]
  );

  const recordWatchProgress = useCallback(
    (entry: Omit<RecentlyWatchedItem, 'id' | 'watchedAgoLabel'>) => {
      const clampedPct = Math.max(0, Math.min(100, Math.round(entry.progressPercent)));
      const normalized: RecentlyWatchedItem = {
        ...entry,
        id: `rw-${entry.type}-${entry.itemId}`,
        progressPercent: clampedPct,
        remainingLabel: clampedPct >= 95 ? 'Watched' : entry.remainingLabel,
        watchedAgoLabel: 'Just now',
      };

      setRecentlyWatched((prev) => [
        normalized,
        ...prev.filter((item) => !(item.itemId === entry.itemId && item.type === entry.type)),
      ].slice(0, 12));

      // Keep Continue Watching synchronized for partial playback items
      setContinueWatching((prev) => {
        const filtered = prev.filter((c) => c.itemId !== entry.itemId);
        if (clampedPct > 0 && clampedPct < 95) {
          const cwItem: ContinueWatchingItem = {
            id: `cw-${entry.itemId}`,
            type: entry.type === 'show' ? 'episode' : 'movie',
            itemId: entry.itemId,
            title: entry.title,
            subtitle: entry.subtitle,
            progressPercent: clampedPct,
            remainingLabel: entry.remainingLabel,
            artSeed: entry.artSeed,
            seasonNumber: entry.seasonNumber,
            episodeNumber: entry.episodeNumber,
          };
          return [cwItem, ...filtered].slice(0, 8);
        }
        return filtered;
      });
    },
    []
  );

  const updateSettings = useCallback((patch: Partial<AppSettings>) => {
    setSettings((prev) => ({ ...prev, ...patch }));
  }, []);

  // Global Back (Backspace / Escape) & Menu ('m') key handler at base priority
  useEffect(() => {
    return registerKeyInterceptor({
      id: 'global-nav-keys',
      priority: 10,
      handler: (e) => {
        if (e.key === 'm' || e.key === 'M') {
          setMenuOpen((prev) => !prev);
          return true;
        }
        if (e.key === 'Backspace' || e.key === 'Escape') {
          if (menuOpen) {
            setMenuOpen(false);
            return true;
          }
          goBack();
          return true;
        }
        return false;
      },
    });
  }, [goBack, menuOpen, registerKeyInterceptor]);

  return (
    <AppStateContext.Provider
      value={{
        currentEntry,
        historyStack,
        navigate,
        goBack,
        resetToTopRoute,
        favoriteChannels,
        favoriteMovies,
        favoriteShows,
        toggleFavoriteChannel,
        toggleFavoriteMovie,
        toggleFavoriteShow,
        continueWatching,
        recentlyWatched,
        recordWatchProgress,
        activeChannelId,
        setActiveChannelId,
        stepChannel,
        settings,
        updateSettings,
        sessionReconnecting,
        setSessionReconnecting,
        pinRequest,
        openPinModal,
        closePinModal,
        unlockedIds,
        relockAll,
        isContentLocked,
        guardRestrictedAccess,
        menuOpen,
        setMenuOpen,
        toastMessage,
        showToast,
        pendingRestoreFocusId: pendingRestoreFocusRef.current,
        consumePendingRestoreFocus,
      }}
    >
      {children}
    </AppStateContext.Provider>
  );
}

export function useAppState() {
  const ctx = useContext(AppStateContext);
  if (!ctx) throw new Error('useAppState must be used within AppStateProvider');
  return ctx;
}
