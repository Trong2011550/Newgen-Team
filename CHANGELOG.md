# Changelog — NewGenTeam

Tất cả thay đổi quan trọng của plugin NewGenTeam được ghi lại trong file này.
Định dạng dựa trên [Keep a Changelog](https://keepachangelog.com/) — tuân thủ [Semantic Versioning](https://semver.org/).

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
