package model.map;

import enumerations.RoomColor;
import enumerations.SquareAdjacency;
import model.cards.AmmoTile;
import model.cards.WeaponCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.*;

public class CardSquareTest {

    private CardSquare noTileSquare;
    private CardSquare tileSquare;

    @BeforeEach
    void before() {
        noTileSquare = new CardSquare(RoomColor.RED, SquareAdjacency.WALL, SquareAdjacency.SQUARE,
                                            SquareAdjacency.DOOR, SquareAdjacency.SQUARE);
        tileSquare = new CardSquare(RoomColor.RED, SquareAdjacency.WALL, SquareAdjacency.SQUARE,
                SquareAdjacency.DOOR, SquareAdjacency.SQUARE, mock(AmmoTile.class));
    }

    @Test
    void defaultMethods() {
        assertNull(noTileSquare.pickAmmoTile());
        assertNotNull(tileSquare.pickAmmoTile());
        assertFalse(noTileSquare.isAmmoTilePresent());

        AmmoTile ammoTile = mock(AmmoTile.class);

        tileSquare.setAmmoTile(ammoTile);
        assertTrue(tileSquare.isAmmoTilePresent());
        assertEquals(ammoTile, tileSquare.pickAmmoTile());
        assertFalse(tileSquare.isAmmoTilePresent());
    }

    @Test
    void spawnSquare() {
        SpawnSquare sq = new SpawnSquare(RoomColor.RED, SquareAdjacency.DOOR, SquareAdjacency.WALL, SquareAdjacency.WALL, SquareAdjacency.SQUARE);
        WeaponCard[] weaponCards = new WeaponCard[SpawnSquare.MAX_WEAPONS];

        assertEquals(RoomColor.RED, sq.getRoomColor());
        assertEquals(SquareAdjacency.DOOR, sq.getNorth());
        assertEquals(SquareAdjacency.WALL, sq.getEast());
        assertEquals(SquareAdjacency.WALL, sq.getSouth());
        assertEquals(SquareAdjacency.SQUARE, sq.getWest());

        weaponCards[0] = mock(WeaponCard.class);
        weaponCards[1] = mock(WeaponCard.class);
        weaponCards[2] = mock(WeaponCard.class);

        sq.addWeapon(weaponCards[0]);
        sq.addWeapon(weaponCards[1]);
        sq.addWeapon(weaponCards[2]);

        assertTrue(sq.hasWeapon(weaponCards[2]));
        assertArrayEquals(weaponCards, sq.getWeapons());
        sq.removeWeapon(weaponCards[2]);
        assertFalse(sq.hasWeapon(weaponCards[2]));
        assertThrows(NullPointerException.class, () -> sq.removeWeapon(null));
    }
}
