package com.kidslock.app.question

/**
 * 选项区（最多 2×2 个按钮 + 页面底部的「家长解锁」）的显隐与四向焦点映射。
 *
 * 为什么抽成纯逻辑：1.0.1.29 之前的 bug 是「按 4 个按钮写死」——判断题只有 2 个选项时，
 * 第 3、4 个按钮既没清空也没隐藏，还残留可点击的旧文案；焦点也会落到隐藏按钮上。
 * 这里把「选项数量 → 显示哪几个 / 每个方向指向谁」变成纯函数，用单测锁住 2/3/4 三种情况，
 * 界面层只负责把返回的下标映射成 View id。
 */
object OptionLayout {

    /** 焦点目标不是某个选项，而是「家长解锁」按钮。 */
    const val TO_PARENT = -1

    enum class Direction { LEFT, RIGHT, UP, DOWN }

    /** 统一选项数量到 2..4，避免调用方传入越界值。 */
    fun countOf(options: Int): Int = options.coerceIn(2, 4)

    /** 3、4 个选项时才需要第二行；判断题（2 选项）隐藏第二行。 */
    fun secondRowVisible(count: Int): Boolean = countOf(count) > 2

    /** 该下标在当前题型的选项里是否可见。 */
    fun isVisible(index: Int, count: Int): Boolean = index in 0 until countOf(count)

    /**
     * 选项 [index] 按 [direction] 键应聚焦到哪个选项；[TO_PARENT] 表示离开选项区、
     * 去「家长解锁」（只可能出现在最后一行按下时）。
     */
    fun focus(index: Int, count: Int, direction: Direction): Int {
        val n = countOf(count)
        val i = index.coerceIn(0, n - 1)
        return when (n) {
            2 -> when (direction) {
                Direction.LEFT, Direction.RIGHT -> 1 - i
                Direction.UP -> i
                Direction.DOWN -> TO_PARENT
            }
            3 -> when (i) {
                0 -> when (direction) {
                    Direction.LEFT -> 2
                    Direction.RIGHT -> 1
                    Direction.UP -> 0
                    Direction.DOWN -> 2
                }
                1 -> when (direction) {
                    Direction.LEFT -> 0
                    Direction.RIGHT -> 2
                    Direction.UP -> 1
                    Direction.DOWN -> 2
                }
                else -> when (direction) {
                    Direction.LEFT -> 1
                    Direction.RIGHT -> 0
                    Direction.UP -> 0
                    Direction.DOWN -> TO_PARENT
                }
            }
            else -> when (i) {
                0 -> when (direction) {
                    // 原实现里左上角「向左」是绕回左下角（btnOption3），不是右下角，
                    // 这里必须保持 1:1，否则遥控器行为会漂移。
                    Direction.LEFT -> 2
                    Direction.RIGHT -> 1
                    Direction.UP -> 0
                    Direction.DOWN -> 2
                }
                1 -> when (direction) {
                    Direction.LEFT -> 0
                    Direction.RIGHT -> 3
                    Direction.UP -> 1
                    Direction.DOWN -> 3
                }
                2 -> when (direction) {
                    Direction.LEFT -> 3
                    Direction.RIGHT -> 0
                    Direction.UP -> 0
                    Direction.DOWN -> TO_PARENT
                }
                else -> when (direction) {
                    Direction.LEFT -> 2
                    Direction.RIGHT -> 1
                    Direction.UP -> 1
                    Direction.DOWN -> TO_PARENT
                }
            }
        }
    }

    /** 「家长解锁」按钮的上下焦点：向上回到最后一行最左的可见选项，向下回到第一个选项。 */
    fun parentFocus(count: Int, direction: Direction): Int {
        val n = countOf(count)
        return when (direction) {
            Direction.UP -> if (n > 2) 2 else 0
            Direction.DOWN -> 0
            Direction.LEFT, Direction.RIGHT -> 0
        }
    }
}
