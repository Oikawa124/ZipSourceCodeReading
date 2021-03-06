package com.livejournal.karino2.zipsourcecodereading.text

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.text.GetChars
import android.text.Spannable
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.LeadingMarginSpan
import android.text.style.ReplacementSpan
import android.text.style.TabStopSpan


/**
 * Created by _ on 2017/10/03.
 * ReadOnly, no emoji support, no rtl support.
 */
class Layout(val text: Spannable, val textPaint: TextPaint, var width: Int, val spacingmult: Float, val spacingadd: Float, val includepad: Boolean) {
    var mFontMetricsInt: Paint.FontMetricsInt? =  Paint.FontMetricsInt()
    private var mChs: CharArray? = null
    private var mWidths: FloatArray? = null

    companion object {
        fun create(text: Spannable, textPaint: TextPaint, width: Int, spacingmult: Float, spacingadd: Float, includepad: Boolean): Layout {
            val ret = Layout(text, textPaint, width, spacingmult, spacingadd, includepad)
            ret.generate(text, 0, text.length, textPaint, width, spacingmult, spacingadd, includepad, includepad, false, false)

            ret.mChs = null
            ret.mWidths = null
            ret.mFontMetricsInt = null
            return ret
        }
    }


    var lineCount = 0

    var lines : IntArray = IntArray(ArrayUtils.idealIntArraySize(2))
    var fileLines : IntArray = IntArray(ArrayUtils.idealIntArraySize(2))

    val START = 0
    val TAB = 0
    val START_MASK = 0x1FFFFFFF
    val TAB_MASK = 0x20000000


    fun getLineStart(line : Int)  : Int {
        val start = lines[line+START] and START_MASK
        if(line == 0 || start != 0) {
            return start
        }
        // if line == linecount, should retrun text length.
        return text.length
    }

    fun getLineContainsTab(line: Int) =  lines[line + TAB] and TAB_MASK !== 0

    fun getParagraphLeft(line: Int) : Int {

        var left = 0

        var par = false
        val off = getLineStart(line)
        if (off == 0 || (text as java.lang.CharSequence).charAt(off - 1) === '\n')
            par = true

        val sp = text
        val spans = sp.getSpans(getLineStart(line),
                getLineEnd(line),
                LeadingMarginSpan::class.java)

        for (i in spans.indices) {
            var margin = par
            val span = spans[i]
            if (span is LeadingMarginSpan.LeadingMarginSpan2) {
                val count = span.leadingMarginLineCount
                margin = count >= line
            }
            left += span.getLeadingMargin(margin)
        }
        return left
    }

    fun getLineVisibleEnd(line: Int) =  getLineVisibleEnd(line, getLineStart(line), getLineStart(line+1))

    fun getLineMax(line : Int) : Float {
        val start = getLineStart(line)
        val end = getLineVisibleEnd(line)

        val tab = getLineContainsTab(line)


        return measureText(textPaint, workPaint,
                text, start, end, null, tab)

    }

    fun getParagraphRight(line : Int) : Int {
        var right = width

        var par = false
        val off = getLineStart(line)
        if (off == 0 || (text as java.lang.CharSequence).charAt(off - 1) === '\n')
            par = true

        val sp = text
        val spans = sp.getSpans(getLineStart(line),
                getLineEnd(line),
                LeadingMarginSpan::class.java)

        for (i in spans.indices) {
            right -= spans[i].getLeadingMargin(par)
        }
        return right
    }

    fun getLineRight(line : Int) : Float {
        val align = getParagraphAlignment(line)
        when(align) {
            android.text.Layout.Alignment.ALIGN_NORMAL -> {
                return getParagraphLeft(line) + getLineMax(line)
            }
            android.text.Layout.Alignment.ALIGN_OPPOSITE-> {
                return width.toFloat()
            }
            else -> {
                val left = getParagraphLeft(line)
                val right = getParagraphRight(line)
                val max = getLineMax(line) as Int and 1.inv()

                return (right - (right - left - max) / 2).toFloat()
            }
        }

    }

    // for non-rtl support and gravity is always normal, this function always return 0
    fun getLineLeft(line: Int) = 0f

    /**
     * Return the text offset after the last character on the specified line.
     */
    fun getLineEnd(line: Int) = getLineStart(line + 1)


    private fun addSelection(line: Int, start: Int, end: Int,
                             top: Int, bottom: Int, dest: Path) {
        val linestart = getLineStart(line)
        var lineend = getLineEnd(line)
        if (lineend > linestart && (text as java.lang.CharSequence).charAt(lineend - 1) === '\n')
            lineend--

        var here = linestart

        var there = here + DIRECTION_ZERO
        if (there > lineend)
            there = lineend

        if (start <= there && end >= here) {
            val st = Math.max(start, here)
            val en = Math.min(end, there)

            if (st != en) {
                val h1 = getHorizontal(st, false, false, line)
                val h2 = getHorizontal(en, true, false, line)

                dest.addRect(h1, top.toFloat(), h2, bottom.toFloat(), Path.Direction.CW)
            }
        }

    }


