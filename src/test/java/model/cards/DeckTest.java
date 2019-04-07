package model.cards;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DeckTest {

    private Deck garbage;
    private Deck noGarbage;

    @BeforeEach
    void before() {
        garbage = new Deck(true);
        noGarbage = new Deck();
    }

    @Test
    void garbage() {
        garbage.addCard(mock(UsableCard.class));
        garbage.addCard(mock(UsableCard.class));
        garbage.addCard(mock(UsableCard.class));
        garbage.addCard(mock(UsableCard.class));

        assertThrows(NullPointerException.class, () -> garbage.addCard(null));
        assertEquals(4, garbage.numOfCards());

        UsableCard c1 = garbage.draw();
        UsableCard c2 = garbage.draw();
        UsableCard c3 = garbage.draw();
        UsableCard c4 = garbage.draw();

        assertNotNull(c1);
        assertNotNull(c2);
        assertNotNull(c3);
        assertNotNull(c4);
        assertNull(garbage.draw());

        garbage.discardCard(c1);
        garbage.discardCard(c2);
        garbage.discardCard(c3);
        garbage.discardCard(c4);

        assertThrows(NullPointerException.class, () -> garbage.discardCard(null));
        assertEquals(4, garbage.numOfDiscards());

        garbage.shuffle();
        garbage.flush();
    }

    @Test
    void noGarbage() {
        noGarbage.addCard(mock(UsableCard.class));
        noGarbage.addCard(mock(UsableCard.class));
        noGarbage.addCard(mock(UsableCard.class));

        noGarbage.shuffle();
        noGarbage.discardCard(noGarbage.draw());

        assertThrows(NullPointerException.class, () -> noGarbage.numOfDiscards());

        noGarbage.flush();
    }

}