package com.openapi.feature.chat.markdown

/**
 * 顶层块切分器（整改清单 P1-12）：把 Markdown 按「顶层块」切成可独立渲染的段，
 * 让 [MarkdownRenderCache] 复用已完成块——流式期每拍只重新解析最后（还在长）的一块，
 * 不再对越来越长的全文做 O(n²) 解析。
 *
 * 契约（由 MarkdownSegmenterTest 钉住）：
 * - `split(md).joinToString("") == md`：切段不丢字符，块间空行归前一块的尾巴；
 * - 增长单调：md 的任意前缀 p，`split(p).dropLast(1)` 必须是 `split(md)` 的前缀
 *   ——流式追加只影响最后一段，已完成的段不许变样，否则缓存全废；
 * - 语义安全：只在「CommonMark 确定新起一个块」的空行处切；拿不准（流式半截 token）
 *   宁可并进最后一段，下一拍再切开。围栏代码、`$$` 块公式、松散列表、
 *   引用/列表的 lazy 续行、缩进续行都不切开。
 */
internal object MarkdownSegmenter {

    private val FENCE = Regex("^ {0,3}(```+|~~~+)")
    private val DOLLAR = Regex("^ {0,3}\\$\\$")
    private val HEADING = Regex("^ {0,3}#{1,6}(?=[ \\t]|$)")
    private val QUOTE = Regex("^ {0,3}>")
    private val UNORDERED = Regex("^ {0,3}([-*+])[ \\t]")
    private val ORDERED = Regex("^ {0,3}\\d{1,9}[.)][ \\t]")
    private val INDENTED = Regex("^ {4,}\\S")
    /** 流式半截的列表标记：`-`、`2`、`2.` 这种「可能正要长成一个列表项」的整行。 */
    private val PARTIAL_MARKER = Regex("^ {0,3}(-|\\*|\\+|\\d+\\.?|\\d+[.)])$")
    /**
     * 文末（无行尾换行）的裸块标记。CommonMark 的段落跨空行仍开着，空行后的
     * 裸 `-`/`=` 会被读成前一段的 Setext 标题下线；`#`/`1.`/`>` 则是空块直接吞掉——
     * 单独成段解析时这些标记却按文本保留，切开拼接和整篇渲染就对不上。
     * 只能并进上一段，等它长成真正的块再切。
     */
    private val DANGLING = Regex("^ {0,3}([-=*+~`]+|#{1,6}|\\d{1,9}[.)]|>+)[ \\t]*$")

    private enum class Kind { None, Paragraph, Heading, Quote, Code, FenceOpen, ListOrdered, ListUnordered }

