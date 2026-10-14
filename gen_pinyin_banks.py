#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_pinyin_banks.py — 生成拼音分级字表 pinyin_0..6.json（见设计 §R22 / Q8 / Q10）

拼音科目：看汉字选读音（k=汉字，v=带声调拼音）。BankProvider 同池取干扰项（拼音互异），绝不占位。
统一 Schema：
    { "subject": "pinyin", "grade": <0..6>, "entries": [ {"k": "<汉字>", "v": "<拼音(带声调)>"}, ... ] }

年级映射：0=学前(幼儿园) 1=一年级 2=二年级 3=三年级 4=四年级 5=五年级 6=六年级

数据来源：作者编撰的分级常用字表（学前偏独体/笔画少；一~六逐级对齐识字范围）。
资产下限（已拍板 Q8）：学前(0)>=60、一~六(1..6)>=100、distinct(v)>=4。

运行：python3 gen_pinyin_banks.py
  —— 需要 pypinyin 把汉字转带调拼音（仅开发期依赖，不进 APK）：pip install pypinyin
  —— 资产 JSON 一旦生成即作为 APK 资源，运行期零依赖（org.json 解析）。

注意：pinyin_5.json 由本脚本【跳过不重写】。它与编译进 dex 的 BuiltinPinyinBank（144 条兜底）
必须逐条一致，由 build.gradle 的 verifyQuestionBanks 在每次构建期校验；为避免任何漂移，
pinyin_5.json 仅通过 assets 原始文件与 BuiltinPinyinBank.java 双向比对维护，不在此处重新生成。
"""
import json
import os

try:
    from pypinyin import pinyin, Style
except ImportError:
    raise SystemExit("需要 pypinyin：pip install pypinyin（仅开发期，不进 APK）")

BANKS_DIR = "app/src/main/assets/banks"

# 年级 -> 资产下限（已拍板 Q8）
MIN_ENTRIES = {0: 60, 1: 100, 2: 100, 3: 100, 4: 100, 5: 144, 6: 100}

# ---------------------------------------------------------------------------
# 各年级精选常用字（单字）。学前偏独体/笔画少；一~六逐级对齐识字范围。
# 仅需保证"单字 + 年级大致难度"，声调由 pypinyin 自动标注，杜绝手抖写错调号。
# ---------------------------------------------------------------------------

G0 = list("一二三十八人一口手日月水火山石田土木禾中小大上下天力刀又卜丁七儿几了乃习飞马门广"
           "工才寸巾千丈勺丸久弓己子女王玉牙牛羊毛虫耳目舌心尺爪父母兄弟头足云雨风电光米")

G1 = list("你我他她们来去吃喝看听说写读跑跳坐站走笑哭红黄蓝绿白黑多少前后左右里外高低长短开关进出"
           "生死早晚春夏秋冬花草树叶果瓜面肉鱼鸡狗猫猪鸟风云雨雪星河海江湖地金空气影声色香味衣帽鞋袜"
           "床桌椅窗房家校书笔纸画歌舞球车船机电灯门口手心")

G2 = list("学校师班级课本墨砚字词句算数加减乘除方圆角分元斤两寸矮胖瘦美丑冷热忙闲快慢轻重甜苦酸辣咸臭软硬"
           "明暗新旧真假好坏对错是非东西南北间远近深浅平安危险幸福痛思想念忘记忆知道明白懂得会用心友爱"
           "帮助感谢照顾保护节约团结诚实认真干净美丽聪明可爱勇敢快乐伤心")

G3 = list("脑思考究展现象征集群众领导革命战胜勇敢坚强勤劳节俭朴素荣耀尊严荣誉责任义务权利经济贸易工业"
           "农业科技网络通信能源民主法治公平正义自由平等和谐文明探索追求奋斗拼搏奉献合作竞争超越理想"
           "梦想目标计划习惯态度价值信念知识智慧创造发明发现研究科学技术实验观察测量记录分类比较历史"
           "文化传统节日习俗礼貌尊重友谊守信负责耐心细心自信乐观坚强善良")

G4 = list("境界深奥博大精深微观辩证逻辑推理论证归纳演绎传承弘扬底蕴瑰宝遗产精髓典范楷模砥砺奋进笃行致远"
           "博学审问慎思明辨勤勉睿智豁达坚毅刚毅谦逊质朴淳厚弘毅渊博卓绝灿烂宏伟壮丽辽阔苍茫幽静绚丽"
           "蜿蜒巍峨浩瀚精湛卓越崇高神圣庄严肃穆古朴雅致清新宁静恬淡悠然从容淡泊安详温婉柔美灵动隽永"
           "深邃璀璨磅礴")

G6 = list("邃璀璨磅礴蕴涵意哲辨宏微证逻辑推理论证归纳演绎传承弘扬底蕴瑰宝遗产精髓典范楷模砥砺奋进笃行"
           "致远博学审问慎思明辨勤勉睿智豁达坚毅刚毅谦逊质朴淳厚弘毅渊博卓绝灿烂崇高神圣庄严肃穆古朴"
           "雅致清新宁静恬淡悠然从容淡泊安详温婉柔美灵动隽永厚德载物自强不息温故知新举一反三融会贯通"
           "精益求精字斟句酌博古通今学而不厌诲人不倦囊萤夜读铁杵成针韦编三绝牛角挂书勤能补拙辉煌繁荣"
           "昌盛富强锦绣宏图")

DATA = {0: G0, 1: G1, 2: G2, 3: G3, 4: G4, 6: G6}  # 五年级(5) 跳过，见文件头说明


def to_pinyin(ch):
    """单个汉字 -> 带声调拼音；空结果视为字典缺失，直接报错中断。"""
    res = pinyin(ch, style=Style.TONE, heteronym=False)
    py = res[0][0].strip()
    if not py:
        raise ValueError(f"pypinyin 无法标注拼音: {ch!r}")
    return py


def build():
    os.makedirs(BANKS_DIR, exist_ok=True)
    for grade in sorted(DATA.keys()):
        chars = DATA[grade]
        entries = []
        seen_k = set()
        seen_v = set()
        for ch in chars:
            ch = ch.strip()
            if not ch or ch in seen_k:
                continue  # 同级同字去重，避免选项冲突
            v = to_pinyin(ch)
            # 拼音允许重复（不同字可同音），仅按汉字 k 去重避免选项冲突
            seen_k.add(ch)
            seen_v.add(v)
            entries.append({"k": ch, "v": v})

        min_n = MIN_ENTRIES[grade]
        if len(entries) < min_n:
            raise ValueError(
                f"pinyin_{grade}.json: 去重后仅 {len(entries)} 条，低于下限 {min_n}")
        if len(seen_v) < 4:
            raise ValueError(f"pinyin_{grade}.json: 去重拼音仅 {len(seen_v)} 种，低于 4")

        obj = {"subject": "pinyin", "grade": grade, "entries": entries}
        path = os.path.join(BANKS_DIR, f"pinyin_{grade}.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
        print(f"pinyin_{grade}.json: {len(entries)} 条，{len(seen_v)} 种拼音 -> {path}")

    print("gen_pinyin_banks: pinyin_0..4/6 生成完毕（pinyin_5 跳过，与 BuiltinPinyinBank 锁定）")


if __name__ == "__main__":
    build()
