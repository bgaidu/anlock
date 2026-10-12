#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_english_banks.py — 生成小学英语通用词表 english_0..6.json

数据来源：作者编撰的通用小学英语常用词表（每级 >=60 词 + 中文释义），
供 KidsLock「多科目分级题库」英语科目使用。字段与设计 §1.4 统一 Schema 一致：
    { "subject": "english", "grade": <0..6>, "entries": [ {"k": "<单词>", "v": "<释义>"}, ... ] }
题库型 provider（EnglishProvider）共用 BankProvider 解析，看英文选中文为「正向」、
看中文选英文为「反向」，干扰项均取自同年级词表、绝不占位。

运行：python3 gen_english_banks.py
依赖：仅标准库（json / os）。
校验：每级条目 >=60、k/v 非空、去重释义 >=4；不符直接抛错中断。
"""
import json
import os

BANKS_DIR = "app/src/main/assets/banks"

# ---------------------------------------------------------------------------
# 各年级通用小学英语常用词表（[单词, 中文释义]）；释义力求准确、同级尽量不重复。
# 年级映射：0=学前(幼儿园) 1=一年级 2=二年级 3=三年级 4=四年级 5=五年级 6=六年级
# ---------------------------------------------------------------------------

G0 = [
    ["apple", "苹果"], ["banana", "香蕉"], ["cat", "猫"], ["dog", "狗"],
    ["red", "红色"], ["blue", "蓝色"], ["yellow", "黄色"], ["green", "绿色"],
    ["one", "一"], ["two", "二"], ["three", "三"], ["book", "书"],
    ["ball", "球"], ["sun", "太阳"], ["moon", "月亮"], ["star", "星星"],
    ["water", "水"], ["milk", "牛奶"], ["egg", "鸡蛋"], ["fish", "鱼"],
    ["bird", "鸟"], ["tree", "树"], ["flower", "花"], ["hand", "手"],
    ["foot", "脚"], ["eye", "眼睛"], ["nose", "鼻子"], ["mouth", "嘴"],
    ["ear", "耳朵"], ["happy", "开心"], ["sad", "伤心"], ["big", "大的"],
    ["small", "小的"], ["hot", "热的"], ["cold", "冷的"], ["eat", "吃"],
    ["drink", "喝"], ["run", "跑"], ["jump", "跳"], ["sleep", "睡觉"],
    ["play", "玩"], ["school", "学校"], ["home", "家"], ["mom", "妈妈"],
    ["dad", "爸爸"], ["boy", "男孩"], ["girl", "女孩"], ["car", "汽车"],
    ["bus", "公交车"], ["train", "火车"], ["plane", "飞机"], ["rain", "雨"],
    ["snow", "雪"], ["fire", "火"], ["door", "门"], ["window", "窗户"],
    ["chair", "椅子"], ["table", "桌子"], ["cup", "杯子"], ["bed", "床"],
    ["hat", "帽子"], ["shoe", "鞋"], ["shirt", "衬衫"], ["rice", "米饭"],
    ["bread", "面包"], ["sweet", "甜的"], ["sour", "酸的"], ["cow", "奶牛"],
    ["pig", "猪"], ["duck", "鸭子"], ["sheep", "绵羊"], ["horse", "马"],
    ["rabbit", "兔子"], ["chicken", "鸡"], ["bee", "蜜蜂"], ["bear", "熊"],
    ["lion", "狮子"], ["tiger", "老虎"], ["monkey", "猴子"], ["elephant", "大象"],
    ["panda", "熊猫"], ["orange", "橙色"], ["black", "黑色"], ["white", "白色"],
    ["pink", "粉色"], ["cake", "蛋糕"],
]

G1 = [
    ["pencil", "铅笔"], ["pen", "钢笔"], ["bag", "书包"], ["desk", "书桌"],
    ["teacher", "老师"], ["student", "学生"], ["friend", "朋友"], ["sister", "姐妹"],
    ["brother", "兄弟"], ["baby", "婴儿"], ["man", "男人"], ["woman", "女人"],
    ["head", "头"], ["hair", "头发"], ["tooth", "牙齿"], ["leg", "腿"],
    ["arm", "手臂"], ["finger", "手指"], ["toe", "脚趾"], ["face", "脸"],
    ["smile", "微笑"], ["cry", "哭"], ["walk", "走"], ["swim", "游泳"],
    ["fly", "飞"], ["sing", "唱歌"], ["dance", "跳舞"], ["read", "读"],
    ["write", "写"], ["draw", "画"], ["color", "颜色"], ["purple", "紫色"],
    ["brown", "棕色"], ["gray", "灰色"], ["four", "四"], ["five", "五"],
    ["six", "六"], ["seven", "七"], ["eight", "八"], ["nine", "九"],
    ["ten", "十"], ["frog", "青蛙"], ["turtle", "乌龟"], ["mouse", "老鼠"],
    ["snake", "蛇"], ["spider", "蜘蛛"], ["butterfly", "蝴蝶"], ["leaf", "叶子"],
    ["grass", "草"], ["mountain", "山"], ["river", "河"], ["cloud", "云"],
    ["wind", "风"], ["box", "盒子"], ["key", "钥匙"], ["clock", "时钟"],
    ["open", "打开"], ["close", "关闭"], ["wash", "洗"], ["cut", "切"],
    ["cook", "做饭"], ["help", "帮助"], ["look", "看"], ["listen", "听"],
    ["speak", "说"], ["learn", "学习"], ["tall", "高的"], ["short", "矮的"],
    ["long", "长的"], ["round", "圆的"], ["clean", "干净的"], ["dirty", "脏的"],
    ["meat", "肉"], ["soup", "汤"], ["cookie", "饼干"], ["fruit", "水果"],
    ["vegetable", "蔬菜"], ["father", "父亲"], ["mother", "母亲"], ["family", "家庭"],
    ["classroom", "教室"], ["playground", "操场"], ["garden", "花园"], ["orange", "橙子"],
    ["pink", "粉色"], ["cow", "奶牛"], ["duck", "鸭子"], ["pig", "猪"],
]

G2 = [
    ["coat", "外套"], ["pants", "裤子"], ["dress", "连衣裙"], ["skirt", "裙子"],
    ["sock", "袜子"], ["sweater", "毛衣"], ["glove", "手套"], ["scarf", "围巾"],
    ["boot", "靴子"], ["deer", "鹿"], ["fox", "狐狸"], ["wolf", "狼"],
    ["whale", "鲸鱼"], ["seal", "海豹"], ["dolphin", "海豚"], ["penguin", "企鹅"],
    ["owl", "猫头鹰"], ["parrot", "鹦鹉"], ["goldfish", "金鱼"], ["noodles", "面条"],
    ["dumpling", "饺子"], ["hamburger", "汉堡"], ["pizza", "披萨"], ["cheese", "奶酪"],
    ["butter", "黄油"], ["sky", "天空"], ["sea", "大海"], ["lake", "湖"],
    ["forest", "森林"], ["island", "岛屿"], ["beach", "海滩"], ["stone", "石头"],
    ["sand", "沙子"], ["park", "公园"], ["zoo", "动物园"], ["library", "图书馆"],
    ["hospital", "医院"], ["shop", "商店"], ["market", "市场"], ["farm", "农场"],
    ["post office", "邮局"], ["bank", "银行"], ["restaurant", "餐厅"], ["buy", "买"],
    ["sell", "卖"], ["give", "给"], ["take", "拿"], ["bring", "带来"],
    ["make", "制作"], ["build", "建造"], ["grow", "生长"], ["plant", "种植"],
    ["catch", "抓住"], ["throw", "扔"], ["kick", "踢"], ["climb", "爬"],
    ["ride", "骑"], ["drive", "驾驶"], ["new", "新的"], ["old", "旧的"],
    ["young", "年轻的"], ["fast", "快的"], ["slow", "慢的"], ["strong", "强壮的"],
    ["weak", "弱的"], ["heavy", "重的"], ["light", "轻的"], ["hard", "硬的"],
    ["soft", "软的"], ["quiet", "安静的"], ["loud", "大声的"], ["angry", "生气的"],
    ["tired", "累的"], ["hungry", "饿的"], ["thirsty", "渴的"], ["week", "周"],
    ["month", "月"], ["year", "年"], ["morning", "早晨"], ["afternoon", "下午"],
    ["evening", "晚上"], ["today", "今天"], ["tomorrow", "明天"], ["yesterday", "昨天"],
    ["holiday", "假期"], ["ruler", "尺子"], ["eraser", "橡皮"], ["notebook", "笔记本"],
]

G3 = [
    ["math", "数学"], ["science", "科学"], ["history", "历史"], ["music", "音乐"],
    ["art", "美术"], ["English", "英语"], ["Chinese", "语文"], ["football", "足球"],
    ["basketball", "篮球"], ["tennis", "网球"], ["badminton", "羽毛球"], ["volleyball", "排球"],
    ["baseball", "棒球"], ["swimming", "游泳"], ["running", "跑步"], ["skating", "滑冰"],
    ["skiing", "滑雪"], ["cycling", "骑自行车"], ["hiking", "徒步"], ["sunny", "晴朗的"],
    ["cloudy", "多云的"], ["rainy", "下雨的"], ["snowy", "下雪的"], ["windy", "有风的"],
    ["storm", "暴风雨"], ["rainbow", "彩虹"], ["fog", "雾"], ["lightning", "闪电"],
    ["thunder", "雷声"], ["watch", "手表"], ["calendar", "日历"], ["Sunday", "星期日"],
    ["Monday", "星期一"], ["Tuesday", "星期二"], ["Wednesday", "星期三"], ["Thursday", "星期四"],
    ["Friday", "星期五"], ["Saturday", "星期六"], ["season", "季节"], ["spring", "春天"],
    ["summer", "夏天"], ["autumn", "秋天"], ["winter", "冬天"], ["January", "一月"],
    ["February", "二月"], ["March", "三月"], ["April", "四月"], ["May", "五月"],
    ["June", "六月"], ["July", "七月"], ["August", "八月"], ["September", "九月"],
    ["October", "十月"], ["November", "十一月"], ["December", "十二月"], ["birthday", "生日"],
    ["party", "聚会"], ["museum", "博物馆"], ["cinema", "电影院"], ["theatre", "剧院"],
    ["airport", "机场"], ["station", "车站"], ["hotel", "酒店"], ["temple", "寺庙"],
    ["bridge", "桥"], ["road", "道路"], ["street", "街道"], ["town", "城镇"],
    ["city", "城市"], ["village", "村庄"], ["country", "国家"], ["world", "世界"],
    ["send", "发送"], ["receive", "接收"], ["borrow", "借入"], ["lend", "借出"],
    ["teach", "教"], ["study", "学习"], ["practice", "练习"], ["win", "赢"],
    ["lose", "输"], ["animal", "动物"], ["plant", "植物"], ["insect", "昆虫"],
    ["ocean", "海洋"], ["space", "太空"], ["planet", "行星"], ["earth", "地球"],
    ["computer", "电脑"], ["phone", "电话"], ["camera", "相机"], ["letter", "信"],
    ["email", "电子邮件"], ["gift", "礼物"], ["money", "钱"], ["coin", "硬币"],
    ["price", "价格"], ["careful", "小心的"], ["healthy", "健康的"], ["sick", "生病的"],
    ["famous", "著名的"], ["strange", "奇怪的"], ["special", "特别的"], ["same", "相同的"],
    ["different", "不同的"], ["difficult", "困难的"], ["easy", "容易的"], ["interesting", "有趣的"],
    ["boring", "无聊的"],
]

G4 = [
    ["class", "班级"], ["grade", "年级"], ["lesson", "课"], ["homework", "家庭作业"],
    ["exam", "考试"], ["test", "测验"], ["score", "分数"], ["pass", "通过"],
    ["fail", "不及格"], ["understand", "理解"], ["remember", "记得"], ["forget", "忘记"],
    ["think", "思考"], ["guess", "猜"], ["answer", "回答"], ["question", "问题"],
    ["idea", "想法"], ["fact", "事实"], ["rule", "规则"], ["example", "例子"],
    ["dictionary", "字典"], ["sentence", "句子"], ["word", "单词"], ["story", "故事"],
    ["poem", "诗歌"], ["picture", "图画"], ["map", "地图"], ["globe", "地球仪"],
    ["energy", "能量"], ["electricity", "电"], ["machine", "机器"], ["robot", "机器人"],
    ["tool", "工具"], ["wheel", "轮子"], ["engine", "引擎"], ["rocket", "火箭"],
    ["satellite", "卫星"], ["gravity", "重力"], ["experiment", "实验"], ["observe", "观察"],
    ["measure", "测量"], ["discover", "发现"], ["invent", "发明"], ["protect", "保护"],
    ["recycle", "回收"], ["pollution", "污染"], ["environment", "环境"], ["nature", "自然"],
    ["climate", "气候"], ["afraid", "害怕的"], ["brave", "勇敢的"], ["proud", "自豪的"],
    ["shy", "害羞的"], ["lonely", "孤独的"], ["excited", "兴奋的"], ["calm", "平静的"],
    ["worried", "担心的"], ["surprised", "惊讶的"], ["neighbor", "邻居"], ["guest", "客人"],
    ["host", "主人"], ["team", "队"], ["group", "组"], ["club", "俱乐部"],
    ["meeting", "会议"], ["visit", "拜访"], ["invite", "邀请"], ["share", "分享"],
    ["promise", "承诺"], ["forgive", "原谅"], ["respect", "尊重"], ["trust", "信任"],
    ["argue", "争论"], ["agree", "同意"], ["refuse", "拒绝"], ["allow", "允许"],
    ["appear", "出现"], ["disappear", "消失"], ["create", "创造"], ["improve", "改善"],
    ["reduce", "减少"], ["increase", "增加"], ["connect", "连接"], ["separate", "分开"],
    ["return", "归还"], ["volcano", "火山"], ["earthquake", "地震"], ["cave", "洞穴"],
    ["valley", "山谷"], ["desert", "沙漠"], ["iceberg", "冰山"], ["waterfall", "瀑布"],
    ["jungle", "丛林"], ["prairie", "草原"], ["pond", "池塘"], ["fountain", "喷泉"],
    ["smoke", "烟"], ["metal", "金属"], ["wood", "木头"], ["plastic", "塑料"],
    ["glass", "玻璃"], ["cotton", "棉花"], ["silk", "丝绸"], ["wool", "羊毛"],
]

G5 = [
    ["essay", "作文"], ["report", "报告"], ["research", "研究"], ["project", "项目"],
    ["presentation", "演讲"], ["debate", "辩论"], ["paragraph", "段落"], ["grammar", "语法"],
    ["vocabulary", "词汇"], ["pronunciation", "发音"], ["spelling", "拼写"], ["translate", "翻译"],
    ["summarize", "总结"], ["analyze", "分析"], ["compare", "比较"], ["contrast", "对比"],
    ["conclude", "结论"], ["evidence", "证据"], ["opinion", "观点"], ["solution", "解决方案"],
    ["method", "方法"], ["system", "系统"], ["process", "过程"], ["result", "结果"],
    ["reason", "原因"], ["effect", "效果"], ["influence", "影响"], ["decision", "决定"],
    ["choice", "选择"], ["challenge", "挑战"], ["opportunity", "机会"], ["responsibility", "责任"],
    ["freedom", "自由"], ["right", "权利"], ["duty", "职责"], ["society", "社会"],
    ["culture", "文化"], ["tradition", "传统"], ["custom", "习俗"], ["government", "政府"],
    ["law", "法律"], ["economy", "经济"], ["trade", "贸易"], ["industry", "工业"],
    ["technology", "科技"], ["progress", "进步"], ["future", "未来"], ["past", "过去"],
    ["present", "现在"], ["memory", "记忆"], ["dream", "梦想"], ["goal", "目标"],
    ["plan", "计划"], ["habit", "习惯"], ["attitude", "态度"], ["value", "价值"],
    ["belief", "信念"], ["knowledge", "知识"], ["wisdom", "智慧"], ["creativity", "创造力"],
    ["imagination", "想象力"], ["curiosity", "好奇心"], ["patience", "耐心"], ["confidence", "信心"],
    ["courage", "勇气"], ["honesty", "诚实"], ["kindness", "善良"], ["politeness", "礼貌"],
    ["generosity", "慷慨"], ["fairness", "公平"], ["peace", "和平"], ["war", "战争"],
    ["conflict", "冲突"], ["cooperation", "合作"], ["competition", "竞争"], ["victory", "胜利"],
    ["defeat", "失败"], ["problem", "问题"], ["subject", "科目"], ["science", "科学"],
    ["history", "历史"], ["geography", "地理"],
]

G6 = [
    ["photosynthesis", "光合作用"], ["ecosystem", "生态系统"], ["atmosphere", "大气层"],
    ["organism", "生物"], ["cell", "细胞"], ["gene", "基因"], ["evolution", "进化"],
    ["molecule", "分子"], ["atom", "原子"], ["element", "元素"], ["compound", "化合物"],
    ["reaction", "反应"], ["force", "力"], ["motion", "运动"], ["velocity", "速度"],
    ["mass", "质量"], ["density", "密度"], ["temperature", "温度"], ["pressure", "压力"],
    ["volume", "体积"], ["circuit", "电路"], ["current", "电流"], ["voltage", "电压"],
    ["magnet", "磁铁"], ["wave", "波"], ["frequency", "频率"], ["spectrum", "光谱"],
    ["telescope", "望远镜"], ["microscope", "显微镜"], ["laboratory", "实验室"],
    ["hypothesis", "假设"], ["theory", "理论"], ["variable", "变量"], ["constant", "常数"],
    ["calculation", "计算"], ["equation", "方程"], ["probability", "概率"], ["statistics", "统计"],
    ["continent", "大陆"], ["hemisphere", "半球"], ["equator", "赤道"], ["latitude", "纬度"],
    ["longitude", "经度"], ["glacier", "冰川"], ["canyon", "峡谷"], ["plateau", "高原"],
    ["peninsula", "半岛"], ["strait", "海峡"], ["bay", "海湾"], ["harbor", "港口"],
    ["delta", "三角洲"], ["tundra", "苔原"], ["savanna", "稀树草原"], ["reef", "礁"],
    ["dune", "沙丘"], ["civilization", "文明"], ["dynasty", "王朝"], ["revolution", "革命"],
    ["independence", "独立"], ["constitution", "宪法"], ["parliament", "议会"], ["democracy", "民主"],
    ["election", "选举"], ["citizen", "公民"], ["immigrant", "移民"], ["ancestor", "祖先"],
    ["heritage", "遗产"], ["monument", "纪念碑"], ["archaeology", "考古学"], ["biography", "传记"],
    ["autobiography", "自传"], ["metaphor", "隐喻"], ["rhythm", "节奏"], ["harmony", "和谐"],
    ["perspective", "视角"], ["contradiction", "矛盾"], ["phenomenon", "现象"], ["catastrophe", "灾难"],
    ["sustainable", "可持续的"], ["efficient", "高效的"], ["inevitable", "不可避免的"],
    ["ambiguous", "模糊的"], ["comprehensive", "全面的"], ["fundamental", "基本的"],
    ["simultaneous", "同时的"], ["approximately", "大约"], ["consequently", "因此"],
    ["furthermore", "此外"], ["communicate", "交流"], ["consolidate", "巩固"], ["demonstrate", "演示"],
    ["elaborate", "详述"], ["evaluate", "评估"], ["illustrate", "说明"], ["negotiate", "谈判"],
    ["participate", "参与"], ["speculate", "推测"], ["symbolize", "象征"], ["transform", "转变"],
    ["utilize", "利用"], ["visualize", "使形象化"], ["accommodate", "容纳"], ["accumulate", "积累"],
    ["appreciate", "欣赏"], ["contribute", "贡献"], ["distinguish", "区分"], ["emphasize", "强调"],
]

DATA = {0: G0, 1: G1, 2: G2, 3: G3, 4: G4, 5: G5, 6: G6}

MIN_ENTRIES = 60  # 设计预算：每级 >=60 条（PRD 英语词表规模下限）


def build():
    os.makedirs(BANKS_DIR, exist_ok=True)
    for grade, pairs in sorted(DATA.items()):
        entries = []
        seen_k = set()
        seen_v = set()
        for w, m in pairs:
            w, m = w.strip(), m.strip()
            if not w or not m:
                raise ValueError(f"english_{grade}.json: 存在空字段 ({w!r}/{m!r})")
            if w in seen_k:  # 同级同词去重，避免选项冲突
                continue
            seen_k.add(w)
            seen_v.add(m)
            entries.append({"k": w, "v": m})
        if len(entries) < MIN_ENTRIES:
            raise ValueError(f"english_{grade}.json: 条目仅 {len(entries)} 条，低于下限 {MIN_ENTRIES}")
        if len(seen_v) < 4:
            raise ValueError(f"english_{grade}.json: 去重释义仅 {len(seen_v)} 种，低于 4")
        obj = {"subject": "english", "grade": grade, "entries": entries}
        path = os.path.join(BANKS_DIR, f"english_{grade}.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
        print(f"english_{grade}.json: {len(entries)} 条，{len(seen_v)} 种释义 -> {path}")
    print("gen_english_banks: 全部 7 级词表生成完毕")


if __name__ == "__main__":
    build()
