package com.kidslock.app.prefs

/**
 * SharedPreferences 的键与默认值集中定义。
 *
 * 这里的字符串是备份文件与老版本数据的兼容边界：只允许新增，不允许改名。
 */
internal object PrefKeys {
    const val PREF_NAME = "kids_lock_prefs"

    const val AUTO_START = "auto_start"
    const val WATCH_LIMIT_MIN = "watch_limit_min"
    const val UNLOCK_COUNT = "unlock_count"
    const val IS_LOCKED = "is_locked"
    const val TIMER_ACTIVE = "timer_active"
    const val TIMER_END_TIME = "timer_end_time"
    const val TIMER_REMAINING = "timer_remaining_ms"
    const val TIMER_STAMP_WALL = "timer_stamp_wall"
    const val TIMER_STAMP_UPTIME = "timer_stamp_uptime"
    const val TIMER_STAMP_ELAPSED = "timer_stamp_elapsed"
    const val PARENT_PIN = "parent_pin"
    const val PIN_SALT = "pin_salt"
    const val PIN_FAIL_COUNT = "pin_fail_count"
    const val PIN_LOCK_UNTIL = "pin_lock_until"
    const val PIN_MODIFIED = "pin_modified"
    const val QUESTION_GRADE = "question_grade"
    const val SUBJECT_GRADE_PREFIX = "question_grade_subject_"
    const val QUESTION_SUBJECTS = "question_subjects"
    const val QUESTION_JUDGMENT = "question_judgment"
    const val QUESTION_FILL = "question_fill"
    const val REVIEW_BOOST = "review_boost"
    const val ADAPTIVE_DIFFICULTY = "adaptive_difficulty"
    const val ADAPTIVE_GRADE = "adaptive_grade"
    const val RECENT_ANSWERS = "recent_answers"
    const val WEEKLY_REPORT = "weekly_report_enabled"
    const val EYE_REST_ENABLED = "eye_rest_enabled"
    const val EYE_REST_INTERVAL_MIN = "eye_rest_interval_min"
    const val EYE_REST_LAST_MS = "eye_rest_last_ms"
    const val SETUP_WIZARD_DONE = "setup_wizard_done"
    const val STAT_CORRECT = "stat_correct"
    const val STAT_TOTAL = "stat_total"
    const val STARS = "stars"
    const val STAT_BEST_COMBO = "stat_best_combo"

    // 学习统计：按天 × 科目记录答题数/正确数，另存每日目标与连续达标
    const val STUDY_STAT_PREFIX = "study_day_"
    const val STUDY_GOAL_MET_PREFIX = "study_goal_met_"
    const val STUDY_GOAL_ENABLED = "study_goal_enabled"
    const val STUDY_GOAL_QUESTIONS = "study_goal_questions"
    const val STUDY_BEST_STREAK = "study_best_streak"
    const val STUDY_LAST_PRUNE = "study_last_prune"
    const val LEGACY_MIGRATED = "legacy_defaults_migrated"
    const val EMERGENCY_REBOOT_UNLOCK = "emergency_reboot_unlock"
    const val SHOW_COUNTDOWN = "show_countdown"
    const val SETTINGS_ALLOW_UNTIL = "settings_allow_until"
    const val DAILY_LIMIT_MIN = "daily_limit_min"
    const val DAILY_LIMIT_WEEKEND_MIN = "daily_limit_weekend_min"
    const val DAILY_USED_MS = "daily_used_ms"
    const val DAILY_DATE = "daily_date"
    const val DAILY_BYPASS_UNTIL = "daily_bypass_until"
    const val USAGE_PREFIX = "usage_"
    const val LAST_SEEN_DAY = "last_seen_day"
    const val LAST_SEEN_WALL = "last_seen_wall"
    const val LAST_SEEN_ELAPSED = "last_seen_elapsed"
    const val CLOCK_ROLLBACK = "clock_rollback"
    const val SCHEDULE_ENABLED = "schedule_enabled"
    const val SCHEDULE_START_PREFIX = "schedule_start_"
    const val SCHEDULE_END_PREFIX = "schedule_end_"
    const val SCHEDULE_BYPASS_UNTIL = "schedule_bypass_until"
    const val LOCKED_BY_SCHEDULE = "locked_by_schedule"
    const val LOCK_REASON = "lock_reason"
    const val SCHEDULE_FREE_MODE = "schedule_free_mode"
    const val STAR_EXCHANGE_STARS = "star_exchange_stars"
    const val STAR_EXCHANGE_MINUTES = "star_exchange_minutes"
    const val BONUS_PENDING_MIN = "bonus_pending_min"
    const val DAILY_BONUS_MIN = "daily_bonus_min"
    const val LAN_ENABLED = "lan_enabled"
    const val LAN_TOKEN = "lan_token"
    const val LAST_BOOT_TIME = "last_boot_time"
    const val REBOOT_COUNT = "reboot_count"

    // 应用级限额：按包名保存平日/周末额度，-1 表示不限，0 表示禁止使用
    const val APP_LIMIT_ENABLED = "app_limit_enabled"
    const val APP_LIMIT_WEEKDAY_PREFIX = "app_limit_min_"
    const val APP_LIMIT_WEEKEND_PREFIX = "app_limit_weekend_"
    const val APP_LIMIT_BONUS_PREFIX = "app_limit_bonus_"
    const val APP_LIMIT_OFFSET_PREFIX = "app_limit_offset_"

    const val DEFAULT_AUTO_START = true
    const val DEFAULT_WATCH_LIMIT_MIN = 30
    const val DEFAULT_UNLOCK_COUNT = 5
    const val DEFAULT_PARENT_PIN = "1234"
    const val MAX_PIN_FAILS = 5
    const val PIN_LOCKOUT_MS = 30_000L
    const val DEFAULT_QUESTION_GRADE = 3
    const val DEFAULT_QUESTION_SUBJECTS = "pinyin,math"
    const val DEFAULT_DAILY_LIMIT_MIN = 0
    const val DEFAULT_SHOW_COUNTDOWN = true
    const val DEFAULT_SCHEDULE_START_MIN = 8 * 60
    const val DEFAULT_SCHEDULE_END_MIN = 21 * 60
    const val DEFAULT_STAR_EXCHANGE_STARS = 10
    const val DEFAULT_STAR_EXCHANGE_MINUTES = 10
    const val DEFAULT_STUDY_GOAL_QUESTIONS = 20
    const val LAN_TOKEN_LENGTH = 10
    const val LAN_TOKEN_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    const val REASON_SCHEDULE = "schedule"
    const val REASON_TIME = "time"
    const val REASON_MANUAL = "manual"
}
