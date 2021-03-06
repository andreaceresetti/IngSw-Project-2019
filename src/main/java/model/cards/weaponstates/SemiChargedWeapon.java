package model.cards.weaponstates;

import exceptions.cards.WeaponNotChargedException;
import model.cards.WeaponCard;
import model.cards.effects.Effect;
import network.message.EffectRequest;

public class SemiChargedWeapon implements WeaponState {

    private static final long serialVersionUID = 2736318954908242454L;

    @Override
    public boolean charged(WeaponCard weapon) {
        return false;
    }

    @Override
    public boolean isRechargeable(WeaponCard weapon) {
        return true;
    }

    @Override
    public int status() {
        return WeaponCard.SEMI_CHARGED;
    }

    @Override
    public void use(Effect effect, EffectRequest request) throws WeaponNotChargedException {
        throw new WeaponNotChargedException();
    }
}
