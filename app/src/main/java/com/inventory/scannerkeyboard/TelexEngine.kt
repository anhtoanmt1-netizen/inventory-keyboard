package com.inventory.scannerkeyboard

/**
 * Bộ gõ tiếng Việt kiểu Telex.
 *
 * Cách hoạt động: lưu lại chuỗi phím thô người dùng đã bấm cho từ hiện tại (raw), mỗi lần cần
 * hiển thị thì dựng lại từ đầu bằng [compose]. Nhờ vậy phím xóa chỉ việc bỏ phím thô cuối cùng,
 * và mọi thao tác sửa lỗi / hoàn tác dấu đều nhất quán.
 *
 * Quy tắc: s f r x j = sắc huyền hỏi ngã nặng, z = xóa dấu, aa ee oo = â ê ô, aw = ă, ow = ơ,
 * uw = ư, w đứng riêng = ư, dd = đ. Gõ lặp phím lần nữa để hoàn tác (ass -> as, aaa -> aa, ddd -> dd).
 * Dấu thanh được đặt theo kiểu mới (hòa, thủy, khỏe).
 */
class TelexEngine {
    private val raw = StringBuilder()

    val isEmpty: Boolean get() = raw.isEmpty()

    fun push(c: Char) { raw.append(c) }

    /** Bỏ phím thô cuối cùng. Trả về false nếu không còn gì để xóa. */
    fun backspace(): Boolean {
        if (raw.isEmpty()) return false
        raw.setLength(raw.length - 1)
        return true
    }

    fun reset() { raw.setLength(0) }

    fun text(): String = compose(raw.toString())

    private class Cell(var c: Char, val up: Boolean)

