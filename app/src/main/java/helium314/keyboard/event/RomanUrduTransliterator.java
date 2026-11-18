// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Small offline first-version transliterator. No model, network, or Android dependencies. */
public final class RomanUrduTransliterator {
    private RomanUrduTransliterator() {}

    private static final Map<String, String> WORDS;
    static {
        Map<String, String> words = new HashMap<>();
        // Original seed vocabulary. Different Roman spellings share an Urdu spelling.
        String[] entries = {
            "aap=آپ", "ap=آپ", "aapka=آپ کا", "aapki=آپ کی", "aapko=آپ کو",
            "main=میں", "mein=میں", "mai=میں", "me=میں", "hum=ہم", "ham=ہم",
            "tum=تم", "tumhara=تمہارا", "tumhari=تمہاری", "wo=وہ", "woh=وہ",
            "ye=یہ", "yeh=یہ", "yahan=یہاں", "wahan=وہاں", "yahaan=یہاں", "wahaan=وہاں",
            "hai=ہے", "hain=ہیں", "hen=ہیں", "ho=ہو", "hun=ہوں", "hoon=ہوں",
            "tha=تھا", "thi=تھی", "thay=تھے", "the=تھے", "hoga=ہوگا", "hogi=ہوگی",
            "ka=کا", "ki=کی", "ke=کے", "ko=کو", "se=سے", "par=پر", "pe=پہ",
            "aur=اور", "or=اور", "lekin=لیکن", "magar=مگر", "bhi=بھی", "hi=ہی",
            "to=تو", "tak=تک", "liye=لیے", "liyeh=لیے", "liyein=لیے", "liyea=لیے",
            "kya=کیا", "kia=کیا", "kyun=کیوں", "kyoon=کیوں", "kab=کب", "kahan=کہاں",
            "kaun=کون", "kon=کون", "kaise=کیسے", "kesay=کیسے", "kaisa=کیسا", "kaisi=کیسی",
            "kitna=کتنا", "kitni=کتنی", "kitne=کتنے", "kuch=کچھ", "koi=کوئی",
            "nahi=نہیں", "nahin=نہیں", "nhi=نہیں", "na=نہ", "haan=ہاں", "han=ہاں", "ji=جی",
            "mera=میرا", "meri=میری", "mere=میرے", "mujhe=مجھے", "mujhay=مجھے", "mujh=مجھ",
            "hamara=ہمارا", "hamari=ہماری", "hamare=ہمارے", "humein=ہمیں", "hamein=ہمیں",
            "iska=اس کا", "iski=اس کی", "isko=اس کو", "uska=اس کا", "uski=اس کی", "usko=اس کو",
            "is=اس", "us=اس", "iss=اس", "uss=اس", "in=ان", "un=ان", "sab=سب", "har=ہر",
            "ab=اب", "abhi=ابھی", "phir=پھر", "fir=پھر", "jab=جب", "tab=تب",
            "aaj=آج", "aj=آج", "kal=کل", "roz=روز", "raat=رات", "din=دن", "subah=صبح",
            "shaam=شام", "waqt=وقت", "waqat=وقت", "pehle=پہلے", "baad=بعد", "bad=بعد",
            "acha=اچھا", "achha=اچھا", "accha=اچھا", "achi=اچھی", "achhi=اچھی", "ache=اچھے",
            "theek=ٹھیک", "thik=ٹھیک", "bohat=بہت", "bahut=بہت", "buhat=بہت",
            "zyada=زیادہ", "ziyada=زیادہ", "kam=کم", "bilkul=بالکل", "zaroor=ضرور",
            "shukriya=شکریہ", "shukria=شکریہ", "meherbani=مہربانی", "mehrbani=مہربانی",
            "salam=سلام", "salaam=سلام", "assalam=السلام", "alaikum=علیکم", "walaikum=وعلیکم",
            "allah=اللہ", "khuda=خدا", "hafiz=حافظ", "inshallah=ان شاء اللہ", "mashallah=ماشاءاللہ",
            "dost=دوست", "bhai=بھائی", "behen=بہن", "behan=بہن", "ammi=امی", "abu=ابو",
            "naam=نام", "nam=نام", "ghar=گھر", "kaam=کام", "kamra=کمرہ", "baat=بات",
            "pani=پانی", "paani=پانی", "khana=کھانا", "chai=چائے", "paisa=پیسہ", "paise=پیسے",
            "kar=کر", "karo=کرو", "karein=کریں", "karen=کریں", "karna=کرنا", "karta=کرتا",
            "karti=کرتی", "karte=کرتے", "kiya=کیا", "karenge=کریں گے", "karega=کرے گا",
            "raha=رہا", "rahi=رہی", "rahe=رہے", "rha=رہا", "rhi=رہی", "rhe=رہے",
            "gaya=گیا", "gayi=گئی", "gaye=گئے", "aya=آیا", "aaya=آیا", "ayi=آئی", "aayi=آئی",
            "aao=آؤ", "ao=آؤ", "jao=جاؤ", "jana=جانا", "aana=آنا", "ana=آنا",
            "do=دو", "de=دے", "dena=دینا", "diya=دیا", "di=دی", "lo=لو", "lena=لینا",
            "likho=لکھو", "likhna=لکھنا", "likh=لکھ", "likha=لکھا", "parho=پڑھو", "parhna=پڑھنا",
            "bolo=بولو", "bol=بول", "bolna=بولنا", "batao=بتاؤ", "batayein=بتائیں",
            "dekho=دیکھو", "dekh=دیکھ", "dekha=دیکھا", "suno=سنو", "sun=سن", "samajh=سمجھ",
            "chahiye=چاہیے", "chahie=چاہیے", "chahta=چاہتا", "chahti=چاہتی", "sakta=سکتا", "sakti=سکتی",
            "sakte=سکتے", "mila=ملا", "mil=مل", "milna=ملنا", "pata=پتا", "pasand=پسند",
            "khush=خوش", "khushi=خوشی", "pyar=پیار", "pyaar=پیار", "mohabbat=محبت",
            "urdu=اردو", "english=انگریزی", "angrezi=انگریزی", "roman=رومن", "zaban=زبان",
            "keyboard=کی بورڈ", "mobile=موبائل", "phone=فون", "app=ایپ", "internet=انٹرنیٹ",
            "pakistan=پاکستان", "lahore=لاہور", "karachi=کراچی", "islamabad=اسلام آباد",
            "ek=ایک", "aik=ایک", "teen=تین", "char=چار", "chaar=چار", "paanch=پانچ", "panch=پانچ"
        };
        for (String entry : entries) {
            int separator = entry.indexOf('=');
            words.put(entry.substring(0, separator), entry.substring(separator + 1));
        }
        WORDS = Collections.unmodifiableMap(words);
    }

