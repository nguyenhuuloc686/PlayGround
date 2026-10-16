package nro.models.npc_list;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import nro.models.item.Item;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.npc.Npc;
import nro.models.npc.NonInteractiveNPC;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 * Duong Tang - nhiem vu ho tong.
 *
 * Item IDs theo item_template:
 * 537 Chữ giải, 538 Chữ khai, 539 Chữ phong, 540 Chữ ấn
 * 543 Vòng Kim Cô
 * 544/545/546 CT Tôn Ngộ Không cho Trái Đất/Namec/Xayda
 * 547/548/1302 CT đệ tử Tôn Ngộ Không/Bát Giới/Sa Tăng
 */
public class DuongTang extends Npc {

    private static final int MAP_LANG_ARU = 0;
    private static final int MAP_NGU_HANH_SON = 123;
    private static final int MAP_PHONG_AN = 122;

    private static final int X_START = 140;
    private static final int Y_START = 382;
    private static final int X_RETURN = 723;
    private static final int Y_RETURN = 432;

    private static final int ITEM_CHU_GIAI = 537;
    private static final int ITEM_CHU_KHAI = 538;
    private static final int ITEM_CHU_PHONG = 539;
    private static final int ITEM_CHU_AN = 540;
    private static final int ITEM_VONG_KIM_CO = 543;

    private static final int CT_TNK_TD = 544;
    private static final int CT_TNK_NAMEC = 545;
    private static final int CT_TNK_XAYDA = 546;

    private static final int CT_TNK_DE_TU = 547;
    private static final int CT_BAT_GIOI_DE_TU = 548;
    private static final int CT_SA_TANG_DE_TU = 1302;

    // Dùng data_event[6] để lưu công đức, tránh đụng point_sukien của event khác.
    private static final int MERIT_SLOT = 6;
    private static final int MERIT_PER_ESCORT = 100;

    // Mỗi player đi tuần tự 63 -> 83, không random lặp map.
    private static final Map<Long, Integer> NEXT_TARGET = new ConcurrentHashMap<>();
    private static final Map<Long, EscortRunner> ACTIVE = new ConcurrentHashMap<>();

    private static final long FOLLOW_DELAY = 400L;
    private static final long FOLLOW_MIN_DIST = 25L;
    private static final long COMPLETE_DISTANCE = 120L;

    public DuongTang(int mapId, int status, int cx, int cy, int tempId, int avatar) {
        super(mapId, status, cx, cy, tempId, avatar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!canOpenNpc(player)) {
            return;
        }

        switch (this.mapId) {
            case MAP_LANG_ARU:
                createOtherMenu(player, 31072002,
                        "A mi phò phò, thí chủ muốn làm gì?\n" +
                        "Cải trang Ngộ Không mới có thể hộ tống ta.",
                        "Đồng ý",
                        "Nhiệm vụ\nhộ tống",
                        "Từ chối",
                        "Nhận thưởng");
                break;

            case MAP_NGU_HANH_SON:
                createOtherMenu(player, 31072002,
                        "A mi phò phò, ta đang trên đường thỉnh kinh.",
                        "Về Làng Aru");
                break;

            case MAP_PHONG_AN:
                createOtherMenu(player, 31072002,
                        "A mi phò phò, thí chủ hãy giúp ta giải cứu đồ đệ.",
                        "Giải Phong Ấn",
                        "Về Làng Aru");
                break;

            default:
                createOtherMenu(player, 31072002,
                        "A mi phò phò, thí chủ muốn làm gì?",
                        "Về Làng Aru");
                break;
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }

        int menu = player.idMark.getIndexMenu();

        // Menu gốc 4 nút ở Làng Aru.
        if (this.mapId == MAP_LANG_ARU && menu == 31072002) {
            switch (select) {
                case 0:
                    goToStartMap(player);
                    break;
                case 1:
                    openEscortConfirm(player);
                    break;
                case 2:
                    break;
                case 3:
                    openRewardMenu(player);
                    break;
                default:
                    break;
            }
            return;
        }

        // 3 nút sau khi chọn "Nhiệm vụ hộ tống".
        if (menu == MENU_ESCORT_CONFIRM) {
            switch (select) {
                case 0:
                    acceptEscort(player);
                    break;
                case 1:
                    break;
                case 2:
                    openRewardMenu(player);
                    break;
                default:
                    break;
            }
            return;
        }

        // Menu map 123.
        if (this.mapId == MAP_NGU_HANH_SON && menu == 31072002) {
            if (select == 0) {
                stopEscort(player, false);
                backToLangAru(player);
            }
            return;
        }

        // Menu map 122.
        if (this.mapId == MAP_PHONG_AN && menu == 31072002) {
            if (select == 0) {
                releaseSeal(player);
            } else if (select == 1) {
                backToLangAru(player);
            }
            return;
        }

        // Menu nhận thưởng.
        if (menu == MENU_REWARD) {
            handleReward(player, select);
        }
    }