    companion object {
        private val TONES = mapOf(
            'a' to "aáàảãạ", 'ă' to "ăắằẳẵặ", 'â' to "âấầẩẫậ",
            'e' to "eéèẻẽẹ", 'ê' to "êếềểễệ",
            'i' to "iíìỉĩị",
            'o' to "oóòỏõọ", 'ô' to "ôốồổỗộ", 'ơ' to "ơớờởỡợ",
            'u' to "uúùủũụ", 'ư' to "ưứừửữự",
            'y' to "yýỳỷỹỵ"
        )
        private const val MARKED = "ăâêôơư"
        private const val FINALS = "cmnptgh"

        private fun toneOf(k: Char): Int = when (k) {
            's' -> 1; 'f' -> 2; 'r' -> 3; 'x' -> 4; 'j' -> 5; else -> 0
        }

        fun compose(raw: String): String {
            val cells = ArrayList<Cell>()
            var tone = 0
            val blocked = HashSet<String>()
            val synth = HashSet<Int>() // vị trí chữ ư sinh ra từ phím w đứng riêng

            fun isV(i: Int): Boolean {
                val c = cells[i].c
                if (!TONES.containsKey(c)) return false
                if (c == 'u' && i > 0 && cells[i - 1].c == 'q') return false
                if (c == 'i' && i > 0 && cells[i - 1].c == 'g' && i + 1 < cells.size && TONES.containsKey(cells[i + 1].c)) return false
                return true
            }

            for (ch in raw) {
                val k = ch.lowercaseChar()
                val up = ch != k
                val hasV = (0 until cells.size).any { isV(it) }
                val t = toneOf(k)

                // --- dấu thanh ---
                if (t != 0 && !blocked.contains(k.toString()) && hasV) {
                    if (tone == t) { tone = 0; blocked.add(k.toString()); cells.add(Cell(k, up)) }
                    else tone = t
                    continue
                }
                if (k == 'z' && !blocked.contains("z") && hasV && tone != 0) { tone = 0; continue }

                // --- đ ---
                if (k == 'd' && cells.isNotEmpty() && !blocked.contains("d")) {
                    val last = cells[cells.size - 1]
                    if (last.c == 'd') { cells[cells.size - 1] = Cell('đ', last.up); continue }
                    if (last.c == 'đ') {
                        cells[cells.size - 1] = Cell('d', last.up)
                        blocked.add("d"); cells.add(Cell(k, up)); continue
                    }
                }

                // --- â ê ô ---
                if (k == 'a' || k == 'e' || k == 'o') {
                    val mark = when (k) { 'a' -> 'â'; 'e' -> 'ê'; else -> 'ô' }
                    var idx = -1
                    var j = cells.size - 1
                    var tail = 0
                    while (j >= 0 && tail <= 2) {
                        if (isV(j)) { idx = j; break }
                        if (FINALS.indexOf(cells[j].c) >= 0) { tail++; j-- } else break
                    }
                    if (idx >= 0 && !blocked.contains(k + "m")) {
                        val base = cells[idx].c
                        if (base == k) { cells[idx] = Cell(mark, cells[idx].up); continue }
                        if (base == mark) {
                            cells[idx] = Cell(k, cells[idx].up)
                            blocked.add(k + "m"); cells.add(Cell(k, up)); continue
                        }
                    }
                    cells.add(Cell(k, up)); continue
                }

                // --- ă ơ ư ---
                if (k == 'w' && !blocked.contains("w")) {
                    if (cells.isNotEmpty() && synth.contains(cells.size - 1)) {
                        val last = cells[cells.size - 1]
                        cells[cells.size - 1] = Cell('w', last.up)
                        synth.remove(cells.size - 1); blocked.add("w"); continue
                    }
                    val vs = (0 until cells.size).filter { isV(it) }
                    var done = false
                    if (vs.isNotEmpty()) {
                        val last = vs[vs.size - 1]
                        val tailOk = cells.size - 1 - last <= 2 && ((last + 1) until cells.size).none { isV(it) }
                        if (tailOk) {
                            val b = cells[last].c
                            val prev: Char? = if (last > 0 && isV(last - 1)) cells[last - 1].c else null
                            val prev2: Char? = if (prev != null && last > 1 && isV(last - 2)) cells[last - 2].c else null
                            val atEnd = last == cells.size - 1
                            if (b == 'o' && (prev == 'u' || prev == 'ư')) {
                                cells[last - 1] = Cell('ư', cells[last - 1].up)
                                cells[last] = Cell('ơ', cells[last].up); done = true
                            } else if (b == 'i' && prev == 'o') {
                                // choiw -> chơi, tuoiw -> tươi
                                if (prev2 == 'u') cells[last - 2] = Cell('ư', cells[last - 2].up)
                                cells[last - 1] = Cell('ơ', cells[last - 1].up); done = true
                            } else if (b == 'i' && prev == 'u') {
                                cells[last - 1] = Cell('ư', cells[last - 1].up); done = true // guiw -> gưi
                            } else if (b == 'i' && prev == 'ơ') {
                                // hoàn tác: chơiw + w -> choiw
                                cells[last - 1] = Cell('o', cells[last - 1].up)
                                if (prev2 == 'ư') cells[last - 2] = Cell('u', cells[last - 2].up)
                                blocked.add("w"); cells.add(Cell(k, up)); done = true
                            } else if (b == 'u' && prev == 'o' && prev2 == 'u') {
                                // uouw -> ươu
                                cells[last - 2] = Cell('ư', cells[last - 2].up)
                                cells[last - 1] = Cell('ơ', cells[last - 1].up); done = true
                            } else if (b == 'u' && prev == 'ơ' && prev2 == 'ư') {
                                // hoàn tác: ươu + w -> uouw
                                cells[last - 2] = Cell('u', cells[last - 2].up)
                                cells[last - 1] = Cell('o', cells[last - 1].up)
                                blocked.add("w"); cells.add(Cell(k, up)); done = true
                            } else if (b == 'u' && prev == 'u') {
                                cells[last - 1] = Cell('ư', cells[last - 1].up); done = true // uuw -> ưu
                            } else if (b == 'a' && prev == 'u' && atEnd) {
                                cells[last - 1] = Cell('ư', cells[last - 1].up); done = true // chuaw -> chưa
                            } else if ((b == 'u' || b == 'a') && prev == 'ư' && (b == 'u' || atEnd)) {
                                // hoàn tác: ưu / ưa + w
                                cells[last - 1] = Cell('u', cells[last - 1].up)
                                blocked.add("w"); cells.add(Cell(k, up)); done = true
                            } else if (b == 'ơ' && prev == 'ư') {
                                // hoàn tác: ươ + w -> uow
                                cells[last - 1] = Cell('u', cells[last - 1].up)
                                cells[last] = Cell('o', cells[last].up)
                                blocked.add("w"); cells.add(Cell(k, up)); done = true
                            } else if (b == 'a' || b == 'o' || b == 'u') {
                                val nb = when (b) { 'a' -> 'ă'; 'o' -> 'ơ'; else -> 'ư' }
                                cells[last] = Cell(nb, cells[last].up); done = true
                            } else if (b == 'ă' || b == 'ơ' || b == 'ư') {
                                val ob = when (b) { 'ă' -> 'a'; 'ơ' -> 'o'; else -> 'u' }
                                cells[last] = Cell(ob, cells[last].up)
                                blocked.add("w"); cells.add(Cell(k, up)); done = true
                            }
                        }
                    }
                    if (!done) {
                        if (vs.isEmpty()) { cells.add(Cell('ư', up)); synth.add(cells.size - 1) }
                        else cells.add(Cell(k, up))
                    }
                    continue
                }

                cells.add(Cell(k, up))
            }

            // --- đặt dấu thanh vào nguyên âm đúng vị trí ---
            if (tone != 0 && cells.isNotEmpty()) {
                val vi = (0 until cells.size).filter { isV(it) }
                if (vi.isNotEmpty()) {
                    val g = ArrayList<Int>()
                    g.add(vi[vi.size - 1])
                    for (n in vi.size - 2 downTo 0) {
                        if (vi[n] == g[0] - 1) g.add(0, vi[n]) else break
                    }
                    val marked = g.filter { MARKED.indexOf(cells[it].c) >= 0 }
                    val target = when {
                        marked.isNotEmpty() -> marked[marked.size - 1]
                        g.size == 1 -> g[0]
                        g.size == 3 -> g[1]
                        else -> if (g[g.size - 1] < cells.size - 1) g[1] else g[0]
                    }
                    val cell = cells[target]
                    TONES[cell.c]?.let { cells[target] = Cell(it[tone], cell.up) }
                }
            }

            val sb = StringBuilder()
            for (cell in cells) sb.append(if (cell.up) cell.c.uppercaseChar() else cell.c)
            return sb.toString()
        }
    }
}