    /**
     * Fills in the specified Path with a representation of a highlight
     * between the specified offsets.  This will often be a rectangle
     * or a potentially discontinuous set of rectangles.  If the start
     * and end are the same, the returned path is empty.
     */
    fun getSelectionPath(start: Int, end: Int, dest: Path) {
        var start = start
        var end = end
        dest.reset()

        if (start == end)
            return

        if (end < start) {
            val temp = end
            end = start
            start = temp
        }

        val startline = getLineForOffset(start)
        val endline = getLineForOffset(end)

        var top = getLineTop(startline)
        var bottom = getLineBottom(endline)

        if (startline == endline) {
            addSelection(startline, start, end, top, bottom, dest)
        } else {

            addSelection(startline, start, getLineEnd(startline),
                    top, getLineBottom(startline), dest)

            dest.addRect(getLineRight(startline), top.toFloat(),
                    width.toFloat(), getLineBottom(startline).toFloat(), Path.Direction.CW)

            for (i in startline + 1 until endline) {
                top = getLineTop(i)
                bottom = getLineBottom(i)
                dest.addRect(0f, top.toFloat(), width.toFloat(), bottom.toFloat(), Path.Direction.CW)
            }

            top = getLineTop(endline)
            bottom = getLineBottom(endline)

            addSelection(endline, getLineStart(endline), end,
                    top, bottom, dest)

            dest.addRect(0f, top.toFloat(), getLineLeft(endline), bottom.toFloat(), Path.Direction.CW)
        }
    }


    /**
     * Get the alignment of the specified paragraph, taking into account
     * markup attached to it.
     */
    fun getParagraphAlignment(line: Int): android.text.Layout.Alignment {
        var align = android.text.Layout.Alignment.ALIGN_NORMAL

        val sp = text
        val spans = sp.getSpans(getLineStart(line),
                getLineEnd(line),
                AlignmentSpan::class.java)

        val spanLength = spans.size
        if (spanLength > 0) {
            align = spans[spanLength - 1].alignment
        }

        return align
    }

    private val FIRST_CJK = '\u2E80'
    /**
     * Returns true if the specified character is one of those specified
     * as being Ideographic (class ID) by the Unicode Line Breaking Algorithm
     * (http://www.unicode.org/unicode/reports/tr14/), and is therefore OK
     * to break between a pair of.
     *
     * @param includeNonStarters also return true for category NS
     * (non-starters), which can be broken
     * after but not before.
     */
    private fun isIdeographic(c: Char, includeNonStarters: Boolean): Boolean {
        if (c in '\u2E80'..'\u2FFF') {
            return true // CJK, KANGXI RADICALS, DESCRIPTION SYMBOLS
        }
        if (c == '\u3000') {
            return true // IDEOGRAPHIC SPACE
        }
        if (c in '\u3001'..'\u303F') {
            if (!includeNonStarters) {
                when (c) {
                    '\u3001' //  # IDEOGRAPHIC COMMA
                        , '\u3002' //  # IDEOGRAPHIC FULL STOP
                    -> return false
                }
            }
            return true // Japanese Symbols
        }
        if (c in '\u3040'..'\u309F') {
            if (!includeNonStarters) {
                when (c) {
                    '\u3041' //  # HIRAGANA LETTER SMALL A
                        , '\u3043' //  # HIRAGANA LETTER SMALL I
                        , '\u3045' //  # HIRAGANA LETTER SMALL U
                        , '\u3047' //  # HIRAGANA LETTER SMALL E
                        , '\u3049' //  # HIRAGANA LETTER SMALL O
                        , '\u3063' //  # HIRAGANA LETTER SMALL TU
                        , '\u3083' //  # HIRAGANA LETTER SMALL YA
                        , '\u3085' //  # HIRAGANA LETTER SMALL YU
                        , '\u3087' //  # HIRAGANA LETTER SMALL YO
                        , '\u308E' //  # HIRAGANA LETTER SMALL WA
                        , '\u3095' //  # HIRAGANA LETTER SMALL KA
                        , '\u3096' //  # HIRAGANA LETTER SMALL KE
                        , '\u309B' //  # KATAKANA-HIRAGANA VOICED SOUND MARK
                        , '\u309C' //  # KATAKANA-HIRAGANA SEMI-VOICED SOUND MARK
                        , '\u309D' //  # HIRAGANA ITERATION MARK
                        , '\u309E' //  # HIRAGANA VOICED ITERATION MARK
                    -> return false
                }
            }
            return true // Hiragana (except small characters)
        }
        if (c in '\u30A0'..'\u30FF') {
            if (!includeNonStarters) {
                when (c) {
                    '\u30A0' //  # KATAKANA-HIRAGANA DOUBLE HYPHEN
                        , '\u30A1' //  # KATAKANA LETTER SMALL A
                        , '\u30A3' //  # KATAKANA LETTER SMALL I
                        , '\u30A5' //  # KATAKANA LETTER SMALL U
                        , '\u30A7' //  # KATAKANA LETTER SMALL E
                        , '\u30A9' //  # KATAKANA LETTER SMALL O
                        , '\u30C3' //  # KATAKANA LETTER SMALL TU
                        , '\u30E3' //  # KATAKANA LETTER SMALL YA
                        , '\u30E5' //  # KATAKANA LETTER SMALL YU
                        , '\u30E7' //  # KATAKANA LETTER SMALL YO
                        , '\u30EE' //  # KATAKANA LETTER SMALL WA
                        , '\u30F5' //  # KATAKANA LETTER SMALL KA
                        , '\u30F6' //  # KATAKANA LETTER SMALL KE
                        , '\u30FB' //  # KATAKANA MIDDLE DOT
                        , '\u30FC' //  # KATAKANA-HIRAGANA PROLONGED SOUND MARK
                        , '\u30FD' //  # KATAKANA ITERATION MARK
                        , '\u30FE' //  # KATAKANA VOICED ITERATION MARK
                    -> return false
                }
            }
            return true // Katakana (except small characters)
        }
        if (c in '\u3400'..'\u4DB5') {
            return true // CJK UNIFIED IDEOGRAPHS EXTENSION A
        }
        if (c in '\u4E00'..'\u9FBB') {
            return true // CJK UNIFIED IDEOGRAPHS
        }
        if (c in '\uF900'..'\uFAD9') {
            return true // CJK COMPATIBILITY IDEOGRAPHS
        }
        if (c in '\uA000'..'\uA48F') {
            return true // YI SYLLABLES
        }
        if (c in '\uA490'..'\uA4CF') {
            return true // YI RADICALS
        }
        if (c in '\uFE62'..'\uFE66') {
            return true // SMALL PLUS SIGN to SMALL EQUALS SIGN
        }
        return if (c in '\uFF10'..'\uFF19') {
            true // WIDE DIGITS
        } else false

    }

