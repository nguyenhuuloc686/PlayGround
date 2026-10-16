package nro.models.boss.Langla;

import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import nro.models.player.Player;
import nro.models.services.TaskService;

/**
 * Akatsuki - boss thứ ba.
 */
public class AKatSuKi extends LanglaBoss {

    public AKatSuKi() throws Exception {
        super(BossID.LANGLA_AKATSUKI, BossesData.LANGLA_AKATSUKI);
    }

    @Override
    public void doneChatS() {
        this.setLanglaActive(false);
    }

    @Override
    public void die(Player plKill) {
        super.die(plKill);
        // Hạ Akatsuki xong: bật Akaza ngay.
        this.activateNextBossAfterDelay(BossID.LANGLA_AKAZA);
    }

    @Override
    public void reward(Player plKill) {
        if (plKill != null) {
            TaskService.gI().checkDoneTaskKillBoss(plKill, this);
            LanglaReward.dropCostume(plKill, 1879, new int[][]{
                {50, 22},
                {77, 22},
                {103, 22},
                {101, 55},
                {14, 10}
            });
        }
    }
}
