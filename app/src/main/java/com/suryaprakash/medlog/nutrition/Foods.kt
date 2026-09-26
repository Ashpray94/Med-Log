package com.suryaprakash.medlog.nutrition

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Calories and protein for food eaten across India, per usual serving, written up for MedLog from general
 * nutrition references (not copied from any one table). Values are estimates for home-style cooking;
 * the doctor page says so. A food that isn't here can be added once with its own numbers ([custom]).
 *
 * Each line: names (first is shown; the rest are other names people use) | serving | grams | kcal | protein g
 */
object Foods {
    data class Food(val names: List<String>, val unit: String, val grams: Int, val kcal: Double, val protein: Double, val custom: Boolean = false,
                    val meal: String = "Other", val cuisine: String = "Home basics") {
        val name get() = names.first()
        /** Eaten with a dish, never on its own: offered only in the dish's "with your …?" list. */
        val side get() = meal == "Sides"
        val measure get() = when {
            unit in listOf("piece", "slice", "packet") -> Measure.PIECE
            unit in listOf("glass", "cup") || name in LIQUID -> Measure.ML
            else -> Measure.GRAMS
        }
        /** What one tap adds: a piece; or an everyday small helping in grams or ml. */
        val start get() = when (measure) {
            Measure.PIECE -> 1.0
            else -> if (grams <= 30) grams.toDouble() else minOf(grams, if (measure == Measure.ML) 150 else 100).toDouble()
        }
        val step get() = when (measure) {
            Measure.PIECE -> 1.0
            Measure.ML -> if (grams <= 30) 5.0 else 50.0
            Measure.GRAMS -> if (grams <= 30) 5.0 else 25.0
        }
        /** "100 g · 130 kcal", "1 medium · 58 kcal", "150 ml · 90 kcal" */
        val startWords get() = Portion(this, start).let { "${it.words} · ${it.kcal.roundToInt()} kcal" }
    }

    /** How a food is measured when eaten: by weight, by volume, or counted with a size. Never by plate, cup or bowl. */
    enum class Measure { GRAMS, ML, PIECE }

    private val LIQUID = setOf("sambar", "rasam", "soup", "chicken soup", "kanji", "dal water", "ragi malt", "kadhi")
    val SIZES = listOf("small" to 0.7, "medium" to 1.0, "large" to 1.4)

    /** One food eaten: how much, and for counted foods, how big. [amount] is grams, ml or pieces. */
    data class Portion(val food: Food, val amount: Double, val size: String = "medium") {
        private val factor get() = when (food.measure) {
            Measure.PIECE -> amount * (SIZES.firstOrNull { it.first == size }?.second ?: 1.0)
            else -> amount / food.grams
        }
        val kcal get() = food.kcal * factor
        val protein get() = food.protein * factor
        val words get() = when (food.measure) {
            Measure.PIECE -> "${fmtQty(amount)} $size"
            Measure.GRAMS -> "${amount.toInt()} g"
            Measure.ML -> "${amount.toInt()} ml"
        }
    }

    /** Stored with the meal: each food's name, amount, calories and protein, main dish first. */
    fun portionsJson(ps: List<Portion>): String {
        val a = JSONArray()
        ps.forEach { p -> a.put(JSONObject().put("name", p.food.name).put("amount", p.words).put("kcal", p.kcal.roundToInt()).put("protein", (p.protein * 10).roundToInt() / 10.0).put("known", true)) }
        return JSONObject().put("items", a).put("kcal", ps.sumOf { it.kcal }.roundToInt()).put("protein", (ps.sumOf { it.protein } * 10).roundToInt() / 10.0).toString()
    }

    /** The filters: when it's eaten, and where it's from. */
    val MEALS = listOf("Breakfast", "Lunch", "Snacks", "Desserts", "Drinks", "Fruits")
    val CUISINES = listOf("South Indian", "North Indian", "Gujarati", "Maharashtrian", "Kerala", "Biryani", "Indo-Chinese", "Street food", "Fast food", "Non-veg", "Sweets", "Home basics")