    fun fileLineToVLine(fline: Int) : Int{
        if(lineCount == 0)
            return 0

        val last = fileLines[lineCount-1]

        if(fline > last)
            return Math.max(0, lineCount)

        var pos = fline
        while(pos < lineCount && fileLines[pos] < fline) {
            pos++
        }
        return pos
    }

    fun generate(source:Spannable, bufstart:Int, bufend:Int, paint:TextPaint, outerwidth:Int,
        spacingmult:Float, spacingadd:Float,
        includepad:Boolean, trackpad:Boolean,
        breakOnlyAtSpaces:Boolean, showTab:Boolean) {
        lineCount = 0
        var curFileLine = 0

        var v = 0
        val needMultiply = spacingmult != 1f || spacingadd != 0f

        val fm = mFontMetricsInt!!

        var end = TextUtils.indexOf(source, '\n', bufstart, bufend)
        val bufsiz = if (end >= 0) end - bufstart else bufend - bufstart
        var first = true


        if (mChs == null)
        {
            mChs = CharArray(ArrayUtils.idealCharArraySize(bufsiz + 1))
            mWidths = FloatArray(ArrayUtils.idealIntArraySize((bufsiz + 1) * 2))
        }

        var chs = mChs
        var widths = mWidths

        var start = bufstart
        while (start <= bufend)
        {
            if (first)
                first = false
            else
                end = TextUtils.indexOf(source, '\n', start, bufend)

            if (end < 0)
                end = bufend
            else
                end++

            var firstWidthLineCount = 1


            if (end - start > chs!!.size)
            {
                chs = CharArray(ArrayUtils.idealCharArraySize(end - start))
                mChs = chs
            }
            if ((end - start) * 2 > widths!!.size)
            {
                widths = FloatArray(ArrayUtils.idealIntArraySize((end - start) * 2))
                mWidths = widths
            }

            (source as GetChars).getChars(start, end, chs, 0)
            val n = end - start


            // Do not supp;ort rtl


            val sub = source



            var width = outerwidth

            var w = 0f
            var here = start

            var ok = start
            var okascent = 0
            var okdescent = 0
            var oktop = 0
            var okbottom = 0

            var fit = start
            var fitascent = 0
            var fitdescent = 0
            var fittop = 0
            var fitbottom = 0

            var tab = false

            var next:Int
            var i = start
            while (i < end)
            {
                next = end

                paint.getTextWidths(sub, i, next, widths)
                System.arraycopy(widths, 0, widths,
                end - start + (i - start), next - i)

                paint.getFontMetricsInt(fm)


                val fmtop = fm.top
                val fmbottom = fm.bottom
                val fmascent = fm.ascent
                val fmdescent = fm.descent


                var j = i
                while (j < next)
                {
                     val c = chs[j - start]


                    if (c == '\n')
                    {
                        if (!tab) tab = showTab
                    }
                    else if (c == '\t')
                    {
                        w = nextTab(w)
                        tab = true
                    } /* emoji not supported */
                    else
                    {

	                    if (c.toInt() == 0x3000)
                        { // ideographic space ( for Japanese )
                            if (!tab) tab = showTab
                        }
                        w += widths[j - start + (end - start)]
                    }

                    if (w <= width)
                    {
                        fit = j + 1

                        if (fmtop < fittop)
                            fittop = fmtop
                        if (fmascent < fitascent)
                            fitascent = fmascent
                        if (fmdescent > fitdescent)
                            fitdescent = fmdescent
                        if (fmbottom > fitbottom)
                            fitbottom = fmbottom

                        /*
                         * From the Unicode Line Breaking Algorithm:
                         * (at least approximately)
                         *
                         * .,:; are class IS: breakpoints
                         *      except when adjacent to digits
                         * /    is class SY: a breakpoint
                         *      except when followed by a digit.
                         * -    is class HY: a breakpoint
                         *      except when followed by a digit.
                         *
                         * Ideographs are class ID: breakpoints when adjacent,
                         * except for NS (non-starters), which can be broken
                         * after but not before.
                         */

                        if (((c == ' ') || (c == '\t')
                            ||
                                (((c == '.') || (c == ',') || (c == ':') || (c == ';')) &&
                                    ((j - 1 < here) || !Character.isDigit(chs[j - 1 - start])) &&
                                    ((j + 1 >= next) || !Character.isDigit(chs[j + 1 - start])))
                            ||
                                ((((c == '/') || (c == '-')) && ((j + 1 >= next) || !Character.isDigit(chs[j + 1 - start]))))
                            ||
                                (((c >= FIRST_CJK) && isIdeographic(c, true) && (j + 1 < next)
                                        && isIdeographic(chs[j + 1 - start], false)))))
                        {
                            ok = j + 1

                            if (fittop < oktop)
                                oktop = fittop
                            if (fitascent < okascent)
                                okascent = fitascent
                            if (fitdescent > okdescent)
                                okdescent = fitdescent
                            if (fitbottom > okbottom)
                                okbottom = fitbottom
                        }
                    }
                    else if (breakOnlyAtSpaces)
                    {
                        if (ok != here)
                        {
                         // Log.e("text", "output ok " + here + " to " +ok);

                            while (ok < next && chs[ok - start] == ' ')
                            {
                                ok++
                            }

                            v = out(source, curFileLine,
                                here, ok,
                                okascent, okdescent, oktop, okbottom,
                                v,
                                spacingmult, spacingadd, fm, tab,
                                needMultiply,
                                ok == bufend, includepad, trackpad)

                            here = ok
                        }
                        else
                        {
                         // Act like it fit even though it didn't.

                            fit = j + 1

                            if (fmtop < fittop)
                                fittop = fmtop
                            if (fmascent < fitascent)
                                fitascent = fmascent
                            if (fmdescent > fitdescent)
                                fitdescent = fmdescent
                            if (fmbottom > fitbottom)
                                fitbottom = fmbottom
                        }
                    }
                    else
                    {
                        if (ok != here)
                        {
                         // Log.e("text", "output ok " + here + " to " +ok);

                            while (ok < next && chs[ok - start] == ' ')
                            {
                                ok++
                            }

                            v = out(source, curFileLine,
                                here, ok,
                                okascent, okdescent, oktop, okbottom,
                                v,
                                spacingmult, spacingadd, fm, tab,
                                needMultiply,
                                ok == bufend, includepad, trackpad)

                            here = ok
                        }
                        else if (fit != here)
                        {
                     // Log.e("text", "output fit " + here + " to " +fit);

                            v = out(source, curFileLine,
                                here, fit,
                                fitascent, fitdescent,
                                fittop, fitbottom,
                                v,
                                spacingmult, spacingadd, fm, tab,
                                needMultiply,
                                fit == bufend, includepad, trackpad)

                            here = fit
                        }
                        else
                        {
                         // Log.e("text", "output one " + here + " to " +(here + 1));
                            measureText(paint, workPaint,
                                source, here, here + 1, fm, tab)

                            v = out(source, curFileLine,
                                here, here + 1,
                                fm.ascent, fm.descent,
                                fm.top, fm.bottom,
                                v,
                                spacingmult, spacingadd, fm, tab,
                                needMultiply,
                                here + 1 == bufend, includepad,
                                trackpad)

                            here = here + 1
                        }

                        if (here < i)
                        {
                            next = here
                            j = next // must remeasure
                        }
                        else
                        {
                            j = here - 1    // continue looping
                        }

                        fit = here
                        ok = fit
                        w = 0f
                        fitbottom = 0
                        fittop = fitbottom
                        fitdescent = fittop
                        fitascent = fitdescent
                        okbottom = 0
                        oktop = okbottom
                        okdescent = oktop
                        okascent = okdescent

                        if (--firstWidthLineCount <= 0)
                        {
                            width = outerwidth
                        }
                    }
                    j++
                }
                i = next
            }

            if (end != here)
            {
                if ((fittop or fitbottom or fitdescent or fitascent) == 0)
                {
                    paint.getFontMetricsInt(fm)

                    fittop = fm.top
                    fitbottom = fm.bottom
                    fitascent = fm.ascent
                    fitdescent = fm.descent
                }

                 // Log.e("text", "output rest " + here + " to " + end);

                v = out(source, curFileLine,
                    here, end, fitascent, fitdescent,
                    fittop, fitbottom,
                    v,
                    spacingmult, spacingadd, fm, tab,
                    needMultiply,
                    end == bufend, includepad, trackpad)
            }

            start = end

            if (end == bufend)
                break
            start = end
            curFileLine++
        }

        if (bufend == bufstart || source[bufend - 1] == '\n')
        {
             // Log.e("text", "output last " + bufend);

            paint.getFontMetricsInt(fm)

            v = out(source,
                    curFileLine,
                bufend, bufend, fm.ascent, fm.descent,
                fm.top, fm.bottom,
                v,
                spacingmult, spacingadd, fm, false,
                needMultiply,
                true, includepad, trackpad)
        }
    }

