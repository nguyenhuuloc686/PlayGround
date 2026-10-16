package nro.models.boss.Langla;

import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.utils.Util;

/**
 * Drop cải trang Langla.
 *
 * HSD: 3 / 7 / 15 / 30 ngày.
 * Tỷ lệ giảm dần khi HSD tăng.
 */
public final class LanglaReward {

    private LanglaReward() {
    }

    public static void dropCostume(Player player, int itemId, int[][] options) {
        if (player == null) {
            return;
        }

        Item item = ItemService.gI().createNewItem((short) itemId);

        if (options != null) {
            for (int[] option : options) {
                if (option != null && option.length >= 2) {
                    item.itemOptions.add(new Item.ItemOption(option[0], option[1]));
                }
            }
        }

        item.itemOptions.add(new Item.ItemOption(93, randomHsd()));
        item.itemOptions.add(new Item.ItemOption(30, 0)); // Không giao dịch

        if (InventoryService.gI().addItemBag(player, item)) {
            InventoryService.gI().sendItemBags(player);
            nro.models.services.Service.gI().sendThongBao(
                    player, "Bạn nhận được cải trang " + item.template.name);
        }
    }

    private static int randomHsd() {
        int r = Util.nextInt(1, 100);
        if (r <= 60) {
            return 3;
        }
        if (r <= 85) {
            return 7;
        }
        if (r <= 95) {
            return 15;
        }
        return 30;
    }
}