    /** What usually comes with a dish, offered when it's added. */
    fun sidesFor(f: Food): List<Food> {
        val n = f.name
        val names = when {
            n in listOf("idli", "dosa", "masala dosa", "rava dosa", "set dosa", "neer dosa", "ragi dosa", "uttapam", "pesarattu", "adai", "medu vada", "masala vada", "pongal", "upma", "idiyappam", "appam") ->
                listOf("sambar", "coconut chutney", "tomato chutney", "coffee")
            n in listOf("roti", "tandoori roti", "paratha", "gobi paratha", "thepla", "bajra roti", "jowar roti", "makki di roti", "ragi roti", "akki rotti", "naan", "butter naan", "garlic naan", "kulcha") ->
                listOf("dal", "mixed veg", "paneer butter masala", "curd", "pickle")
            n in listOf("aloo paratha", "paneer paratha") -> listOf("curd", "pickle", "butter", "tea")
            n == "puri" -> listOf("aloo sabzi", "chole", "halwa")
            n == "bhatura" -> listOf("chole", "pickle", "lassi")
            n in listOf("rice", "brown rice", "jeera rice", "ghee rice") -> listOf("dal", "sambar", "rasam", "curd", "poriyal", "papad", "pickle")
            n.contains("biryani") -> listOf("raita", "mirchi ka salan")
            n in listOf("curd rice", "lemon rice", "tamarind rice", "khichdi", "bisi bele bath") -> listOf("pickle", "papad", "curd")
            n in listOf("dal", "dal fry", "rajma", "chole", "kadhi", "sambar") -> listOf("rice", "roti")
            n in listOf("tea", "coffee", "milk") -> listOf("biscuit", "rusk", "sugar")
            n == "kerala parotta" -> listOf("chicken curry", "egg curry", "kootu")
            n == "ragi mudde" -> listOf("sambar", "chicken curry")
            n == "puttu" -> listOf("banana", "egg curry")
            n in listOf("samosa", "kachori", "pakora", "dhokla") -> listOf("mint chutney", "tea")
            n == "pav bhaji" || n == "vada pav" || n == "misal pav" -> listOf("pav", "butter")
            else -> emptyList()
        }
        return names.mapNotNull { nm -> all.firstOrNull { it.name == nm } }.filter { it.name != n }
    }

