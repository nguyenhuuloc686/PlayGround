package nro.models.boss.Langla;

import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import nro.models.player.Player;
import nro.models.utils.Util;

/**
 * Base cho 4 boss Langla.
 *
 * Cả 4 boss được tạo cùng một đợt và đứng trong cùng khu.
 * Chỉ boss tới lượt mới ACTIVE; các boss còn lại AFK + không nhận sát thương.
 */
public abstract class LanglaBoss extends Boss {

    protected boolean langlaActive;

    protected LanglaBoss(int bossId, nro.models.boss.BossData data) throws Exception {
        super(bossId, false, true, data);
        this.langlaActive = false;
    }

    protected final void setLanglaActive(boolean active) {
        this.langlaActive = active;
        if (active) {
            this.changeStatus(BossStatus.ACTIVE);
            if (this.zone != null) {
                this.changeToTypePK();
            }
        } else {
            this.changeStatus(BossStatus.AFK);
            if (this.zone != null) {
                this.changeToTypeNonPK();
            }
        }
    }

    protected final boolean isLanglaActive() {
        return this.langlaActive;
    }

    /**
     * Kích hoạt boss kế tiếp trong cùng pack.
     */
    protected final void activateBoss(int bossId) {
        if (this.parentBoss == null || this.parentBoss.bossAppearTogether == null
                || this.parentBoss.currentLevel >= this.parentBoss.bossAppearTogether.length
                || this.parentBoss.bossAppearTogether[this.parentBoss.currentLevel] == null) {
            return;
        }

        for (Boss boss : this.parentBoss.bossAppearTogether[this.parentBoss.currentLevel]) {
            if (boss == null || boss.id != bossId || !(boss instanceof LanglaBoss)) {
                continue;
            }

            LanglaBoss next = (LanglaBoss) boss;

            // Boss kế tiếp phải đứng cùng khu với boss trước.
            if (this.zone != null && next.zone == null) {
                next.joinMapByZone(this.zone);
            }

            // Nếu instance kế tiếp từng bị chết do lỗi trạng thái, dựng lại HP
            // trước khi mở lượt. Bình thường nhánh này không chạy.
            if (next.isDie()) {
                next.initBase();
            }

            next.setLanglaActive(true);
            return;
        }
    }

    /**
     * Kích hoạt boss kế tiếp NGAY khi boss hiện tại bị hạ.
     * Không phụ thuộc vào CHAT_E của Boss.java.
     */
    protected final void activateNextBossAfterDelay(int nextBossId) {
        // Theo cơ chế Tiểu Đội Sát Thủ: boss kế tiếp chỉ mở sau vài giây,
        // không mở ngay tại tick chết của boss trước.
        int delay = Util.nextInt(3000, 5001);
        Util.setTimeout(() -> activateBoss(nextBossId), delay);
    }

    /**
     * Boss chờ không được nhận sát thương.
     */
    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (!this.langlaActive) {
            return 0;
        }
        return super.injured(plAtt, damage, piercing, isMobAttack);
    }

    /**
     * Boss standby khi vừa xuất hiện.
     * Boss.java sẽ giữ AFK, không tự chuyển sang ACTIVE.
     */
    @Override
    public void doneChatS() {
        if (!this.langlaActive) {
            this.setLanglaActive(false);
        }
    }

    /**
     * Không cho từng boss tự respawn.
     * Chỉ Naruto được phép mở đợt mới sau khi Akaza chết + đủ 5 phút.
     */
    @Override
    public void rest() {
        // Langla được điều khiển bởi chuỗi boss, không dùng REST mặc định.
    }
}