    private val TOP = 1
    var mTopPadding = 0
    var mBottomPadding = 0


    private fun out(text: Spannable, curFileLine: Int,  start: Int, end: Int,
                    above: Int, below: Int, top: Int, bottom: Int, v: Int,
                    spacingmult: Float, spacingadd: Float,
                    fm: Paint.FontMetricsInt?, tab: Boolean,
                    needMultiply: Boolean, last: Boolean,
                    includepad: Boolean, trackpad: Boolean): Int {
        var above = above
        var below = below
        var top = top
        var bottom = bottom
        val j = lineCount
        val off = j * 1
        val want = off + 1 + TOP
        var tmplines = this.lines

        // Log.e("text", "line " + start + " to " + end + (last ? "===" : ""));

        if (want >= tmplines.size) {
            val nlen = ArrayUtils.idealIntArraySize(want + 1)
            val grow = IntArray(nlen)
            System.arraycopy(lines, 0, grow, 0, lines.size)
            this.lines = grow
            tmplines = grow

            val growFileLine = IntArray(nlen)
            System.arraycopy(fileLines, 0, growFileLine, 0, fileLines.size)
            this.fileLines = growFileLine
        }

        fileLines[off] = curFileLine

        if (j == 0) {
            if (trackpad) {
                mTopPadding = top - above
            }

            if (includepad) {
                above = top
            }
        }
        if (last) {
            if (trackpad) {
                mBottomPadding = bottom - below
            }

            if (includepad) {
                below = bottom
            }
        }

        val extra: Int

        if (needMultiply) {
            val ex = ((below - above) * (spacingmult - 1) + spacingadd).toDouble()
            if (ex >= 0) {
                extra = (ex + 0.5).toInt()
            } else {
                extra = -(-ex + 0.5).toInt()
            }
        } else {
            extra = 0
        }

        lines[off + START] = start

        _height = below - above + extra
        descent = below + extra

        if (tab)
            lines[off + TAB] = lines[off + TAB] or TAB_MASK

        // do not support RTL
        // lines[off + DIR] = lines[off + DIR] or (dir shl DIR_SHIFT)

        var cur = Character.DIRECTIONALITY_LEFT_TO_RIGHT.toInt()
        var count = 0

        lineCount++
        return v
    }


