import React from 'react';
import { useAppState } from '../context/AppStateContext';
import { useItemRatings } from '../services/ratingsService';

/**
 * High-contrast IMDb star icon (5-point gold star with subtle dark stroke for 10-foot clarity).
 */
export function ImdbStarIcon({ className = 'w-6 h-6' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      className={`${className} shrink-0`}
    >
      <path
        d="M12 2.4L14.95 8.38L21.55 9.34L16.77 13.99L17.9 20.56L12 17.46L6.1 20.56L7.23 13.99L2.45 9.34L9.05 8.38L12 2.4Z"
        fill="#F5C518"
        stroke="#0B0E14"
        strokeWidth="1.2"
        strokeLinejoin="round"
      />
    </svg>
  );
}

/**
 * Distinct Fresh Tomato vs Rotten Splat glyphs with high-contrast silhouettes
 * so Rotten Tomatoes status is never communicated by color alone (10-foot accessibility).
 */
export function RtGlyph({
  score,
  className = 'w-5 h-5',
}: {
  score: number;
  className?: string;
}) {
  const isFresh = score >= 60;

  if (isFresh) {
    // High-contrast Fresh Tomato SVG
    return (
      <svg
        viewBox="0 0 24 24"
        fill="none"
        aria-hidden="true"
        className={`${className} shrink-0`}
      >
        <path
          d="M12 6.6C7.5 6.6 4 9.9 4 14.1C4 18.5 7.4 21.7 12 21.7C16.6 21.7 20 18.5 20 14.1C20 9.9 16.5 6.6 12 6.6Z"
          fill="#EF4444"
          stroke="#FEE2E2"
          strokeWidth="0.9"
        />
        <path
          d="M12 7.2C11.3 4.8 9.9 3.5 8 3.2M12 7.2C12.6 4.7 14.2 3.5 16.2 3.3M12 7.4V2.8"
          stroke="#34D399"
          strokeWidth="2.3"
          strokeLinecap="round"
        />
        <ellipse cx="9" cy="11.8" rx="1.8" ry="1.2" fill="#FFFFFF" opacity="0.7" />
      </svg>
    );
  }

  // High-contrast Rotten Splat SVG (distinct irregular multi-lobe splatter)
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      className={`${className} shrink-0`}
    >
      <path
        d="M12.2 2.8L14 6.9L18.5 5.1L16.9 9.5L21.4 11.4L17.3 14.1L19.4 18.5L14.6 17.2L12.6 21.4L10.2 17.4L5.5 19L7.2 14.5L2.8 12.1L7.1 9.8L5.2 5.3L9.8 7L12.2 2.8Z"
        fill="#A3E635"
        stroke="#ECFCCB"
        strokeWidth="0.85"
        strokeLinejoin="round"
      />
      <circle cx="11.8" cy="12.2" r="2.4" fill="#1A2E05" opacity="0.7" />
    </svg>
  );
}

/**
 * High-contrast Popcorn Bucket icon for Rotten Tomatoes Audience score.
 */
export function AudiencePopcornIcon({ className = 'w-6 h-6' }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      className={`${className} shrink-0`}
    >
      <circle cx="8.2" cy="7.2" r="2.4" fill="#FDE047" />
      <circle cx="12" cy="5.8" r="2.6" fill="#FEF08A" />
      <circle cx="15.8" cy="7.2" r="2.4" fill="#FDE047" />
      <path
        d="M5.8 9.6H18.2L16.6 20.8H7.4L5.8 9.6Z"
        fill="#F8FAFC"
        stroke="#0B0E14"
        strokeWidth="1"
        strokeLinejoin="round"
      />
      <path d="M9.6 9.8L10.2 20.6M14.4 9.8L13.8 20.6" stroke="#EF4444" strokeWidth="2.1" />
    </svg>
  );
}

/**
 * Measured bottom scrim overlay for 2:3 Movie and Show poster cards.
 * Fetches ratings lazily per mounted poster (matching 14-item API paging) and
 * displays a low-GPU skeleton while fetching, then IMDb + RT scores once cached.
 */
