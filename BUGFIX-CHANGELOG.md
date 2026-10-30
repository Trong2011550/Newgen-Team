# BUGFIX CHANGELOG — Đợt rà soát bug & tối ưu hiệu năng

> File này **riêng biệt** với `CHANGELOG.md` (lịch sử phát hành v1.0.0/v1.1.0).
> Ghi lại toàn bộ sửa lỗi từ đợt "phân tích toàn bộ dự án, tìm bug gây lag".
> Trạng thái build: **compileJava BUILD SUCCESSFUL** (Java 25, Paper 26.2). Chỉ còn 2 cảnh báo
> deprecation có sẵn ở `HomeService.java` (không liên quan đợt sửa này).

Phân loại mức độ:
- **A** — Nóng: gây lag / tụt TPS / chạy sai luồng (thread).
- **B** — Nghiêm trọng: race condition, mất dữ liệu / mất tiền, crash.
- **C** — Logic / UX / bảo mật nhẹ.
- **D** — Dọn dẹp, hardening, code chết.

---

## A — Hiệu năng & luồng thực thi (lag / TPS)

- **A1 — `PlayerListMenu`** duyệt `Bukkit.getOfflinePlayers()` (quét toàn bộ world/playerdata, rất nặng)
  → chuyển sang `Bukkit.getOnlinePlayers()`. Sort đơn giản hoá với
  `Comparator.comparing((OfflinePlayer p) -> p.getName(), String.CASE_INSENSITIVE_ORDER)`.
- **A2 — `PlaceholderHook.rankOf()`** tính lại thứ hạng (sort full danh sách) **mỗi lần** placeholder
  được gọi → thêm cache TTL 5s (`rankCache` volatile + `rankCacheAt`), chỉ build list + map hạng 1 lần/5s.
- **A3 — `StatsService`** sort lại toàn bộ bảng xếp hạng mỗi lần gọi `top()`/`rank()` → viết lại với
  cache snapshot per-`TopType` (`ConcurrentHashMap<TopType, Snapshot>`, TTL 5s). `Snapshot` giữ list đã sort
  + map hạng. `TopMenu` trước đây **bỏ qua** tham số `type` (luôn dùng KDR) → đã lưu `type` thật và dùng
  `cachedRank` thay vì `indexOf` (O(n²)).
- **A4 — Memo hoá `elements()`** cho các menu phân trang để không query/sort lại khi chỉ `refresh()`:
  `TeamListMenu`, `SearchResultMenu`, `JoinRequestMenu`, `MyInvitesMenu`, `admin/AdminTeamListMenu`
  (thêm `memo` + `refresh(){ memo=null; super.refresh(); }`).
- **A6 — `SearchManager.searchPlayers()`** thiếu chặn ở vòng lặp team ngoài → thêm
  `if (hits.size() >= MAX_RESULTS) break;` tránh duyệt hết tất cả team khi đã đủ kết quả.
- **A10 — `ChatService`** fan-out tin nhắn team chạy trên thread async/netty (từ `AsyncChatEvent`) gọi
  `Bukkit.getPlayer`/`sendMessage` → gom UUID người nhận rồi đẩy toàn bộ fan-out vào `Schedulers.global(...)`.

## B — Nghiêm trọng (race / mất dữ liệu / crash)

- **B1 — Reload race khi đọc từ thread async.** `/team reload` (main thread) sửa map trong khi
  `AsyncChatEvent` đọc → `ConcurrentModificationException`. Đã đổi sang **volatile-swap map bất biến**:
  - `LanguageManager`: `messages`/`fallback` → `volatile Map`, `load()` build map cục bộ rồi publish
    `Collections.unmodifiableMap(...)` nguyên tử, set `language` cuối cùng.
  - `ThemeManager`: `tokens` → `volatile Map`, `load()` build `next` rồi gán 1 lần.
  - `ConfigManager`: đánh dấu `volatile` cho **toàn bộ** field scalar + FileConfiguration
    (`database`/`hooks`/`logs`).