    private const val TABLE = """
#Breakfast|South Indian
idli,idly,iddli|piece|40|58|2
dosa,plain dosa,sada dosa,dosai,dose|piece|80|135|3
masala dosa|piece|180|320|6
rava dosa|piece|90|170|3
set dosa|piece|60|110|2.5
neer dosa|piece|50|85|1.5
ragi dosa|piece|80|130|3.5
uttapam,uthappam,oothappam|piece|120|200|5
pesarattu,moong dosa|piece|100|170|8
adai|piece|100|190|8
appam,palappam|piece|60|110|2
idiyappam,string hoppers,sevai|piece|40|55|1
puttu|piece|100|180|3
medu vada,vada,uzhunnu vada,wada,vadai|piece|40|97|3.5
masala vada,paruppu vada,dal vada|piece|40|110|4
#Sides|South Indian
sambar,sambhar,kuzhambu|katori|150|110|5
rasam,saaru|katori|150|45|1.5
coconut chutney,chutney,thengai chutney|tbsp|15|35|0.5
tomato chutney|tbsp|15|25|0.3
mint chutney,pudina chutney,green chutney|tbsp|15|10|0.3
#Breakfast|South Indian
upma,rava upma,uppittu|katori|150|200|5
pongal,ven pongal,khara pongal|katori|150|250|7
sweet pongal,sakkarai pongal|katori|150|320|5
#Breakfast|Maharashtrian
poha,aval,avalakki,pohe,chivda poha|katori|150|220|4
#Lunch|South Indian
lemon rice,chitranna,elumichai sadam|katori|150|250|4
tamarind rice,puliyogare,puliodarai|katori|150|280|4
curd rice,thayir sadam,mosaranna,dahi chawal|katori|150|200|6
bisi bele bath|katori|150|230|7
vangi bath|katori|150|250|5
#Lunch|Kerala
kerala parotta,malabar parotta,porotta|piece|90|300|6
#Breakfast|Maharashtrian
sabudana khichdi,sago khichdi|katori|150|300|3
#Lunch|Home basics
rice,chawal,cooked rice,plain rice,sadam,annam,bhaat,bhat,white rice,steamed rice|katori|150|195|4
brown rice|katori|150|180|4
jeera rice|katori|150|230|4
ghee rice,neychoru|katori|150|290|4
#Lunch|Biryani
biryani,chicken biryani,dum biryani|plate|300|500|20
mutton biryani|plate|300|560|24
egg biryani|plate|300|470|15
veg biryani,vegetable biryani|plate|300|420|9
#Sides|Biryani
raita,boondi raita,cucumber raita|katori|100|70|3
mirchi ka salan,salan|katori|100|150|2
#Lunch|North Indian
pulao,pulav,veg pulao|katori|150|230|5
#Lunch|Home basics
khichdi,khichri|katori|150|190|7
#Lunch|Indo-Chinese
fried rice,veg fried rice|plate|250|400|9
chicken fried rice|plate|250|480|18
noodles,hakka noodles,chowmein|plate|250|400|9
#Lunch|North Indian
roti,chapati,chapathi,phulka,fulka,rotli,chappati|piece|40|104|3
tandoori roti|piece|60|150|5
paratha,parotha,plain paratha|piece|80|260|5
aloo paratha|piece|120|300|6
gobi paratha|piece|120|280|6
paneer paratha|piece|120|330|11
thepla,methi thepla|piece|50|140|4
puri,poori|piece|25|100|1.5
bhatura,bhature|piece|80|300|6
naan|piece|90|260|8
butter naan|piece|100|310|8
garlic naan|piece|100|300|8
kulcha|piece|80|240|7
#Lunch|Home basics
bajra roti,bajre ki roti,sajje rotti|piece|60|180|5
jowar roti,jolada rotti,bhakri,jowar bhakri|piece|60|170|5
makki di roti,makki roti,makai roti|piece|60|190|4
ragi mudde,ragi ball,ragi sangati|piece|150|250|5
ragi roti,nachni roti|piece|60|160|4
akki rotti,rice roti|piece|60|170|3
#Breakfast|Home basics
bread,white bread,bread slice|slice|25|67|2
brown bread,wheat bread,whole wheat bread|slice|28|70|3
toast,bread toast|slice|25|80|2
pav,bun,ladi pav|piece|40|110|3.5
#Lunch|Home basics
dal,daal,dal tadka,yellow dal,toor dal,arhar dal,paruppu,pappu,varan|katori|150|150|8
dal fry|katori|150|180|8
#Lunch|North Indian
dal makhani,maa ki dal|katori|150|280|10
moong dal,mung dal|katori|150|130|8
masoor dal,red lentil|katori|150|140|9
chana dal|katori|150|180|10
rajma,rajma masala,kidney beans|katori|150|210|10
chole,chana masala,chickpea curry,chhole|katori|150|240|10
kadhi,kadhi pakora|katori|150|180|6
#Snacks|Home basics
sprouts,moong sprouts,sprout salad|katori|100|110|8
sundal,chana sundal|katori|100|150|7
#Lunch|North Indian
lobia,black eyed peas curry|katori|150|180|10
#Lunch|North Indian
aloo sabzi,potato curry,aloo curry,batata bhaji,aloo|katori|150|180|3
aloo gobi|katori|150|160|4
aloo matar|katori|150|190|5
bhindi,okra,bhindi fry,bendakaya|katori|150|150|3
baingan bharta,brinjal curry,baingan,brinjal,eggplant|katori|150|140|3
palak paneer|katori|150|260|12
paneer butter masala,paneer masala,paneer makhani|katori|150|350|13
matar paneer,mutter paneer|katori|150|280|13
shahi paneer|katori|150|360|12
kadai paneer|katori|150|320|14
paneer bhurji|katori|100|280|15
#Lunch|Home basics
mixed veg,mix veg,vegetable curry,sabzi,subzi,sabji|katori|150|150|4
#Lunch|South Indian
poriyal,thoran,cabbage poriyal,cabbage|katori|100|90|2
beans poriyal,beans,green beans|katori|100|90|3
avial,aviyal|katori|150|180|3
kootu|katori|150|160|6
#Lunch|North Indian
sarson ka saag,saag|katori|150|180|5
lauki,bottle gourd,lauki sabzi,sorakaya,dudhi|katori|150|90|2
karela,bitter gourd,pavakkai|katori|100|110|2
methi aloo,aloo methi|katori|150|170|3
tinda|katori|150|90|2
#Lunch|Indo-Chinese
gobi manchurian,manchurian|katori|150|300|6
#Lunch|Non-veg
chicken curry,chicken gravy,murgh curry|katori|150|240|22
butter chicken,murgh makhani|katori|150|340|22
chicken tikka|piece|30|50|7
tandoori chicken|piece|150|260|30
chicken fry,chicken 65,fried chicken|katori|100|260|22
chilli chicken|katori|150|320|20
chicken kebab,seekh kebab,kebab|piece|40|90|8
mutton curry,mutton,goat curry,lamb curry|katori|150|300|22
keema,mutton keema,kheema|katori|150|320|22
fish curry,meen curry,machher jhol|katori|150|200|20
fish fry,fried fish,meen fry|piece|80|190|16
prawn curry,prawns,jhinga|katori|150|190|20
#Breakfast|Non-veg
egg,boiled egg,anda|piece|50|78|6.3
omelette,omelet,egg omelette|piece|60|120|7
egg curry,anda curry|katori|150|220|12
egg bhurji,scrambled egg,anda bhurji|katori|100|180|11
#Snacks|Street food
samosa|piece|60|260|4
kachori|piece|50|200|4
pakora,pakoda,bhajji,bhaji,bajji,onion pakoda|piece|20|65|1.5
vada pav|piece|150|300|7
pav bhaji|plate|250|450|10
misal pav|plate|300|480|15
bhel puri,bhel|plate|100|250|5
pani puri,golgappa,puchka,gol gappe|piece|15|35|0.5
sev puri|plate|100|300|5
dahi puri|plate|120|280|6
#Snacks|Gujarati
dhokla|piece|40|65|2.5
khandvi|piece|25|45|2
khakhra|piece|20|80|3
#Snacks|South Indian
murukku,chakli|piece|20|100|1.5
mixture,namkeen,chivda|katori|30|160|4
bonda,aloo bonda|piece|50|150|3
cutlet,veg cutlet|piece|60|140|3
#Snacks|Home basics
biscuit,marie biscuit,biscuits|piece|7|30|0.5
cream biscuit|piece|12|60|0.6
rusk,toast rusk|piece|15|60|1.8
#Snacks|Kerala
banana chips|katori|30|160|1
#Snacks|Street food
chips,potato chips,wafers|katori|30|160|2
#Snacks|Fast food
maggi,instant noodles|packet|70|320|7
sandwich,veg sandwich|piece|150|280|8
pizza,pizza slice|slice|100|270|11
burger,veg burger|piece|150|350|10
momos,momo|piece|30|45|2
#Desserts|Sweets
gulab jamun|piece|40|150|2
rasgulla,rosogolla|piece|40|110|2
rasmalai|piece|60|180|6
jalebi,jilebi|piece|25|110|0.5
laddu,ladoo,besan laddu,boondi laddu,motichoor laddu|piece|40|185|3
kheer,payasam,payasa,paysam,firni|katori|150|250|6
halwa,sooji halwa,sheera,kesari,kesari bath,rava kesari|katori|100|300|4
gajar halwa,carrot halwa,gajar ka halwa|katori|100|280|5
barfi,burfi,kaju katli,kaju barfi|piece|25|120|2
mysore pak|piece|30|160|1.5
sandesh|piece|30|90|3
peda,pedha|piece|25|110|2.5
ice cream,icecream|scoop|60|120|2
chocolate|piece|10|55|0.7
cake,cake slice|slice|60|230|3
#Drinks|Drinks
milk,doodh,paal,haal,full cream milk|glass|250|150|8
toned milk,low fat milk,skimmed milk|glass|250|120|8
tea,chai,masala chai,milk tea|cup|150|90|2.5
coffee,filter coffee,milk coffee|cup|150|90|3
black coffee|cup|150|5|0.3
black tea,lemon tea,green tea|cup|150|5|0
curd,dahi,yogurt,yoghurt,thayir,mosaru|katori|100|60|3.5
buttermilk,chaas,chhaas,majjige,mor,moru|glass|250|40|2
lassi,sweet lassi|glass|250|220|7
badam milk,badam doodh|glass|250|220|9
#Sides|Home basics
paneer,cottage cheese|katori|100|265|18
cheese,cheese slice|slice|20|65|4
ghee,clarified butter|tbsp|15|135|0
butter,makhan|tbsp|15|108|0.1
oil,cooking oil|tbsp|15|135|0
#Drinks|Drinks
horlicks,bournvita,complan,boost,health drink,protinex|glass|250|200|9
coconut water,elaneer,tender coconut,nariyal pani|glass|250|50|0.5
juice,fresh juice,orange juice,mosambi juice,fruit juice|glass|250|110|1.5
sugarcane juice,ganne ka ras|glass|250|180|0
lemon water,nimbu pani,lime juice,shikanji|glass|250|60|0.1
soft drink,cola,coke,pepsi|glass|250|105|0
soup,tomato soup,veg soup|katori|150|80|2
chicken soup|katori|150|90|8
ragi malt,ragi java,ambali,ragi porridge|glass|250|150|4
kanji,rice kanji,ganji,congee,rice porridge,pej|katori|200|100|2
dal water,dal ka pani|katori|150|40|3
#Fruits|Fruits
banana,kela,vazhaipazham,balehannu|piece|100|90|1.1
apple,seb|piece|150|78|0.4
orange,santra,narangi|piece|130|60|1.2
mosambi,sweet lime|piece|150|65|1
mango,aam|piece|200|120|1.6
papaya,papita|katori|150|65|0.7
guava,amrood,peru,koyya|piece|100|68|2.5
grapes,angoor|katori|100|70|0.7
pomegranate,anar|katori|100|83|1.7
watermelon,tarbooz|katori|150|45|0.9
chikoo,sapota,chiku|piece|100|83|0.4
pineapple,ananas|katori|150|75|0.8
jackfruit,kathal,chakka|katori|100|95|1.7
dates,khajur,pericham pazham|piece|8|23|0.2
fruit salad,fruits,fruit|katori|150|90|1
#Snacks|Nuts
almonds,badam|handful|20|116|4.2
peanuts,groundnut,moongphali,kadalai|handful|30|170|7.5
cashew,kaju,cashews|handful|20|110|3.6
walnut,walnuts,akhrot|handful|20|130|3
raisins,kishmish|handful|20|60|0.6
#Sides|Home basics
salad,green salad,cucumber,kachumber|katori|100|30|1
pickle,achar,achaar,oorugai,uppinakayi|tbsp|15|30|0.2
papad,papadum,appalam,pappadam|piece|10|40|2
sugar,cheeni,sakkar|tsp|5|20|0
jaggery,gur,bellam,vellam|piece|10|38|0
honey,shahad|tsp|7|21|0
#Breakfast|Home basics
oats,oatmeal,oats porridge,daliya,dalia|katori|150|150|5
cornflakes,muesli|katori|30|110|2
"""

