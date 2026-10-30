# Changelog — NewGenTeam

Tất cả thay đổi quan trọng của plugin NewGenTeam được ghi lại trong file này.
Định dạng dựa trên [Keep a Changelog](https://keepachangelog.com/) — tuân thủ [Semantic Versioning](https://semver.org/).

> Đợt rà soát bug & vá lỗi hiệu năng (A/B/C/D) đã được **gộp toàn bộ vào mục phát hành `[1.2.0]`**
> bên dưới. Bản trình bày chi tiết từng mục vẫn giữ tham chiếu trong **`BUGFIX-CHANGELOG.md`**.

## [1.2.0] — 2026-10-29 — Big update: Paper 26.3, Dialog API, lệnh quản trị & tối ưu 500 người chơi

### Nâng cấp nền tảng
- **Paper / Minecraft 26.3** — nâng `paper-api` từ `26.2.build.121-stable` lên `26.3.build.230-stable`.
- Phiên bản plugin: `1.1.0` → `1.2.0`. `api-version: '1.21'` giữ nguyên để tương thích cả dòng 1.21+ lẫn 26.x.
- Build kiểm chứng: `./gradlew build` **BUILD SUCCESSFUL**, đóng gói `NewGenTeam-1.2.0.jar` (shade + relocate dependencies).

### Dialog API cho người chơi 1.21.6+ (`DialogInputService`)
- Thay thế hack sign ảo bằng **hộp thoại gốc của Paper** (`io.papermc.paper.dialog.Dialog`): nhập text có tiêu đề, không đặt block giả, không cần packet restore, hoạt động cả khi đang mở inventory.
- **Nhận diện theo TỪNG người chơi (protocol routing)**: dialog packet chỉ tồn tại từ Minecraft **1.21.6** trở lên, nên plugin đọc **phiên bản client thật** của từng player qua **PacketEvents** (`PlayerManager.getClientVersion` so với `ClientVersion.V_1_21_6`):
  - Client **≥ 1.21.6** → hộp thoại gốc.
  - Client **< 1.21.6** (vd. người chơi 1.20 vào server 26.x qua ViaVersion) → **tự động lùi về fake-sign** (vẫn nhập được trên mọi protocol). Không có tình huống player bị kẹt vì không thấy ô nhập.
- **Cấu hình `settings.yml → gui.input-method`**: `AUTO` (mặc định, thông minh theo client) | `DIALOG` (ép dialog cho mọi người) | `SIGN` (luôn dùng sign giả). Đọc trực tiếp lúc reload, không cần khởi động lại.
- **Hộp thoại cao cấp**: có icon (sách), mô tả hướng dẫn, nhãn Confirm themed, tiêu đề lấy từ `lang` (vi/en). Toàn bộ ~14 điểm nhập liệu (tạo/đổi tên team, tag, tìm kiếm, bank, home, admin...) nâng cấp **minh bạch** qua một chốt duy nhất — không đổi call-site.
- **An toàn luồng & phiên**: callback submit và timeout dùng `AtomicBoolean` nên không bao giờ fire hai lần; đóng thoại bằng Escape được coi là huỷ (`null`), không rò rỉ session. Đầu vào được sanitize (64 ký tự, bỏ `§`/ký tự điều khiển). Server không có Dialog API → `AUTO`/`DIALOG` tự về sign.
- Kỹ thuật: `Dialog.create(...)` + `TextDialogInput` + `DialogBody.item/plainMessage` + `ActionButton`/`DialogAction.customClick(callback, options)` + `Audience.showDialog(DialogLike)`.

### Lệnh mới
- **`/newgenteam status`** (alias `/newgen status`) — **chỉ OP** (hoặc quyền `newgenteam.status`): báo cáo tài nguyên **chuẩn**:
  - CPU: `getProcessCpuLoad` / `getSystemCpuLoad` / `getSystemLoadAverage` / số nhân (HotSpot `com.sun.management`).
  - RAM: heap used/committed/max + non-heap (từ `MemoryMXBean`).
  - **Disk của plugin**: đi bộ `getDataFolder()` tính tổng byte + số file (chạy async, cache 10s) kèm dung lượng ổ trống/tổng.
  - **Đối tượng plugin đang giữ**: số team, số thành viên, số thành viên online.
  - **CPU time riêng của plugin**: tích luỹ thời gian các scheduled task (chest flush + sweep) qua `ResourceMetrics.measured(...)`.
  - Ghi rõ heap/CPU là toàn JVM (dùng chung), các chỉ số disk/object/task là của riêng plugin.
- **`/newgen info`** — thống kê công khai: **số team đang có trên server**, tổng thành viên, số online, trung bình thành viên/team, team lớn nhất.
- `reload` (admin) và `version` trên cùng lệnh gốc; tab-complete theo quyền.
- Đăng ký qua Brigadier lifecycle (`LifecycleEvents.COMMANDS`) như `/team`.
- **`status` hiển thị tỷ lệ plugin / tổng tài nguyên server** (bản cuối): CPU có thêm dòng `<plugin>` = phần CPU do scheduled-task của riêng plugin tiêu thụ (tính trên 1 nhân, tích lũy từ lúc bật); RAM hiển thị `%` dùng trên heap max và `%` trên **tổng RAM vật lý của server** (`getTotalMemorySize`) + dung lượng GB vật lý; Disk hiển thị `%` dung lượng data-folder plugin so với **tổng ổ đĩa**; thêm `uptime` của plugin. Helper mới `ResourceMetrics.percentOf(part,total)` + `gb()`.
- **Sửa gợi ý lệnh (tab-complete)**: `NewGenCommand` và `TeamCommand` trước đây bỏ sót trường hợp Brigadier truyền `args.length == 0` (bấm Tab ngay sau `/newgenteam ` hoặc `/team ` chưa gõ gì) → không hiện gì. Nay `args.length <= 1` đều liệt kê toàn bộ subcommand (vẫn lọc theo quyền).

### Tối ưu cho 500 người chơi
- **Gộp ghi kill/death (đóng A7 đã hoãn)**: `addKill`/`addDeath` chỉ tăng trong bộ nhớ và đánh dấu team “dirty”; một lần flush chu kỳ (30s) ghi gộp mỗi team **một lần** thay vì mỗi mạng. Dữ liệu vẫn đọc tức thì từ cache; `saveAll()` khi tắt sạch + executor storage được drain nên **không mất dữ liệu** (chỉ crash cứng trong 1 cửa sổ flush mới trễ).
- **Đường nóng `PlayerMoveEvent`**: thêm fast-path `hasAnyWarmup()` (O(1)) — khi không ai đang dịch chuyển home, bỏ hoàn toàn thao tác dò map per-player trên mỗi lần đổi block.
- **`/newgenteam status` không block main thread**: mọi đo lường nặng (đi bộ disk) chạy async, có cache TTL để OP spam lệnh không quét lại.
- Giữ nguyên các cache TTL 5s cho rank placeholder và bảng xếp hạng (đợt trước).

### Ngôn ngữ
- Thêm key `command.info.*`, `command.status.*`, `command.newgen-help`, `command.version` và `gui.dialog.*` (gồm `desc`) cho cả `vi_VN.yml` lẫn `en_US.yml`.
- `settings.yml` thêm mục `gui.input-method` (kèm chú thích song ngữ AUTO/DIALOG/SIGN).
- **Dọn ký tự unicode trang trí** trong `lang/*.yml` và `AdminService.java`: bỏ `█ ─ ✔ ✖ ← → ↑ ↓ ★ » ● ○ — •`, thay bằng nhãn chữ/ASCII gọn (`Back`, `Confirm`, `Cancel`, `ON`/`OFF`, `+`/`-`, `->`, `-`). Header lệnh chỉ còn tiêu đề chữ, không còn dải gạch trang trí. Dấu tiếng Việt và nội dung giữ nguyên.
- `en_US.yml` + `vi_VN.yml`: thêm `notify.tier-changed`, `team.list-truncated` (đợt rà soát bug).

### Vá lỗi & rà soát toàn diện (đợt A/B/C/D — gộp vào bản phát hành này)
> Chi tiết đầy đủ vẫn còn trong `BUGFIX-CHANGELOG.md`; tóm tắt dưới đây là toàn bộ thay đổi đã merge vào `1.2.0`.

**A — Hiệu năng / luồng (chống lag, tụt TPS)**
- **A1** `PlayerListMenu`: bỏ quét `getOfflinePlayers()` (đọc toàn bộ playerdata, rất nặng) → dùng `getOnlinePlayers()`.
- **A2** `PlaceholderHook.rankOf()`: thêm cache TTL 5s, không sort lại full danh sách mỗi lần placeholder được gọi.
- **A3** `StatsService`: snapshot cache per-`TopType` (TTL 5s) cho `top()`/`rank()`; `TopMenu` sửa bug bỏ qua tham số `type` (luôn dùng KDR) và thay `indexOf` O(n²) bằng `cachedRank`.
- **A4** Memo hoá `elements()` cho menu phân trang (`TeamListMenu`, `SearchResultMenu`, `JoinRequestMenu`, `MyInvitesMenu`, `AdminTeamListMenu`) — `refresh()` chỉ invalidate memo, không re-query/re-sort.
- **A6** `SearchManager.searchPlayers()`: thêm `break` khi đủ `MAX_RESULTS`, không duyệt hết mọi team.
- **A10** `ChatService`: fan-out chat team (từ `AsyncChatEvent`) gọi API Bukkit không thread-safe → gom UUID rồi đẩy toàn bộ vào `Schedulers.global(...)`.
- **A7** (đóng mục từng hoãn): gộp ghi kill/death — xem mục "Tối ưu cho 500 người chơi".

**B — Nghiêm trọng (race / mất dữ liệu / crash)**
- **B1** Reload race đọc từ thread async (`ConcurrentModificationException`) → chuyển sang **volatile-swap map bất biến** ở `LanguageManager`, `ThemeManager`; đánh dấu `volatile` toàn bộ field scalar + `FileConfiguration` trong `ConfigManager`.
- **B2** `ChatService.sendTeamMessage()` build Component trên main, `stripFormatting` áp cho cả tag lẫn tên team (chống chèn màu/MiniMessage), fan-out trong `Schedulers.global`; `notifyTeam()` cũng bọc global.
- **B3** `TierService.upgrade()` mất tiền khi team đã bị xoá → guard zombie-team (`teams.byId(id)!=team → FAILED`) trước khi trừ điểm; hoàn tiền fail thì log `severe` yêu cầu bồi thường.
- **B4** `AdminService.addMember()` không nguyên tử (đẩy player vào 2 team) → `putMemberIndexIfAbsent` + `synchronized(team)` kiểm tra lại + rollback index khi fail; `kick()`/`removeAllMembers()` dùng `removeMemberIndex(player, teamId)` có điều kiện.
- **B5** `TeamService`: thêm `putMemberIndexIfAbsent` (`putIfAbsent`) và overload `removeMemberIndex(player, teamId)` cho claim/release nguyên tử.
- **B6** `UpgradeMenu` IndexOutOfBounds → bỏ mảng cứng `TIER_SLOTS`/`BUTTON_SLOTS`, tính slot động theo số tier; `buildTierTile()` bỏ index `t.level-1`.
- **B7** `Inventories.fromBytes()` cấp mảng theo `len` đọc từ blob → guard `len < 0 || len > data.length` chống OOM/deserialization độc hại.
- **B8** `SignInputService.onPacketReceive()` xoá phiên trước null-check player (ghost sign) → đưa null-check lên trước để timeout còn khôi phục/huỷ.
- **B9** `SimpleDateFormat` (không thread-safe) dùng chung ở nhiều menu → thay bằng `DateTimeFormatter` bất biến: `StatsMenu`, `TeamDetailMenu`, `AdminTeamDetailMenu`, `AdminLogsMenu` (field `loaded` thành `volatile`).
- **B10** `TeamRole.defaultPermissions()` dùng `switch(ordinal)` + `default -> throw` → viết lại `switch(this)`, trả `Collections.unmodifiableSet(...)`.
- **B11** `RoleMenu` NPE khi viewer rời team (`team.member(viewer)` null) → null-check, nếu null mở lại Members menu.

**C — Logic / UX / bảo mật**
- **C1** Auto-flush rương chết: `ChestService.markDirty()` chưa từng được gọi → `InventoryListener` gọi sau click/drag hợp lệ, heartbeat `flushAll()` hoạt động trở lại.
- **C2** Setting `open`/`publicJoin` bị bỏ qua: `TeamService.requestJoin()` luôn tạo invite → thêm nhánh `joinDirect(...)` nguyên tử (putIfAbsent + synchronized + addMember + persist + `TeamJoinEvent`), trả `Result.JOINED`; caller `TeamDetailMenu`/`TeamListMenu` xử lý `case JOINED`.
- **C3** `TeamDetailMenu.back()` luôn về main → tôn trọng callback `onBack` nếu có.
- **C4** Chèn MiniMessage qua tag admin: `changeTag()` từ chối `<` `>`; `setTier()` gửi `notify.tier-changed`; `disband()`/`changeTag()` guard `byId!=team`; `rename()` tách `NOT_IN_TEAM` vs `NAME_INVALID`; `resetData()` bọc `balance(0)` trong `chestLock`.
- **C6** Quan hệ enemy/allied để request tồn → `setEnemy()` gọi `purgeRequests(...)` (xoá 2 chiều) trước khi set; thêm `clearTeam(teamId)` và `disbandTeam()` gọi `relations.clearTeam(...)`.
- **C7** `ChatService.toggle()` không nguyên tử → `if (toggled.remove(p)) return false; toggled.add(p); return true;`.
- **C8** `/team list` flood chat → sort theo size desc, giới hạn `LIST_LIMIT=50`, gửi `team.list-truncated` (shown/total) khi bị cắt.
- **C9** `Json.readHome()` trả `(0,0,0)` âm thầm khi blob hỏng → đọc x/y/z/yaw/pitch dạng double, trả null nếu NaN; `unescape()` viết 1 pass xử lý `\"` và `\\`.
- **C10** `InventoryListener` resync inventory khi click bị huỷ ở rương dưới → `Schedulers.entity(p, p::updateInventory)` chống desync.
- **C11** Dọn code chết / hardening: `AuditLogService` nhận `Logger` + log `WARNING` khi record/history/purge fail (bỏ overload chết); `ChestService` bỏ field `holders`; `ChestConfig` bỏ `roundToMultipleOfNine`; `Icons.disabledPage()` đổi `<muted>—` → `&8—`.

**D — Hardening / dọn dẹp**
- **D6** `PlayerPointsHook` dupe do tràn int → bỏ `clamp(long)`, thêm `fitsInt(long)`; `take()`/`give()` guard rồi mới cast `(int)`.
- **D9** Khoá chat với RECRUIT: đường gửi trực tiếp (`args.length >= 2`) chạy trước và không cần quyền `TOGGLE_TEAM_CHAT`; quyền chỉ guard nhánh bật/tắt.
- **D11** `RelationService` `clearTeam`/`purgeRequests` (đi kèm C6).

### Sửa lỗi nghiêm trọng (crash lúc startup)
- **Vỡ onEnable khi chưa cài PlaceholderAPI**: `new PlaceholderHook(...)` được gọi vô điều kiện, nhưng lớp này `extends` `PlaceholderExpansion` (chỉ có khi cài PlaceholderAPI). Server không có plugin → `NoClassDefFoundError` at `NewGenTeamPlugin.onEnable` → **plugin abort, không bật**. Nay chỉ chạm vào class khi `getPlugin("PlaceholderAPI") != null`; không có thì log `"PlaceholderAPI not installed - placeholders disabled."` và chạy tiếp bình thường. Đây là bẫy soft-dependency kinh điển ( superclass `compileOnly`).
- **Banner xanh NewGen Studio biến mất**: banner đặt ở cuối `onEnable`, nên khi crash xảy ra phía trên nó bị bỏ qua. Chuyển banner (`printStartupBanner()`) lên **ngay đầu `onEnable`** (sau `Schedulers.init`) để logo + Discord + Server luôn in, bất kể hook nào sau đó lỗi. Dòng `Database:` giữ cuối (cần biến local `dbSettings`).

### Đã biết (không ảnh hưởng)
- `TeleportFlag.EntityState.RETAIN_PASSENGERS` (HomeService) vẫn deprecated-for-removal trên 26.3, chưa có hằng thay thế — giữ nguyên vì vẫn đúng ngữ nghĩa teleport giữ hành khách.

---

## [1.1.0] — 2026-09-04

### Nâng cấp nền tảng
- **Paper / Minecraft 26.2** — nâng `paper-api` từ `1.21.4-R0.1-SNAPSHOT` lên `26.2.build.121-stable` (Mojang chuyển sang đánh số phiên bản theo năm từ 2026).
- **packetevents-spigot** `2.11.2` → `2.13.0`.
- **Java toolchain** `21` → `25` (Paper 26.2 yêu cầu JVM ≥ 25).
- **Gradle wrapper** `8.10.2` → `9.7.1`.
- **Shadow plugin** `8.3.5` → `9.6.1` (bản cũ không đọc được bytecode Java 25 / class major 69).
- Phiên bản plugin: `1.0.0` → `1.1.0`. `api-version: '1.21'` được giữ nguyên để tương thích cả dòng 1.21+ lẫn 26.x.

### Sign Input (tối ưu cho server 500+ người chơi)
- **Thêm timeout 60s cho mỗi phiên nhập sign**: nếu client không gửi `UPDATE_SIGN` (lag, mất gói tin, editor bỏ mở), phiên tự huỷ, block thật được khôi phục và callback nhận `null` — không còn session treo trong bộ nhớ.
- **Sửa ghost sign khi hủy**: `cancel()` trước đây chỉ xóa khỏi map mà không gửi block-change khôi phục, khiến sign ảo kẹt trên màn hình client. Nay luôn gửi restore cho player còn online; bỏ qua an toàn nếu player đã offline (an toàn trên Folia).
- **Chống race double-callback**: timeout và packet trả lời dùng `ConcurrentHashMap.remove(key, value)` nguyên tử — không bao giờ hai luồng cùng xử lý một phiên.
- **Sanitize đầu vào**: giới hạn tối đa 64 ký tự, loại ký tự `§` và ký tự điều khiển từ client độc hại.
- **Tối ưu hiệu năng**: globalId của `OAK_SIGN` được cache tính một lần (trước đây gọi `SpigotConversionUtil` mỗi lần mở sign); fast-path trong `onPacketReceive` thoát sớm trước khi parse wrapper, gần như zero-cost cho packet sign của người chơi thường.

### Sửa lỗi
- **Chặn inject màu vào team chat** (`ChatService`): người chơi gõ `&c`, `§a`, `&k`... trong tin nhắn team chat trước đây làm tô màu/obfuscate cả dòng chat. Đầu vào giờ được làm sạch mã màu legacy trước khi ghép vào format.
- **Xoá dead code**: bỏ `ChatInputService` (không được tham chiếu — dự án dùng `SignInputService`).

### Đã biết (không ảnh hưởng)
- `TeleportFlag.EntityState.RETAIN_PASSENGERS` (HomeService) bị đánh dấu deprecated-for-removal trên Paper 26.2 nhưng vẫn hoạt động và chưa có hằng thay thế — giữ nguyên vì vẫn là flag đúng cho teleport home team.

---

## [1.0.0] — Bản phát hành đầu tiên

### Phase 1-2 — Nền tảng
- Rebrand sang **NewGenTeam**; hệ thống theme (`theme.yml`) và ngôn ngữ (`lang/vi_VN.yml`, `lang/en_US.yml`) cho toàn bộ GUI và tin nhắn.
- GUI đầy đủ: menu chính, thành viên, mời, cài đặt, quyền, nâng cấp tier, bank, home, thống kê, top, tìm kiếm, danh sách team/người chơi, menu admin.

### Phase 3 — Tách cấu hình
- Tách config đơn thành nhiều file: `settings.yml`, `levels.yml`, `homes.yml`, `chest.yml`, `database.yml`; bỏ legacy `messages.yml`.

### Phase 4 — Nhiều loại cơ sở dữ liệu
- `JdbcStorage` hỗ trợ **SQLITE / MYSQL / MARIADB / POSTGRESQL** qua `database.yml` (HikariCP).
- Shade driver MariaDB + PostgreSQL; bỏ `SqliteStorage` cũ.

### Phase 5 — Layout menu tuỳ biến
- `MenuLayoutManager` + file `menus/*.yml`: tuỳ biến slot/material/glow/custom-model-data/enable từng nút trong menu (bắt đầu với `main.yml`).

### Tính năng chính
- Team: tạo/giải tán/đổi tên, mời & duyệt đơn vào team, phân quyền theo role, quyền chi tiết per-role.
- Team chest nhiều trang theo tier, team bank, named homes + warmup/cooldown teleport, quan hệ đồng minh/kẻ thù, audit log, thống kê kill/death.
- Soft dependencies: Vault, PlayerPoints, PlaceholderAPI. Hỗ trợ **Paper + Folia**.