- **B2 — `ChatService.sendTeamMessage()`** build Component trên main thread, `stripFormatting` áp cho cả
  tag **và** tên team (chống chèn màu/MiniMessage), rồi mới fan-out trong `Schedulers.global`.
  `notifyTeam()` cũng bọc vòng lặp trong `Schedulers.global`.
- **B3 — `TierService.upgrade()` mất tiền khi team đã bị xoá.** Thêm guard zombie-team
  `if (teams != null && teams.byId(team.id()) != team) return Result.FAILED;` **trước** khi trừ điểm.
  Nhánh hoàn tiền: nếu hoàn tiền cũng fail → log `severe` yêu cầu bồi thường thủ công (không im lặng).
- **B4 — `AdminService.addMember()` không nguyên tử** (có thể đẩy player vào 2 team). Viết lại:
  `putMemberIndexIfAbsent` trước, sau đó `synchronized(team)` kiểm tra lại `byId==team`, `isMember`,
  giới hạn thành viên — rollback index (`removeMemberIndex(target, team.id())`) nếu fail.
  `kick()`/`removeAllMembers()` dùng `removeMemberIndex(player, teamId)` có điều kiện.
- **B5 — `TeamService`** thêm `putMemberIndexIfAbsent` (`putIfAbsent`) và overload
  `removeMemberIndex(player, teamId)` (`remove(k,v)`) cho claim/release nguyên tử.
- **B6 — `UpgradeMenu` IndexOutOfBounds.** Bỏ mảng cứng `TIER_SLOTS`/`BUTTON_SLOTS`; tính slot động theo
  số tier (`spacing`, `row`, `break` khi vượt 25/44). `buildTierTile()` bỏ index `t.level-1` (nguồn AIOOBE).
- **B7 — `Inventories.fromBytes()`** cấp phát mảng theo `len` đọc từ blob không kiểm tra → thêm
  guard `if (len < 0 || len > data.length) throw ...` chống OOM/deserialization độc hại.
- **B8 — `SignInputService.onPacketReceive()`** xoá phiên (`pending.remove`) **trước** khi null-check player
  → nếu player null, phiên bị mất (ghost sign). Đã đưa null-check lên trước để timeout còn khôi phục/huỷ.
- **B9 — `SimpleDateFormat` (không thread-safe) dùng chung/static** ở nhiều menu → thay bằng
  `DateTimeFormatter` bất biến: `StatsMenu` (yyyy-MM-dd), `TeamDetailMenu` (dd/MM/yyyy),
  `admin/AdminTeamDetailMenu` (dd/MM/yyyy HH:mm), `admin/AdminLogsMenu` (dd/MM HH:mm:ss; field `loaded`
  chuyển `volatile`).
- **B10 — `TeamRole.defaultPermissions()`** dùng `switch(ordinal)` + `default -> throw MatchException`
  (dễ crash, trả set có thể bị sửa). Viết lại thành `switch(this)` theo hằng enum, trả
  `Collections.unmodifiableSet(...)`.
- **B11 — `RoleMenu`** gọi `team.member(viewer).role()` không null-check → NPE nếu viewer rời team.
  Đã null-check `self`, nếu null thì mở lại Members menu.

## C — Logic / UX / bảo mật

- **C1 — Auto-flush rương chết.** `ChestService.markDirty()` không được gọi ở đâu → rương đang mở không
  được lưu định kỳ. `InventoryListener`: gọi `markDirty(team)` sau click/drag hợp lệ (`!isCancelled()`),
  nối lại heartbeat `flushAll()`.
- **C2 — Setting `open`/`publicJoin` bị bỏ qua.** `TeamService.requestJoin()` luôn tạo invite. Thêm nhánh
  `if (team.settings().open || team.settings().publicJoin) return joinDirect(...)` với `joinDirect()`
  nguyên tử (putIfAbsent + synchronized + addMember + persist + `TeamJoinEvent`), trả `Result.JOINED`.
  Caller (`TeamDetailMenu`, `TeamListMenu`) thêm `case JOINED` (thành công + notify `notify.joined`).