    private fun measureText(paint: TextPaint,
                            workPaint: TextPaint,
                            text: Spannable,
                            start: Int, offset: Int, end: Int,
                            trailing: Boolean, alt: Boolean,
                            hasTabs: Boolean): Float {
        var trailing = trailing
        var buf: CharArray? = null

        if (hasTabs) {
            buf = TextUtils.obtain(end - start)
            (text as GetChars).getChars(start, end, buf, 0)
        }

        var h = 0f

        var here = 0

        if (alt)
            trailing = !trailing

        var there = here + DIRECTION_ZERO
        if (there > end - start)
            there = end - start

        var segstart = here
        var j = if (hasTabs) here else there
        while (j <= there) {
            var codept = 0

            if (hasTabs && j < there) {
                codept = buf!![j].toInt()
            }

            if (codept >= 0xD800 && codept <= 0xDFFF && j + 1 < there) {
                codept = Character.codePointAt(buf, j)

                /*
                if (codept >= MIN_EMOJI && codept <= MAX_EMOJI) {
                    bm = EMOJI_FACTORY.getBitmapFromAndroidPua(codept)
                }
                */
            }

            if (j == there || codept == '\t'.toInt()) {
                val segw: Float

                if (offset < start + j || trailing && offset <= start + j) {
                    h += Styled.measureText(paint, workPaint, text,
                            start + segstart, offset, null)
                    return h
                }

                segw = Styled.measureText(paint, workPaint, text,
                        start + segstart, start + j, null)

                if (offset < start + j || trailing && offset <= start + j) {
                    h += segw - Styled.measureText(paint, workPaint,
                            text,
                            start + segstart,
                            offset, null)
                    return h
                }

                h += segw

                if (j != there && buf!![j] == '\t') {
                    if (offset == start + j)
                        return h

                    h = nextTab(h)
                }

                segstart = j + 1
            }
            j++
        }

        here = there


        if (hasTabs)
            TextUtils.recycle(buf!!)

        return h
    }


