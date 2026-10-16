package nro.models.boss.Langla;

import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import nro.models.player.Player;
import nro.models.services.TaskService;

/**
 * Akaza - boss cuối.
 * Khi chết bắt đầu cooldown 5 phút cho cả pack.
 */
public class AKaZa extends LanglaBoss {

    public AKaZa() throws Exception {
        super(BossID.LANGLA_AKAZA, BossesData.LANGLA_AKAZA);
    }

    @Override
    public void doneChatS() {
        this.setLanglaActive(false);
    }

    @Override
    public void die(Player plKill) {
        super.die(plKill);
        // Tính 5 phút ngay tại thời điểm Akaza bị hạ.
        LanglaCycle.startCooldown();
    }

    @Override
    public void doneChatE() {
        // Không có boss tiếp theo. LanglaCycle đã khóa 5 phút từ die().
    }

    @Override
    public void reward(Player plKill) {
        if (plKill != null) {
            TaskService.gI().checkDoneTaskKillBoss(plKill, this);
            LanglaReward.dropCostume(plKill, 1886, new int[][]{
                {50, 25},
                {77, 25},
                {103, 25},
                {14, 15},
                {5, 15}
            });
        }
    }
}
