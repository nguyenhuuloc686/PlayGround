package nro.models.boss.Langla;

import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import nro.models.consts.BossStatus;
import nro.models.player.Player;
import nro.models.services.TaskService;

/**
 * Naruto - boss đầu tiên.
 */
public class NaRuTo extends LanglaBoss {

    public NaRuTo() throws Exception {
        super(BossID.LANGLA_NARUTO, BossesData.LANGLA_NARUTO);
    }

    @Override
    public void doneChatS() {
        this.setLanglaActive(true);
    }

    @Override
    public void die(Player plKill) {
        super.die(plKill);
        // Hạ Naruto xong: bật Sasuke ngay, không chờ CHAT_E.
        this.activateNextBossAfterDelay(BossID.LANGLA_SASUKE);
    }

    @Override
    public void reward(Player plKill) {
        if (plKill != null) {
            TaskService.gI().checkDoneTaskKillBoss(plKill, this);
            LanglaReward.dropCostume(plKill, 1876, new int[][]{
                {50, 25}, // Sức đánh 25%
                {77, 25}, // HP 25%
                {6, 25},  // HP bonus - phần range xử lý riêng nếu source dùng option 6
                {94, 15}  // Giảm sát thương 15%
            });
        }
    }

    /**
     * Naruto chết trước Akaza thì không được tự respawn.
     * Chỉ khi đủ 5 phút sau Akaza mới cho respawn.
     */
    @Override
    public void rest() {
        if (LanglaCycle.canStartNextWave() && LanglaCycle.markWaveStarted()) {
            this.changeStatus(BossStatus.RESPAWN);
        }
    }
}
