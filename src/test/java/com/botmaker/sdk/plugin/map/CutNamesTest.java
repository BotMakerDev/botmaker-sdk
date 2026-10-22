package com.botmaker.sdk.plugin.map;

import com.botmaker.sdk.plugin.map.CutForm.Kind;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The names the cut form suggests: from the words on a cut, else its screen, kind and a number. */
class CutNamesTest {

    @Test
    void wordsMakeTheName() {
        assertEquals("claim_all", CutNames.suggest("Claim all!", "HOME", Kind.BUTTON, Set.of()));
        assertEquals("equipe", CutNames.fromWords("  Équipe\nsecond line"), "accents go, the first line only");
        assertEquals("x_speed", CutNames.fromWords("2x speed"), "a name starts with no digit");
    }

    @Test
    void noWordsNamesItByScreenAndKind() {
        assertEquals("home_button_1", CutNames.suggest(null, "HOME", Kind.BUTTON, Set.of()));
        assertEquals("home_marker_2", CutNames.suggest("", "HOME", Kind.MARKER, Set.of("home_marker_1")));
        assertEquals("cut_zone_1", CutNames.suggest(null, null, Kind.ZONE, Set.of()), "a cut on no screen");
        // A number or one misread letter is no name.
        assertEquals("tavern_number_1", CutNames.suggest("12,500", "TAVERN", Kind.DIGITS, Set.of()));
        assertEquals("tavern_back_1", CutNames.suggest("X", "TAVERN", Kind.BACK, Set.of()));
    }

    @Test
    void aTakenNameGetsANumber() {
        assertEquals("claim_2", CutNames.suggest("Claim", "HOME", Kind.BUTTON, Set.of("claim")));
        assertEquals("claim_3", CutNames.unique("claim", Set.of("claim", "claim_2")));
    }

    @Test
    void aSentenceIsCutAtAWord() {
        String name = CutNames.fromWords("Collect every reward of the daily quests before they expire");
        assertEquals("collect_every_reward_of_the", name);
        assertEquals(true, name.length() <= CutNames.LONGEST);
    }
}
