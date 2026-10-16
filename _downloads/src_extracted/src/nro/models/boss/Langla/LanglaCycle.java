package nro.models.boss.Langla;

/**
 * Điều khiển chu kỳ spawn pack Langla.
 *
 * - Đợt đầu được phép xuất hiện ngay khi BossManager load boss.
 * - Sau khi Akaza chết, khóa toàn bộ pack đúng 5 phút.
 * - Naruto chỉ được respawn khi 5 phút đã hết.
 */
public final class LanglaCycle {

    private static final long WAVE_COOLDOWN = 5L * 60L * 1000L;

    private static volatile boolean waveStarted = false;
    private static volatile long nextWaveAt = 0L;

    private LanglaCycle() {
    }

    /**
     * Dùng cho Naruto.rest().
     * Đợt đầu: true.
     * Trong lúc một đợt đang chạy: false.
     * Sau khi Akaza chết: chỉ true khi đủ 5 phút.
     */
    public static synchronized boolean canStartNextWave() {
        if (waveStarted) {
            return false;
        }
        return System.currentTimeMillis() >= nextWaveAt;
    }

    /**
     * Đánh dấu đợt mới đã bắt đầu.
     */
    public static synchronized boolean markWaveStarted() {
        if (waveStarted || System.currentTimeMillis() < nextWaveAt) {
            return false;
        }
        waveStarted = true;
        return true;
    }

    /**
     * Gọi ngay khi Akaza chết.
     */
    public static synchronized void startCooldown() {
        waveStarted = false;
        nextWaveAt = System.currentTimeMillis() + WAVE_COOLDOWN;
    }

    public static long getRemainingMillis() {
        return Math.max(0L, nextWaveAt - System.currentTimeMillis());
    }
}