    /**
     * Measure width of a run of text on a single line that is known to all be
     * in the same direction as the paragraph base direction. Returns the width,
     * and the line metrics in fm if fm is not null.
     *
     * @param paint the paint for the text; will not be modified
     * @param workPaint paint available for modification
     * @param text text
     * @param start start of the line
     * @param end limit of the line
     * @param fm object to return integer metrics in, can be null
     * @param hasTabs true if it is known that the line has tabs
     * @return the width of the text from start to end
     */
    fun measureText(paint: TextPaint,
                                   workPaint: TextPaint,
                                   text: Spannable,
                                   start: Int, end: Int,
                                   fm: Paint.FontMetricsInt?,
                                   hasTabs: Boolean): Float {
        var buf: CharArray? = null

        if (hasTabs) {
            buf = TextUtils.obtain(end - start)
            (text as GetChars).getChars(start, end, buf, 0)
        }

        val len = end - start

        var lastPos = 0
        var width = 0f
        var ascent = 0
        var descent = 0
        var top = 0
        var bottom = 0

        if (fm != null) {
            fm.ascent = 0
            fm.descent = 0
        }

        var pos = if (hasTabs) 0 else len
        while (pos <= len) {
            var codept = 0

            if (hasTabs && pos < len) {
                codept = buf!![pos].toInt()
            }

            if (codept in 0xD800..0xDFFF && pos < len) {
                codept = Character.codePointAt(buf, pos)
            }

            if (pos == len || codept == '\t'.toInt()) {
                workPaint.baselineShift = 0

                width += Styled.measureText(paint, workPaint, text,
                        start + lastPos, start + pos,
                        fm)

                if (fm != null) {
                    if (workPaint.baselineShift < 0) {
                        fm.ascent += workPaint.baselineShift
                        fm.top += workPaint.baselineShift
                    } else {
                        fm.descent += workPaint.baselineShift
                        fm.bottom += workPaint.baselineShift
                    }
                }

                if (pos != len) {
                    width = nextTab(width)
                }

                if (fm != null) {
                    if (fm.ascent < ascent) {
                        ascent = fm.ascent
                    }
                    if (fm.descent > descent) {
                        descent = fm.descent
                    }

                    if (fm.top < top) {
                        top = fm.top
                    }
                    if (fm.bottom > bottom) {
                        bottom = fm.bottom
                    }

                    // No need to take bitmap height into account here,
                    // since it is scaled to match the text height.
                }

                lastPos = pos + 1
            }
            pos++
        }

        if (fm != null) {
            fm.ascent = ascent
            fm.descent = descent
            fm.top = top
            fm.bottom = bottom
        }

        if (hasTabs)
            TextUtils.recycle(buf!!)

        return width
    }

    fun getLineForOffset(offset: Int): Int {
        var high = lineCount
        var low = -1
        var guess =0

        while(high - low > 1) {
            guess = (high+low)/2

            if(getLineStart(guess)>offset) {
                high = guess
            } else {
                low = guess
            }
        }

        return Math.max(0, low)
    }

    val tempRect by lazy { Rect() }

    var _height = 0

    val height
    get() = getLineTop(lineCount)

    val oneLineHeight
    get() = _height

    fun getLineTop(line: Int) = _height * line


    fun getLineForVertical(vertical: Int): Int {
        var high = lineCount
        var low = -1
        var guess: Int
        while (high - low > 1) {
            guess = high + low shr 1
            if (guess * _height > vertical) {
                high = guess
            } else {
                low = guess
            }
        }
        return Math.max(0, low)
    }

    private fun getLineVisibleEnd(line: Int, start: Int, endArg: Int): Int {
        var end = endArg

        var ch: Char
        if (line == lineCount - 1) {
            return end
        }

        while (end > start) {
            ch = text.get(end - 1)

            if (ch == '\n') {
                return end - 1
            }

            if (ch != ' ' && ch != '\t') {
                break
            }
            end--

        }

        return end
    }


    fun getLineBottom(line: Int) =  getLineTop(line + 1)

    val workPaint = TextPaint()

    var descent = 0


