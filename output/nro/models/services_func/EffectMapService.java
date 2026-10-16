package nro.models.services_func;

import nro.models.map.Zone;
import nro.models.player.Player;
import nro.models.network.Message;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 * 
 */

public class EffectMapService {

    private static EffectMapService i;

    private EffectMapService() {

    }

    public static EffectMapService gI() {
        if (i == null) {
            i = new EffectMapService();
        }
        return i;
    }

    public void sendEffectMapToPlayer(Player player, int id, int layer, int loop, int x, int y, int delay) {
        Message msg = null;
        try {
            msg = new Message(113);
            msg.writer().writeByte(id);
            msg.writer().writeByte(layer);
            msg.writer().writeByte(id);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            msg.writer().writeShort(delay);
            player.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendEffectMapToAllInMap(Zone zone, int id, int layer, int loop, int x, int y, int delay) {
        Message msg = null;
        try {
            msg = new Message(113);
            msg.writer().writeByte(loop);
            msg.writer().writeByte(layer);
            msg.writer().writeByte(id);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            msg.writer().writeShort(delay);
            Service.gI().sendMessAllPlayerInMap(zone, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void sendEffectMapToAllInMap(Player player, int id, int layer, int loop, int x, int y, int delay) {
        Message msg = null;
        try {
            msg = new Message(113);
            msg.writer().writeByte(loop);
            msg.writer().writeByte(layer);
            msg.writer().writeByte(id);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            msg.writer().writeShort(delay);
            Service.gI().sendMessAllPlayerInMap(player, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    // ========== Hiệu ứng hoa dưới chân nhân vật khi di chuyển ==========
    // Bật/tắt toàn bộ hiệu ứng hoa
    public static final boolean FLOWER_EFFECT_ENABLED = true;
    // Khoảng cách tối thiểu (ms) giữa 2 lần tạo hoa cho mỗi nhân vật
    public static final long FLOWER_EFFECT_SPAWN_DELAY = 500L;
    // Layer vẽ trên client (3 = cùng layer với hiệu ứng phục hồi dưới chân)
    public static final int FLOWER_EFFECT_LAYER = 3;
    // Số lần lặp hoạt ảnh khi client nhận lệnh
    public static final int FLOWER_EFFECT_LOOP = 1;
    // Delay hoạt ảnh gửi kèm máy client (-1 = client tự dùng mặc định)
    public static final int FLOWER_EFFECT_DELAY = -1;
    // Hiệu ứng 369 = bông hoa hồng đỏ, 370 = bông hoa xanh (có trong gói tài nguyên)
    public static final int FLOWER_EFFECT_ID_ROSE = 369;
    public static final int FLOWER_EFFECT_ID_BLUE = 370;
    // Tỷ lệ chọn hoa hồng (369) so với hoa xanh (370)
    public static final int FLOWER_EFFECT_ROSE_RATIO = 50;
    // Căn tọa độ: góc trên - trái của ảnh hiệu ứng (41px) so với chân nhân vật
    public static final int FLOWER_EFFECT_X_OFFSET = 20;
    public static final int FLOWER_EFFECT_Y_OFFSET = 34;

    public void sendFlowerEffectOnMove(Player player) {
        if (!FLOWER_EFFECT_ENABLED || player == null || player.zone == null || !player.isPl()) {
            return;
        }
        if (!Util.canDoWithTime(player.location.lastTimeFlowerEffect, FLOWER_EFFECT_SPAWN_DELAY)) {
            return;
        }
        player.location.lastTimeFlowerEffect = System.currentTimeMillis();
        int effectId = Util.isTrue(FLOWER_EFFECT_ROSE_RATIO, 100)
                ? FLOWER_EFFECT_ID_ROSE : FLOWER_EFFECT_ID_BLUE;
        int x = player.location.x - FLOWER_EFFECT_X_OFFSET;
        int y = player.location.y - FLOWER_EFFECT_Y_OFFSET;
        sendFlowerToAllInMap(player.zone, effectId, FLOWER_EFFECT_LAYER, FLOWER_EFFECT_LOOP, x, y, FLOWER_EFFECT_DELAY);
    }

    public void sendFlowerToAllInMap(Zone zone, int id, int layer, int loop, int x, int y, int delay) {
        Message msg = null;
        try {
            msg = new Message(113);
            msg.writer().writeByte(loop);
            msg.writer().writeByte(layer);
            msg.writer().writeShort(id);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            msg.writer().writeShort(delay);
            Service.gI().sendMessAllPlayerInMap(zone, msg);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

}
