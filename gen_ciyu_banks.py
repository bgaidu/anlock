#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gen_ciyu_banks.py — 生成小学「语文（词语/成语）」分级词表 ciyu_0..6.json

数据来源：作者编撰的通用小学词语/成语词表（每级 >=60 条 [词语, 拼音(带声调)]），
供 KidsLock「多科目分级题库」语文科目使用。与英语词表同构，但题干是**词语/四字成语**，
选项是其**拼音**（带声调、音节间空格），即"看词语选拼音"单一方向（与原始拼音科目一致）。
字段与统一 Schema 一致：
    { "subject": "ciyu", "grade": <0..6>, "entries": [ {"k": "<词语>", "v": "<拼音>"}, ... ] }
干扰项由 EnglishProvider 同族的 BankProvider 从同年级词表取其他词的拼音，绝不占位。

运行：python3 gen_ciyu_banks.py
依赖：仅标准库（json / os）。
校验：每级条目 >=60、k/v 非空、去重拼音 >=4；不符直接抛错中断。
"""
import json
import os

BANKS_DIR = "app/src/main/assets/banks"

# ---------------------------------------------------------------------------
# 各年级通用小学词语/成语词表（[词语, 拼音(带声调)]）；拼音力求准确，同级尽量不重复。
# 年级映射：0=学前(幼儿园) 1=一年级 2=二年级 3=三年级 4=四年级 5=五年级 6=六年级
# 成语主要集中在 3 年级及以上，低龄以常用双字词为主。
# ---------------------------------------------------------------------------

G0 = [
    ["爸爸", "bà ba"], ["妈妈", "mā ma"], ["爷爷", "yé ye"], ["奶奶", "nǎi nai"],
    ["哥哥", "gē ge"], ["姐姐", "jiě jie"], ["弟弟", "dì di"], ["妹妹", "mèi mei"],
    ["吃饭", "chī fàn"], ["喝水", "hē shuǐ"], ["睡觉", "shuì jiào"], ["看书", "kàn shū"],
    ["写字", "xiě zì"], ["唱歌", "chàng gē"], ["画画", "huà huà"], ["走路", "zǒu lù"],
    ["太阳", "tài yáng"], ["月亮", "yuè liang"], ["星星", "xīng xing"], ["下雨", "xià yǔ"],
    ["大风", "dà fēng"], ["雪花", "xuě huā"], ["白云", "bái yún"], ["火车", "huǒ chē"],
    ["红色", "hóng sè"], ["黄色", "huáng sè"], ["绿色", "lǜ sè"], ["蓝色", "lán sè"],
    ["黑色", "hēi sè"], ["白色", "bái sè"], ["小猫", "xiǎo māo"], ["小狗", "xiǎo gǒu"],
    ["小鸟", "xiǎo niǎo"], ["小鱼", "xiǎo yú"], ["花朵", "huā duǒ"], ["苹果", "píng guǒ"],
    ["香蕉", "xiāng jiāo"], ["西瓜", "xī guā"], ["牛奶", "niú nǎi"], ["鸡蛋", "jī dàn"],
    ["手", "shǒu"], ["脚", "jiǎo"], ["眼睛", "yǎn jing"], ["嘴巴", "zuǐ ba"],
    ["鼻子", "bí zi"], ["耳朵", "ěr duo"], ["头发", "tóu fa"], ["牙齿", "yá chǐ"],
    ["开门", "kāi mén"], ["关门", "guān mén"], ["穿衣", "chuān yī"], ["洗脸", "xǐ liǎn"],
    ["刷牙", "shuā yá"], ["洗澡", "xǐ zǎo"], ["上学", "shàng xué"], ["回家", "huí jiā"],
    ["高兴", "gāo xìng"], ["生气", "shēng qì"], ["害怕", "hài pà"], ["喜欢", "xǐ huan"],
    ["长大", "zhǎng dà"], ["朋友", "péng you"], ["老师", "lǎo shī"], ["学校", "xué xiào"],
    ["天空", "tiān kōng"], ["大海", "dà hǎi"], ["高山", "gāo shān"], ["河流", "hé liú"],
    ["草地", "cǎo dì"], ["森林", "sēn lín"], ["春天", "chūn tiān"], ["冬天", "dōng tiān"],
]

G1 = [
    ["学生", "xué sheng"], ["同学", "tóng xué"], ["教室", "jiào shì"], ["铅笔", "qiān bǐ"],
    ["橡皮", "xiàng pí"], ["书包", "shū bāo"], ["课本", "kè běn"], ["作业", "zuò yè"],
    ["汉字", "hàn zì"], ["拼音", "pīn yīn"], ["读书", "dú shū"], ["计算", "jì suàn"],
    ["数学", "shù xué"], ["语文", "yǔ wén"], ["英语", "yīng yǔ"], ["早晨", "zǎo chen"],
    ["晚上", "wǎn shang"], ["中午", "zhōng wǔ"], ["今天", "jīn tiān"], ["明天", "míng tiān"],
    ["昨天", "zuó tiān"], ["星期", "xīng qī"], ["日历", "rì lì"], ["公园", "gōng yuán"],
    ["花园", "huā yuán"], ["操场", "cāo chǎng"], ["商店", "shāng diàn"], ["医院", "yī yuàn"],
    ["车站", "chē zhàn"], ["飞机", "fēi jī"], ["轮船", "lún chuán"], ["苹果", "píng guǒ"],
    ["葡萄", "pú táo"], ["草莓", "cǎo méi"], ["桃子", "táo zi"], ["梨子", "lí zi"],
    ["橙子", "chéng zi"], ["白菜", "bái cài"], ["萝卜", "luó bo"], ["紫色", "zǐ sè"],
    ["蓝色", "lán sè"], ["绿色", "lǜ sè"], ["黄色", "huáng sè"], ["橙色", "chéng sè"],
    ["粉色", "fěn sè"], ["棕色", "zōng sè"], ["跑步", "pǎo bù"], ["跳绳", "tiào shéng"],
    ["踢球", "tī qiú"], ["游泳", "yóu yǒng"], ["唱歌", "chàng gē"], ["跳舞", "tiào wǔ"],
    ["画画", "huà huà"], ["弹琴", "tán qín"], ["勇敢", "yǒng gǎn"], ["快乐", "kuài lè"],
    ["伤心", "shāng xīn"], ["认真", "rèn zhēn"], ["干净", "gān jìng"], ["美丽", "měi lì"],
    ["聪明", "cōng ming"], ["可爱", "kě ài"],
]

G2 = [
    ["祖国", "zǔ guó"], ["首都", "shǒu dū"], ["国旗", "guó qí"], ["长城", "cháng chéng"],
    ["黄河", "huáng hé"], ["长江", "cháng jiāng"], ["北京", "běi jīng"], ["上海", "shàng hǎi"],
    ["草原", "cǎo yuán"], ["沙漠", "shā mò"], ["森林", "sēn lín"], ["海洋", "hǎi yáng"],
    ["动物", "dòng wù"], ["植物", "zhí wù"], ["昆虫", "kūn chóng"], ["鱼类", "yú lèi"],
    ["老虎", "lǎo hǔ"], ["狮子", "shī zi"], ["大象", "dà xiàng"], ["猴子", "hóu zi"],
    ["熊猫", "xióng māo"], ["兔子", "tù zi"], ["松鼠", "sōng shǔ"], ["蝴蝶", "hú dié"],
    ["医生", "yī shēng"], ["护士", "hù shi"], ["警察", "jǐng chá"], ["农民", "nóng mín"],
    ["工人", "gōng rén"], ["司机", "sī jī"], ["厨师", "chú shī"], ["米饭", "mǐ fàn"],
    ["面条", "miàn tiáo"], ["包子", "bāo zi"], ["饺子", "jiǎo zi"], ["蛋糕", "dàn gāo"],
    ["饼干", "bǐng gān"], ["糖果", "táng guǒ"], ["巧克力", "qiǎo kè lì"], ["故事", "gù shi"],
    ["童话", "tóng huà"], ["诗歌", "shī gē"], ["日记", "rì jì"], ["地图", "dì tú"],
    ["地球", "dì qiú"], ["宇宙", "yǔ zhòu"], ["温暖", "wēn nuǎn"], ["明亮", "míng liàng"],
    ["黑暗", "hēi àn"], ["潮湿", "cháo shī"], ["干燥", "gān zào"], ["安静", "ān jìng"],
    ["热闹", "rè nao"], ["帮助", "bāng zhù"], ["感谢", "gǎn xiè"], ["照顾", "zhào gù"],
    ["保护", "bǎo hù"], ["节约", "jié yuē"], ["团结", "tuán jié"], ["诚实", "chéng shí"],
    ["早晨", "zǎo chen"], ["傍晚", "bàng wǎn"], ["清晨", "qīng chén"], ["深夜", "shēn yè"],
    ["刚才", "gāng cái"], ["立刻", "lì kè"], ["慢慢", "màn man"], ["飞快", "fēi kuài"],
]

G3 = [
    ["邻居", "lín jū"], ["街道", "jiē dào"], ["城市", "chéng shì"], ["乡村", "xiāng cūn"],
    ["工厂", "gōng chǎng"], ["实验室", "shí yàn shì"], ["图书馆", "tú shū guǎn"], ["博物馆", "bó wù guǎn"],
    ["风筝", "fēng zheng"], ["气球", "qì qiú"], ["灯笼", "dēng long"], ["鞭炮", "biān pào"],
    ["礼物", "lǐ wù"], ["贺卡", "hè kǎ"], ["照片", "zhào piàn"], ["地图", "dì tú"],
    ["蝴蝶", "hú dié"], ["蜜蜂", "mì fēng"], ["蚂蚁", "mǎ yǐ"], ["蜘蛛", "zhī zhū"],
    ["青蛙", "qīng wā"], ["乌龟", "wū guī"], ["燕子", "yàn zi"], ["鸽子", "gē zi"],
    # 成语（三年级起步）
    ["一心一意", "yì xīn yí yì"], ["三心二意", "sān xīn èr yì"], ["五颜六色", "wǔ yán liù sè"],
    ["七上八下", "qī shàng bā xià"], ["四面八方", "sì miàn bā fāng"], ["九牛一毛", "jiǔ niú yì máo"],
    ["十全十美", "shí quán shí měi"], ["千方百计", "qiān fāng bǎi jì"],
    ["冰天雪地", "bīng tiān xuě dì"], ["春暖花开", "chūn nuǎn huā kāi"], ["万紫千红", "wàn zǐ qiān hóng"],
    ["鸟语花香", "niǎo yǔ huā xiāng"], ["风和日丽", "fēng hé rì lì"], ["山清水秀", "shān qīng shuǐ xiù"],
    ["勤学好问", "qín xué hào wèn"], ["不懂就问", "bù dǒng jiù wèn"], ["专心致志", "zhuān xīn zhì zhì"],
    ["一丝不苟", "yì sī bù gǒu"], ["全神贯注", "quán shén guàn zhù"], ["聚精会神", "jù jīng huì shén"],
    ["持之以恒", "chí zhī yǐ héng"], ["实事求是", "shí shì qiú shì"],
    # 补充双字词
    ["高兴", "gāo xìng"], ["难过", "nán guò"], ["激动", "jī dòng"], ["平静", "píng jìng"],
    ["害羞", "hài xiū"], ["骄傲", "jiāo ào"], ["谦虚", "qiān xū"], ["友爱", "yǒu ài"],
    ["彩虹", "cǎi hóng"], ["闪电", "shǎn diàn"], ["雷声", "léi shēng"], ["冰雹", "bīng báo"],
    ["露水", "lù shui"], ["雾气", "wù qì"], ["阳光", "yáng guāng"], ["月光", "yuè guāng"],
]

G4 = [
    ["知识", "zhī shi"], ["智慧", "zhì huì"], ["思考", "sī kǎo"], ["想象", "xiǎng xiàng"],
    ["创造", "chuàng zào"], ["发明", "fā míng"], ["发现", "fā xiàn"], ["研究", "yán jiū"],
    ["科学", "kē xué"], ["技术", "jì shù"], ["实验", "shí yàn"], ["观察", "guān chá"],
    ["测量", "cè liáng"], ["记录", "jì lù"], ["分类", "fēn lèi"], ["比较", "bǐ jiào"],
    ["历史", "lì shǐ"], ["文化", "wén huà"], ["传统", "chuán tǒng"], ["节日", "jié rì"],
    ["习俗", "xí sú"], ["礼貌", "lǐ mào"], ["尊重", "zūn zhòng"], ["友谊", "yǒu yì"],
    ["守信", "shǒu xìn"], ["负责", "fù zé"], ["耐心", "nài xīn"], ["细心", "xì xīn"],
    ["自信", "zì xìn"], ["乐观", "lè guān"], ["坚强", "jiān qiáng"], ["善良", "shàn liáng"],
    # 成语
    ["千变万化", "qiān biàn wàn huà"], ["千军万马", "qiān jūn wàn mǎ"], ["兴高采烈", "xìng gāo cǎi liè"],
    ["迫不及待", "pò bù jí dài"], ["津津有味", "jīn jīn yǒu wèi"], ["井井有条", "jǐng jǐng yǒu tiáo"],
    ["小心翼翼", "xiǎo xīn yì yì"], ["画蛇添足", "huà shé tiān zú"],
    ["画龙点睛", "huà lóng diǎn jīng"], ["守株待兔", "shǒu zhū dài tù"], ["亡羊补牢", "wáng yáng bǔ láo"],
    ["对牛弹琴", "duì niú tán qín"], ["井底之蛙", "jǐng dǐ zhī wā"], ["狐假虎威", "hú jiǎ hǔ wēi"],
    ["掩耳盗铃", "yǎn ěr dào líng"],
    # 补充双字词（地理）
    ["湖泊", "hú pō"], ["溪流", "xī liú"], ["峡谷", "xiá gǔ"], ["平原", "píng yuán"],
    ["盆地", "pén dì"], ["高原", "gāo yuán"], ["丘陵", "qiū líng"], ["岛屿", "dǎo yǔ"],
    # 补充双字词 + 成语（凑足下限 60）
    ["勤奋", "qín fèn"], ["刻苦", "kè kǔ"], ["努力", "nǔ lì"], ["勤劳", "qín láo"],
    ["节俭", "jié jiǎn"], ["仔细", "zǐ xì"], ["专心", "zhuān xīn"], ["自豪", "zì háo"],
    ["自相矛盾", "zì xiāng máo dùn"], ["滥竽充数", "làn yú chōng shù"],
    ["杯弓蛇影", "bēi gōng shé yǐng"], ["叶公好龙", "yè gōng hào lóng"],
]

G5 = [
    ["真理", "zhēn lǐ"], ["理想", "lǐ xiǎng"], ["梦想", "mèng xiǎng"], ["目标", "mù biāo"],
    ["计划", "jì huà"], ["责任", "zé rèn"], ["义务", "yì wù"], ["权利", "quán lì"],
    ["经济", "jīng jì"], ["贸易", "mào yì"], ["工业", "gōng yè"], ["农业", "nóng yè"],
    ["科技", "kē jì"], ["网络", "wǎng luò"], ["通信", "tōng xìn"], ["能源", "néng yuán"],
    ["民主", "mín zhǔ"], ["法治", "fǎ zhì"], ["公平", "gōng píng"], ["正义", "zhèng yì"],
    ["自由", "zì yóu"], ["平等", "píng děng"], ["和谐", "hé xié"], ["文明", "wén míng"],
    ["探索", "tàn suǒ"], ["追求", "zhuī qiú"], ["奋斗", "fèn dòu"], ["拼搏", "pīn bó"],
    ["奉献", "fèng xiàn"], ["合作", "hé zuò"], ["竞争", "jìng zhēng"], ["超越", "chāo yuè"],
    # 成语
    ["胸有成竹", "xiōng yǒu chéng zhú"], ["入木三分", "rù mù sān fēn"], ["闻鸡起舞", "wén jī qǐ wǔ"],
    ["凿壁偷光", "záo bì tōu guāng"], ["程门立雪", "chéng mén lì xuě"], ["悬梁刺股", "xuán liáng cì gǔ"],
    ["手不释卷", "shǒu bù shì juàn"], ["废寝忘食", "fèi qǐn wàng shí"],
    ["一鸣惊人", "yì míng jīng rén"], ["举世闻名", "jǔ shì wén míng"], ["名落孙山", "míng luò sūn shān"],
    ["水滴石穿", "shuǐ dī shí chuān"], ["绳锯木断", "shéng jù mù duàn"], ["愚公移山", "yú gōng yí shān"],
    ["精卫填海", "jīng wèi tián hǎi"], ["夸父逐日", "kuā fù zhú rì"],
    # 补充双字词（风景形容）
    ["蜿蜒", "wān yán"], ["巍峨", "wēi é"], ["浩瀚", "hào hàn"], ["壮丽", "zhuàng lì"],
    ["辽阔", "liáo kuò"], ["苍茫", "cāng máng"], ["幽静", "yōu jìng"], ["绚丽", "xuàn lì"],
    # 补充双字词 + 成语（凑足下限 60）
    ["繁荣", "fán róng"], ["富强", "fù qiáng"], ["昌盛", "chāng shèng"], ["辉煌", "huī huáng"],
    ["画饼充饥", "huà bǐng chōng jī"], ["望梅止渴", "wàng méi zhǐ kě"],
    ["破釜沉舟", "pò fǔ chén zhōu"], ["卧薪尝胆", "wò xīn cháng dǎn"],
    ["指鹿为马", "zhǐ lù wéi mǎ"], ["完璧归赵", "wán bì guī zhào"],
]

G6 = [
    ["深邃", "shēn suì"], ["璀璨", "cuǐ càn"], ["磅礴", "páng bó"], ["隽永", "juàn yǒng"],
    ["蕴涵", "yùn hán"], ["意蕴", "yì yùn"], ["哲理", "zhé lǐ"], ["思辨", "sī biàn"],
    ["宏观", "hóng guān"], ["微观", "wēi guān"], ["辩证", "biàn zhèng"], ["逻辑", "luó ji"],
    ["推理", "tuī lǐ"], ["归纳", "guī nà"], ["演绎", "yǎn yì"], ["论证", "lùn zhèng"],
    ["传承", "chuán chéng"], ["弘扬", "hóng yáng"], ["底蕴", "dǐ yùn"], ["瑰宝", "guī bǎo"],
    ["遗产", "yí chǎn"], ["精髓", "jīng suǐ"], ["典范", "diǎn fàn"], ["楷模", "kǎi mó"],
    ["砥砺", "dǐ lì"], ["奋进", "fèn jìn"], ["笃行", "dǔ xíng"], ["致远", "zhì yuǎn"],
    ["博学", "bó xué"], ["审问", "shěn wèn"], ["慎思", "shèn sī"], ["明辨", "míng biàn"],
    # 成语
    ["锲而不舍", "qiè ér bù shě"], ["自强不息", "zì qiáng bù xī"], ["厚德载物", "hòu dé zài wù"],
    ["温故知新", "wēn gù zhī xīn"], ["举一反三", "jǔ yī fǎn sān"], ["融会贯通", "róng huì guàn tōng"],
    ["精益求精", "jīng yì qiú jīng"], ["字斟句酌", "zì zhēn jù zhuó"], ["博古通今", "bó gǔ tōng jīn"],
    ["囊萤夜读", "náng yíng yè dú"], ["铁杵成针", "tiě chǔ chéng zhēn"], ["韦编三绝", "wéi biān sān jué"],
    ["牛角挂书", "niú jiǎo guà shū"], ["学而不厌", "xué ér bù yàn"], ["诲人不倦", "huì rén bù juàn"],
    ["不耻下问", "bù chǐ xià wèn"], ["勤能补拙", "qín néng bǔ zhuō"],
    # 补充双字词
    ["勤勉", "qín miǎn"], ["睿智", "ruì zhì"], ["豁达", "huò dá"], ["坚毅", "jiān yì"],
    ["刚毅", "gāng yì"], ["谦逊", "qiān xùn"], ["质朴", "zhì pǔ"], ["淳厚", "chún hòu"],
    ["弘毅", "hóng yì"], ["渊博", "yuān bó"], ["卓绝", "zhuó jué"], ["灿烂", "càn làn"],
]

DATA = {0: G0, 1: G1, 2: G2, 3: G3, 4: G4, 5: G5, 6: G6}

MIN_ENTRIES = 60  # 设计预算：每级 >=60 条（与英语词表规模下限一致）


def build():
    os.makedirs(BANKS_DIR, exist_ok=True)
    for grade, pairs in sorted(DATA.items()):
        entries = []
        seen_k = set()
        seen_v = set()
        for w, m in pairs:
            w, m = w.strip(), m.strip()
            if not w or not m:
                raise ValueError(f"ciyu_{grade}.json: 存在空字段 ({w!r}/{m!r})")
            if w in seen_k:  # 同级同词去重，避免选项冲突
                continue
            seen_k.add(w)
            seen_v.add(m)
            entries.append({"k": w, "v": m})
        if len(entries) < MIN_ENTRIES:
            raise ValueError(f"ciyu_{grade}.json: 条目仅 {len(entries)} 条，低于下限 {MIN_ENTRIES}")
        if len(seen_v) < 4:
            raise ValueError(f"ciyu_{grade}.json: 去重拼音仅 {len(seen_v)} 种，低于 4")
        obj = {"subject": "ciyu", "grade": grade, "entries": entries}
        path = os.path.join(BANKS_DIR, f"ciyu_{grade}.json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(obj, f, ensure_ascii=False, indent=2)
        print(f"ciyu_{grade}.json: {len(entries)} 条，{len(seen_v)} 种拼音 -> {path}")
    print("gen_ciyu_banks: 全部 7 级语文词表生成完毕")


if __name__ == "__main__":
    build()
