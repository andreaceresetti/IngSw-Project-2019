package model.cards.weaponstates;

import exceptions.cards.WeaponNotChargedException;
import model.cards.FiringAction;
import model.cards.effects.Effect;
import model.cards.WeaponCard;
import model.cards.WeaponState;
import model.player.Player;

public class ChargedWeapon implements WeaponState {

    @Override
    public boolean charged(WeaponCard weapon) {
        return true;
    }

    @Override
    public boolean rechargeable(WeaponCard weapon) {
        return false;
    }

    @Override
    public int status() {
        return WeaponCard.CHARGED;
    }

    @Override
    public void use(Effect effect, FiringAction firingAction, Player playerDealer) throws WeaponNotChargedException {
        effect.execute(firingAction, playerDealer);
    }
}
