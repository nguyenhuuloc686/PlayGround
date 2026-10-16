package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.shop.ShopService;
import nro.models.utils.Util;

public class TrungThu extends Npc {

    private static final int MENU_THO_CUNG = 4101;
    private static final int MENU_CA_ROT = 4102;
    private static final int MENU_DUOI_KHI = 4103;

    // 890 = Bánh trung thu Gà quay
    private static final int BANH_GA_QUAY = 890;

    // 891 = Bánh trung thu Thập cẩm
    private static final int BANH_THAP_CAM = 891;

    // 1313 = Bánh trung thu Hạt sen
    private static final int BANH_HAT_SEN = 1313;

    // 892 = Thỏ xám
    private static final int THO_XAM = 892;

    // 893 = Thỏ trắng
    private static final int THO_TRANG = 893;

    // 1188 = Pet mèo đen đuôi vàng
    private static final int PET_MEO_DEN_DUOI_VANG = 1188;

    // 462 = Củ cà rốt
    private static final int CU_CA_ROT = 462;

    // 1045 = Đuôi khỉ
    private static final int DUOI_KHI = 1045;

    private static final int SO_LUONG_CA_ROT = 99;
    private static final int SO_LUONG_DUOI_KHI = 99;

    // Sức đánh %
    private static final int OPT_SUC_DANH = 50;

    // HP %
    private static final int OPT_HP = 77;

    // KI %
    private static final int OPT_KI = 103;

    // Không thể giao dịch
    private static final int OPT_KHONG_GIAO_DICH = 30;

    // Kháng choáng
    private static final int OPT_KHANG_CHOANG = 116;

    // Sức đánh cho bản thân và người xung quanh
    private static final int OPT_SUC_DANH_XUNG_QUANH = 117;

    // Hạn sử dụng
    private static final int OPT_HAN_SU_DUNG = 93;


    private static final int[] QUA_CA_ROT = {
        463,
        464,
        1039,
        1040,
        1041,
        1042,
        1043,
        1046
    };

    private static final int[] QUA_DUOI_KHI = {
        1039,
        1040,
        1041,
        1042,
        1043,
        1046
    };

    public TrungThu(
            int mapId,
            int status,
            int cx,
            int cy,
            int tempId,
            int avartar) {

        super(
                mapId,
                status,
                cx,
                cy,
                tempId,
                avartar
        );
    }

    @Override
    public void openBaseMenu(Player player) {

        if (!canOpenNpc(player)) {
            return;
        }

        createOtherMenu(
                player,
                ConstNpc.BASE_MENU,

                "Xin chào!\n"
                + "Ta có rất nhiều vật phẩm Trung Thu.\n"
                + "Cậu muốn làm gì?",

                "Cửa hàng",
                "Đổi Thỏ Cưng",
                "Đổi 99 Cà Rốt\nLấy Quà",
                "Đổi 99 Đuôi Khỉ\nLấy Quà",
                "Đóng"
        );
    }

    @Override
    public void confirmMenu(Player player, int select) {

        if (!canOpenNpc(player)) {
            return;
        }
        if (player.idMark.isBaseMenu()) {

            switch (select) {

                // Cửa hàng
                case 0:

                    ShopService.gI().opendShop(
                            player,
                            "Trung_Thu",
                            false
                    );

                    break;

                // Đổi Thỏ Cưng
                case 1:

                    moMenuThoCung(player);

                    break;

                // 99 Cà Rốt
                case 2:

                    moMenuCaRot(player);

                    break;

                // 99 Đuôi Khỉ
                case 3:

                    moMenuDuoiKhi(player);

                    break;

                // Đóng
                case 4:

                    break;
            }

            return;
        }

        if (player.idMark.getIndexMenu() == MENU_THO_CUNG) {

            switch (select) {

                case 0:

                    doiThoXam(player);

                    break;

                case 1:

                    doiThoTrang(player);

                    break;

                case 2:

                    doiMeoDenDuoiVang(player);

                    break;

                case 3:

                    break;
            }

            return;
        }

        if (player.idMark.getIndexMenu() == MENU_CA_ROT) {

            if (select == 0) {

                doi99CaRot(player);

            }

            return;
        }

        if (player.idMark.getIndexMenu() == MENU_DUOI_KHI) {

            if (select == 0) {

                doi99DuoiKhi(player);

            }
        }
    }

    private void moMenuThoCung(Player player) {

        createOtherMenu(
                player,
                MENU_THO_CUNG,

                "Đổi 20 Bánh trung thu Gà quay lấy Thỏ xám\n"
                + "Đổi 10 Bánh trung thu Thập cẩm lấy Thỏ trắng\n"
                + "Đổi 10 Bánh trung thu Hạt sen lấy Mèo đen đuôi vàng\n"
                + "Hạn sử dụng: 3 - 7 - 15 - 30 ngày.",

                "Đổi\nThỏ xám",
                "Đổi\nThỏ trắng",
                "Đổi Mèo đen\nđuôi vàng",
                "Đóng"
        );
        }