    private static final int MENU_ESCORT_CONFIRM = 31072003;
    private static final int MENU_REWARD = 31072004;

    private void openEscortConfirm(Player player) {
        int ct = getCurrentNgoKhong(player);
        if (!isValidNgoKhong(ct)) {
            Service.gI().sendThongBao(player,
                    "Thí chủ phải mặc Cải trang Tôn Ngộ Không phù hợp với hành tinh.");
            return;
        }

        if (getQuantity(player, ITEM_VONG_KIM_CO) < 5) {
            Service.gI().sendThongBao(player, "Cần đủ 5 Vòng Kim Cô mới có thể nhận nhiệm vụ hộ tống.");
            return;
        }

        if (ACTIVE.containsKey(player.id)) {
            Service.gI().sendThongBao(player, "Thí chủ đang thực hiện nhiệm vụ hộ tống.");
            return;
        }

        createOtherMenu(player, MENU_ESCORT_CONFIRM,
                "A mi phò phò, Ngộ Không mau hộ tống sư phụ đi thỉnh chân kinh nào",
                "Đồng ý",
                "Từ chối",
                "Nhận thưởng");
    }

    private void acceptEscort(Player player) {
        if (!isValidNgoKhong(getCurrentNgoKhong(player))) {
            Service.gI().sendThongBao(player, "Hãy mặc Cải trang Tôn Ngộ Không trước khi hộ tống.");
            return;
        }

        // Bắt buộc phải có đủ 5 Vòng Kim Cô trong hành trang.
        // Không trừ ở bước này; 5 vòng chỉ bị trừ khi đổi phần thưởng.
        if (getQuantity(player, ITEM_VONG_KIM_CO) < 5) {
            Service.gI().sendThongBao(player, "Cần đủ 5 Vòng Kim Cô mới được hộ tống Đường Tăng.");
            return;
        }

        if (ACTIVE.containsKey(player.id)) {
            Service.gI().sendThongBao(player, "Thí chủ đang thực hiện nhiệm vụ hộ tống.");
            return;
        }

        // Vào đúng map 123, đúng tọa độ yêu cầu.
        Zone zone = MapService.gI().getMapCanJoin(player, MAP_NGU_HANH_SON, -1);
        if (zone == null) {
            Service.gI().sendThongBao(player, "Không tìm thấy khu vực Ngũ Hành Sơn.");
            return;
        }

        ChangeMapService.gI().changeMap(player, zone, X_START, Y_START);

        int targetMap = getNextTarget(player);
        EscortRunner runner = new EscortRunner(player, targetMap);
        ACTIVE.put(player.id, runner);
        runner.start();

        Service.gI().sendThongBao(player,
                "Đã tới điểm xuất phát. Đường Tăng sẽ dẫn thí chủ tới bản đồ " + targetMap + ".");
    }

    private int getNextTarget(Player player) {
        int target = NEXT_TARGET.getOrDefault(player.id, 63);
        int next = target + 1;
        if (next > 83) {
            next = 63;
        }
        NEXT_TARGET.put(player.id, next);
        return target;
    }

    private void goToStartMap(Player player) {
        Zone zone = MapService.gI().getMapCanJoin(player, MAP_NGU_HANH_SON, -1);
        if (zone == null) {
            Service.gI().sendThongBao(player, "Không tìm thấy khu vực Ngũ Hành Sơn.");
            return;
        }
        ChangeMapService.gI().changeMap(player, zone, X_START, Y_START);
    }

    private void backToLangAru(Player player) {
        stopEscort(player, false);
        Zone zone = MapService.gI().getMapCanJoin(player, MAP_LANG_ARU, -1);
        if (zone == null) {
            Service.gI().sendThongBao(player, "Không tìm thấy Làng Aru.");
            return;
        }
        ChangeMapService.gI().changeMap(player, zone, X_RETURN, Y_RETURN);
    }

