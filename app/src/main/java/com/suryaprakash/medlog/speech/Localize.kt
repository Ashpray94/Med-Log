package com.suryaprakash.medlog.speech

/**
 * The conversation in the person's own language. English stays on screen too (bilingual),
 * so a helper can always follow. Hindi and Tamil are done; other languages speak English until a
 * native speaker has checked the wording.
 */
object Localize {
    fun of(text: String, lang: String): String? {
        // the app-wide translation (everyday, spoken words) comes first
        if (I18n.lang == lang.substringBefore('-')) I18n.tr(text).takeIf { it != text }?.let { return it }
        return if (lang.startsWith("hi")) HI[text] else null
    }

    private val HI = mapOf(
        "How are you feeling? Tell me in your own words." to "आप कैसा महसूस कर रहे हैं? अपने शब्दों में बताइए।",
        "When did it start?" to "यह कब शुरू हुआ?",
        "Just now" to "अभी", "Earlier today" to "आज", "Yesterday" to "कल", "A few days ago" to "कुछ दिन पहले", "A week or more" to "एक हफ़्ता या ज़्यादा",
        "How many times today?" to "आज कितनी बार?",
        "Once" to "एक बार", "2 times" to "दो बार", "3 times" to "तीन बार", "4 times" to "चार बार", "5 or more" to "पाँच या ज़्यादा",
        "Show me where it is." to "दिखाइए, कहाँ है?",
        "How deep does it feel?" to "यह कितना अंदर महसूस होता है?",
        "On the skin" to "त्वचा पर", "Just under the skin" to "त्वचा के ठीक नीचे", "In the muscle" to "मांसपेशी में", "Deep inside" to "बहुत अंदर", "In the bone or joint" to "हड्डी या जोड़ में",
        "How bad is it?" to "कितना ज़्यादा है?",
        "A little" to "थोड़ा", "Some" to "कुछ", "Bad" to "ज़्यादा", "Very bad" to "बहुत ज़्यादा", "Worst ever" to "सहन नहीं होता",
        "What does it feel like?" to "यह कैसा लगता है?",
        "Sharp" to "तेज़", "Dull ache" to "हल्का दर्द", "Burning" to "जलन", "Throbbing" to "धक-धक", "Cramping" to "ऐंठन", "Pressing" to "दबाव", "Stabbing" to "चुभन", "Tingling" to "झनझनाहट",
        "Can you tell me a little more? It helps your doctor." to "क्या आप थोड़ा और बता सकते हैं? इससे डॉक्टर को मदद मिलेगी।",
        "Is it there all the time, or does it come and go?" to "क्या यह हर समय रहता है, या आता-जाता है?",
        "All the time" to "हर समय", "Comes and goes" to "आता-जाता है",
        "What makes it worse?" to "किससे बढ़ता है?", "What makes it better?" to "किससे आराम मिलता है?",
        "Moving" to "हिलने से", "Walking" to "चलने से", "Eating" to "खाने से", "Lying down" to "लेटने से", "Breathing in" to "साँस लेने से", "Nothing" to "किसी से नहीं",
        "Rest" to "आराम", "Medicine" to "दवा", "Warmth" to "गरमाहट",
        "Did you take any medicine for it?" to "क्या आपने इसके लिए कोई दवा ली?",
        "Which medicine? Just say its name." to "कौन सी दवा? बस उसका नाम बताइए।",
        "Anything else you want your doctor to know?" to "डॉक्टर को और कुछ बताना चाहते हैं?",
        "Yes" to "हाँ", "No" to "नहीं", "I'm done" to "बस हो गया", "Skip" to "छोड़ें",
        "Was there any blood?" to "क्या खून था?", "Did it look like coffee grounds?" to "क्या यह कॉफ़ी के दानों जैसा दिखता था?",
        "Can you keep water down?" to "क्या पानी पेट में टिकता है?", "Have you passed urine in the last 8 hours?" to "क्या पिछले 8 घंटों में पेशाब आया?",
        "Any blood in it?" to "क्या उसमें खून था?", "Was it black and sticky?" to "क्या यह काला और चिपचिपा था?",
        "Is there any confusion?" to "क्या कोई उलझन है?", "Does it spread to your arm, jaw or back?" to "क्या दर्द हाथ, जबड़े या पीठ तक जाता है?",
        "Are you sweating?" to "क्या पसीना आ रहा है?", "Is it hard to breathe?" to "क्या साँस लेने में तकलीफ़ है?", "Is it hard even when resting?" to "क्या आराम करते समय भी तकलीफ़ है?",
        "Any blood when you cough?" to "खाँसी में खून आता है?", "Did you hit your head?" to "क्या सिर पर चोट लगी?", "Could you get up by yourself?" to "क्या आप खुद उठ पाए?",
        "Did it start suddenly, the worst ever?" to "क्या यह अचानक और अब तक का सबसे ज़्यादा था?", "Any change in your eyesight?" to "क्या नज़र में कोई बदलाव है?",
        "Is one side of the face drooping?" to "क्या चेहरे का एक तरफ़ लटक रहा है?", "Is an arm or leg weak?" to "क्या हाथ या पैर कमज़ोर है?", "Is speech slurred?" to "क्या बोली लड़खड़ा रही है?",
        "Any swelling of lips, face or tongue?" to "क्या होंठ, चेहरे या जीभ पर सूजन है?", "Are you unable to pass urine?" to "क्या पेशाब नहीं हो रहा?",
        "Are you having thoughts of hurting yourself?" to "क्या आपको खुद को नुकसान पहुँचाने के विचार आ रहे हैं?",
        "Does the bleeding stop when you press on it?" to "दबाने पर खून रुकता है?", "Do you take a blood thinner?" to "क्या आप खून पतला करने की दवा लेते हैं?",
        "Can you swallow water?" to "क्या आप पानी निगल पाते हैं?", "Is your neck stiff?" to "क्या गर्दन अकड़ी हुई है?", "Did you pass out?" to "क्या आप बेहोश हुए?",
        "Is it only one leg?" to "क्या सिर्फ़ एक पैर में है?",
        "Here's what I noted" to "मैंने यह लिखा है", "Saved" to "सेव हो गया",
    )