export function PosterRatingsOverlay({
  itemId,
  imdbRating,
  seed,
  compact = false,
}: {
  itemId: string;
  imdbRating: number;
  seed: number;
  compact?: boolean;
}) {
  const { settings } = useAppState();
  const { status, ratings } = useItemRatings(
    itemId,
    imdbRating,
    seed,
    settings.showExternalRatings
  );

  if (!settings.showExternalRatings) {
    return null;
  }

  const textClass = compact ? 'text-[18px]' : 'text-[20px]';
  const iconClass = compact ? 'w-[18px] h-[18px]' : 'w-[20px] h-[20px]';

  return (
    <div className="absolute inset-x-0 bottom-0 pt-8 pb-2.5 px-3 bg-gradient-to-t from-[#070A0F]/96 via-[#070A0F]/82 to-transparent pointer-events-none">
      {status === 'loading' || !ratings ? (
        <div className="flex items-center justify-between gap-2">
          <div className="h-[22px] w-[62px] rounded-[5px] tv-skeleton" />
          <div className="h-[22px] w-[58px] rounded-[5px] tv-skeleton" />
        </div>
      ) : (
        <div className="flex items-center justify-between gap-2">
          {/* IMDb Score */}
          <span
            className={`inline-flex items-center gap-1 font-mono-tv ${textClass} font-bold text-[#FBBF24] leading-none`}
          >
            <ImdbStarIcon className={iconClass} />
            <span>{ratings.imdbScore.toFixed(1)}</span>
          </span>

          {/* Rotten Tomatoes Tomatometer (gracefully omitted if unavailable) */}
          {ratings.rtCritics !== null ? (
            <span
              className={`inline-flex items-center gap-1.5 font-mono-tv ${textClass} font-bold leading-none ${
                ratings.rtCritics >= 60 ? 'text-[#F3F6FA]' : 'text-[#BEF264]'
              }`}
            >
              <RtGlyph score={ratings.rtCritics} className={iconClass} />
              <span>{ratings.rtCritics}%</span>
            </span>
          ) : (
            <span className="font-mono-tv text-[16px] font-semibold text-[#7B889C] leading-none">
              IMDb
            </span>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * High-contrast IMDb & Rotten Tomatoes rating badges for Movie Detail and Show Detail screens.
 * Uses distinct, high-contrast vector icons, explicit Fresh/Rotten text labels, and tabular numerals.
 */
export function DetailRatingsStrip({
  itemId,
  imdbRating,
  seed,
  compact = false,
}: {
  itemId: string;
  imdbRating: number;
  seed: number;
  compact?: boolean;
}) {
  const { settings } = useAppState();
  const { status, ratings, fromCache } = useItemRatings(
    itemId,
    imdbRating,
    seed,
    settings.showExternalRatings
  );

  if (!settings.showExternalRatings) {
    return (
      <div className="flex items-center flex-wrap gap-4">
        <div className="inline-flex items-center gap-2.5 px-4 py-2 rounded-[10px] bg-[#10141C] border border-[#F5C518]/60">
          <span className="px-2 py-0.5 rounded-[5px] bg-[#F5C518] text-[#0B0E14] font-mono-tv text-[18px] font-extrabold leading-none">
            IMDb
          </span>
          <ImdbStarIcon className="w-6 h-6" />
          <span className="font-mono-tv text-[24px] font-bold text-[#F3F6FA] leading-none">
            {imdbRating.toFixed(1)}
          </span>
          <span className="font-mono-tv text-[19px] text-[#7B889C] leading-none">/ 10</span>
        </div>
        <span className="text-[20px] text-[#7B889C]">
          Rotten Tomatoes overlay hidden in Settings
        </span>
      </div>
    );
  }

  if (status === 'loading' || !ratings) {
    return (
      <div className="flex items-center flex-wrap gap-3.5">
        <div
          className={`${
            compact ? 'h-[48px] w-[230px]' : 'h-[56px] w-[260px]'
          } rounded-[10px] tv-skeleton border border-[#262F40]`}
        />
        <div
          className={`${
            compact ? 'h-[48px] w-[230px]' : 'h-[56px] w-[260px]'
          } rounded-[10px] tv-skeleton border border-[#262F40]`}
        />
        <div
          className={`${
            compact ? 'h-[48px] w-[190px]' : 'h-[56px] w-[210px]'
          } rounded-[10px] tv-skeleton border border-[#262F40]`}
        />
        <span className="font-mono-tv text-[19px] text-[#7B889C]">
          Fetching IMDb &amp; RT…
        </span>
      </div>
    );
  }

  const rtFresh = ratings.rtCritics !== null && ratings.rtCritics >= 60;
  const padClass = compact ? 'px-3.5 py-2' : 'px-4.5 py-2.5';
  const scoreClass = compact ? 'text-[24px]' : 'text-[28px]';
  const iconClass = compact ? 'w-6 h-6' : 'w-7 h-7';

  return (
    <div className="flex items-center flex-wrap gap-3.5">
      {/* IMDb High-Contrast Rating Badge */}
      <div
        className={`inline-flex items-center gap-2.5 ${padClass} rounded-[10px] bg-[#10141C] border border-[#F5C518]/70`}
      >
        <span className="px-2 py-1 rounded-[5px] bg-[#F5C518] text-[#0B0E14] font-mono-tv text-[18px] font-extrabold tracking-tight leading-none">
          IMDb
        </span>
        <ImdbStarIcon className={iconClass} />
        <span className={`font-mono-tv font-bold text-[#F3F6FA] ${scoreClass} leading-none`}>
          {ratings.imdbScore.toFixed(1)}
        </span>
        <span className="font-mono-tv text-[19px] text-[#A6B3C6] leading-none">
          /10 · {ratings.imdbVotes}
        </span>
      </div>

      {/* Rotten Tomatoes Critics High-Contrast Rating Badge */}
      {ratings.rtCritics !== null ? (
        <div
          className={`inline-flex items-center gap-2.5 ${padClass} rounded-[10px] bg-[#10141C] border ${
            rtFresh ? 'border-[#EF4444]/75' : 'border-[#A3E635]/75'
          }`}
        >
          <RtGlyph score={ratings.rtCritics} className={iconClass} />
          <span
            className={`font-mono-tv font-bold leading-none ${scoreClass} ${
              rtFresh ? 'text-[#F3F6FA]' : 'text-[#BEF264]'
            }`}
          >
            {ratings.rtCritics}%
          </span>
          <span
            className={`text-[20px] font-bold leading-none ${
              rtFresh ? 'text-[#FCA5A5]' : 'text-[#A3E635]'
            }`}
          >
            {rtFresh ? 'Fresh' : 'Rotten'}
          </span>
        </div>
      ) : (
        <div
          className={`inline-flex items-center gap-2.5 ${padClass} rounded-[10px] bg-[#10141C] border border-[#334155]`}
        >
          <RtGlyph score={75} className={`${iconClass} opacity-40`} />
          <span className="text-[20px] font-semibold text-[#7B889C] leading-none">
            RT: Not rated
          </span>
        </div>
      )}

      {/* Rotten Tomatoes Audience High-Contrast Badge */}
      {ratings.rtCritics !== null && ratings.rtAudience !== null && (
        <div
          className={`inline-flex items-center gap-2.5 ${padClass} rounded-[10px] bg-[#10141C] border border-[#38BDF8]/55`}
        >
          <AudiencePopcornIcon className={iconClass} />
          <span className={`font-mono-tv font-bold text-[#F3F6FA] ${scoreClass} leading-none`}>
            {ratings.rtAudience}%
          </span>
          <span className="text-[20px] font-semibold text-[#A6B3C6] leading-none">Audience</span>
        </div>
      )}

      {/* Subtle Cache / Provider Status */}
      <span className="font-mono-tv text-[18px] text-[#7B889C] pl-1">
        {fromCache ? 'Cached' : 'Fetched'} · {ratings.source}
      </span>
    </div>
  );
}

/**
 * Compact inline IMDb + RT readout for Favorites list rows.
 */
export function InlineRatingsReadout({
  itemId,
  imdbRating,
  seed,
}: {
  itemId: string;
  imdbRating: number;
  seed: number;
}) {
  const { settings } = useAppState();
  const { status, ratings } = useItemRatings(
    itemId,
    imdbRating,
    seed,
    settings.showExternalRatings
  );

  return (
    <span className="inline-flex items-center gap-2.5 shrink-0">
      <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-[6px] bg-[#10141C] border border-[#F5C518]/55 text-[#FBBF24] font-mono-tv text-[20px] font-bold leading-tight">
        <ImdbStarIcon className="w-[18px] h-[18px]" />
        <span>{imdbRating.toFixed(1)}</span>
      </span>

      {settings.showExternalRatings &&
        (status === 'loading' ? (
          <span className="inline-block w-[68px] h-[28px] rounded-[6px] tv-skeleton" />
        ) : ratings && ratings.rtCritics !== null ? (
          <span
            className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-[6px] bg-[#10141C] border font-mono-tv text-[20px] font-bold leading-tight ${
              ratings.rtCritics >= 60
                ? 'border-[#EF4444]/60 text-[#F3F6FA]'
                : 'border-[#A3E635]/60 text-[#BEF264]'
            }`}
          >
            <RtGlyph score={ratings.rtCritics} className="w-[18px] h-[18px]" />
            <span>{ratings.rtCritics}%</span>
          </span>
        ) : null)}
    </span>
  );
}
