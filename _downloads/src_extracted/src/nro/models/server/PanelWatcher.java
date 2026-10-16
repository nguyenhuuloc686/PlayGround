// FIX-2026-10-01: PanelWatcher - game doc lenh tu web panel (panel_cmd.txt) va THI HANH THAT.
// Copy file nay vao: src/nro/models/server/PanelWatcher.java roi build lai server.
// Hook: them 1 dong vao ServerManager.run() (xem ServerManager_HOOK.txt).
// Doc tu CA 2 noi: ./panel_cmd.txt (game_root) + duong truyen -Dpanel.webqueue=<...>/sv1/panel_queue/panel_cmd.txt
package nro.models.server;

import java.io.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.*;

import nro.models.boss.Boss;
import nro.models.boss.Boss_Manager.BossManager;
import nro.models.data.LocalManager;
import nro.models.event.EventManager;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Logger;

public class PanelWatcher {

    private static ScheduledExecutorService svc;
    private static final Set<String> SEEN = Collections.synchronizedSet(new HashSet<>());

    public static synchronized void start() {
        if (svc != null && !svc.isShutdown()) return;
        svc = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PanelWatcher");
            t.setDaemon(true);
            return t;
        });
        try {
            // FIX-2026-10-02: khoi phuc event da chon sau restart (truoc day restart la mat).
            int eid = -1;
            try {
                String ej = new String(Files.readAllBytes(Paths.get("panel_event_status.json")), "UTF-8");
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(ej);
                if (m.find()) eid = Integer.parseInt(m.group());
            } catch (Exception ignored) {}
            if (eid < 0) {
                for (String cf : new String[]{"Config.properties", "data/config/config.properties"}) {
                    try {
                        for (String ln : Files.readAllLines(Paths.get(cf))) {
                            String s = ln.trim();
                            if (s.startsWith("server.event")) {
                                eid = Integer.parseInt(s.substring(s.indexOf('=') + 1).trim());
                                break;
                            }
                        }
                        if (eid >= 0) break;
                    } catch (Exception ignored) {}
                }
            }
            if (eid >= 0) {
                try { ServerManager.EVENT_SEVER = eid; } catch (Throwable ignored) {}
                applyEventFlags(eid);
                log("khoi phuc event id=" + eid);
            }
        } catch (Throwable ignored) {}
        svc.scheduleAtFixedRate(() -> {
            try { poll(); } catch (Exception e) { log("poll loi: " + e.getMessage()); }
        }, 5, 5, java.util.concurrent.TimeUnit.SECONDS);
        log("PanelWatcher started");
    }

    private static List<Path> cmdFiles() {
        List<Path> out = new ArrayList<>();
        out.add(Paths.get("panel_cmd.txt"));
        String wq = System.getProperty("panel.webqueue", "");
        if (wq != null && !wq.isEmpty()) out.add(Paths.get(wq));
        // Doan hang doi web ke ben source: ../htdocs/sv1/panel_queue/panel_cmd.txt (tuy moi truong)
        String[] guesses = {
            "../htdocs/sv1/panel_queue/panel_cmd.txt",
            "../../htdocs/sv1/panel_queue/panel_cmd.txt",
            "data/panel_queue/panel_cmd.txt"
        };
        for (String g : guesses) out.add(Paths.get(g));
        return out;
    }

    private static void poll() {
        for (Path p : cmdFiles()) {
            if (!Files.isRegularFile(p)) continue;
            List<String> lines;
            try { lines = Files.readAllLines(p); } catch (Exception e) { continue; }
            if (lines.isEmpty()) continue;
            List<String> keep = new ArrayList<>();
            boolean changed = false;
            for (String raw : lines) {
                String line = raw == null ? "" : raw.trim();
                if (line.isEmpty()) { changed = true; continue; }
                String key = p.toString() + "|" + line;
                if (SEEN.contains(key)) { changed = true; continue; }
                SEEN.add(key);
                if (SEEN.size() > 5000) SEEN.clear();
                try { handle(line); } catch (Exception e) { log("lenh loi [" + line + "]: " + e.getMessage()); }
                appendDone(p, line);
                changed = true;
            }
            // Don file lenh da xu ly de khong phinh to
            if (changed) {
                try {
                    if (keep.isEmpty()) Files.write(p, new byte[0]);
                } catch (Exception ignored) {}
            }
        }
        writeBossStatus();
    }

    private static void appendDone(Path cmdFile, String line) {
        try {
            Path dir = cmdFile.toAbsolutePath().getParent();
            if (dir == null) dir = Paths.get(".");
            Files.write(dir.resolve("panel_cmd.done.log"),
                    (new Date() + " " + line + "\n").getBytes("UTF-8"),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    private static void handle(String line) {
        String[] a = line.split("\\|", -1);
        String cmd = a[0].trim().toUpperCase();
        switch (cmd) {
            case "PANEL_TEST": log("PANEL_TEST ok"); break;
            case "SET_EXP": {
                int v = a.length > 1 ? Integer.parseInt(a[1].trim()) : 10;
                if (v <= 0) v = 1; if (v > 127) v = 127;
                Manager.RATE_EXP_SERVER = (byte) v;
                writeStatus("panel_exp_status.txt", String.valueOf(v));
                setConfig("server.expserver", String.valueOf(v));
                log("SET_EXP x" + v);
                break;
            }
            case "SET_DROP": {
                int v = a.length > 1 ? Integer.parseInt(a[1].trim()) : 5;
                if (v <= 0) v = 1;
                writeStatus("panel_drop_status.txt", String.valueOf(v));
                setConfig("server.dropserver", String.valueOf(v));
                log("SET_DROP x" + v);
                break;
            }
            case "SET_EVENT": {
                int id = a.length > 1 ? Integer.parseInt(a[1].trim()) : 0;
                ServerManager.EVENT_SEVER = id;
                applyEventFlags(id);
                writeStatus("panel_event_status.json",
                        "{\"event_id\":" + id + ",\"enabled\":" + (id > 0 ? "true" : "false") + "}");
                setConfig("server.event", String.valueOf(id));
                try { setConfig("event", String.valueOf(id)); } catch (Exception ignored) {}
                log("SET_EVENT id=" + id);
                break;
            }
            case "BUFF_VND": {
                if (a.length < 3) break;
                buffVnd(a[1].trim(), Long.parseLong(a[2].trim()));
                break;
            }
            case "BUFF_ITEM": {
                // payload JSON co the chua ky tu '|' -> cat theo 2 dau '|' dau tien
                int p1 = line.indexOf('|'), p2 = line.indexOf('|', p1 + 1);
                if (p1 < 0 || p2 < 0) break;
                buffItem(line.substring(p1 + 1, p2).trim(), line.substring(p2 + 1));
                break;
            }
            case "KICK_PLAYER": {
                if (a.length < 2) break;
                kick(a[1].trim());
                break;
            }
            case "ADMIN_MODE": {
                boolean on = a.length > 1 && a[1].trim().equals("1");
                writeStatus("panel_admin_mode_status.txt", on ? "1" : "0");
                log("ADMIN_MODE " + (on ? "ON" : "OFF"));
                break;
            }
            case "BOT_PEM": {
                boolean on = a.length > 1 && a[1].trim().equals("1");
                writeStatus("panel_xnxx_status.txt", on ? "1" : "0");
                log("BOT_PEM " + (on ? "ON" : "OFF"));
                break;
            }
            case "BOSS_PERSIST_MODE": {
                boolean on = a.length > 1 && a[1].trim().equals("1");
                writeStatus("panel_boss_persist_status.txt", on ? "1" : "0");
                if (!on) { try { Files.deleteIfExists(Paths.get("panel_boss_overrides.properties")); } catch (Exception ignored) {} }
                log("BOSS_PERSIST " + (on ? "ON" : "OFF"));
                break;
            }
            case "EDIT_BOSS": {
                // Moi: EDIT_BOSS|bossId|mapId|zoneId|hp|dame|urlEncodedName (co dame)
                // Cu:  EDIT_BOSS|bossId|mapId|zoneId|hp|urlEncodedName (tuong thich nguoc)
                if (a.length < 6) break;
                if (a.length >= 7) {
                    editBoss(Integer.parseInt(a[1].trim()), Integer.parseInt(a[2].trim()),
                            Integer.parseInt(a[3].trim()), Long.parseLong(a[4].trim()),
                            Long.parseLong(a[5].trim()), decode(a[6]));
                } else {
                    editBoss(Integer.parseInt(a[1].trim()), Integer.parseInt(a[2].trim()),
                            Integer.parseInt(a[3].trim()), Long.parseLong(a[4].trim()), -1L, decode(a[5]));
                }
                break;
            }
            case "SPAWN_BOSS": {
                if (a.length < 2) break;
                int id = Integer.parseInt(a[1].trim());
                spawnBoss(id);
                break;
            }
            case "MAINTENANCE_NOW": {
                log("MAINTENANCE_NOW");
                try { Maintenance.gI().startSeconds(30); } catch (Throwable t) { log("maintenance: " + t.getMessage()); }
                break;
            }
            case "RELOAD_GIFTCODE": {
                reloadGiftcodes();
                break;
            }
            default: log("lenh la: " + cmd);
        }
    }

    // ---- Event: map id panel (0-8) -> flag EventManager ----
    private static void applyEventFlags(int id) {
        try {
            EventManager.LUNNAR_NEW_YEAR = (id == 1);
            EventManager.TRUNG_THU = (id == 2);
            EventManager.HALLOWEEN = (id == 3);
            EventManager.CHRISTMAS = (id == 4);
            EventManager.HUNG_VUONG = (id == 5);
            // id 6 (8/3-20/10): dung chung INTERNATIONAL_WOMANS_DAY
            EventManager.INTERNATIONAL_WOMANS_DAY = (id == 6);
            EventManager.TOP_UP = true;
        } catch (Throwable ignored) {}
    }

    private static void buffVnd(String charName, long amount) {
        if (charName.isEmpty() || amount <= 0) return;
        // 1) Cong truc tiep vao DB (co hieu luc ca khi offline)
        try (Connection con = LocalManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE account SET vnd = vnd + ? WHERE id = (SELECT account_id FROM player WHERE name = ? LIMIT 1)")) {
            ps.setLong(1, amount);
            ps.setString(2, charName);
            int n = ps.executeUpdate();
            log("BUFF_VND " + charName + " +" + amount + " rows=" + n);
        } catch (Exception e) { log("BUFF_VND loi DB: " + e.getMessage()); }
        // 2) Neu dang online: bao cho player biet
        try {
            Player p = Client.gI().getPlayer(charName);
            if (p != null) Service.gI().sendThongBao(p, "Admin vua cong +" + amount + " Ca. Kiem tra so du!");
        } catch (Throwable ignored) {}
    }

    // payload JSON: [{"temp_id":..,"quantity":..,"options":[{"id":..,"param":..}]}]
    private static void buffItem(String charName, String payload) {
        if (charName.isEmpty()) return;
        Player p = null;
        try { p = Client.gI().getPlayer(charName); } catch (Throwable ignored) {}
        List<ItemSpec> items = parseItems(payload);
        if (items.isEmpty()) { log("BUFF_ITEM rong"); return; }
        if (p == null) {
            // Offline: tao giftcode 1 lan de player nhap khi vao game
            String code = "PANEL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            try (Connection con = LocalManager.getConnection()) {
                long maxId = 0;
                try (PreparedStatement ps = con.prepareStatement("SELECT COALESCE(MAX(id),0) FROM giftcode");
                     ResultSet rs = ps.executeQuery()) { if (rs.next()) maxId = rs.getLong(1); }
                String detail = toDetailJson(items);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO giftcode (id, code, count_left, detail, expired, type) VALUES (?,?,?,?,?,?)")) {
                    ps.setLong(1, maxId + 1);
                    ps.setString(2, code);
                    ps.setInt(3, 1);
                    ps.setString(4, detail);
                    ps.setString(5, "2030-01-01 00:00:00");
                    ps.setInt(6, 0);
                    ps.executeUpdate();
                }
                try {
                    nro.models.player_system.GiftCode gc = new nro.models.player_system.GiftCode();
                    gc.id = (int) (maxId + 1);
                    gc.code = code;
                    gc.countLeft = 1;
                    gc.datecreate = new java.sql.Timestamp(System.currentTimeMillis());
                    gc.dateexpired = java.sql.Timestamp.valueOf("2030-01-01 00:00:00");
                    for (ItemSpec s : items) {
                        gc.detail.put(s.tempId, s.quantity);
                        java.util.ArrayList<ItemOption> ol = new java.util.ArrayList<>();
                        if (s.options != null) for (int[] op : s.options) ol.add(new ItemOption(op[0], op[1]));
                        gc.option.put(s.tempId, ol);
                    }
                    nro.models.managers.GiftCodeManager.gI().listGiftCode.add(gc);
                } catch (Throwable _g) { log("BUFF_ITEM memory add loi: " + _g.getMessage()); }
                writeStatus("panel_gift_result.txt", charName + "|" + code + "\n");
                log("BUFF_ITEM offline -> code " + code + " cho " + charName + " (nhap ngay, khong can restart)");
            } catch (Exception e) { log("BUFF_ITEM loi DB: " + e.getMessage()); }
            return;
        }
        int ok = 0;
        for (ItemSpec s : items) {
            try {
                Item item = ItemService.gI().createNewItem((short) s.tempId, s.quantity);
                if (s.options != null) {
                    for (int[] o : s.options) {
                        item.itemOptions.add(new ItemOption(o[0], o[1]));
                    }
                }
                if (InventoryService.gI().addItemBag(p, item)) ok++;
            } catch (Exception e) { log("BUFF_ITEM loi item " + s.tempId + ": " + e.getMessage()); }
        }
        try {
            InventoryService.gI().sendItemBags(p);
            Service.gI().sendThongBao(p, "Admin vua tang " + ok + " mon. Kiem tra hanh trang!");
        } catch (Throwable ignored) {}
        log("BUFF_ITEM online " + charName + " ok=" + ok + "/" + items.size());
    }

    private static void kick(String charName) {
        try {
            Player p = Client.gI().getPlayer(charName);
            if (p != null && p.getSession() != null) {
                Client.gI().kickSession(p.getSession());
                log("KICK " + charName);
            } else log("KICK: " + charName + " offline");
        } catch (Throwable t) { log("KICK loi: " + t.getMessage()); }
    }

    // FIX-2026-10-02: goi boss THAT ngay lap tuc (truoc day chi log).
    private static void spawnBoss(int bossId) {
        try {
            int removed = 0;
            for (Boss b : new ArrayList<>(BossManager.gI().getBosses())) {
                if (b != null && b.id == bossId) {
                    try { b.leaveMap(); } catch (Throwable ignored) {}
                    try { BossManager.gI().removeBoss(b); removed++; } catch (Throwable ignored) {}
                }
            }
            Boss nb = BossManager.gI().createBoss(bossId);
            if (nb == null) { log("SPAWN_BOSS id=" + bossId + " that bai (id khong ton tai trong BossManager)"); return; }
            applyStoredOverrides(nb);
            try { Service.gI().sendThongBaoAllPlayer("Boss " + nb.name + " vua xuat hien!"); } catch (Throwable ignored) {}
            writeBossStatus();
            log("SPAWN_BOSS id=" + bossId + " ok (xoa cu: " + removed + ")");
        } catch (Throwable e) { log("SPAWN_BOSS loi: " + e.getMessage()); }
    }

    // Ap dung cau hinh panel_boss_overrides.properties cho 1 boss (dung khi spawn + edit)
    private static void applyStoredOverrides(Boss b) {
        try {
            Path ov = Paths.get("panel_boss_overrides.properties");
            if (!Files.isRegularFile(ov)) return;
            String hp = null, dame = null, name = null;
            for (String ln : Files.readAllLines(ov)) {
                String s = ln.trim();
                if (s.startsWith(b.id + ".hp=")) hp = s.substring((b.id + ".hp=").length()).trim();
                else if (s.startsWith(b.id + ".dame=")) dame = s.substring((b.id + ".dame=").length()).trim();
                else if (s.startsWith(b.id + ".name=")) name = s.substring((b.id + ".name=").length()).trim();
            }
            if (hp != null) {
                long h = Long.parseLong(hp);
                try { b.nPoint.hpMax = (int) Math.min(h, 2000000000L); b.nPoint.hp = (int) Math.min(h, 2000000000L); } catch (Throwable ignored) {}
            }
            if (dame != null) {
                long d = Long.parseLong(dame);
                if (d >= 0) try { b.nPoint.dameg = (int) Math.min(d, 2000000000L); b.nPoint.dame = b.nPoint.dameg; } catch (Throwable ignored) {}
            }
            if (name != null && !name.isEmpty()) { try { b.name = name; } catch (Throwable ignored) {} }
        } catch (Throwable ignored) {}
    }

    private static void editBoss(int bossId, int mapId, int zoneId, long hp, long dame, String name) {
        int n = 0;
        try {
            for (Boss b : BossManager.gI().getBosses()) {
                if (b == null || b.id != bossId) continue;
                // map/zone check nhe (neu boss dang o map khac thi bo qua khi mapId >= 0)
                boolean mapOk = true;
                try {
                    if (mapId >= 0 && b.zone != null && b.zone.map != null && b.zone.map.mapId != mapId) mapOk = false;
                    if (zoneId >= 0 && b.zone != null && b.zone.zoneId != zoneId) mapOk = false;
                } catch (Throwable ignored) {}
                if (!mapOk) continue;
                try { b.nPoint.hpMax = (int) Math.min(hp, 2000000000L); b.nPoint.hp = (int) Math.min(hp, 2000000000L); } catch (Throwable t1) {
                    try {
                        java.lang.reflect.Field f = findField(b.nPoint.getClass(), "hpMax", "hpFull", "maxHp");
                        if (f != null) { f.setAccessible(true); f.setInt(b.nPoint, (int) Math.min(hp, 2000000000L)); }
                        java.lang.reflect.Field f2 = findField(b.nPoint.getClass(), "hp");
                        if (f2 != null) { f2.setAccessible(true); f2.setInt(b.nPoint, (int) Math.min(hp, 2000000000L)); }
                    } catch (Throwable ignored) {}
                }
                try { b.name = name; } catch (Throwable ignored) {}
                if (dame >= 0) {
                    try { b.nPoint.dameg = (int) Math.min(dame, 2000000000L); b.nPoint.dame = b.nPoint.dameg; } catch (Throwable t2) {
                        try {
                            java.lang.reflect.Field f = findField(b.nPoint.getClass(), "dameg", "dame");
                            if (f != null) { f.setAccessible(true); f.setInt(b.nPoint, (int) Math.min(dame, 2000000000L)); }
                        } catch (Throwable ignored) {}
                    }
                }
                try { Service.gI().sendThongBaoAllPlayer("Boss " + name + " vua duoc admin dieu chinh HP=" + hp); } catch (Throwable ignored) {}
                n++;
            }
            // Luu override de giu sau restart
            appendOverride(bossId + ".hp=" + hp + "\n" + bossId + ".dame=" + dame + "\n" + bossId + ".name=" + name.replace("\n", " ") + "\n");
            log("EDIT_BOSS id=" + bossId + " hp=" + hp + " dame=" + dame + " name=" + name + " matched=" + n);
        } catch (Throwable t) { log("EDIT_BOSS loi: " + t.getMessage()); }
    }

    private static java.lang.reflect.Field findField(Class<?> c, String... names) {
        for (String n : names) {
            try { return c.getField(n); } catch (Exception ignored) {}
            try { return c.getDeclaredField(n); } catch (Exception ignored) {}
        }
        return null;
    }

    // ---- file helpers ----
    private static void writeStatus(String name, String content) {
        try { Files.write(Paths.get(name), content.getBytes("UTF-8")); } catch (Exception ignored) {}
    }

    private static void appendOverride(String content) {
        try {
            Files.write(Paths.get("panel_boss_overrides.properties"), content.getBytes("UTF-8"),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    private static void setConfig(String key, String val) {
        // FIX-2026-10-02: game doc Config.properties (root), web viet data/config/config.properties -> ghi CA 2.
        String[] targets = {"Config.properties", "data/config/config.properties"};
        for (String tn : targets) {
            try {
                Path cfg = Paths.get(tn);
                if (tn.equals("data/config/config.properties") && !Files.isRegularFile(cfg)) continue;
                List<String> lines = Files.isRegularFile(cfg) ? Files.readAllLines(cfg) : new ArrayList<>();
                boolean found = false;
                for (int i = 0; i < lines.size(); i++) {
                    String t = lines.get(i).trim();
                    if (t.startsWith(key + "=") || t.startsWith(key + " =")) { lines.set(i, key + "=" + val); found = true; }
                }
                if (!found) lines.add(key + "=" + val);
                Files.write(cfg, lines);
            } catch (Exception e) { log("setConfig loi " + tn + ": " + e.getMessage()); }
        }
    }

    private static void writeBossStatus() {
        try {
            StringBuilder sb = new StringBuilder("{\"bosses\":[");
            boolean first = true;
            for (Boss b : BossManager.gI().getBosses()) {
                if (b == null) continue;
                if (!first) sb.append(',');
                first = false;
                long hp = 0, maxHp = 0, dame = 0;
                try { hp = b.nPoint.hp; maxHp = b.nPoint.hpMax; dame = b.nPoint.dameg; } catch (Throwable ignored) {}
                String state = "S\u1ED1ng";
                try { state = b.isDie() ? "Ch\u1EBFt" : "S\u1ED1ng"; } catch (Throwable ignored) {}
                int outfit0 = 0;
                try { outfit0 = b.getHead(); } catch (Throwable ignored) {}
                int mapId = -1, zoneId = -1;
                String mapName = "";
                try { if (b.zone != null) { zoneId = b.zone.zoneId; if (b.zone.map != null) { mapId = b.zone.map.mapId; mapName = b.zone.map.mapName; } } } catch (Throwable ignored) {}
                sb.append("{\"id\":").append(b.id)
                  .append(",\"name\":\"").append(esc(b.name))
                  .append("\",\"state\":\"").append(esc(state)).append("\",\"status\":\"").append(esc(state)).append("\",\"mapName\":\"").append(esc(mapName))
                  .append("\",\"mapId\":").append(mapId).append(",\"zoneId\":").append(zoneId)
                  .append(",\"hp\":").append(hp).append(",\"maxHp\":").append(maxHp).append(",\"dame\":").append(dame).append(",\"outfit0\":").append(outfit0).append(",\"avatar_id\":").append(outfit0).append("}");
            }
            sb.append("]}");
            Files.write(Paths.get("boss_status.json"), sb.toString().getBytes("UTF-8"));
        } catch (Throwable ignored) {}
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String decode(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static class ItemSpec { int tempId; int quantity; List<int[]> options; }

    private static void reloadGiftcodes() {
        // FIX-2026-10-02: nap code moi tu DB vao RAM de nhap ngay, khong can restart.
        try (Connection con = LocalManager.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM giftcode");
             ResultSet rs = ps.executeQuery()) {
            java.util.Set<Integer> have = new java.util.HashSet<>();
            for (nro.models.player_system.GiftCode g : nro.models.managers.GiftCodeManager.gI().listGiftCode) have.add(g.id);
            int n = 0;
            while (rs.next()) {
                int gid = rs.getInt("id");
                if (have.contains(gid)) continue;
                try {
                    nro.models.player_system.GiftCode gc = new nro.models.player_system.GiftCode();
                    gc.code = rs.getString("code");
                    gc.id = gid;
                    gc.countLeft = rs.getInt("count_left");
                    if (gc.countLeft == -1) gc.countLeft = 999999999;
                    try { gc.datecreate = rs.getTimestamp("datecreate"); } catch (Exception ignored) { gc.datecreate = new java.sql.Timestamp(System.currentTimeMillis()); }
                    try { gc.dateexpired = rs.getTimestamp("expired"); } catch (Exception ignored) { gc.dateexpired = java.sql.Timestamp.valueOf("2030-01-01 00:00:00"); }
                    String det = null;
                    try { det = rs.getString("detail"); } catch (Exception ignored) {}
                    if (det != null) {
                        org.json.simple.JSONArray jar = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(det);
                        if (jar != null) for (int i = 0; i < jar.size(); i++) {
                            org.json.simple.JSONObject jo = (org.json.simple.JSONObject) jar.get(i);
                            if (jo == null) continue;
                            Object _id = jo.get("id"); if (_id == null) _id = jo.get("temp_id"); if (_id == null) continue;
                            Object _q = jo.get("quantity"); if (_q == null) _q = jo.get("count"); if (_q == null) continue;
                            int iid, qty;
                            try { iid = Integer.parseInt(_id.toString()); qty = Integer.parseInt(_q.toString()); } catch (Exception e2) { continue; }
                            java.util.ArrayList<ItemOption> ol = new java.util.ArrayList<>();
                            Object _oo = jo.get("options");
                            if (_oo instanceof org.json.simple.JSONArray) {
                                org.json.simple.JSONArray arr = (org.json.simple.JSONArray) _oo;
                                for (int u = 0; u < arr.size(); u++) {
                                    org.json.simple.JSONObject oo = (org.json.simple.JSONObject) arr.get(u);
                                    if (oo == null || oo.get("id") == null || oo.get("param") == null) continue;
                                    try { ol.add(new ItemOption(Integer.parseInt(oo.get("id").toString()), Integer.parseInt(oo.get("param").toString()))); } catch (Exception e3) {}
                                }
                            }
                            gc.detail.put(iid, qty);
                            gc.option.put(iid, ol);
                        }
                    }
                    nro.models.managers.GiftCodeManager.gI().listGiftCode.add(gc);
                    n++;
                } catch (Exception e2) { log("reload 1 code loi: " + e2.getMessage()); }
            }
            log("RELOAD_GIFTCODE +" + n);
        } catch (Exception e) { log("RELOAD_GIFTCODE loi: " + e.getMessage()); }
    }

    private static List<ItemSpec> parseItems(String json) {
        List<ItemSpec> out = new ArrayList<>();
        try {
            String t = json.trim();
            // Parse don gian khong can lib: tim cac doi {temp_id, quantity, options[[id,param]..]}
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{[^\\{\\}]*?temp_id[^\\{\\}]*?\\}").matcher(t);
            while (m.find()) {
                String o = m.group();
                ItemSpec s = new ItemSpec();
                s.tempId = num(o, "temp_id"); s.quantity = Math.max(1, num(o, "quantity"));
                s.options = new ArrayList<>();
                java.util.regex.Matcher mo = java.util.regex.Pattern.compile("\\{[^{}]*?\"?id\"?\\s*:\\s*(\\d+)\\s*,\\s*\"?param\"?\\s*:\\s*(-?\\d+)").matcher(o);
                while (mo.find()) s.options.add(new int[]{Integer.parseInt(mo.group(1)), Integer.parseInt(mo.group(2))});
                if (s.tempId > 0) out.add(s);
            }
        } catch (Exception e) { log("parseItems loi: " + e.getMessage()); }
        return out;
    }

    private static int num(String o, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"?" + key + "\"?\\s*:\\s*(-?\\d+)").matcher(o);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String toDetailJson(List<ItemSpec> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            ItemSpec s = items.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"id\":").append(s.tempId).append(",\"quantity\":").append(s.quantity).append(",\"options\":[");
            for (int j = 0; j < s.options.size(); j++) {
                if (j > 0) sb.append(',');
                sb.append("{\"id\":").append(s.options.get(j)[0]).append(",\"param\":").append(s.options.get(j)[1]).append("}");
            }
            sb.append("]}");
        }
        return sb.append("]").toString();
    }

    private static void log(String msg) {
        try { Logger.log("PanelWatcher: " + msg + "\n"); } catch (Throwable t) { System.out.println("[PanelWatcher] " + msg); }
    }
}
