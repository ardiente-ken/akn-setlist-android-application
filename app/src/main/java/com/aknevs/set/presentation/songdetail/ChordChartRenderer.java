package com.aknevs.set.presentation.songdetail;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a song's lyrics text into a monospace chord chart with strict alignment.
 *
 * Rules that guarantee exact alignment and no overlaps:
 *  - Everything is monospace, so one character = one column in both rows.
 *  - A chord is NEVER moved. It sits exactly above the first letter that follows it.
 *    If a chord is wider than the lyric before the next chord, the LYRIC row is padded
 *    with spaces instead, so the next chord still lands over its own syllable.
 *  - Long lines are wrapped by cutting the chord row and lyric row at the same column,
 *    only where both rows are blank, so a chord is never separated from its lyric.
 *
 * Two source formats are understood:
 *  1. Inline chords, e.g. "[C]Amazing [G]grace".
 *  2. Plain text with chords already typed on the line above the lyrics. Each chord line
 *     is kept together with the lyric line under it.
 * A line that is just "[Verse 1]" (not a chord) is shown as a section label.
 */
final class ChordChartRenderer {

    private static final Pattern BRACKET = Pattern.compile("\\[([^\\]\\[]+)\\]");
    private static final Pattern CHORD = Pattern.compile(
            "^[A-G][#b\u266F\u266D]?(?:maj|min|dim|aug|sus|add|m|M|\\+|\u00B0|\u00F8|-|\\d|#|b|\\(|\\)|,)*"
                    + "(?:/[A-G][#b\u266F\u266D]?)?$");
    private static final Pattern SEPARATOR_TOKEN =
            Pattern.compile("^(?:[|/\\-\u2013%.:]+|\\(?x\\d+\\)?)$");

    private static final class Palette {
        final int chord;
        final int lyric;
        final int label;

        Palette(int chord, int lyric, int label) {
            this.chord = chord;
            this.lyric = lyric;
            this.label = label;
        }
    }

    private ChordChartRenderer() {}

    static CharSequence render(String source, boolean showChords, int cols,
                               int chordColor, int lyricColor, int labelColor) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (source == null || source.isEmpty()) return out;