    /** A glass, a bowl or a spoon, in grams, so "a glass of dal" or "2 spoons of rice" still counts sensibly. */
    private val UNIT_GRAMS = mapOf("katori" to 150, "cup" to 150, "glass" to 250, "plate" to 250, "tbsp" to 15, "tsp" to 5, "handful" to 25, "scoop" to 60)
    private val UNIT_WORDS = mapOf(
        "katori" to "katori", "katoris" to "katori", "bowl" to "katori", "bowls" to "katori", "kattori" to "katori", "vati" to "katori",
        "cup" to "cup", "cups" to "cup", "glass" to "glass", "glasses" to "glass", "tumbler" to "glass",
        "plate" to "plate", "plates" to "plate", "spoon" to "tbsp", "spoons" to "tbsp", "tablespoon" to "tbsp", "tbsp" to "tbsp",
        "teaspoon" to "tsp", "tsp" to "tsp", "piece" to "piece", "pieces" to "piece", "pcs" to "piece", "slice" to "slice", "slices" to "slice",
        "handful" to "handful", "scoop" to "scoop", "scoops" to "scoop", "packet" to "packet", "packets" to "packet",
    )
    private val NUMBER_WORDS = mapOf(
        "a" to 1.0, "an" to 1.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0, "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0,
        "nine" to 9.0, "ten" to 10.0, "half" to 0.5, "quarter" to 0.25, "few" to 3.0, "some" to 1.0,
        "ek" to 1.0, "do" to 2.0, "teen" to 3.0, "char" to 4.0, "chaar" to 4.0, "paanch" to 5.0, "aadha" to 0.5, "adha" to 0.5,
    )

