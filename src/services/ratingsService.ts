import { useEffect, useState } from 'react';
import { ExternalRatings } from '../types/tv';

/**
 * Simulated lazy-fetch & local cache layer for IMDb + Rotten Tomatoes ratings.
 *
 * Production Kotlin / Fire TV architecture reference:
 * - Neither IMDb nor Rotten Tomatoes offers a free public API.
 * - Real open-source IPTV clients query OMDb API (returns IMDb + RT Tomatometer in one call)
 *   or MDBList API lazily per visible 14-item page or Detail view, caching responses in
 *   Room SQLite with a 7–30 day TTL.
 * - Regional/obscure VOD titles frequently lack an RT score (`rtCritics = null`), which
 *   this service models (~18% of items) so the UI demonstrates graceful degradation.
 */

const ratingsCache = new Map<string, ExternalRatings>();
const cacheListeners = new Set<() => void>();
let cacheVersion = 0;

function notifyCacheListeners() {
  cacheVersion += 1;
  cacheListeners.forEach((fn) => fn());
}

function hashString(input: string): number {
  let h = 2166136261;
  for (let i = 0; i < input.length; i += 1) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return Math.abs(h);
}

function formatVotes(rawVotes: number): string {
  if (rawVotes >= 100000) {
    return `${Math.round(rawVotes / 1000)}K`;
  }
  return `${(rawVotes / 1000).toFixed(1)}K`;
}

function computeSimulatedRatings(id: string, imdbRating: number, seed: number): ExternalRatings {
  const h = hashString(`${id}:${seed}`);

  // Votes between 4.2K and 285K, weighted slightly higher for stronger IMDb scores
  const baseVotes = 4200 + ((h % 140) * 1850) + (imdbRating >= 8.0 ? 48000 : 0);
  const imdbVotes = formatVotes(baseVotes);

  // ~18% of regional/niche catalog titles have no Rotten Tomatoes listing
  const hasRtScore = h % 11 !== 0 && h % 13 !== 0;

  if (!hasRtScore) {
    return {
      imdbScore: imdbRating,
      imdbVotes,
      rtCritics: null,
      rtAudience: null,
      source: 'OMDb / MDBList',
    };
  }

  // Correlate RT Critics loosely with IMDb rating, with realistic critic variance (+/- 14%)
  const normalizedBase = Math.round(((imdbRating - 4.5) / 4.8) * 60 + 36);
  const criticOffset = ((h >> 3) % 29) - 14;
  const rtCritics = Math.max(32, Math.min(98, normalizedBase + criticOffset));

  const audienceOffset = ((h >> 7) % 21) - 9;
  const rtAudience = Math.max(41, Math.min(97, rtCritics + audienceOffset));

  return {
    imdbScore: imdbRating,
    imdbVotes,
    rtCritics,
    rtAudience,
    source: 'OMDb / MDBList',
  };
}

export function getCachedRatings(id: string): ExternalRatings | undefined {
  return ratingsCache.get(id);
}

export function getRatingsCacheCount(): number {
  return ratingsCache.size;
}

export function clearRatingsCache(): void {
  ratingsCache.clear();
  notifyCacheListeners();
}

export function subscribeRatingsCache(listener: () => void): () => void {
  cacheListeners.add(listener);
  return () => {
    cacheListeners.delete(listener);
  };
}

export interface UseItemRatingsResult {
  status: 'idle' | 'loading' | 'ready';
  ratings: ExternalRatings | null;
  fromCache: boolean;
}

export function useItemRatings(
  id: string,
  imdbRating: number,
  seed: number,
  enabled: boolean
): UseItemRatingsResult {
  const [version, setVersion] = useState(cacheVersion);
  const [state, setState] = useState<UseItemRatingsResult>(() => {
    if (!enabled) return { status: 'idle', ratings: null, fromCache: false };
    const cached = ratingsCache.get(id);
    if (cached) return { status: 'ready', ratings: cached, fromCache: true };
    return { status: 'loading', ratings: null, fromCache: false };
  });

  useEffect(() => {
    return subscribeRatingsCache(() => {
      setVersion(cacheVersion);
    });
  }, []);

  useEffect(() => {
    if (!enabled) {
      setState({ status: 'idle', ratings: null, fromCache: false });
      return;
    }

    const existing = ratingsCache.get(id);
    if (existing) {
      setState({ status: 'ready', ratings: existing, fromCache: true });
      return;
    }

    setState({ status: 'loading', ratings: null, fromCache: false });

    // Staggered realistic async batch lookup (~240ms–460ms) simulating per-page OMDb/MDBList fetch
    const delayMs = 240 + (Math.abs(seed + hashString(id)) % 5) * 55;
    let cancelled = false;

    const timer = window.setTimeout(() => {
      if (cancelled) return;
      const fetched = computeSimulatedRatings(id, imdbRating, seed);
      const wasEmpty = !ratingsCache.has(id);
      ratingsCache.set(id, fetched);
      setState({ status: 'ready', ratings: fetched, fromCache: false });
      if (wasEmpty) {
        // Notify cache count observers (e.g., Settings screen) without resetting active items
        cacheListeners.forEach((fn) => fn());
      }
    }, delayMs);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [id, imdbRating, seed, enabled, version]);

  return state;
}

export function useRatingsCacheCount(): number {
  const [count, setCount] = useState<number>(() => ratingsCache.size);
  useEffect(() => {
    const sync = () => setCount(ratingsCache.size);
    sync();
    return subscribeRatingsCache(sync);
  }, []);
  return count;
}
