package com.kidslock.app.question

import com.kidslock.app.question.OptionLayout.Direction
import com.kidslock.app.question.OptionLayout.TO_PARENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionLayoutTest {

    private fun assertFocus(count: Int, index: Int, direction: Direction, expected: Int) {
        assertEquals(
            "count=$count index=$index direction=$direction",
            expected,
            OptionLayout.focus(index, count, direction)
        )
    }

    @Test
    fun countIsClampedToTwoToFour() {
        assertEquals(2, OptionLayout.countOf(0))
        assertEquals(2, OptionLayout.countOf(1))
        assertEquals(2, OptionLayout.countOf(2))
        assertEquals(3, OptionLayout.countOf(3))
        assertEquals(4, OptionLayout.countOf(4))
        assertEquals(4, OptionLayout.countOf(9))
    }

    @Test
    fun secondRowOnlyForThreeOrFourOptions() {
        assertFalse(OptionLayout.secondRowVisible(2))
        assertTrue(OptionLayout.secondRowVisible(3))
        assertTrue(OptionLayout.secondRowVisible(4))
        // 越界数量按 2..4 夹紧，不能因此多显示一行
        assertFalse(OptionLayout.secondRowVisible(1))
        assertTrue(OptionLayout.secondRowVisible(7))
    }

    @Test
    fun visibilityFollowsOptionCount() {
        assertTrue(OptionLayout.isVisible(0, 2))
        assertTrue(OptionLayout.isVisible(1, 2))
        assertFalse(OptionLayout.isVisible(2, 2))
        assertFalse(OptionLayout.isVisible(3, 2))

        assertTrue(OptionLayout.isVisible(2, 3))
        assertFalse(OptionLayout.isVisible(3, 3))

        assertTrue(OptionLayout.isVisible(3, 4))
        assertFalse(OptionLayout.isVisible(4, 4))
    }

    @Test
    fun twoOptions_areSideBySideAndDownGoesToParent() {
        assertFocus(2, 0, Direction.LEFT, 1)
        assertFocus(2, 0, Direction.RIGHT, 1)
        assertFocus(2, 0, Direction.UP, 0)
        assertFocus(2, 0, Direction.DOWN, TO_PARENT)

        assertFocus(2, 1, Direction.LEFT, 0)
        assertFocus(2, 1, Direction.RIGHT, 0)
        assertFocus(2, 1, Direction.UP, 1)
        assertFocus(2, 1, Direction.DOWN, TO_PARENT)

        assertEquals(0, OptionLayout.parentFocus(2, Direction.UP))
        assertEquals(0, OptionLayout.parentFocus(2, Direction.DOWN))
    }

    @Test
    fun threeOptions_neverFocusesTheHiddenFourthButton() {
        // 2 + 1 布局：0/1 在第一行，2 在第二行最左，3 隐藏
        assertFocus(3, 0, Direction.RIGHT, 1)
        assertFocus(3, 0, Direction.LEFT, 2)
        assertFocus(3, 0, Direction.DOWN, 2)

        assertFocus(3, 1, Direction.LEFT, 0)
        assertFocus(3, 1, Direction.RIGHT, 2)
        assertFocus(3, 1, Direction.DOWN, 2)

        assertFocus(3, 2, Direction.LEFT, 1)
        assertFocus(3, 2, Direction.RIGHT, 0)
        assertFocus(3, 2, Direction.UP, 0)
        assertFocus(3, 2, Direction.DOWN, TO_PARENT)

        assertEquals(2, OptionLayout.parentFocus(3, Direction.UP))
        assertEquals(0, OptionLayout.parentFocus(3, Direction.DOWN))
    }

    @Test
    fun fourOptions_matchTwoByTwoGrid() {
        assertFocus(4, 0, Direction.RIGHT, 1)
        assertFocus(4, 0, Direction.DOWN, 2)
        assertFocus(4, 1, Direction.LEFT, 0)
        assertFocus(4, 1, Direction.DOWN, 3)
        assertFocus(4, 2, Direction.RIGHT, 0)
        assertFocus(4, 2, Direction.UP, 0)
        assertFocus(4, 2, Direction.DOWN, TO_PARENT)
        assertFocus(4, 3, Direction.LEFT, 2)
        assertFocus(4, 3, Direction.UP, 1)
        assertFocus(4, 3, Direction.DOWN, TO_PARENT)

        assertEquals(2, OptionLayout.parentFocus(4, Direction.UP))
        assertEquals(0, OptionLayout.parentFocus(4, Direction.DOWN))
    }

    @Test
    fun everyFocusTargetIsVisibleOrParentForAllCounts() {
        for (count in 2..4) {
            for (index in 0 until count) {
                for (direction in Direction.values()) {
                    val target = OptionLayout.focus(index, count, direction)
                    assertTrue(
                        "count=$count index=$index direction=$direction target=$target",
                        target == TO_PARENT || OptionLayout.isVisible(target, count)
                    )
                }
            }
            for (direction in Direction.values()) {
                val target = OptionLayout.parentFocus(count, direction)
                assertTrue(
                    "parent count=$count direction=$direction target=$target",
                    OptionLayout.isVisible(target, count)
                )
            }
        }
    }

    @Test
    fun horizontalAndVerticalMovesAreSymmetricWithinTheGrid() {
        // 2 选项：左右互逆
        assertEquals(0, OptionLayout.focus(OptionLayout.focus(0, 2, Direction.RIGHT), 2, Direction.LEFT))
        // 4 选项：上下互逆（2×2 的两列）
        assertEquals(0, OptionLayout.focus(OptionLayout.focus(0, 4, Direction.DOWN), 4, Direction.UP))
        assertEquals(1, OptionLayout.focus(OptionLayout.focus(1, 4, Direction.DOWN), 4, Direction.UP))
        // 4 选项：第一行左右互逆（边界回绕是刻意行为，不参与互逆断言）
        assertEquals(0, OptionLayout.focus(OptionLayout.focus(0, 4, Direction.RIGHT), 4, Direction.LEFT))
    }
}