- **C3 — `TeamDetailMenu.back()`** luôn về main, bỏ qua callback → tôn trọng `onBack` nếu có.
- **C4 — Chèn MiniMessage qua tag admin.** `AdminService.changeTag()` từ chối ký tự `<` `>`;
  `setTier()` gửi `notify.tier-changed`; `disband()`/`changeTag()` thêm guard `byId!=team`.
  `AdminService.rename()` tách thông báo `NOT_IN_TEAM` vs `NAME_INVALID`. `resetData()` bọc
  `team.balance(0)` trong `chestLock`.
- **C6 — Quan hệ enemy/allied còn request tồn.** `RelationService.setEnemy()` gọi `purgeRequests(...)`
  (xoá cả 2 chiều) trước khi set ENEMY; thêm `clearTeam(teamId)` — và `TeamService.disbandTeam()` gọi
  `relations.clearTeam(...)` (dây `setRelations` trong `NewGenTeamPlugin`).
- **C7 — `ChatService.toggle()` không nguyên tử** → viết lại `if (toggled.remove(p)) return false; toggled.add(p); return true;`.
- **C8 — `/team list` flood chat.** `TeamCommand.handleList()` sort theo size desc, giới hạn `LIST_LIMIT=50`,
  gửi `team.list-truncated` (shown/total) khi bị cắt.
- **C9 — `Json.readHome()`** trả `(0,0,0)` âm thầm khi blob hỏng → đọc x/y/z/yaw/pitch dạng double,
  `num()` trả `Double.NaN` khi thiếu/lỗi, trả null nếu NaN. `unescape()` viết lại 1 pass trái-phải
  (StringBuilder) xử lý `\"` và `\\`.
- **C10 — `InventoryListener`** resync inventory khi click bị huỷ ở rương dưới (bottom):
  `Schedulers.entity(p, p::updateInventory)` chống desync item.
- **C11 — Dọn code chết / hardening:**
  - `AuditLogService`: nhận `Logger`, log `WARNING` khi record/history/purge fail (không nuốt im lặng);
    bỏ overload chết `history(UUID, Consumer)`.
  - `ChestService`: bỏ field chết `holders`.
  - `ChestConfig`: bỏ method chết `roundToMultipleOfNine`.
  - `Icons.disabledPage()`: đổi `<muted>—` → `&8—` (factory này đi ngoài ThemeManager nên dùng legacy code).

## D — Hardening / dọn dẹp

- **D6 — `PlayerPointsHook` dupe do tràn int.** Bỏ `clamp(long)`; thêm `fitsInt(long)`
  (`amount > 0 && <= Integer.MAX_VALUE`); `take()`/`give()` guard rồi mới cast `(int)`.
- **D9 — Khoá chat với RECRUIT.** `TeamCommand.handleChat()`: đường gửi trực tiếp (`args.length >= 2`)
  chạy **trước** và không cần quyền TOGGLE_TEAM_CHAT; quyền chỉ còn guard nhánh bật/tắt.
- **D11 — `RelationService`** (xem C6) `clearTeam`/`purgeRequests`.

## Lang bổ sung

- `en_US.yml` + `vi_VN.yml`: thêm khoá `notify.tier-changed` và `team.list-truncated`.

---

## Tạm hoãn (cần thiết kế thêm, rủi ro cao nếu sửa vội)

- **A7 — Throttle ghi kill/death.** Hiện `StatsService.addKill/addDeath` persist **mỗi** sự kiện.
  Chưa gộp batch vì cần thiết kế flush (mất dữ liệu nếu crash trước flush) — giữ nguyên để an toàn.
- **A8 / A9 — Serialization / YAML trên main thread.** Một số đường ghi config/save chạy main thread;
  chuyển hết sang async cần rà soát vòng đời reload + shutdown, để đợt sau.
- **C5.2 — Slot cứng biến mất dưới layout tuỳ chỉnh kích thước khác.** `MenuLayoutManager` cho phép size
  tuỳ ý; vài menu còn giả định 54 slot. Cần chuẩn hoá layout-driven toàn bộ GUI.
