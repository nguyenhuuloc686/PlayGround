package nro.models.boss.Langla;

import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import nro.models.player.Player;
import nro.models.services.TaskService;

/**
 * Sasuke - boss thứ hai.
 */
public class SaSuKe extends LanglaBoss {

    public SaSuKe() throws Exception {
        super(BossID.LANGLA_SASUKE, BossesData.LANGLA_SASUKE);
    }

    @Override
    public void doneChatS() {
        this.setLanglaActive(false);
    }

    @Override
    public void die(Player plKill) {
        super.die(plKill);
        // Hạ Sasuke xong: bật Akatsuki ngay.
        this.activateNextBossAfterDelay(BossID.LANGLA_AKATSUKI);
    }

    @Override
    public void reward(Player plKill) {
        if (plKill != null) {
            TaskService.gI().checkDoneTaskKillBoss(plKill, this);
            LanglaReward.dropCostume(plKill, 1877, new int[][]{
                {50, 25},
                {77, 25},
                {6, 25},
                {14, 15}
            });
        }
    }
}