    private void releaseSeal(Player player) {
        if (!has10EachSeal(player)) {
            Service.gI().sendThongBao(player,
                    "Cần đủ x10 Chữ giải, x10 Chữ khai, x10 Chữ phong và x10 Chữ ấn.");
            return;
        }

        removeOneSealSet(player);
        Service.gI().sendThongBao(player, "Đã giải phong ấn. Vui lòng chờ 3-4 giây...");

        new Thread(() -> {
            try {
                Thread.sleep(3500L);
                if (player == null || player.isDie()) {
                    return;
                }
                Zone zone = MapService.gI().getMapCanJoin(player, MAP_LANG_ARU, -1);
                if (zone != null) {
                    ChangeMapService.gI().changeMap(player, zone, X_RETURN, Y_RETURN);
                    giveRandomNgoKhongCostume(player);
                    Service.gI().sendThongBaoOK(player,
                            "Giải phong ấn thành công! Bạn nhận được Cải trang Tôn Ngộ Không.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "DuongTang-ReleaseSeal-" + player.id).start();
    }

    private boolean has10EachSeal(Player player) {
        return hasItem(player, ITEM_CHU_GIAI, 10)
                && hasItem(player, ITEM_CHU_KHAI, 10)
                && hasItem(player, ITEM_CHU_PHONG, 10)
                && hasItem(player, ITEM_CHU_AN, 10);
    }

    private void removeOneSealSet(Player player) {
        removeQuantity(player, ITEM_CHU_GIAI, 10);
        removeQuantity(player, ITEM_CHU_KHAI, 10);
        removeQuantity(player, ITEM_CHU_PHONG, 10);
        removeQuantity(player, ITEM_CHU_AN, 10);
        InventoryService.gI().sendItemBags(player);
    }

    private void giveRandomNgoKhongCostume(Player player) {
        int itemId;
        switch (player.gender) {
            case 0:
                itemId = CT_TNK_TD;
                break;
            case 1:
                itemId = CT_TNK_NAMEC;
                break;
            default:
                itemId = CT_TNK_XAYDA;
                break;
        }

        Item item = createCostume(itemId, true);
        if (!InventoryService.gI().addItemBag(player, item)) {
            Service.gI().sendThongBao(player, "Túi đồ đầy, không thể nhận Cải trang.");
            return;
        }
        InventoryService.gI().sendItemBags(player);
    }

    private void openRewardMenu(Player player) {
        int merit = getMerit(player);
        int rings = getQuantity(player, ITEM_VONG_KIM_CO);

        createOtherMenu(player, MENU_REWARD,
                "A mi phò phò, thí chủ đang có " + merit + " điểm công đức.\n" +
                "Cần 5 Vòng Kim Cô để đổi thưởng. Hiện có: " + rings + " cái.",
                "500\ncộng thêm 7 ngày sử dụng Cải Trang đang dùng",
                "600\ncộng thêm 9 ngày sử dụng Cải Trang đệ tử đang dùng",
                "700\ncải trang thành Bát Giới (dành riêng cho đệ tử)",
                "1.000\ncải trang thành Tôn Ngộ Không (dành riêng cho đệ tử)",
                "1.300\ncải trang thành Sa Tăng (dành riêng cho đệ tử)",
                "800\nQuả núi Làng Lăng (có đeo lưng)",
                "Từ chối");
    }

    private void handleReward(Player player, int select) {
        if (select < 0 || select > 5) {
            return;
        }

        final int[] costs = {500, 600, 700, 1000, 1300, 800};
        int cost = costs[select];
        int merit = getMerit(player);

        if (merit < cost) {
            Service.gI().sendThongBao(player,
                    "Chưa đủ điểm công đức. Hiện có " + merit + "/" + cost + ".");
            return;
        }

        if (getQuantity(player, ITEM_VONG_KIM_CO) < 5) {
            Service.gI().sendThongBao(player, "Cần đủ 5 Vòng Kim Cô mới được đổi thưởng.");
            return;
        }

        switch (select) {
            case 0:
                if (!extendCurrentCostume(player, false, 7)) {
                    return;
                }
                break;
            case 1:
                if (!extendCurrentCostume(player, true, 9)) {
                    return;
                }
                break;
            case 2:
                if (!givePetCostume(player, CT_BAT_GIOI_DE_TU)) {
                    return;
                }
                break;
            case 3:
                if (!givePetCostume(player, CT_TNK_DE_TU)) {
                    return;
                }
                break;
            case 4:
                if (!givePetCostume(player, CT_SA_TANG_DE_TU)) {
                    return;
                }
                break;
            case 5:
                // ID của Quả núi Làng Lăng không có trong classes.zip,
                // để -1 tránh phát item sai. Có thể thay bằng ID thật sau khi đối chiếu SQL.
                Service.gI().sendThongBao(player, "Phần thưởng Quả núi Làng Lăng chưa được cấu hình ID.");
                return;
            default:
                return;
        }

        setMerit(player, merit - cost);
        removeQuantity(player, ITEM_VONG_KIM_CO, 5);
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendThongBaoOK(player,
                "Đổi thưởng thành công, đã trừ " + cost + " điểm công đức và 5 Vòng Kim Cô.");
    }

    private boolean extendCurrentCostume(Player player, boolean disciple, int days) {
        Player owner = disciple ? player.pet : player;
        if (owner == null || owner.inventory == null || owner.inventory.itemsBody == null
                || owner.inventory.itemsBody.size() <= 5) {
            Service.gI().sendThongBao(player, "Không tìm thấy Cải trang đang dùng.");
            return false;
        }

        Item item = owner.inventory.itemsBody.get(5);
        if (item == null || !item.isNotNullItem() || item.getType() != 5) {
            Service.gI().sendThongBao(player, "Chưa mặc Cải trang cần gia hạn.");
            return false;
        }

        long add = days * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();
        item.expire = item.expire > now ? item.expire + add : now + add;
        Service.gI().Send_Caitrang(owner);
        return true;
    }

    private boolean givePetCostume(Player player, int itemId) {
        if (player.pet == null) {
            Service.gI().sendThongBao(player, "Bạn chưa có đệ tử.");
            return false;
        }
        if (player.pet.inventory == null) {
            Service.gI().sendThongBao(player, "Không tìm thấy túi đồ đệ tử.");
            return false;
        }

        Item item = createCostume(itemId, true);
        if (!InventoryService.gI().addItemBag(player.pet, item)) {
            Service.gI().sendThongBao(player, "Túi đồ đệ tử đầy.");
            return false;
        }
        InventoryService.gI().sendItemBags(player.pet);
        return true;
    }

    /**
     * Stats theo yêu cầu ảnh demo:
     * - Sức đánh: 20-100%
     * - KI: 20-100%
     * - HP: 20-100%
     * - Giảm sát thương: 10-20%
     *
     * Option 0/1/2 là bộ option HP/KI/Sức đánh chuẩn của source NRO này.
     * OPTION_GIAM_ST cần khớp item_option_template của SQL; đặt riêng để dễ sửa.
     */
    private Item createCostume(int itemId, boolean randomStats) {
        Item item = ItemService.gI().createNewItem((short) itemId);
        if (randomStats) {
            // Cải trang nhận từ nhiệm vụ có hạn dùng 3 ngày như ảnh demo.
            item.expire = System.currentTimeMillis() + 3L * 24L * 60L * 60L * 1000L;
            // Bộ chỉ số theo ảnh demo: các % chính 20-100, riêng giảm sát thương 10-20.
            item.itemOptions.add(new Item.ItemOption(50, Util.nextInt(20, 100)));  // Sức đánh +%
            item.itemOptions.add(new Item.ItemOption(94, Util.nextInt(10, 20)));   // Giảm sát thương +%
            item.itemOptions.add(new Item.ItemOption(103, Util.nextInt(20, 100))); // KI +%
            item.itemOptions.add(new Item.ItemOption(77, Util.nextInt(20, 100)));  // HP +%
            item.itemOptions.add(new Item.ItemOption(101, Util.nextInt(20, 100)));  // Tiềm năng +%
            item.itemOptions.add(new Item.ItemOption(100, Util.nextInt(20, 100)));  // Vàng rơi +%
            item.itemOptions.add(new Item.ItemOption(114, Util.nextInt(20, 100)));  // Tốc độ chạy +%
            item.itemOptions.add(new Item.ItemOption(106, 0));                       // Chống lạnh
            item.itemOptions.add(new Item.ItemOption(30, 0));                        // Không giao dịch
        }
        return item;
    }

    private int getCurrentNgoKhong(Player player) {
        if (player.inventory == null || player.inventory.itemsBody == null
                || player.inventory.itemsBody.size() <= 5) {
            return -1;
        }
        Item item = player.inventory.itemsBody.get(5);
        if (item == null || !item.isNotNullItem()) {
            return -1;
        }
        return item.getId();
    }

    private boolean isValidNgoKhong(int id) {
        return id == CT_TNK_TD || id == CT_TNK_NAMEC || id == CT_TNK_XAYDA;
    }

    private boolean hasItem(Player player, int itemId, int quantity) {
        return getQuantity(player, itemId) >= quantity;
    }

    private int getQuantity(Player player, int itemId) {
        Item item = InventoryService.gI().findItemBag(player, itemId);
        return item == null ? 0 : item.quantity;
    }

    private void removeQuantity(Player player, int itemId, int quantity) {
        Item item = InventoryService.gI().findItemBag(player, itemId);
        if (item != null) {
            InventoryService.gI().subQuantityItemsBag(player, item, quantity);
        }
    }

    private int getMerit(Player player) {
        return player.eventPointType6;
    }

    private void setMerit(Player player, int value) {
        player.eventPointType6 = Math.max(0, value);
    }

    private static void addMerit(Player player, int value) {
        player.eventPointType6 = Math.max(0, player.eventPointType6 + value);
    }

    private void stopEscort(Player player, boolean completed) {
        EscortRunner runner = ACTIVE.remove(player.id);
        if (runner != null) {
            runner.halt();
            if (completed) {
                addMerit(player, MERIT_PER_ESCORT);
                Service.gI().sendThongBaoOK(player,
                        "Hộ tống thành công! +" + MERIT_PER_ESCORT + " điểm công đức.");
            }
        }
    }

    /** NPC động dùng sprite Đường Tăng (template 49: head 467/body 468/leg 469). */
    private static final class Follower extends NonInteractiveNPC {
        @Override
        public short getHead() {
            return 467;
        }

        @Override
        public short getBody() {
            return 468;
        }

        @Override
        public short getLeg() {
            return 469;
        }
    }

    /** Theo dõi player + cho Đường Tăng đi theo phía sau. */
    private static final class EscortRunner extends Thread {
        private final Player player;
        private final int targetMap;
        private volatile boolean running = true;
        private NonInteractiveNPC follower;
        private Zone lastZone;

        EscortRunner(Player player, int targetMap) {
            super("DuongTang-Escort-" + player.id);
            this.player = player;
            this.targetMap = targetMap;
            setDaemon(true);
        }

        void stopRunner() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            try {
                Thread.sleep(700L);
                createFollower();
                Service.gI().sendThongBao(player,
                        "Đường Tăng: Nam mô! Ta sẽ tới bản đồ " + targetMap + ".");
                // Không tự động đưa player sang map đích. Player phải tự dẫn Đường Tăng
                // qua đường/điểm chuyển map, Đường Tăng sẽ bám theo phía sau.
                long start = System.currentTimeMillis();
                boolean announced = false;

                while (running && player != null && !player.isDie()) {
                    if (player.zone == null) {
                        break;
                    }

                    int mapId = player.zone.map.mapId;
                    if (mapId == targetMap) {
                        if (!announced) {
                            announced = true;
                            Service.gI().sendThongBao(player,
                                    "Đã tới nơi! Đường Tăng đã đến bản đồ " + targetMap + ".");
                        }
                        if (player.location != null && follower != null
                                && follower.location != null
                                && Util.getDistance(player, follower) <= COMPLETE_DISTANCE) {
                            complete();
                            return;
                        }
                    }

                    // Player được tự do đi qua các map trung gian. Đường Tăng sẽ đổi khu
                    // theo player và tiếp tục bám theo cho tới khi player tới targetMap.
                    syncFollowerZone();
                    followBehind();

                    // Không treo nhiệm vụ vô hạn nếu player đứng yên quá lâu.
                    if (System.currentTimeMillis() - start > 30L * 60L * 1000L) {
                        cancel();
                        Service.gI().sendThongBao(player, "Nhiệm vụ hộ tống đã hết thời gian.");
                        return;
                    }

                    Thread.sleep(FOLLOW_DELAY);
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                removeFollower();
                ACTIVE.remove(player.id, this);
            }
        }

        private void createFollower() {
            if (player.zone == null || player.location == null) {
                return;
            }
            try {
                follower = new Follower();
                // KHONG goi initNonInteractiveNPC(): method do quet toan bo Manager.MAPS
                // va spawn hang loat NPC tinh (Mr.PoPo, Yajiro, ...) - chi dung 1 lan
                // khi server khoi dong. new Follower() da co location/nPoint/effectSkill/idMark.
                follower.id = -1000000L - Math.abs(player.id % 900000L);
                follower.name = "Đường Tăng";
                follower.gender = 0;
                follower.nPoint.hpMax = 30000;
                follower.nPoint.hpg = 30000;
                follower.nPoint.hp = 30000;
                follower.nPoint.setFullHpMp();
                follower.location.x = Math.max(20, player.location.x - 60);
                follower.location.y = player.location.y;
                // Dua follower vao dung zone cua player: goToMap set follower.zone
                // + Zone.addPlayer (humanoids/nonInteractiveNPCs), roi broadcast -5
                // cho toan bo player trong zone de client spawn thay Duong Tang.
                // (Truoc day goi follower.joinMap(player.zone, player) la sai: tham so
                // thu 2 phai la follower, truyen player khien goToMap re-add owner,
                // follower.zone van null nen move() khong broadcast duoc -7.)
                MapService.gI().goToMap(follower, player.zone);
                lastZone = player.zone;
                player.zone.load_Me_To_Another(follower);
                // Snap 1 nhip -7 ngay de client dat vi tri phia sau player.
                follower.move(Math.max(20, player.location.x - 60), player.location.y);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void syncFollowerZone() {
            if (follower == null || player.zone == null || follower.location == null) {
                return;
            }
            if (follower.zone != player.zone) {
                try {
                    // goToMap tu exitMap khoi zone cu (gui -6 disappear + removePlayer
                    // khoi humanoids/nonInteractiveNPCs) roi add vao zone moi.
                    // Khong them follower vao Zone.players: list do chi danh cho
                    // account that (isPl), them vao se sai count/isFullPlayer.
                    MapService.gI().goToMap(follower, player.zone);
                    lastZone = player.zone;
                    // Spawn lai (-5) o zone moi de client thay, tranh nhan ban o map cu.
                    player.zone.load_Me_To_Another(follower);
                    follower.move(Math.max(20, player.location.x - 60), player.location.y);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                lastZone = player.zone;
            }
        }

        private void followBehind() {
            if (follower == null || follower.zone == null) {
                return;
            }
            if (player.location == null || follower.location == null) {
                return;
            }
            if (follower.zone != player.zone) {
                return;
            }
            int direction = player.location.x >= follower.location.x ? 1 : -1;
            int wantedX = Math.max(20, player.location.x - direction * 55);
            int wantedY = player.location.y;
            // Player.move() set location + MapService.sendPlayerMove (-7) toi cac
            // player that trong zone. Khong dung PlayerService.playerMove vi method
            // do chi doi location noi bo, khong broadcast -7 (client thay dung yen).
            try {
                if (Util.getDistance(player, follower) > FOLLOW_MIN_DIST) {
                    follower.move(wantedX, wantedY);
                } else if (follower.location.y != wantedY) {
                    follower.move(follower.location.x, wantedY);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void complete() {
            running = false;
            addMerit(player, MERIT_PER_ESCORT);
            Service.gI().sendThongBaoOK(player,
                    "Hộ tống thành công! +" + MERIT_PER_ESCORT + " điểm công đức.");
        }

        private void cancel() {
            running = false;
            Service.gI().sendThongBao(player, "Nhiệm vụ hộ tống đã bị hủy.");
        }

        void halt() {
            stopRunner();
            removeFollower();
        }

        private void removeFollower() {
            if (follower != null) {
                try {
                    // exitMap gui -6 disappear toi zone cu + removePlayer khoi moi list,
                    // tranh ghost/nhan ban sau nhieu lan doi map.
                    if (follower.zone != null) {
                        ChangeMapService.gI().exitMap(follower);
                    }
                } catch (Exception ignored) {
                }
                try {
                    follower.dispose();
                } catch (Exception ignored) {
                }
                follower = null;
            }
            lastZone = null;
        }
    }
}