        Palette palette = new Palette(chordColor, lyricColor, labelColor);
        String text = source.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        boolean inline = hasInlineChords(text);
        String[] lines = text.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.trim().isEmpty()) {
                out.append('\n');
                continue;
            }
            String label = sectionLabel(line);
            if (label != null) {
                if (out.length() > 0 && !endsWithBlankLine(out)) out.append('\n');
                append(out, label, palette.label, true);
                out.append('\n');
            } else if (inline) {
                String[] rows = buildInlineRows(line, showChords);
                appendAligned(out, rows[0], rows[1], showChords, cols, palette);
            } else if (isChordLine(line)) {
                // Keep a typed chord line together with the lyric line beneath it.
                String lyric = "";
                if (i + 1 < lines.length) {
                    String below = lines[i + 1];
                    if (!below.trim().isEmpty() && sectionLabel(below) == null
                            && !isChordLine(below)) {
                        lyric = below;
                        i++;
                    }
                }
                appendAligned(out, line, lyric, showChords, cols, palette);
            } else {
                appendAligned(out, "", line, showChords, cols, palette);
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.delete(out.length() - 1, out.length());
        }
        return out;
    }

    // ---------- building aligned rows ----------

    /**
     * Returns {chordRow, lyricRow}, column-aligned. With chords hidden, the lyric row is
     * the plain lyric with no padding, so words never show gaps that only chords explained.
     */
    private static String[] buildInlineRows(String line, boolean showChords) {
        StringBuilder chordRow = new StringBuilder();
        StringBuilder lyricRow = new StringBuilder();
        Matcher m = BRACKET.matcher(line);
        int last = 0;
        while (m.find()) {
            String token = m.group(1).trim();
            if (!CHORD.matcher(token).matches()) continue; // stays in the lyric as literal text
            lyricRow.append(line, last, m.start());
            last = m.end();
            if (!showChords) continue;
            // Previous chord must end before this one begins: pad the LYRIC, never move the chord.
            if (chordRow.length() > 0) {
                while (lyricRow.length() < chordRow.length() + 1) lyricRow.append(' ');
            }
            int col = lyricRow.length();
            while (chordRow.length() < col) chordRow.append(' ');
            chordRow.append(token);
        }
        lyricRow.append(line, last, line.length());
        return new String[]{chordRow.toString(), lyricRow.toString()};
    }

    /** Writes the two rows, wrapping both at identical columns so they stay aligned. */
    private static void appendAligned(SpannableStringBuilder out, String chordRow,
                                      String lyricRow, boolean showChords, int cols,
                                      Palette palette) {
        int len = Math.max(chordRow.length(), lyricRow.length());
        int start = 0;
        do {
            int end = len;
            int next = len;
            if (len - start > cols) {
                end = findBreak(chordRow, lyricRow, start, cols);
                next = end;
                while (next < len && charAt(chordRow, next) == ' ' && charAt(lyricRow, next) == ' ') {
                    next++;
                }
            }
            appendPair(out, slice(chordRow, start, end), slice(lyricRow, start, end),
                    showChords, palette);
            start = next;
        } while (start < len);
    }

    /** A column where BOTH rows are blank, so no chord or word is cut. Falls back to a hard cut. */
    private static int findBreak(String chordRow, String lyricRow, int start, int cols) {
        int limit = start + cols;
        for (int b = limit; b > start; b--) {
            if (charAt(lyricRow, b) == ' ' && charAt(chordRow, b) == ' ') return b;
        }
        int b = limit; // no gap at all: cut, but never through the middle of a chord
        while (b > start + 1 && charAt(chordRow, b) != ' ' && charAt(chordRow, b - 1) != ' ') b--;
        return b;
    }

    private static void appendPair(SpannableStringBuilder out, String chords, String lyrics,
                                   boolean showChords, Palette palette) {
        boolean chordsPresent = showChords && !chords.trim().isEmpty();
        boolean lyricsPresent = !lyrics.trim().isEmpty();
        if (chordsPresent) {
            append(out, rstrip(chords), palette.chord, true);
            out.append('\n');
        }
        if (lyricsPresent) {
            append(out, rstrip(lyrics), palette.lyric, false);
            out.append('\n');
        }
        if (chordsPresent && lyricsPresent) appendSpacer(out);
    }

    // ---------- helpers ----------

    private static boolean isChordLine(String line) {
        int chords = 0;
        for (String token : line.trim().split("\\s+")) {
            if (CHORD.matcher(token).matches()) chords++;
            else if (!SEPARATOR_TOKEN.matcher(token).matches()) return false;
        }
        return chords > 0;
    }

    private static boolean hasInlineChords(String text) {
        Matcher m = BRACKET.matcher(text);
        while (m.find()) {
            if (CHORD.matcher(m.group(1).trim()).matches()) return true;
        }
        return false;
    }

    /** "[Verse 1]" on its own line -> "Verse 1". A lone chord like "[C]" returns null. */
    private static String sectionLabel(String line) {
        Matcher m = BRACKET.matcher(line.trim());
        if (!m.matches()) return null;
        String token = m.group(1).trim();
        return CHORD.matcher(token).matches() ? null : token;
    }

    private static char charAt(String s, int i) {
        return i < s.length() ? s.charAt(i) : ' ';
    }

    private static String slice(String s, int from, int to) {
        if (from >= s.length()) return "";
        return s.substring(from, Math.min(to, s.length()));
    }

    private static String rstrip(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') end--;
        return s.substring(0, end);
    }

    private static boolean endsWithBlankLine(SpannableStringBuilder sb) {
        int n = sb.length();
        return n >= 2 && sb.charAt(n - 1) == '\n' && sb.charAt(n - 2) == '\n';
    }

    private static void append(SpannableStringBuilder out, String text, int color, boolean bold) {
        if (text.isEmpty()) return;
        int s = out.length();
        out.append(text);
        out.setSpan(new ForegroundColorSpan(color), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) {
            out.setSpan(new StyleSpan(Typeface.BOLD), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /** A half-height empty line separating chord/lyric pairs. */
    private static void appendSpacer(SpannableStringBuilder out) {
        int s = out.length();
        out.append('\n');
        out.setSpan(new RelativeSizeSpan(0.45f), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
