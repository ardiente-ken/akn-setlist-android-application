package com.aknevs.set.presentation.songdetail;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a song's lyrics text into a monospace chord chart.
 *
 * Two source formats are understood:
 *  1. Inline chords, e.g. "[C]Amazing [G]grace": chords are lifted onto their own line
 *     directly above the syllable they sit on, and long lines are word-wrapped to the
 *     screen width so chords stay aligned.
 *  2. Plain text (chords already typed on lines above the lyrics): spacing is kept
 *     exactly, and lines made only of chords are highlighted.
 * A line that is just "[Verse 1]" (not a chord) is shown as a section label.
 */
final class ChordChartRenderer {

    private static final Pattern BRACKET = Pattern.compile("\\[([^\\]\\[]+)\\]");
    private static final Pattern CHORD = Pattern.compile(
            "^[A-G][#b\u266F\u266D]?(?:maj|min|dim|aug|sus|add|m|M|\\+|\u00B0|\u00F8|-|\\d|#|b|\\(|\\)|,)*"
                    + "(?:/[A-G][#b\u266F\u266D]?)?$");
    private static final Pattern SEPARATOR_TOKEN =
            Pattern.compile("^(?:[|/\\-\u2013%.:]+|\\(?x\\d+\\)?)$");

    private ChordChartRenderer() {}

    static CharSequence render(String source, boolean showChords, int cols,
                               int chordColor, int lyricColor, int labelColor) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (source == null || source.isEmpty()) return out;

        String text = source.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        boolean inline = hasInlineChords(text);

        for (String line : text.split("\n", -1)) {
            if (line.trim().isEmpty()) {
                out.append('\n');
                continue;
            }
            String label = sectionLabel(line);
            if (label != null) {
                if (out.length() > 0 && !endsWithBlankLine(out)) out.append('\n');
                append(out, label, labelColor, true);
                out.append('\n');
            } else if (inline) {
                appendInlineLine(out, line, showChords, cols, chordColor, lyricColor);
            } else {
                appendPlainLine(out, line, showChords, chordColor, lyricColor);
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.delete(out.length() - 1, out.length());
        }
        return out;
    }

    // ---------- inline "[C]word" format ----------

    private static void appendInlineLine(SpannableStringBuilder out, String line,
                                         boolean showChords, int cols,
                                         int chordColor, int lyricColor) {
        Matcher m = BRACKET.matcher(line);
        StringBuilder lyric = new StringBuilder();
        List<Integer> positions = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int last = 0;
        while (m.find()) {
            lyric.append(line, last, m.start());
            String token = m.group(1).trim();
            if (CHORD.matcher(token).matches()) {
                positions.add(lyric.length());
                names.add(token);
            } else {
                lyric.append(m.group()); // not a chord: keep as literal text
            }
            last = m.end();
        }
        lyric.append(line, last, line.length());
        String text = lyric.toString();

        int start = 0;
        int ci = 0;
        do {
            int end;
            int next;
            if (text.length() - start <= cols) {
                end = text.length();
                next = text.length();
            } else {
                int limit = start + cols;
                int brk = text.lastIndexOf(' ', limit);
                if (brk <= start) {            // no space to break on: hard break
                    end = limit;
                    next = limit;
                } else {
                    end = brk;
                    next = brk;
                    while (next < text.length() && text.charAt(next) == ' ') next++;
                }
            }
            boolean lastChunk = next >= text.length();

            StringBuilder chordRow = new StringBuilder();
            boolean hasChords = false;
            while (ci < positions.size() && (lastChunk || positions.get(ci) < next)) {
                int col = positions.get(ci) - start;
                int min = chordRow.length() == 0 ? 0 : chordRow.length() + 1; // never overlap
                if (col < min) col = min;
                while (chordRow.length() < col) chordRow.append(' ');
                chordRow.append(names.get(ci));
                hasChords = true;
                ci++;
            }

            String chunk = text.substring(start, end);
            if (showChords && hasChords) {
                append(out, chordRow.toString(), chordColor, true);
                out.append('\n');
            }
            if (!chunk.trim().isEmpty()) {
                append(out, chunk, lyricColor, false);
                out.append('\n');
            }
            if (showChords && hasChords) appendSpacer(out);
            start = next;
        } while (start < text.length());
    }

    // ---------- plain text format ----------

    private static void appendPlainLine(SpannableStringBuilder out, String line,
                                        boolean showChords, int chordColor, int lyricColor) {
        if (isChordLine(line)) {
            if (!showChords) return;
            append(out, line, chordColor, true);
        } else {
            append(out, line, lyricColor, false);
        }
        out.append('\n');
    }

    private static boolean isChordLine(String line) {
        int chords = 0;
        for (String token : line.trim().split("\\s+")) {
            if (CHORD.matcher(token).matches()) chords++;
            else if (!SEPARATOR_TOKEN.matcher(token).matches()) return false;
        }
        return chords > 0;
    }

    // ---------- helpers ----------

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