    val all: List<Food> by lazy {
        var meal = "Other"; var cuisine = "Home basics"
        TABLE.trim().lines().mapNotNull { line ->
            if (line.startsWith("#")) { line.drop(1).split("|").let { meal = it[0]; cuisine = it[1] }; return@mapNotNull null }
            val c = line.split("|"); if (c.size < 5) return@mapNotNull null
            Food(c[0].split(",").map { it.trim() }, c[1], c[2].toInt(), c[3].toDouble(), c[4].toDouble(), meal = meal, cuisine = cuisine)
        }
    }

    /** Foods the person added themselves, kept as JSON. */
    fun customFrom(json: String?): List<Food> = runCatching {
        val a = JSONArray(json ?: "[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> Food(listOf(o.getString("name")), o.optString("unit", "katori"), o.optInt("grams", 150), o.getDouble("kcal"), o.getDouble("protein"), custom = true, meal = o.optString("meal", "Other"), cuisine = "My foods") } }
    }.getOrDefault(emptyList())

    fun customWith(json: String?, f: Food): String {
        val a = JSONArray(json ?: "[]")
        a.put(JSONObject().put("name", f.name).put("unit", f.unit).put("grams", f.grams).put("kcal", f.kcal).put("protein", f.protein).put("meal", f.meal))
        return a.toString()
    }

    /** One part of a meal: how much, of what, and what it gives. [food] is null when nothing matched. */
    data class Item(val said: String, val qty: Double, val unit: String, val food: Food?, val estimated: Boolean) {
        val factor: Double get() {
            val f = food ?: return 0.0
            if (unit == f.unit || unit == "piece" || f.unit == "piece" || unit.isEmpty()) return qty
            val a = UNIT_GRAMS[unit] ?: return qty
            val b = UNIT_GRAMS[f.unit] ?: f.grams
            return qty * a / b
        }
        val kcal get() = (food?.kcal ?: 0.0) * factor
        val protein get() = (food?.protein ?: 0.0) * factor
        val amountWords get() = "${fmtQty(qty)} ${unitWord(unit.ifEmpty { food?.unit ?: "" }, qty)}".trim()
    }

    fun fmtQty(q: Double) = when (q) { 0.5 -> "½"; 0.25 -> "¼"; 1.5 -> "1½"; else -> if (q == q.toLong().toDouble()) q.toLong().toString() else "%.1f".format(q) }
    fun unitWord(u: String, q: Double): String {
        val one = when (u) { "tbsp" -> "spoon"; "tsp" -> "teaspoon"; "katori" -> "katori"; else -> u }
        return if (q > 1 && one.isNotEmpty() && !one.endsWith("s")) (if (one == "glass") "glasses" else if (one == "piece") "" else "${one}s") else if (one == "piece") "" else one
    }

    /** "two idlis and a katori of sambar, coffee" → items. */
    fun parse(text: String, custom: List<Food> = emptyList()): List<Item> {
        val foods = custom + all
        return text.lowercase().replace(Regex("[^a-z0-9½¼.\\s,&+/]"), " ")
            .split(Regex("\\s*(,|\\band\\b|&|\\+|/|\\bwith\\b|\\baur\\b|\\bthen\\b)\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
            .map { part -> parsePart(part, foods) }
    }

    private fun parsePart(part: String, foods: List<Food>): Item {
        var words = part.split(Regex("\\s+")).filter { it.isNotEmpty() && it != "of" }
        var qty = 1.0
        words.firstOrNull()?.let { w ->
            val n = w.replace("½", ".5").toDoubleOrNull() ?: NUMBER_WORDS[w]
            if (n != null) { qty = n; words = words.drop(1) }
        }
        var unit = ""
        words.firstOrNull()?.let { w -> UNIT_WORDS[w]?.let { unit = it; words = words.drop(1) } }
        // "1 and a half" style leftovers
        if (words.firstOrNull() == "half") { qty += 0.5; words = words.drop(1) }
        val name = words.joinToString(" ").trim()
        if (name.isEmpty()) return Item(part, qty, unit, null, false)
        val (food, estimated) = match(name, foods)
        return Item(name, qty, unit, food, estimated)
    }

    private fun singular(w: String) = when {
        w.endsWith("ies") && w.length > 4 -> w.dropLast(3) + "y"
        w.endsWith("es") && w.length > 4 && (w.endsWith("oes") || w.endsWith("ches") || w.endsWith("shes")) -> w.dropLast(2)
        w.endsWith("s") && w.length > 3 && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    /** The food whose name best fits; a near spelling counts but is marked estimated. */
    fun match(name: String, foods: List<Food> = all): Pair<Food?, Boolean> {
        val n = name.split(" ").joinToString(" ") { singular(it) }
        // exact name
        foods.firstOrNull { f -> f.names.any { it == n || it == name } }?.let { return it to false }
        // the longest known name inside what was said ("hot masala dosa" → masala dosa)
        foods.flatMap { f -> f.names.map { it to f } }.filter { (nm, _) -> Regex("\\b${Regex.escape(nm)}\\b").containsMatchIn(n) }
            .maxByOrNull { it.first.length }?.let { return it.second to false }
        // a near spelling of a whole name ("chapathy", "sambhar")
        var best: Food? = null; var bestD = Int.MAX_VALUE
        for (f in foods) for (nm in f.names) {
            val d = distance(nm, n)
            if (d < bestD) { bestD = d; best = f }
        }
        if (best != null && bestD <= maxOf(1, n.length / 4)) return best to true
        return null to false
    }

    private fun distance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val t = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return dp[b.length]
    }

    /** Stored with the food note, so reports don't have to parse again. */
    fun toJson(items: List<Item>): String {
        val a = JSONArray()
        items.forEach { i ->
            a.put(JSONObject().put("name", i.food?.name ?: i.said).put("amount", i.amountWords).put("kcal", i.kcal.roundToInt()).put("protein", (i.protein * 10).roundToInt() / 10.0)
                .put("estimated", i.estimated).put("known", i.food != null))
        }
        return JSONObject().put("items", a).put("kcal", items.sumOf { it.kcal }.roundToInt()).put("protein", (items.sumOf { it.protein } * 10).roundToInt() / 10.0).toString()
    }
}

/**
 * Liquid feeds, by mouth or by tube, defined by whoever gives them: a name, what goes in (each with its own calories
 * and protein), how much per feed and when. MedLog adds it up per feed.
 */
object Feeds {
    data class Part(val name: String, val amount: String, val kcal: Double, val protein: Double)
    /** Kept per feed (the "medicine" it's scheduled as). */
    data class Info(val parts: List<Part>, val tube: Boolean) {
        val kcal get() = parts.sumOf { it.kcal }
        val protein get() = parts.sumOf { it.protein }
    }

    fun infoFrom(json: String?, medId: Long): Info? = runCatching {
        JSONObject(json ?: "{}").optJSONObject("$medId")?.let { o ->
            val a = o.getJSONArray("parts")
            Info((0 until a.length()).map { i -> a.getJSONObject(i).let { Part(it.getString("name"), it.optString("amount"), it.getDouble("kcal"), it.getDouble("protein")) } }, o.optBoolean("tube"))
        }
    }.getOrNull()

    fun infoWith(json: String?, medId: Long, i: Info): String {
        val a = JSONArray(); i.parts.forEach { p -> a.put(JSONObject().put("name", p.name).put("amount", p.amount).put("kcal", p.kcal).put("protein", p.protein)) }
        return JSONObject(json ?: "{}").put("$medId", JSONObject().put("parts", a).put("tube", i.tube)).toString()
    }

    /** "200 ml" → 200. */
    fun ml(amount: String): Double = amount.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
}