    fun draw(canvas: Canvas, highlight: Path?, highlightPaint: Paint?, cursorOffsetVertical: Int, selLine: Int, lineNumberWidth: Int, lineNumberPaint: Paint, spacePaths: Array<Path>) {
        if(!canvas.getClipBounds(tempRect)) {
            return
        }

        val dtop = tempRect.top
        val dbottom = tempRect.bottom

        val top = Math.max(0, dtop)
        val bottom = Math.min(dbottom, getLineTop(lineCount))

        val first = getLineForVertical(top)
        val last = getLineForVertical(bottom)

        var previousLineBottom = getLineTop(first)
        var previousLineEnd = getLineStart(first)

        highlight?.let {
            if(lineNumberWidth != 0) {
                canvas.translate(lineNumberWidth.toFloat(), 0f)
            }
            if(cursorOffsetVertical != 0) {
                canvas.translate(0f, cursorOffsetVertical.toFloat())
            }
            canvas.drawPath(highlight, highlightPaint)

            if(cursorOffsetVertical != 0) {
                canvas.translate(0f, -cursorOffsetVertical.toFloat())
            }
            if(lineNumberWidth != 0) {
                canvas.translate(-lineNumberWidth.toFloat(), 0f)
            }
        }

        var lastDrawnLineNum = 0

        for(i in first..last) {
            val start = previousLineEnd
            previousLineEnd = getLineStart(i+1)

            val end = getLineVisibleEnd(i, start, previousLineEnd)

            val ltop = previousLineBottom
            val lbottom = getLineTop(i+1)

            previousLineBottom = lbottom

            val lbaseline = lbottom - descent

            val left = 0
            val right = width

            val x = left

            if(lineNumberWidth != 0) {
                val fline = fileLines[i]+1
                if(fline != lastDrawnLineNum) {
                    lastDrawnLineNum = fline
                    val linenum = "      ${fline}"
                    canvas.drawText(linenum, linenum.length - 5, linenum.length, x.toFloat(), lbaseline.toFloat(), lineNumberPaint)
                }

                val linebottom = if(i < lineCount -1) { getLineTop(i+1) } else { getLineBottom(i) }
                canvas.drawLine((lineNumberWidth-4).toFloat(), getLineTop(i).toFloat(), (lineNumberWidth-4).toFloat(), linebottom.toFloat(), lineNumberPaint);

                canvas.translate(lineNumberWidth.toFloat(), 0F);
            }

            val hasTab = getLineContainsTab(i)

            // if not spanned, this is faster.
            //  canvas.drawText(text, start, end, x, lbaseline, paint);

            val spacePaint :Paint? = null


            drawText(canvas, text, start, end, x.toFloat(), ltop, lbaseline.toFloat(), lbottom, textPaint, workPaint, hasTab,/* noparaspans, */ spacePaint,  spacePaths)

            if ( lineNumberWidth != 0 ){
                canvas.translate(-lineNumberWidth.toFloat(), 0F);
            }
        }



    }

    var TAB_INCREMENT = 20

    var tabSize:Int
    get() = TAB_INCREMENT
    set(newsize) {
        TAB_INCREMENT = newsize
    }

    /**
     * Increase the width of this layout to the specified width.
     * Be careful to use this only when you know it is appropriate
     * it does not cause the text to reflow to use the full new width.
     */
    fun increaseWidthTo(wid: Int) {
        if (wid < width) {
            throw RuntimeException("attempted to reduce Layout width")
        }

        width = wid
    }

    fun nextTab(h: Float) :Float =   ( ((h + TAB_INCREMENT).toInt() / TAB_INCREMENT) * TAB_INCREMENT).toFloat()

    fun drawText(canvas: Canvas, text: Spannable, start: Int, end: Int, x: Float, top: Int, y: Float, bottom: Int, textPaint: TextPaint, workPaint: TextPaint, hasTab: Boolean, spacePaint: Paint?, spacePaths: Array<Path>) {
        if(!hasTab) {
            Styled.drawText(canvas, text, start, end, x, top, y, bottom, textPaint, workPaint, false)
            return
        }

        val buf = TextUtils.obtain(end - start)
        (text as GetChars).getChars(start, end, buf, 0)

        var h = 0F

        // Do not support bidi
        val here = 0
        val there = end-start

        var segstart = here

        for(j in here..there) {
            if((j == there) || (buf[j] == '\t')){
                h += Styled.drawText(canvas, text,
                        start + segstart, start + j,
                        x + h,
                        top, y, bottom, textPaint, workPaint,
                        (start + j !== end) || hasTab)
                if((j != there) && (buf[j] == '\t')) {
                    spacePaint?.let {
                        canvas.translate(x+h, y)
                        canvas.drawPath(spacePaths[0], spacePaint)
                        canvas.translate(-x-h, -y)
                    }
                    h = nextTab(h)

                }

                if ( spacePaint != null && j== there ){
                    // IDE messed up for text.charAt, so I add cast....
                    if ( (end < text.length) &&  ((text as java.lang.CharSequence).charAt(end)=='\n')){
                        canvas.translate(x+h, y);
                        canvas.drawPath(spacePaths[1], spacePaint);
                        canvas.translate(-x-h, -y);
                    }
                }
                segstart = j + 1;
            } else if ( (spacePaint!=null) &&  (buf[j]==0x3000.toChar()) ){    // Ideographic Space ( for Japanese charset )
                h += Styled.drawText(canvas, text,
                        start + segstart, start + j,
                         x + h,
                top, y, bottom, textPaint, workPaint,
                start + j != end);

                val width = textPaint.measureText("\u3000");
                canvas.translate(x+h, y);
                canvas.drawPath(spacePaths[2], spacePaint);
                canvas.translate(-x-h, -y);
                h += width;

                segstart = j + 1;
            } /* emoji support here if you wantj */
        }
        TextUtils.recycle(buf)
    }

