package com.kidslock.app.question

object BuiltinPinyinBank {

    private val ENTRIES: Array<BankEntry> = arrayOf(
        BankEntry("誉", "yù"), BankEntry("邦", "bāng"), BankEntry("餐", "cān"),
        BankEntry("沧", "cāng"), BankEntry("巢", "cháo"), BankEntry("撑", "chēng"),
        BankEntry("炽", "chì"), BankEntry("储", "chǔ"), BankEntry("簇", "cù"),
        BankEntry("岱", "dài"), BankEntry("涤", "dí"), BankEntry("淀", "diàn"),
        BankEntry("鼎", "dǐng"), BankEntry("锻", "duàn"), BankEntry("恩", "ēn"),
        BankEntry("伐", "fá"), BankEntry("仿", "fǎng"), BankEntry("绯", "fēi"),
        BankEntry("蜂", "fēng"), BankEntry("赋", "fù"), BankEntry("钢", "gāng"),
        BankEntry("隔", "gé"), BankEntry("弧", "hú"), BankEntry("焕", "huàn"),
        BankEntry("惶", "huáng"), BankEntry("慧", "huì"), BankEntry("寂", "jì"),
        BankEntry("驾", "jià"), BankEntry("歼", "jiān"), BankEntry("茧", "jiǎn"),
        BankEntry("俭", "jiǎn"), BankEntry("嚼", "jué"), BankEntry("襟", "jīn"),
        BankEntry("钧", "jūn"), BankEntry("咖", "kā"), BankEntry("恳", "kěn"),
        BankEntry("叩", "kòu"), BankEntry("窟", "kū"), BankEntry("框", "kuàng"),
        BankEntry("溃", "kuì"), BankEntry("辣", "là"), BankEntry("揽", "lǎn"),
        BankEntry("烙", "lào"), BankEntry("蕾", "lěi"), BankEntry("凛", "lǐn"),
        BankEntry("拎", "līn"), BankEntry("隆", "lóng"), BankEntry("搂", "lǒu"),
        BankEntry("鹿", "lù"), BankEntry("履", "lǚ"), BankEntry("峦", "luán"),
        BankEntry("掠", "lüè"), BankEntry("脉", "mài"), BankEntry("盲", "máng"),
        BankEntry("贸", "mào"), BankEntry("朦", "méng"), BankEntry("弥", "mí"),
        BankEntry("觅", "mì"), BankEntry("蔑", "miè"), BankEntry("铭", "míng"),
        BankEntry("陌", "mò"), BankEntry("钮", "niǔ"), BankEntry("诺", "nuò"),
        BankEntry("畔", "pàn"), BankEntry("抛", "pāo"), BankEntry("喷", "pēn"),
        BankEntry("鹏", "péng"), BankEntry("聘", "pìn"), BankEntry("魄", "pò"),
        BankEntry("剖", "pōu"), BankEntry("祈", "qí"), BankEntry("契", "qì"),
        BankEntry("歉", "qiàn"), BankEntry("腔", "qiāng"), BankEntry("俏", "qiào"),
        BankEntry("窃", "qiè"), BankEntry("钦", "qīn"), BankEntry("躯", "qū"),
        BankEntry("趋", "qū"), BankEntry("雀", "què"), BankEntry("嚷", "rǎng"),
        BankEntry("绕", "rào"), BankEntry("刃", "rèn"), BankEntry("戎", "róng"),
        BankEntry("锐", "ruì"), BankEntry("辱", "rǔ"), BankEntry("嫂", "sǎo"),
        BankEntry("莎", "shā"), BankEntry("韶", "sháo"), BankEntry("誓", "shì"),
        BankEntry("舒", "shū"), BankEntry("鼠", "shǔ"), BankEntry("栓", "shuān"),
        BankEntry("硕", "shuò"), BankEntry("撕", "sī"), BankEntry("肆", "sì"),
        BankEntry("颂", "sòng"), BankEntry("俗", "sú"), BankEntry("遂", "suì"),
        BankEntry("贪", "tān"), BankEntry("毯", "tǎn"), BankEntry("滔", "tāo"),
        BankEntry("藤", "téng"), BankEntry("恬", "tián"), BankEntry("廷", "tíng"),
        BankEntry("凸", "tū"), BankEntry("颓", "tuí"), BankEntry("褪", "tuì"),
        BankEntry("婉", "wǎn"), BankEntry("亡", "wáng"), BankEntry("枉", "wǎng"),
        BankEntry("巍", "wēi"), BankEntry("蔚", "wèi"), BankEntry("涡", "wō"),
        BankEntry("污", "wū"), BankEntry("熄", "xī"), BankEntry("狭", "xiá"),
        BankEntry("弦", "xián"), BankEntry("馅", "xiàn"), BankEntry("翔", "xiáng"),
        BankEntry("卸", "xiè"), BankEntry("械", "xiè"), BankEntry("辛", "xīn"),
        BankEntry("锈", "xiù"), BankEntry("崖", "yá"), BankEntry("宴", "yàn"),
        BankEntry("秧", "yāng"), BankEntry("妖", "yāo"), BankEntry("钥", "yuè"),
        BankEntry("蕴", "yùn"), BankEntry("咋", "zǎ"), BankEntry("榨", "zhà"),
        BankEntry("瞻", "zhān"), BankEntry("障", "zhàng"), BankEntry("哲", "zhé"),
        BankEntry("轴", "zhóu"), BankEntry("铸", "zhù"), BankEntry("拽", "zhuài"),
        BankEntry("篆", "zhuàn"), BankEntry("桩", "zhuāng"), BankEntry("浊", "zhuó"),
        BankEntry("滋", "zī"), BankEntry("棕", "zōng"), BankEntry("奏", "zòu")
    )

    @JvmStatic
    fun entries(): List<BankEntry> = ENTRIES.toList()

    @JvmStatic
    fun size(): Int = ENTRIES.size
}
