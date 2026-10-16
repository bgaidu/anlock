package com.kidslock.app.question

class RecentQuestionQueue {
    companion object {
        private const val CAPACITY = 10
    }

    private val recent = ArrayDeque<String>()

    fun contains(id: String): Boolean = id in recent

    fun offer(id: String) {
        if (recent.size >= CAPACITY) recent.removeFirst()
        recent.addLast(id)
    }

    fun clear() {
        recent.clear()
    }
}
