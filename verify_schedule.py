#!/usr/bin/env python3
# Standalone port of QuestionEngine.buildSchedule to verify per-subject quantity rules.
# Rule (D6 revision): each subject >= 1; when N > 5, cap = ceil(N/M) (M = #enabled subjects),
#   math <= cap, english <= cap, pinyin+ciyu <= cap (HARD, never exceeded);
#   N == 5 -> no per-subject cap. Caps hard => schedule length may be < N in degenerate configs;
#   engine wraps schedule so unlock at N still works.
import math

PINYIN, MATH, ENGLISH, CITU = "pinyin", "math", "english", "ciyu"
ORDER = [PINYIN, MATH, ENGLISH, CITU]
POOL = [PINYIN, MATH, ENGLISH, CITU]  # for generating all combos in ORDER

def can_add(s, counts, cap, pin_on, ciyu_on):
    if s == MATH:
        return counts[s] < cap
    if s == ENGLISH:
        return counts[s] < cap
    if s == PINYIN:
        if ciyu_on:
            return (counts[PINYIN] + counts[CITU]) < cap
        return counts[s] < cap
    if s == CITU:
        if pin_on:
            return (counts[PINYIN] + counts[CITU]) < cap
        return counts[s] < cap
    return True

def build_schedule(subjects, total):
    if total < 1:
        total = 1
    subjects = [s for s in ORDER if s in subjects]
    list_ = list(subjects)
    counts = {s: 1 for s in list_}
    remaining = total - len(list_)
    if remaining < 0:
        for k in range(total, len(list_)):
            counts[list_[k]] = 0
        remaining = 0
    m = len(list_)
    apply_caps = (total > 5)
    cap = int(math.ceil(total / m)) if apply_caps else float("inf")
    pin_on = PINYIN in subjects
    ciyu_on = CITU in subjects
    guard = 0
    while remaining > 0 and guard < 10000:
        guard += 1
        added = False
        for s in list_:
            if remaining <= 0:
                break
            if (not apply_caps) or can_add(s, counts, cap, pin_on, ciyu_on):
                counts[s] += 1
                remaining -= 1
                added = True
        if not added:
            break
    return counts

def all_combos():
    res = []
    n = len(POOL)
    for mask in range(1, 1 << n):
        combo = [POOL[i] for i in range(n) if mask & (1 << i)]
        res.append(combo)
    return res

def achievable_max(combo, total):
    # Max sum reachable under the per-subject caps for this (combo, total).
    # Three independent cap buckets: 数学, 英语, 拼音+语文(合).
    if total <= 5:
        return total  # N=5 -> no caps; fills to total (total >= len(combo) always for our options)
    m = len(combo)
    cap = int(math.ceil(total / m))
    am = 0
    if MATH in combo:
        am += cap
    if ENGLISH in combo:
        am += cap
    if PINYIN in combo or CITU in combo:
        am += cap  # joint cap on the pinyin+ciyu pair as a single bucket
    return am

def check(combo, total):
    counts = build_schedule(combo, total)
    total_actual = sum(counts[s] for s in combo)
    errs = []
    # each >= 1 (feasible since options {5,10,15} and len(combo) <= 4)
    for s in combo:
        if counts.get(s, 0) < 1:
            errs.append(f"{s} < 1 ({counts.get(s,0)})")
    # caps when N > 5
    if total > 5:
        m = len(combo)
        cap = int(math.ceil(total / m))
        if MATH in combo and counts.get(MATH, 0) > cap:
            errs.append(f"math {counts[MATH]} > cap {cap}")
        if ENGLISH in combo and counts.get(ENGLISH, 0) > cap:
            errs.append(f"english {counts[ENGLISH]} > cap {cap}")
        pc = counts.get(PINYIN, 0) + counts.get(CITU, 0)
        if pc > cap:
            errs.append(f"pinyin+ciyu {pc} > cap {cap}")
    # total must equal min(total, achievable_max) under caps
    expect = min(total, achievable_max(combo, total))
    if total_actual != expect:
        errs.append(f"sum {total_actual} != min({total},{achievable_max(combo,total)})={expect}")
    return errs

ok = True
combos = all_combos()
for combo in combos:
    label = ",".join(combo)
    for total in (5, 10, 15):
        errs = check(combo, total)
        if errs:
            ok = False
            print(f"[FAIL] subjects={{{label}}} N={total}: {errs}")
            print(f"        counts={build_schedule(combo, total)}")
print(f"\nScenarios checked: {len(combos)} subject combos x {{5,10,15}} = {len(combos)*3}")
print("RESULT:", "ALL PASS" if ok else "SOME FAILED")