    private val TA = mapOf(
        "How are you feeling? Tell me in your own words." to "நீங்கள் எப்படி இருக்கிறீர்கள்? உங்கள் வார்த்தைகளில் சொல்லுங்கள்.",
        "When did it start?" to "இது எப்போது தொடங்கியது?",
        "Just now" to "இப்போதுதான்", "Earlier today" to "இன்று", "Yesterday" to "நேற்று", "A few days ago" to "சில நாட்களுக்கு முன்", "A week or more" to "ஒரு வாரம் அல்லது அதற்கு மேல்",
        "How many times today?" to "இன்று எத்தனை முறை?",
        "Once" to "ஒரு முறை", "2 times" to "இரண்டு முறை", "3 times" to "மூன்று முறை", "4 times" to "நான்கு முறை", "5 or more" to "ஐந்து அல்லது அதிகம்",
        "Show me where it is." to "எங்கே என்று காட்டுங்கள்.",
        "How deep does it feel?" to "இது எவ்வளவு ஆழமாக உணர்கிறது?",
        "On the skin" to "தோலின் மேல்", "Just under the skin" to "தோலுக்கு சற்று கீழே", "In the muscle" to "தசையில்", "Deep inside" to "உள்ளே ஆழமாக", "In the bone or joint" to "எலும்பு அல்லது மூட்டில்",
        "How bad is it?" to "எவ்வளவு அதிகமாக இருக்கிறது?",
        "A little" to "கொஞ்சம்", "Some" to "ஓரளவு", "Bad" to "அதிகம்", "Very bad" to "ரொம்ப அதிகம்", "Worst ever" to "தாங்க முடியவில்லை",
        "What does it feel like?" to "இது எப்படி இருக்கிறது?",
        "Sharp" to "கூர்மையான", "Dull ache" to "மந்தமான வலி", "Burning" to "எரிச்சல்", "Throbbing" to "துடிப்பு", "Cramping" to "பிடிப்பு", "Pressing" to "அழுத்தம்", "Stabbing" to "குத்துவது போல்", "Tingling" to "கூச்சம்",
        "Can you tell me a little more? It helps your doctor." to "இன்னும் கொஞ்சம் சொல்ல முடியுமா? இது உங்கள் மருத்துவருக்கு உதவும்.",
        "Is it there all the time, or does it come and go?" to "இது எப்போதும் இருக்கிறதா, அல்லது வந்து போகிறதா?",
        "All the time" to "எப்போதும்", "Comes and goes" to "வந்து போகிறது",
        "What makes it worse?" to "எதனால் அதிகமாகிறது?", "What makes it better?" to "எதனால் குறைகிறது?",
        "Moving" to "அசைந்தால்", "Walking" to "நடந்தால்", "Eating" to "சாப்பிட்டால்", "Lying down" to "படுத்தால்", "Breathing in" to "மூச்சு இழுத்தால்", "Nothing" to "எதுவும் இல்லை",
        "Rest" to "ஓய்வு", "Medicine" to "மருந்து", "Warmth" to "சூடு",
        "Did you take any medicine for it?" to "இதற்கு ஏதாவது மருந்து எடுத்தீர்களா?",
        "Which medicine? Just say its name." to "எந்த மருந்து? அதன் பெயரைச் சொல்லுங்கள்.",
        "Anything else you want your doctor to know?" to "மருத்துவரிடம் சொல்ல வேறு ஏதாவது உள்ளதா?",
        "Yes" to "ஆம்", "No" to "இல்லை", "I'm done" to "போதும்", "Skip" to "விடுங்கள்",
        "Was there any blood?" to "ரத்தம் இருந்ததா?", "Did it look like coffee grounds?" to "காபி தூள் போல இருந்ததா?",
        "Can you keep water down?" to "தண்ணீர் குடித்தால் நிற்கிறதா?", "Have you passed urine in the last 8 hours?" to "கடந்த 8 மணி நேரத்தில் சிறுநீர் கழித்தீர்களா?",
        "Any blood in it?" to "அதில் ரத்தம் இருந்ததா?", "Was it black and sticky?" to "கருப்பாகவும் ஒட்டும்படியும் இருந்ததா?",
        "Is there any confusion?" to "ஏதாவது குழப்பம் இருக்கிறதா?", "Does it spread to your arm, jaw or back?" to "வலி கை, தாடை அல்லது முதுகுக்கு பரவுகிறதா?",
        "Are you sweating?" to "வியர்க்கிறதா?", "Is it hard to breathe?" to "மூச்சு விட கஷ்டமாக இருக்கிறதா?", "Is it hard even when resting?" to "ஓய்வில் இருக்கும்போதும் கஷ்டமாக இருக்கிறதா?",
        "Any blood when you cough?" to "இருமலில் ரத்தம் வருகிறதா?", "Did you hit your head?" to "தலையில் அடிபட்டதா?", "Could you get up by yourself?" to "நீங்களே எழுந்திருக்க முடிந்ததா?",
        "Did it start suddenly, the worst ever?" to "இது திடீரென்று, இதுவரை இல்லாத அளவு வந்ததா?", "Any change in your eyesight?" to "பார்வையில் ஏதாவது மாற்றம் உள்ளதா?",
        "Is one side of the face drooping?" to "முகத்தின் ஒரு பக்கம் தொங்குகிறதா?", "Is an arm or leg weak?" to "கை அல்லது கால் பலவீனமாக உள்ளதா?", "Is speech slurred?" to "பேச்சு குழறுகிறதா?",
        "Any swelling of lips, face or tongue?" to "உதடு, முகம் அல்லது நாக்கில் வீக்கம் உள்ளதா?", "Are you unable to pass urine?" to "சிறுநீர் கழிக்க முடியவில்லையா?",
        "Are you having thoughts of hurting yourself?" to "உங்களை காயப்படுத்திக்கொள்ளும் எண்ணம் வருகிறதா?",
        "Does the bleeding stop when you press on it?" to "அழுத்தினால் ரத்தம் நிற்கிறதா?", "Do you take a blood thinner?" to "ரத்தத்தை மெலிதாக்கும் மருந்து எடுக்கிறீர்களா?",
        "Can you swallow water?" to "தண்ணீர் விழுங்க முடிகிறதா?", "Is your neck stiff?" to "கழுத்து இறுக்கமாக உள்ளதா?", "Did you pass out?" to "மயக்கம் அடைந்தீர்களா?",
        "Is it only one leg?" to "ஒரு காலில் மட்டும் தானா?",
        "Here's what I noted" to "நான் குறித்தது இது", "Saved" to "சேமிக்கப்பட்டது",
    )
}