    private void moMenuCaRot(Player player) {

        createOtherMenu(
                player,
                MENU_CA_ROT,

                "Dùng 99 Củ Cà Rốt để đổi 1 phần quà.\n"
                + "Quà được chọn ngẫu nhiên.\n"
                + "Cải trang và Pet có chỉ số ngẫu nhiên.",

                "Đổi ngay",
                "Đóng"
        );
    }

    private void moMenuDuoiKhi(Player player) {

        createOtherMenu(
                player,
                MENU_DUOI_KHI,

                "Dùng 99 Đuôi Khỉ để đổi 1 phần quà.\n"
                + "Quà được chọn ngẫu nhiên.",

                "Đổi ngay",
                "Đóng"
        );
    }

    private void doiThoXam(Player player) {

        if (!checkEmptyBag(player)) {
            return;
        }

        Item item = InventoryService.gI().findItemBag(
                player,
                BANH_GA_QUAY
        );

        if (item == null || item.quantity < 20) {

            Service.gI().sendThongBao(
                    player,
                    "Bạn cần 20 Bánh trung thu Gà quay!"
            );

            return;
        }

        InventoryService.gI().subQuantityItemsBag(
                player,
                item,
                20
        );

        Item reward = ItemService.gI().createNewItem(
                (short) THO_XAM
        );

        addRandomHSD(reward);

        InventoryService.gI().addItemBag(
                player,
                reward
        );

        InventoryService.gI().sendItemBags(player);

        Service.gI().sendThongBao(
                player,
                "Đổi thành công!\n"
                + "Bạn nhận được Thỏ xám."
        );
    }

    private void doiThoTrang(Player player) {

        if (!checkEmptyBag(player)) {
            return;
        }

        Item item = InventoryService.gI().findItemBag(
                player,
                BANH_THAP_CAM
        );

        if (item == null || item.quantity < 10) {

            Service.gI().sendThongBao(
                    player,
                    "Bạn cần 10 Bánh trung thu Thập cẩm!"
            );

            return;
        }

        InventoryService.gI().subQuantityItemsBag(
                player,
                item,
                10
        );

        Item reward = ItemService.gI().createNewItem(
                (short) THO_TRANG
        );

        addRandomHSD(reward);

        InventoryService.gI().addItemBag(
                player,
                reward
        );

        InventoryService.gI().sendItemBags(player);

        Service.gI().sendThongBao(
                player,
                "Đổi thành công!\n"
                + "Bạn nhận được Thỏ trắng."
        );
    }

    private void doiMeoDenDuoiVang(Player player) {

        if (!checkEmptyBag(player)) {
            return;
        }

        Item item = InventoryService.gI().findItemBag(
                player,
                BANH_HAT_SEN
        );

        if (item == null || item.quantity < 10) {

            Service.gI().sendThongBao(
                    player,
                    "Bạn cần 10 Bánh trung thu Hạt sen!"
            );

            return;
        }

        InventoryService.gI().subQuantityItemsBag(
                player,
                item,
                10
        );

        Item reward = ItemService.gI().createNewItem(
                (short) PET_MEO_DEN_DUOI_VANG
        );

        addRandomHSD(reward);

        InventoryService.gI().addItemBag(
                player,
                reward
        );

        InventoryService.gI().sendItemBags(player);

        Service.gI().sendThongBao(
                player,
                "Đổi thành công!\n"
                + "Bạn nhận được Pet mèo đen đuôi vàng."
        );
    }

    private void doi99CaRot(Player player) {

        if (!checkEmptyBag(player)) {
            return;
        }

        Item carrot = InventoryService.gI().findItemBag(
                player,
                CU_CA_ROT
        );

        if (carrot == null || carrot.quantity < SO_LUONG_CA_ROT) {

            Service.gI().sendThongBao(
                    player,
                    "Bạn cần đủ 99 Củ Cà Rốt!"
            );

            return;
        }

        // Random phần thưởng
        int rewardId = QUA_CA_ROT[
                Util.nextInt(
                        0,
                        QUA_CA_ROT.length - 1
                )
        ];

        // Trừ nguyên liệu
        InventoryService.gI().subQuantityItemsBag(
                player,
                carrot,
                SO_LUONG_CA_ROT
        );

        // Tạo item
        Item reward = ItemService.gI().createNewItem(
                (short) rewardId
        );

        // Thêm chỉ số riêng cho từng loại
        addOptionForCarrotReward(reward);

        // HSD
        addRandomHSD(reward);

        // Thêm túi
        InventoryService.gI().addItemBag(
                player,
                reward
        );

        InventoryService.gI().sendItemBags(player);

        Service.gI().sendThongBao(
                player,
                "Đổi 99 Củ Cà Rốt thành công!\n"
                + "Bạn nhận được: "
                + reward.template.name
        );
    }