    private static final String[] PAIRS = {
        "chh", "چھ", "kh", "خ", "gh", "غ", "sh", "ش", "ch", "چ",
        "th", "تھ", "dh", "دھ", "ph", "پھ", "bh", "بھ", "jh", "جھ",
        "aa", "ا", "ee", "ی", "ii", "ی", "oo", "و", "uu", "و", "ai", "ے", "ay", "ے", "au", "و"
    };
    private static final String[] LETTERS = {
        "ا", "ب", "ک", "د", "ے", "ف", "گ", "ہ", "ی", "ج", "ک", "ل", "م",
        "ن", "و", "پ", "ق", "ر", "س", "ت", "و", "و", "و", "کس", "ی", "ز"
    };

    /** Converts one Latin word; unsupported characters pass through without loss. */
    public static String convert(String roman) {
        String word = roman.toLowerCase(Locale.ROOT);
        String known = WORDS.get(word);
        if (known != null) return known;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < word.length();) {
            boolean matched = false;
            for (int p = 0; p < PAIRS.length; p += 2) {
                if (word.startsWith(PAIRS[p], i)) {
                    out.append(PAIRS[p + 1]);
                    i += PAIRS[p].length();
                    matched = true;
                    break;
                }
            }
            if (matched) continue;
            char c = word.charAt(i);
            out.append(c >= 'a' && c <= 'z' ? LETTERS[c - 'a'] : roman.substring(i, i + 1));
            i++;
        }
        return out.toString();
    }
}