    /**
     * Get the primary horizontal position for the specified text offset.
     * This is the location where a new character would be inserted in
     * the paragraph's primary direction.
     */
    fun getPrimaryHorizontal(offset: Int) =  getHorizontal(offset, false, true)

    private fun getHorizontal(offset: Int, trailing: Boolean, alt: Boolean): Float {
        val line = getLineForOffset(offset)

        return getHorizontal(offset, trailing, alt, line)
    }

    private fun getHorizontal(offset: Int, trailing: Boolean, alt: Boolean,
                              line: Int): Float {
        val start = getLineStart(line)
        val end = getLineVisibleEnd(line)
        val tab = getLineContainsTab(line)

        var tabs: Array<TabStopSpan>? = null
        if (tab) {
            tabs = text.getSpans(start, end, TabStopSpan::class.java)
        }

        var wid = measureText(textPaint, workPaint, text, start, offset, end,
                trailing, alt, tab)

        if (offset > end) {
            wid += measureText(textPaint, workPaint,
                    text, end, offset, null, tab)
        }

        val align = getParagraphAlignment(line)
        val left = getParagraphLeft(line)
        val right = getParagraphRight(line)

        if (align === android.text.Layout.Alignment.ALIGN_NORMAL) {
            return left + wid
        }

        val max = getLineMax(line)

        if (align === android.text.Layout.Alignment.ALIGN_OPPOSITE) {
            return right - max + wid
        } else { /* align == Alignment.ALIGN_CENTER */
            val imax = max.toInt() and 1.inv()

            return  left.toFloat() + ((right - left - imax) / 2).toFloat() + wid
        }
    }

    private fun getOffsetAtStartOf(offset: Int): Int {
        var offset = offset
        if (offset == 0)
            return 0

        val c = text[offset]

        if (c in '\uDC00'..'\uDFFF') {
            val c1 = text[offset - 1]

            if (c1 in '\uD800'..'\uDBFF')
                offset -= 1
        }

        val spans = text.getSpans(offset, offset,
                ReplacementSpan::class.java)

        for (i in spans.indices) {
            val start = text.getSpanStart(spans[i])
            val end = text.getSpanEnd(spans[i])

            if (offset in (start + 1)..(end - 1))
                offset = start
        }

        return offset
    }


    val DIRECTION_ZERO = 32767

    /**
     * Get the character offset on the specified line whose position is
     * closest to the specified horizontal position.
     */
    fun getOffsetForHorizontal(line: Int, horiz: Float): Int {
        var max = getLineEnd(line) - 1
        val min = getLineStart(line)
        if (line == lineCount - 1)
            max++

        var best = min
        var bestdist = Math.abs(getPrimaryHorizontal(best) - horiz)

        var here = min

        var there = here + DIRECTION_ZERO
        val swap = 1

        if (there > max)
            there = max

        var high = there - 1 + 1
        var low = here + 1 - 1
        var guess: Int

        while (high - low > 1) {
            guess = (high + low) / 2
            val adguess = getOffsetAtStartOf(guess)

            if (getPrimaryHorizontal(adguess) * swap >= horiz * swap)
                high = guess
            else
                low = guess
        }

        if (low < here + 1)
            low = here + 1

        if (low < there) {
            low = getOffsetAtStartOf(low)

            var dist = Math.abs(getPrimaryHorizontal(low) - horiz)

            val aft = TextUtils.getOffsetAfter(text, low)
            if (aft < there) {
                val other = Math.abs(getPrimaryHorizontal(aft) - horiz)

                if (other < dist) {
                    dist = other
                    low = aft
                }
            }

            if (dist < bestdist) {
                bestdist = dist
                best = low
            }
        }

        val dist2 = Math.abs(getPrimaryHorizontal(here) - horiz)

        if (dist2 < bestdist) {
            bestdist = dist2
            best = here
        }

        here = there

        val dist3 = Math.abs(getPrimaryHorizontal(max) - horiz)

        if (dist3 < bestdist) {
            bestdist = dist3
            best = max
        }

        return best
    }

}