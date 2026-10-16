package nro.models.boss.event_trung_thu;


import nro.models.boss.Boss;
import nro.models.boss.BossesData;
import nro.models.boss.BossID;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.services.EffectSkillService;
import nro.models.services.Service;
import nro.models.utils.Util;

public class ThoDaiCa extends Boss {

    public ThoDaiCa() throws Exception {
        super(BossID.THO_DAI_CA, true, true, BossesData.THO_DAI_CA);
    }

    @Override
    public void reward(Player plKill) {
        ItemMap it = new ItemMap(this.zone, 1045, 1, this.location.x, this.zone.map.yPhysicInTop(this.location.x,
                this.location.y - 24), plKill.id);
        Service.gI().dropItemMap(this.zone, it);
    }

    @Override
   public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
    if (this.isDie()) {
        return 0;
    }

    if (!piercing && Util.isTrue(this.nPoint.tlNeDon, 1000)) {
        this.chat("Xí hụt");
        return 0;
    }

    // Thỏ Đại Ca chỉ nhận tối đa 500 HP mỗi lần đánh
    damage = Math.min(damage, 500);

    // Tính phòng thủ của boss
    if (!piercing) {
        damage = this.nPoint.subDameInjureWithDeff(damage);
    }

    // Không cho damage vượt quá 500 sau khi tính phòng thủ
    damage = Math.min(damage, 500);

    if (damage < 0) {
        damage = 0;
    }

    this.nPoint.subHP(damage);

    if (this.isDie()) {
        this.setDie(plAtt);
        this.die(plAtt);
    }

    return (int) damage;
}

    @Override
    public void joinMap() {
        super.joinMap(); //To change body of generated methods, choose Tools | Templates.
        st = System.currentTimeMillis();
    }

    private long st;

    @Override
    public void autoLeaveMap() {
        if (Util.canDoWithTime(st, 900000)) {
            this.leaveMapNew();
        }
        if (this.zone != null && this.zone.getNumOfPlayers() > 0) {
            st = System.currentTimeMillis();
        }
    }
}