    private void doi99DuoiKhi(Player player) {

        if (!checkEmptyBag(player)) {
            return;
        }

        Item duoiKhi = InventoryService.gI().findItemBag(
                player,
                DUOI_KHI
        );

        if (duoiKhi == null || duoiKhi.quantity < SO_LUONG_DUOI_KHI) {

            Service.gI().sendThongBao(
                    player,
                    "Bạn cần đủ 99 Đuôi Khỉ!"
            );

            return;
        }

        int rewardId = QUA_DUOI_KHI[
                Util.nextInt(
                        0,
                        QUA_DUOI_KHI.length - 1
                )
        ];

        // Trừ 99 Đuôi Khỉ
        InventoryService.gI().subQuantityItemsBag(
                player,
                duoiKhi,
                SO_LUONG_DUOI_KHI
        );

        // Tạo quà
        Item reward = ItemService.gI().createNewItem(
                (short) rewardId
        );

        // Chỉ số Pet
        addOptionForPet(reward);

        // HSD
        addRandomHSD(reward);

        // Thêm túi
        InventoryService.gI().addItemBag(
                player,
                reward
        );

        InventoryService.gI().sendItemBags(player);

        Service.gI().sendThongBao(
                player,
                "Đổi 99 Đuôi Khỉ thành công!\n"
                + "Bạn nhận được: "
                + reward.template.name
        );
    }

    private void addOptionForCarrotReward(Item item) {

        if (item == null || item.template == null) {
            return;
        }

        int id = item.template.id;

        if (id == 463) {

            int dame = Util.nextInt(10, 15);
            int hp = Util.nextInt(10, 15);
            int ki = Util.nextInt(10, 15);

            item.itemOptions.add(
                    new ItemOption(
                            OPT_SUC_DANH,
                            dame
                    )
            );

            item.itemOptions.add(
                    new ItemOption(
                            OPT_HP,
                            hp
                    )
            );

            item.itemOptions.add(
                    new ItemOption(
                            OPT_KI,
                            ki
                    )
            );

            // Kháng choáng
            item.itemOptions.add(
                    new ItemOption(
                            OPT_KHANG_CHOANG,
                            0
                    )
            );

            // Không thể giao dịch
            item.itemOptions.add(
                    new ItemOption(
                            OPT_KHONG_GIAO_DICH,
                            0
                    )
            );

            return;
        }

        if (id == 464) {

            int dame = Util.nextInt(15, 25);
            int hp = Util.nextInt(15, 25);
            int ki = Util.nextInt(15, 25);

            item.itemOptions.add(
                    new ItemOption(
                            OPT_SUC_DANH,
                            dame
                    )
            );

            item.itemOptions.add(
                    new ItemOption(
                            OPT_HP,
                            hp
                    )
            );

            item.itemOptions.add(
                    new ItemOption(
                            OPT_KI,
                            ki
                    )
            );

            // Không thể giao dịch
            item.itemOptions.add(
                    new ItemOption(
                            OPT_KHONG_GIAO_DICH,
                            0
                    )
            );

            // Sức đánh cho mình + người xung quanh
            int buffAround = Util.nextInt(10, 15);

            item.itemOptions.add(
                    new ItemOption(
                            OPT_SUC_DANH_XUNG_QUANH,
                            buffAround
                    )
            );

            return;
        }

        addOptionForPet(item);
    }

    private void addOptionForPet(Item item) {

        if (item == null || item.template == null) {
            return;
        }

        if (!isPetTrungThu(item.template.id)) {
            return;
        }

        int dame = Util.nextInt(10, 15);
        int hp = Util.nextInt(10, 15);
        int ki = Util.nextInt(10, 15);

        // Sức đánh
        item.itemOptions.add(
                new ItemOption(
                        OPT_SUC_DANH,
                        dame
                )
        );

        // HP
        item.itemOptions.add(
                new ItemOption(
                        OPT_HP,
                        hp
                )
        );

        // KI
        item.itemOptions.add(
                new ItemOption(
                        OPT_KI,
                        ki
                )
        );

        // Không giao dịch
        item.itemOptions.add(
                new ItemOption(
                        OPT_KHONG_GIAO_DICH,
                        0
                )
        );
    }

    private boolean isPetTrungThu(int id) {

        switch (id) {

            case 1039:
            case 1040:
            case 1041:
            case 1042:
            case 1043:
            case 1046:
            case 1188:

                return true;

            default:

                return false;
        }
    }

    private void addRandomHSD(Item item) {

        if (item == null) {
            return;
        }

        int random = Util.nextInt(1, 100);

        int days;

        if (random <= 55) {

            days = 3;

        } else if (random <= 85) {

            days = 7;

        } else if (random <= 90) {

            days = 15;

        } else {

            days = 30;
        }

        item.itemOptions.add(
                new ItemOption(
                        OPT_HAN_SU_DUNG,
                        days
                )
        );
    }

    private boolean checkEmptyBag(Player player) {

        if (InventoryService.gI().getCountEmptyBag(player) <= 0) {

            Service.gI().sendThongBao(
                    player,
                    "Hành trang không còn chỗ trống!"
            );

            return false;
        }

        return true;
    }
}