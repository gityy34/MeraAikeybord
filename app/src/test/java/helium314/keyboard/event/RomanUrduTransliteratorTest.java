// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.event;

import org.junit.Test;
import java.util.Locale;
import static org.junit.Assert.assertEquals;

public class RomanUrduTransliteratorTest {
    @Test public void commonSentences() {
        assertEquals("آپ کیسے ہیں", sentence("aap kaise hain"));
        assertEquals("میں ٹھیک ہوں", sentence("main theek hoon"));
        assertEquals("مجھے اردو لکھنا پسند ہے", sentence("mujhe urdu likhna pasand hai"));
        assertEquals("السلام علیکم", sentence("assalam alaikum"));
    }

    @Test public void spellingVariants() {
        for (String word : new String[]{"acha", "achha", "accha"})
            assertEquals("اچھا", RomanUrduTransliterator.convert(word));
        for (String word : new String[]{"nahi", "nahin", "nhi"})
            assertEquals("نہیں", RomanUrduTransliterator.convert(word));
    }

    @Test public void unknownWordsUseDigraphs() {
        assertEquals("خز", RomanUrduTransliterator.convert("khz"));
        assertEquals("چھز", RomanUrduTransliterator.convert("chhz"));
        assertEquals("شز", RomanUrduTransliterator.convert("shz"));
        assertEquals("", RomanUrduTransliterator.convert(""));
        assertEquals("123🙂", RomanUrduTransliterator.convert("123🙂"));
    }

    @Test public void capitalizationIsIndependentOfPhoneLocale() {
        Locale old = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals("ہیں", RomanUrduTransliterator.convert("HAIN"));
            assertEquals("میں", RomanUrduTransliterator.convert("MAIN"));
        } finally {
            Locale.setDefault(old);
        }
    }

    private static String sentence(String roman) {
        StringBuilder out = new StringBuilder();
        for (String word : roman.split(" ")) {
            if (out.length() > 0) out.append(' ');
            out.append(RomanUrduTransliterator.convert(word));
        }
        return out.toString();
    }
}