    fun split(md: String): List<String> {
        if (md.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        var segStart = 0
        var blankRun = false          // 正在一段空行里（下一行非空时要决定切不切）
        var kind = Kind.None          // 当前块的类型
        var marker = ""               // 当前无序列表的弹头（不同弹头算新列表）
        var fenceChar = ""            // 打开围栏的字符，决定谁来闭合
        var lastDangling = false      // 空行前最后一个非空行是不是光杆标记行

        fun flush(cut: Int) {
            out += md.substring(segStart, cut)
            segStart = cut
        }

        var i = 0
        while (i < md.length) {
            val nl = md.indexOf('\n', i)
            val end = if (nl < 0) md.length else nl
            val line = md.substring(i, end)

            if (kind == Kind.FenceOpen) {
                // 围栏内：空行、正文都原样并进当前段；只认同字符围栏闭合（``` 不被 ~~~ 关掉）
                if ((FENCE.matches(line) || DOLLAR.matches(line)) && line.trim().startsWith(fenceChar)) {
                    kind = Kind.Paragraph
                }
                blankRun = false
            } else if (line.isBlank()) {
                blankRun = true
            } else {
                val fence = FENCE.containsMatchIn(line)
                val dollar = DOLLAR.containsMatchIn(line)
                val ordered = ORDERED.containsMatchIn(line)
                val unordered = UNORDERED.containsMatchIn(line)
                val partial = PARTIAL_MARKER.matches(line)
                val heading = HEADING.containsMatchIn(line)
                val quote = QUOTE.containsMatchIn(line)
                val indented = INDENTED.containsMatchIn(line)
                // 「普通文本行」= 不是任何块起始的行：空行后出现它 = CommonMark 的 lazy 续行
                val plain = !fence && !dollar && !ordered && !unordered && !partial &&
                    !heading && !quote && !indented

                if (blankRun) {
                    blankRun = false
                    // 只在「确定新起一块」处切。安全性论证：每段都交给 CommonMark 整段解析，
                    // 合并永远无损；切错的代价是渲染不同（松散列表重排编号、代码块一分为二、
                    // 相邻列表之间整篇渲染根本不带空行分隔）。所以拿不准一律并进上一段。
                    //
                    // 光杆标记行（`-`、`#`、`2.`、`>`、`=`，后面还没跟内容）一律并进上一段：
                    // 实测它是否被「吞掉」（解析成空块）取决于前文有没有段落，
                    // 单独成段与在整篇里的解析结果不同（commonmark-java 的列表打断规则），
                    // 切开拼接必然和整篇渲染对不上。等它长出头（`- 项`）就是正常块，能切。
                    val containerBreak = heading || fence || dollar
                    // 光杆标记前后都不切（实测）：它是否被解析成空块（渲染出一个孤立空行）
                    // 取决于上下文；单独成段按文本渲染、并进前一段按空块渲染，两头都对不上。
                    // 整块并进上一段后，它由 CommonMark 整段解析，天然和整篇一致。
                    // ```/~~~ 长得像光杆串但照常切：围栏在哪种上下文里都开块。
                    val merge = lastDangling || (DANGLING.matches(line) && !fence && !dollar) || when (kind) {
                        Kind.None -> true // 段首前没有内容，切在开头只会产生空段
                        // 纯文本段落到空行就结束：下一行不管是什么都另起一段，拆开渲染与整篇等价
                        Kind.Paragraph, Kind.Heading -> false
                        // 缩进代码块内部允许空行，后面还接缩进行就仍是同一个代码块
                        Kind.Code -> indented
                        // 未闭合围栏到不了这里（上面的围栏分支先吃掉空行），到不了也得写上
                        Kind.FenceOpen -> true
                        // 引用/列表是容器：普通文字是 lazy 续行、同种弹头继续、缩进进内容——都留在容器里；
                        // 换弹头的相邻列表整篇渲染之间没有空行，切开拼接会多出空行，同样必须并进来。
                        // 容器只有遇到真块起始（标题/围栏/公式）才结束（CommonMark 松散列表与 lazy 规则）。
                        Kind.Quote, Kind.ListOrdered, Kind.ListUnordered -> !containerBreak
                    }
                    if (!merge) flush(i)
                }

                // 更新当前块类型（非空行总是决定「现在处于什么块」）
                when {
                    fence || dollar -> {
                        fenceChar = line.trim().take(3)
                        kind = Kind.FenceOpen
                    }
                    heading -> kind = Kind.Heading
                    quote -> kind = Kind.Quote
                    ordered -> kind = Kind.ListOrdered
                    unordered -> {
                        marker = UNORDERED.find(line)!!.groupValues[1]
                        kind = Kind.ListUnordered
                    }
                    partial -> if (line.trim().first().isDigit()) kind = Kind.ListOrdered else {
                        marker = line.trim()
                        kind = Kind.ListUnordered
                    }
                    indented -> if (kind != Kind.ListOrdered && kind != Kind.ListUnordered) kind = Kind.Code
                    else -> kind = Kind.Paragraph
                }
                lastDangling = DANGLING.matches(line) && !fence && !dollar
            }
            i = end + 1 // 跳过换行符（end 是行尾或 len，末行没有换行也安全退出循环）
        }
        // 尾部空行不再切段：原样留在最后一段的尾巴上
        out += md.substring(segStart)
        return out
    }
}
